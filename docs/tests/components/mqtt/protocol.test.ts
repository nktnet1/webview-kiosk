import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  type MqttMessage,
  MqttWebSocketClient,
} from "../../../src/components/mqtt/protocol";

const CONNECT_OPTIONS = {
  brokerUrl: "wss://broker.example.com/mqtt",
  clientId: "docs-test",
  username: "u",
  password: "p",
  keepAlive: 60,
};

class FakeWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 3;
  static instances: FakeWebSocket[] = [];
  readonly frames: Uint8Array[] = [];
  readonly url: string;
  readonly protocols: string[];
  readyState = FakeWebSocket.CONNECTING;
  binaryType = "blob";
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;

  constructor(url: string, protocols: string[]) {
    this.url = url;
    this.protocols = protocols;
    FakeWebSocket.instances.push(this);
  }
  open() {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.();
  }
  send(data: BufferSource) {
    this.frames.push(Uint8Array.from(new Uint8Array(data as ArrayBuffer)));
  }
  receive(packet: Uint8Array) {
    this.onmessage?.({ data: Uint8Array.from(packet).buffer } as MessageEvent);
  }
  close() {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.();
  }
}

function body(frame: Uint8Array): Uint8Array {
  let multiplier = 1;
  let length = 0;
  let pos = 1;
  for (;;) {
    const byte = frame[pos++];
    length += (byte & 127) * multiplier;
    if (!(byte & 128)) break;
    multiplier *= 128;
  }
  return frame.subarray(pos, pos + length);
}

function utf(value: string): Uint8Array {
  const raw = new TextEncoder().encode(value);
  return Uint8Array.of(raw.length >> 8, raw.length & 255, ...raw);
}

function frame(header: number, ...data: number[]): Uint8Array {
  return Uint8Array.of(header, data.length, ...data);
}

function lastFrame(socket: FakeWebSocket): Uint8Array {
  const data = socket.frames.at(-1);
  if (!data) throw new Error("No outgoing packet.");
  return data;
}

function lastSocket(): FakeWebSocket {
  const socket = FakeWebSocket.instances.at(-1);
  if (!socket) throw new Error("Missing WebSocket instance.");
  return socket;
}

async function connected(options = CONNECT_OPTIONS) {
  const statuses: string[] = [];
  const messages: MqttMessage[] = [];
  const client = new MqttWebSocketClient(
    options,
    (status) => statuses.push(status),
    (payload) => messages.push(payload),
  );
  const pending = client.connect();
  const socket = lastSocket();
  socket.open();
  socket.receive(frame(0x20, 0, 0, 0)); // MQTT 5 CONNACK success
  await pending;
  return { client, socket, statuses, messages };
}

