import { describe, expect, it, vi } from "vitest";
import {
  assertEditableRequest,
  createRequestPayload,
  parseRequestPayload,
  setRequestSettingKeys,
} from "../../../src/components/mqtt/request";
import {
  loadMqttCredentials,
  loadMqttPreferences,
  saveMqttCredentials,
  saveMqttPreferences,
} from "../../../src/components/mqtt/storage";
import {
  DEFAULT_PREFERENCES,
  EMPTY_MQTT_CREDENTIALS,
  normalizeMqttCredentials,
  parseMqttCredentials,
  validateBrokerUrl,
  validateKeepAlive,
  validateTopic,
} from "../../../src/components/mqtt/validation";
import {
  parseCredentialJson,
  serializeCredentialJson,
} from "../../../src/components/remote-control/credential-json";

describe("MQTT form validation and storage", () => {
  it("defaults to secure WebSockets on port 443 and path /mqtt", () => {
    expect(EMPTY_MQTT_CREDENTIALS).toMatchObject({
      brokerUrl: "",
      protocol: "wss",
      port: "443",
      path: "/mqtt",
    });
    expect(validateBrokerUrl("broker.example.com")).toBe(
      "wss://broker.example.com/mqtt",
    );
    expect(validateBrokerUrl("localhost")).toBe("wss://localhost/mqtt");
    expect(validateBrokerUrl("192.168.1.2")).toBe("wss://192.168.1.2/mqtt");
    expect(validateBrokerUrl("[::1]")).toBe("wss://[::1]/mqtt");
  });

  it("uses the required protocol, port and path instead of URL inference", () => {
    expect(
      validateBrokerUrl("example.com", {
        protocol: "ws",
        port: "9001",
        path: "/socket",
      }),
    ).toBe("ws://example.com:9001/socket");
    expect(
      validateBrokerUrl("example.com", {
        protocol: "wss",
        port: "9443",
        path: "/mqtt/v5",
      }),
    ).toBe("wss://example.com:9443/mqtt/v5");

    vi.stubGlobal("location", { protocol: "http:" });
    try {
      expect(validateBrokerUrl("example.com")).toBe("wss://example.com/mqtt");
    } finally {
      vi.unstubAllGlobals();
    }
    vi.stubGlobal("location", { protocol: "https:" });
    try {
      expect(() =>
        validateBrokerUrl("example.com", {
          protocol: "ws",
          port: "80",
          path: "/mqtt",
        }),
      ).toThrow(/HTTPS pages/);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("rejects full URLs, ports, paths, credentials and invalid broker domains", () => {
    for (const input of [
      "",
      "ftp://example.com",
      "wss://example.com/mqtt",
      "http://localhost:8080/ws",
      "example.com:9001",
      "example.com/mqtt",
      "//example.com/mqtt",
      "example.com?token=abc",
      "user:pass@example.com",
      "example..com",
      "bad domain.com",
      "/mqtt",
      "-example.com",
    ]) {
      expect(() => validateBrokerUrl(input), input).toThrow(/domain|hostname/);
    }
  });

  it("requires an explicit valid protocol, port and path", () => {
    for (const port of ["", " ", "0", "65536", "-1", "1.5", "foo", "80e2"]) {
      expect(() =>
        validateBrokerUrl("example.com", {
          protocol: "wss",
          port,
          path: "/mqtt",
        }),
      ).toThrow(/Port/);
    }
    for (const path of ["", " ", "mqtt", "/mqtt?token=1", "/mqtt#frag"]) {
      expect(() =>
        validateBrokerUrl("example.com", {
          protocol: "wss",
          port: "443",
          path,
        }),
      ).toThrow(/path/);
    }
    expect(() =>
      validateBrokerUrl("example.com", {
        protocol: "" as "wss",
        port: "443",
        path: "/mqtt",
      }),
    ).toThrow(/protocol/);
  });

  it("validates MQTT topic names, filters, and keepalive", () => {
    expect(validateTopic("wk/event/+/status", true)).toBe("wk/event/+/status");
    expect(validateTopic("wk/event/#", true)).toBe("wk/event/#");
    expect(() => validateTopic("wk/#/status", true)).toThrow(/filter/);
    expect(() => validateTopic("wk/event/+", false)).toThrow(/wildcards/);
    expect(() => validateTopic("", false)).toThrow(/topic/);
    expect(validateKeepAlive(0)).toBe(0);
    expect(validateKeepAlive(65535)).toBe(65535);
    expect(() => validateKeepAlive(1.5)).toThrow(/whole/);
    expect(() => validateKeepAlive(65536)).toThrow(/whole/);
  });

  it("backs up credentials and upgrades older full-URL backups", () => {
    const credentials = {
      brokerUrl: "broker.example.com",
      protocol: "wss" as const,
      port: "9443",
      path: "/mqtt",
      clientId: "docs",
      username: "user",
      password: "secret",
    };
    const raw = serializeCredentialJson(credentials, EMPTY_MQTT_CREDENTIALS);
    expect(parseCredentialJson(raw, EMPTY_MQTT_CREDENTIALS)).toEqual(
      credentials,
    );
    expect(
      normalizeMqttCredentials(
        parseCredentialJson(
          '{"brokerUrl":"wss://example.com"}',
          EMPTY_MQTT_CREDENTIALS,
        ),
      ),
    ).toEqual({ ...EMPTY_MQTT_CREDENTIALS, brokerUrl: "example.com" });
    expect(() => parseCredentialJson("[]", EMPTY_MQTT_CREDENTIALS)).toThrow(
      /JSON object/,
    );
    expect(parseMqttCredentials(credentials)).toEqual(credentials);
    expect(
      parseMqttCredentials({
        brokerUrl: "ws://localhost:9001/ws",
        clientId: "docs",
        username: "user",
        password: "secret",
      }),
    ).toEqual({
      ...EMPTY_MQTT_CREDENTIALS,
      brokerUrl: "localhost",
      protocol: "ws",
      port: "9001",
      path: "/ws",
      clientId: "docs",
      username: "user",
      password: "secret",
    });
    expect(
      parseMqttCredentials({
        brokerUrl: "broker.example.com:8883/mqtt",
        clientId: "",
        username: "",
        password: "",
      }),
    ).toEqual({
      ...EMPTY_MQTT_CREDENTIALS,
      brokerUrl: "broker.example.com",
      port: "8883",
    });
    expect(() => parseMqttCredentials({ username: 123 })).toThrow(
      /credentials/,
    );
  });

  it("persists connection options, removes reset credentials, and recovers older storage", () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value);
      },
      removeItem: (key: string) => {
        values.delete(key);
      },
    };
    const key = "webview-kiosk:mqtt-debug:credentials:v1";
    saveMqttCredentials(storage, {
      ...EMPTY_MQTT_CREDENTIALS,
      password: "secret",
    });
    expect(loadMqttCredentials(values.get(key) ?? null)).toEqual({
      ...EMPTY_MQTT_CREDENTIALS,
      password: "secret",
    });
    saveMqttCredentials(storage, {
      ...EMPTY_MQTT_CREDENTIALS,
      path: "/socket",
    });
    expect(loadMqttCredentials(values.get(key) ?? null).path).toBe("/socket");
    saveMqttCredentials(storage, EMPTY_MQTT_CREDENTIALS);
    expect(values.has(key)).toBe(false);
    saveMqttPreferences(storage, DEFAULT_PREFERENCES);
    expect(
      loadMqttPreferences(
        values.get("webview-kiosk:mqtt-debug:preferences:v1") ?? null,
      ),
    ).toEqual(DEFAULT_PREFERENCES);
    expect(loadMqttPreferences("[]")).toEqual(DEFAULT_PREFERENCES);
    expect(loadMqttCredentials("invalid")).toEqual(EMPTY_MQTT_CREDENTIALS);
    expect(
      loadMqttCredentials(
        '{"brokerUrl":"ws://localhost:9001/mqtt","password":"old"}',
      ),
    ).toEqual({
      ...EMPTY_MQTT_CREDENTIALS,
      brokerUrl: "localhost",
      protocol: "ws",
      port: "9001",
      password: "old",
    });
    expect(
      loadMqttCredentials(
        '{"brokerUrl":"example.com","protocol":"","port":"","path":""}',
      ),
    ).toEqual({
      ...EMPTY_MQTT_CREDENTIALS,
      brokerUrl: "example.com",
    });
  });
});

