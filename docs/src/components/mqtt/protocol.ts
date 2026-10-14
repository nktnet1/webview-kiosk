/** Small browser MQTT 5 WebSocket client for the documentation debugger.
 * Supports QoS 0/1/2 publish and subscribe, acknowledgements, pings and MQTT 5
 * response-topic/correlation-data properties. No TCP access or auto-reconnect.
 */
import { type MqttQos, validateBrokerUrl, validateTopic } from "./validation";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const MAX_PACKET_BYTES = 1024 * 1024;
const ACK_TIMEOUT_MS = 15_000;

export type MqttConnection = {
  brokerUrl: string;
  clientId: string;
  username: string;
  password: string;
  keepAlive: number;
};

export type MqttMessage = {
  topic: string;
  payload: string;
  qos: MqttQos;
  retain: boolean;
};

export type PublishOptions = {
  qos: MqttQos;
  retain: boolean;
  responseTopic?: string;
  correlationData?: string;
};

export type MqttConnectionStatus = "connecting" | "connected" | "disconnected";

type PendingOperation = {
  packetType: number;
  resolve: () => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

function bytes(...parts: Uint8Array[]): Uint8Array {
  const result = new Uint8Array(
    parts.reduce((total, part) => total + part.length, 0),
  );
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}

function short(value: number): Uint8Array {
  return Uint8Array.of((value >> 8) & 0xff, value & 0xff);
}

function variable(value: number): Uint8Array {
  if (!Number.isInteger(value) || value < 0 || value > 268_435_455) {
    throw new Error("Invalid MQTT variable integer.");
  }
  const result: number[] = [];
  do {
    let digit = value % 128;
    value = Math.floor(value / 128);
    if (value > 0) digit |= 0x80;
    result.push(digit);
  } while (value > 0);
  return Uint8Array.from(result);
}

function readVariable(
  data: Uint8Array,
  start: number,
): { value: number; end: number } | null {
  let value = 0;
  let multiplier = 1;
  for (let index = start; index < start + 4; index++) {
    if (index >= data.length) return null;
    const octet = data[index];
    value += (octet & 0x7f) * multiplier;
    if ((octet & 0x80) === 0) return { value, end: index + 1 };
    multiplier *= 128;
  }
  throw new Error("Malformed MQTT variable integer.");
}

function utf(value: string): Uint8Array {
  if (value.includes("\0")) throw new Error("MQTT strings cannot contain NUL.");
  const data = encoder.encode(value);
  if (data.length > 65_535) throw new Error("MQTT string is too long.");
  return bytes(short(data.length), data);
}

function readUtf(
  data: Uint8Array,
  start: number,
): { text: string; end: number } {
  if (start + 2 > data.length) throw new Error("Truncated MQTT string.");
  const size = data[start] * 256 + data[start + 1];
  const end = start + 2 + size;
  if (end > data.length) throw new Error("Truncated MQTT string.");
  return { text: decoder.decode(data.subarray(start + 2, end)), end };
}

function packet(header: number, body: Uint8Array): Uint8Array {
  if (body.length > MAX_PACKET_BYTES)
    throw new Error("MQTT packet is too large.");
  return bytes(Uint8Array.of(header), variable(body.length), body);
}

function mqttError(value: unknown): Error {
  return value instanceof Error ? value : new Error("MQTT connection failed.");
}

function reasonText(code: number): string {
  return `MQTT reason code 0x${code.toString(16).padStart(2, "0")}`;
}

export class MqttWebSocketClient {
  private socket: WebSocket | null = null;
  private connected = false;
  private receiving: Uint8Array = new Uint8Array(0);
  private readChain: Promise<void> = Promise.resolve();
  private nextId = 0;
  private pending = new Map<number, PendingOperation>();
  private incomingQos2 = new Set<number>();
  private connectionPending: {
    resolve: () => void;
    reject: (error: Error) => void;
    timer: ReturnType<typeof setTimeout>;
  } | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private awaitingPing = false;
  private stopped = false;

  constructor(
    private readonly options: MqttConnection,
    private readonly onStatus: (
      status: MqttConnectionStatus,
      error?: string,
    ) => void,
    private readonly onMessage: (message: MqttMessage) => void,
  ) {}

  connect(): Promise<void> {
    if (this.socket) throw new Error("MQTT client is already started.");
    const url = validateBrokerUrl(this.options.brokerUrl);
    this.stopped = false;
    this.onStatus("connecting");
    return new Promise<void>((resolve, reject) => {
      const timer = setTimeout(
        () => this.fail(new Error("MQTT connection timed out.")),
        ACK_TIMEOUT_MS,
      );
      this.connectionPending = { resolve, reject, timer };
      try {
        const socket = new WebSocket(url, ["mqtt"]);
        this.socket = socket;
        socket.binaryType = "arraybuffer";
        socket.onopen = () => {
          if (this.socket !== socket) return;
          try {
            const flags =
              (this.options.username ? 0x80 : 0)
              | (this.options.password ? 0x40 : 0)
              | 0x02;
            const body = bytes(
              utf("MQTT"),
              Uint8Array.of(5, flags),
              short(this.options.keepAlive),
              Uint8Array.of(0), // MQTT 5 CONNECT properties
              utf(this.options.clientId),
              ...(this.options.username ? [utf(this.options.username)] : []),
              ...(this.options.password ? [utf(this.options.password)] : []),
            );
            socket.send(Uint8Array.from(packet(0x10, body)));
          } catch (error) {
            this.fail(mqttError(error));
          }
        };
        socket.onmessage = (event: MessageEvent) => {
          if (this.socket !== socket) return;
          this.readChain = this.readChain
            .then(async () => {
              const data: unknown = event.data;
              let content: Uint8Array;
              if (data instanceof ArrayBuffer) content = new Uint8Array(data);
              else if (data instanceof Blob)
                content = new Uint8Array(await data.arrayBuffer());
              else throw new Error("Broker sent a non-binary MQTT packet.");
              if (this.socket === socket) this.receive(content);
            })
            .catch((error: unknown) => this.fail(mqttError(error)));
        };
        socket.onerror = () => {
          // Browsers deliberately hide the details of WebSocket errors.
          if (this.socket === socket)
            this.fail(
              new Error(
                "WebSocket connection failed. Check the broker URL, TLS and credentials.",
              ),
            );
        };
        socket.onclose = () => {
          if (this.socket === socket) {
            this.teardown(new Error("MQTT WebSocket closed."), !this.stopped);
          }
        };
      } catch (error) {
        this.fail(mqttError(error));
      }
    });
  }

  private receive(chunk: Uint8Array) {
    if (this.receiving.length + chunk.length > MAX_PACKET_BYTES + 5) {
      throw new Error("Received an oversized MQTT packet.");
    }
    this.receiving = bytes(this.receiving, chunk);
    let offset = 0;
    while (offset < this.receiving.length) {
      const remaining = readVariable(this.receiving, offset + 1);
      if (!remaining) break;
      if (remaining.value > MAX_PACKET_BYTES)
        throw new Error("Received an oversized MQTT packet.");
      const end = remaining.end + remaining.value;
      if (end > this.receiving.length) break;
      const header = this.receiving[offset];
      this.handlePacket(
        header >> 4,
        header & 0x0f,
        this.receiving.subarray(remaining.end, end),
      );
      offset = end;
    }
    this.receiving = this.receiving.slice(offset);
  }

  private handlePacket(type: number, flags: number, data: Uint8Array) {
    if (type === 2) {
      // CONNACK
      if (data.length < 3 || !this.connectionPending)
        throw new Error("Unexpected MQTT CONNACK.");
      const reason = data[1];
      if (reason !== 0)
        throw new Error(`Broker rejected connection (${reasonText(reason)}).`);
      const pending = this.connectionPending;
      this.connectionPending = null;
      clearTimeout(pending.timer);
      this.connected = true;
      this.onStatus("connected");
      if (this.options.keepAlive > 0) {
        this.pingTimer = setInterval(() => {
          if (this.awaitingPing) {
            this.fail(new Error("MQTT keep-alive timed out."));
          } else {
            this.awaitingPing = true;
            this.socket?.send(Uint8Array.of(0xc0, 0));
          }
        }, this.options.keepAlive * 1000);
      }
      pending.resolve();
      return;
    }
    if (!this.connected)
      throw new Error("Unexpected MQTT packet before CONNACK.");
    if (type === 3) {
      // PUBLISH
      let cursor = readUtf(data, 0);
      const qos = (flags >> 1) & 0x03;
      if (qos === 3) throw new Error("Invalid MQTT PUBLISH QoS.");
      let messageId = 0;
      if (qos > 0) {
        if (cursor.end + 2 > data.length)
          throw new Error("Truncated MQTT message ID.");
        messageId = data[cursor.end] * 256 + data[cursor.end + 1];
        if (messageId === 0) throw new Error("Invalid MQTT message ID.");
        cursor = { ...cursor, end: cursor.end + 2 };
      }
      const properties = readVariable(data, cursor.end);
      if (!properties || properties.end + properties.value > data.length) {
        throw new Error("Malformed MQTT PUBLISH properties.");
      }
      if (qos !== 2 || !this.incomingQos2.has(messageId)) {
        this.onMessage({
          topic: cursor.text,
          payload: decoder.decode(
            data.subarray(properties.end + properties.value),
          ),
          qos: qos as MqttQos,
          retain: Boolean(flags & 1),
        });
        if (qos === 2) this.incomingQos2.add(messageId);
      }
      if (qos === 1) this.send(packet(0x40, short(messageId))); // PUBACK
      if (qos === 2) this.send(packet(0x50, short(messageId))); // PUBREC
      return;
    }
    if (type === 6) {
      // PUBREL from broker completes an inbound QoS 2 delivery.
      if (flags !== 2 || data.length < 2)
        throw new Error("Malformed MQTT PUBREL.");
      const id = data[0] * 256 + data[1];
      this.incomingQos2.delete(id);
      this.send(packet(0x70, short(id))); // PUBCOMP
      return;
    }
    if (type === 4 || type === 5 || type === 7 || type === 9 || type === 11) {
      // PUBACK / PUBREC / PUBCOMP / SUBACK / UNSUBACK
      if (data.length < 2) throw new Error("Truncated MQTT acknowledgement.");
      const id = data[0] * 256 + data[1];
      const waiting = this.pending.get(id);
      if (!waiting || waiting.packetType !== type) return;
      let reason = 0;
      if (type === 4 || type === 5 || type === 7) {
        reason = data.length > 2 ? data[2] : 0;
      } else {
        const props = readVariable(data, 2);
        if (!props || props.end + props.value >= data.length)
          throw new Error("Malformed MQTT acknowledgement properties.");
        reason = data[props.end + props.value];
      }
      if (type === 5 && reason < 0x80) {
        // PUBREC -> PUBREL -> PUBCOMP, preserving the pending timeout.
        waiting.packetType = 7;
        this.send(packet(0x62, short(id)));
        return;
      }
      this.pending.delete(id);
      clearTimeout(waiting.timer);
      if (reason >= 0x80)
        waiting.reject(
          new Error(`Broker rejected operation (${reasonText(reason)}).`),
        );
      else waiting.resolve();
      return;
    }
    if (type === 13) {
      // PINGRESP
      this.awaitingPing = false;
      return;
    }
    if (type === 14) {
      // broker initiated DISCONNECT
      throw new Error(`Broker disconnected (${reasonText(data[0] ?? 0)}).`);
    }
  }

  private send(data: Uint8Array) {
    if (!this.connected || this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error("Connect to a broker first.");
    }
    this.socket.send(Uint8Array.from(data));
  }

  private id(): number {
    for (let attempt = 0; attempt < 65_535; attempt++) {
      this.nextId = (this.nextId % 65_535) + 1;
      if (!this.pending.has(this.nextId)) return this.nextId;
    }
    throw new Error("Too many outstanding MQTT operations.");
  }

  private ackOperation(
    type: number,
    id: number,
    data: Uint8Array,
  ): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error("MQTT acknowledgement timed out."));
      }, ACK_TIMEOUT_MS);
      this.pending.set(id, { packetType: type, resolve, reject, timer });
      try {
        this.send(data);
      } catch (error) {
        clearTimeout(timer);
        this.pending.delete(id);
        reject(mqttError(error));
      }
    });
  }

  subscribe(filter: string, qos: MqttQos): Promise<void> {
    validateTopic(filter, true);
    const id = this.id();
    const data = packet(
      0x82,
      bytes(short(id), Uint8Array.of(0), utf(filter), Uint8Array.of(qos)),
    );
    return this.ackOperation(9, id, data);
  }

  unsubscribe(filter: string): Promise<void> {
    validateTopic(filter, true);
    const id = this.id();
    const data = packet(0xa2, bytes(short(id), Uint8Array.of(0), utf(filter)));
    return this.ackOperation(11, id, data);
  }

  publish(
    topic: string,
    payload: string,
    options: PublishOptions,
  ): Promise<void> {
    validateTopic(topic, false);
    const parts: Uint8Array[] = [Uint8Array.of(0x01, 0x01)]; // payloadFormatIndicator: UTF-8
    if (options.responseTopic) {
      validateTopic(options.responseTopic, false);
      parts.push(Uint8Array.of(0x08), utf(options.responseTopic));
    }
    if (options.correlationData) {
      parts.push(Uint8Array.of(0x09), utf(options.correlationData));
    }
    const properties = bytes(...parts);
    const id = options.qos > 0 ? this.id() : 0;
    const data = packet(
      0x30 | (options.qos << 1) | (options.retain ? 1 : 0),
      bytes(
        utf(topic),
        ...(id ? [short(id)] : []),
        variable(properties.length),
        properties,
        encoder.encode(payload),
      ),
    );
    if (id) return this.ackOperation(options.qos === 2 ? 5 : 4, id, data);
    this.send(data);
    return Promise.resolve();
  }

  disconnect() {
    this.stopped = true;
    if (this.connected) {
      try {
        this.send(Uint8Array.of(0xe0, 2, 0, 0));
      } catch {
        /* socket already closed */
      }
    }
    this.teardown(new Error("MQTT client disconnected."), false);
  }

  private fail(error: Error) {
    this.teardown(error, true);
  }

  private teardown(error: Error, report: boolean) {
    const socket = this.socket;
    if (!socket && !this.connectionPending) return;
    this.socket = null;
    this.connected = false;
    this.receiving = new Uint8Array(0);
    this.awaitingPing = false;
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.pingTimer = null;
    const connection = this.connectionPending;
    this.connectionPending = null;
    if (connection) {
      clearTimeout(connection.timer);
      connection.reject(error);
    }
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error);
    }
    this.pending.clear();
    this.incomingQos2.clear();
    if (socket) {
      socket.onopen = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.onmessage = null;
      socket.close();
    }
    this.onStatus("disconnected", report ? error.message : undefined);
  }
}
