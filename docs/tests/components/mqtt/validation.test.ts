import { describe, expect, it } from "vitest";
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
  it("accepts WebSocket URLs and rejects HTTP and embedded login", () => {
    expect(validateBrokerUrl("wss://example.com/mqtt")).toBe(
      "wss://example.com/mqtt",
    );
    expect(() => validateBrokerUrl("https://example.com")).toThrow(
      /ws:\/\/ or wss:\/\//,
    );
    expect(() => validateBrokerUrl("wss://user:pass@example.com/mqtt")).toThrow(
      /embedded credentials/,
    );
    expect(() => validateBrokerUrl("wss://example.com/mqtt#secret")).toThrow(
      /fragments/,
    );
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

  it("backs up broker credentials independently of subscriptions", () => {
    const credentials = {
      brokerUrl: "wss://example.com/mqtt",
      clientId: "docs",
      username: "user",
      password: "secret",
    };
    const raw = serializeCredentialJson(credentials, EMPTY_MQTT_CREDENTIALS);
    expect(parseCredentialJson(raw, EMPTY_MQTT_CREDENTIALS)).toEqual(
      credentials,
    );
    expect(
      parseCredentialJson(
        '{"brokerUrl":"wss://example.com"}',
        EMPTY_MQTT_CREDENTIALS,
      ).password,
    ).toBe("");
    expect(() => parseCredentialJson("[]", EMPTY_MQTT_CREDENTIALS)).toThrow(
      /JSON object/,
    );
    expect(parseMqttCredentials(credentials)).toEqual(credentials);
    expect(() => parseMqttCredentials({ username: 123 })).toThrow(
      /credentials/,
    );
  });

  it("persists typed preferences and recovers from corrupt JSON", () => {
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
    saveMqttCredentials(storage, {
      ...EMPTY_MQTT_CREDENTIALS,
      password: "secret",
    });
    expect(
      loadMqttCredentials(
        values.get("webview-kiosk:mqtt-debug:credentials:v1") ?? null,
      ),
    ).toEqual({ ...EMPTY_MQTT_CREDENTIALS, password: "secret" });
    saveMqttPreferences(storage, DEFAULT_PREFERENCES);
    expect(
      loadMqttPreferences(
        values.get("webview-kiosk:mqtt-debug:preferences:v1") ?? null,
      ),
    ).toEqual(DEFAULT_PREFERENCES);
    expect(loadMqttPreferences("[]")).toEqual(DEFAULT_PREFERENCES);
    expect(loadMqttCredentials("invalid")).toEqual(EMPTY_MQTT_CREDENTIALS);
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