describe("MQTT request payloads", () => {
  it("builds default request and selects setting keys", () => {
    const request = createRequestPayload();
    expect(request.requestType).toBe("get_status");
    const settings = setRequestSettingKeys(
      { requestType: "get_settings" },
      "appearance.theme, device.rotation",
    );
    expect(settings).toEqual({
      requestType: "get_settings",
      data: { settings: ["appearance.theme", "device.rotation"] },
    });
    expect(parseRequestPayload(JSON.stringify(settings))).toEqual(settings);
    expect(setRequestSettingKeys(settings, "")).toEqual({
      requestType: "get_settings",
      data: {},
    });
    expect(assertEditableRequest(JSON.stringify(settings))).toEqual(settings);
    expect(() =>
      assertEditableRequest(
        '{"requestType":"get_settings","data":{"settings":[{"key":"appearance.theme","parseAsArray":true}]}}',
      ),
    ).toThrow(/JSON tab/);
  });
  it("allows custom valid requests and rejects primitive, malformed and incorrect metadata", () => {
    expect(parseRequestPayload('{"requestType":"custom"}')).toEqual({
      requestType: "custom",
    });
    expect(() => parseRequestPayload("[]")).toThrow(/JSON object/);
    expect(() => parseRequestPayload('{"requestType":42}')).toThrow(
      /non-empty/,
    );
    expect(() =>
      parseRequestPayload(
        '{"requestType":"get_status","targetInstances":true}',
      ),
    ).toThrow(/metadata/);
  });
});