beforeEach(() => {
  FakeWebSocket.instances = [];
  vi.stubGlobal("WebSocket", FakeWebSocket);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("MQTT 5 WebSocket transport", () => {
  it("connects with MQTT 5, WebSocket subprotocol, clean start, and credentials", async () => {
    const { client, socket, statuses } = await connected();
    expect(socket.url).toBe("wss://broker.example.com/mqtt");
    expect(socket.protocols).toEqual(["mqtt"]);
    expect(socket.binaryType).toBe("arraybuffer");
    expect(socket.frames[0][0]).toBe(0x10);
    const connect = body(socket.frames[0]);
    expect(connect.subarray(0, 6)).toEqual(utf("MQTT"));
    expect(connect[6]).toBe(5);
    expect(connect[7]).toBe(0xc2); // username + password + clean start
    expect(connect[8]).toBe(0);
    expect(connect[9]).toBe(60);
    expect(connect[10]).toBe(0); // no MQTT 5 properties
    expect(new TextDecoder().decode(connect)).toContain("docs-test");
    expect(statuses).toEqual(["connecting", "connected"]);
    client.disconnect();
    expect(socket.frames.at(-1)).toEqual(Uint8Array.of(0xe0, 2, 0, 0));
    expect(statuses.at(-1)).toBe("disconnected");
  });

  it("connects using a normalized WebSocket URL from a bare broker address", async () => {
    const { client, socket } = await connected({
      ...CONNECT_OPTIONS,
      brokerUrl: "broker.example.com:9443/mqtt",
    });
    expect(socket.url).toBe("wss://broker.example.com:9443/mqtt");
    client.disconnect();
  });

  it("rejects failed CONNACK with broker reason code", async () => {
    const client = new MqttWebSocketClient(
      CONNECT_OPTIONS,
      () => {},
      () => {},
    );
    const connect = client.connect();
    const socket = lastSocket();
    socket.open();
    socket.receive(frame(0x20, 0, 0x87, 0));
    await expect(connect).rejects.toThrow(/0x87/);
    expect(socket.readyState).toBe(FakeWebSocket.CLOSED);
  });

  it("rejects WebSocket constructor errors instead of hanging on connect", async () => {
    class ThrowingWebSocket {
      constructor() {
        throw new Error("Blocked by CSP");
      }
    }
    vi.stubGlobal("WebSocket", ThrowingWebSocket);
    const statuses: string[] = [];
    const client = new MqttWebSocketClient(
      CONNECT_OPTIONS,
      (status) => statuses.push(status),
      () => {},
    );
    await expect(client.connect()).rejects.toThrow(/Blocked by CSP/);
    expect(statuses).toEqual(["connecting", "disconnected"]);
  });

  it("subscribes/unsubscribes using MQTT 5 acknowledgements", async () => {
    const { client, socket } = await connected();
    const subPromise = client.subscribe("wk/response/#", 1);
    const sub = lastFrame(socket);
    expect(sub[0]).toBe(0x82);
    expect(new TextDecoder().decode(body(sub))).toContain("wk/response/#");
    expect(body(sub)[2]).toBe(0); // properties length
    const [hi, lo] = body(sub);
    socket.receive(frame(0x90, hi, lo, 0, 1));
    await subPromise;

    const unsubPromise = client.unsubscribe("wk/response/#");
    const unsub = lastFrame(socket);
    expect(unsub[0]).toBe(0xa2);
    const [uHi, uLo] = body(unsub);
    socket.receive(frame(0xb0, uHi, uLo, 0, 0));
    await unsubPromise;
    client.disconnect();
  });

  it("publishes with QoS 1 and MQTT 5 response metadata and waits for PUBACK", async () => {
    const { client, socket } = await connected();
    const sending = client.publish(
      "wk/request",
      '{"requestType":"get_status"}',
      {
        qos: 1,
        retain: false,
        responseTopic: "wk/debugger/reply",
        correlationData: "request-123",
      },
    );
    const published = lastFrame(socket);
    expect(published[0]).toBe(0x32);
    const packetData = body(published);
    expect(new TextDecoder().decode(packetData)).toContain("wk/debugger/reply");
    expect(new TextDecoder().decode(packetData)).toContain("request-123");
    expect(new TextDecoder().decode(packetData)).toContain("get_status");
    const offset = utf("wk/request").length;
    socket.receive(frame(0x40, packetData[offset], packetData[offset + 1]));
    await sending;
    client.disconnect();
  });

  it("completes QoS 2 publish with PUBREC, PUBREL, and PUBCOMP", async () => {
    const { client, socket } = await connected();
    const sending = client.publish("wk/settings", '{"update":true}', {
      qos: 2,
      retain: false,
    });
    const publish = lastFrame(socket);
    expect(publish[0]).toBe(0x34);
    const bytes = body(publish);
    const offset = utf("wk/settings").length;
    const id = [bytes[offset], bytes[offset + 1]];
    expect(id).not.toEqual([0, 0]);

    let resolved = false;
    void sending.then(() => {
      resolved = true;
    });
    socket.receive(frame(0x50, ...id)); // PUBREC
    await vi.waitFor(() =>
      expect(lastFrame(socket)).toEqual(frame(0x62, ...id)),
    );
    expect(resolved).toBe(false);
    socket.receive(frame(0x70, ...id)); // PUBCOMP
    await sending;
    expect(resolved).toBe(true);
    client.disconnect();
  });

  it("rejects QoS 2 publish if PUBREC has a failure reason", async () => {
    const { client, socket } = await connected();
    const sending = client.publish("wk/settings", "{}", {
      qos: 2,
      retain: false,
    });
    const bytes = body(lastFrame(socket));
    const offset = utf("wk/settings").length;
    socket.receive(frame(0x50, bytes[offset], bytes[offset + 1], 0x87, 0));
    await expect(sending).rejects.toThrow(/0x87/);
    expect(lastFrame(socket)[0]).toBe(0x34); // no PUBREL
    client.disconnect();
  });

  it("delivers inbound QoS 2 once and completes the broker handshake", async () => {
    const { client, socket, messages } = await connected();
    const bytes = Uint8Array.of(
      ...utf("wk/event/test"),
      0,
      9,
      0, // zero MQTT 5 PUBLISH properties
      ...new TextEncoder().encode('{"message":"once"}'),
    );
    const publish = frame(0x34, ...bytes);
    socket.receive(publish);
    await vi.waitFor(() => expect(messages).toHaveLength(1));
    expect(messages[0].qos).toBe(2);
    expect(lastFrame(socket)).toEqual(frame(0x50, 0, 9)); // PUBREC
    socket.receive(publish); // duplicate packet: acknowledge but don't deliver again
    await vi.waitFor(() =>
      expect(socket.frames.slice(-2)).toEqual([
        frame(0x50, 0, 9),
        frame(0x50, 0, 9),
      ]),
    );
    expect(messages).toHaveLength(1);
    socket.receive(frame(0x62, 0, 9)); // PUBREL
    await vi.waitFor(() =>
      expect(lastFrame(socket)).toEqual(frame(0x70, 0, 9)),
    );
    expect(messages).toHaveLength(1);
    client.disconnect();
  });

  it("sends unacknowledged QoS 0 and rejects a negative QoS 1 PUBACK", async () => {
    const { client, socket } = await connected();
    await client.publish("wk/command", '{"command":"go_home"}', {
      qos: 0,
      retain: true,
    });
    expect(socket.frames.at(-1)?.[0]).toBe(0x31);

    const send = client.publish("wk/command", "{}", { qos: 1, retain: false });
    const msg = body(lastFrame(socket));
    const offset = utf("wk/command").length;
    socket.receive(frame(0x40, msg[offset], msg[offset + 1], 0x87, 0));
    await expect(send).rejects.toThrow(/0x87/);
    client.disconnect();
  });

  it("reassembles WebSocket chunks and PUBACKs inbound QoS 1", async () => {
    const { client, socket, messages } = await connected();
    const publish = Uint8Array.of(
      ...utf("wk/event/test"),
      0,
      7,
      0,
      ...new TextEncoder().encode('{"eventType":"test"}'),
    );
    const frameBytes = frame(0x33, ...publish);
    socket.receive(frameBytes.subarray(0, 5));
    socket.receive(frameBytes.subarray(5));
    await vi.waitFor(() => expect(messages).toHaveLength(1));
    expect(messages[0]).toEqual({
      topic: "wk/event/test",
      payload: '{"eventType":"test"}',
      qos: 1,
      retain: true,
    });
    expect(socket.frames.at(-1)).toEqual(frame(0x40, 0, 7));
    client.disconnect();
  });

  it("stops operations when disconnected and rejects malformed topic filters", async () => {
    const { client, socket, statuses } = await connected();
    expect(() => client.subscribe("wk/#/event", 0)).toThrow(/topic filter/);
    const pending = client.subscribe("wk/response/#", 0);
    socket.close();
    await expect(pending).rejects.toThrow(/closed/);
    expect(statuses.at(-1)).toBe("disconnected");
    expect(() =>
      client.publish("wk/command", "{}", { qos: 0, retain: false }),
    ).toThrow(/Connect/);
  });

  it("rejects oversized incoming packets instead of buffering them", async () => {
    const errors: string[] = [];
    const client = new MqttWebSocketClient(
      CONNECT_OPTIONS,
      (_, error) => {
        if (error) errors.push(error);
      },
      () => {},
    );
    const ready = client.connect();
    const socket = lastSocket();
    socket.open();
    socket.receive(frame(0x20, 0, 0, 0));
    await ready;
    socket.receive(Uint8Array.of(0x30, 0xff, 0xff, 0x7f));
    await vi.waitFor(() => expect(errors).toHaveLength(1));
    expect(errors[0]).toMatch(/oversized/);
    expect(socket.readyState).toBe(FakeWebSocket.CLOSED);
  });
});
