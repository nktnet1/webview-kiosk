import { describe, expect, it } from "vitest";
import {
  mqttHex,
  parseMqttPublishProperties,
  reportedMqttSender,
} from "../../../src/components/mqtt/properties";

const utf = (value: string) => {
  const bytes = new TextEncoder().encode(value);
  return [bytes.length >> 8, bytes.length & 255, ...bytes];
};

describe("MQTT 5 PUBLISH metadata", () => {
  it("decodes MQTT 5 metadata and Android's publisher user properties", () => {
    const metadata = parseMqttPublishProperties(
      Uint8Array.of(
        0x01,
        1, // UTF-8 payload
        0x03,
        ...utf("application/json"),
        0x02,
        0,
        0,
        1,
        44, // 300-second expiry
        0x08,
        ...utf("wk/response/device"),
        0x09,
        0,
        3,
        0,
        0xff,
        1,
        0x0b,
        0x82,
        1, // subscription ID 130
        0x0b,
        1,
        0x23,
        0,
        2,
        0x26,
        ...utf("username"),
        ...utf("kiosk-user"),
        0x26,
        ...utf("appInstanceId"),
        ...utf("android-123"),
        0x26,
        ...utf("custom"),
        ...utf("one"),
        0x26,
        ...utf("custom"),
        ...utf("two"),
      ),
    );
    expect(metadata).toEqual({
      payloadFormatIndicator: 1,
      contentType: "application/json",
      messageExpiryInterval: 300,
      responseTopic: "wk/response/device",
      correlationData: "00ff01",
      subscriptionIdentifiers: [130, 1],
      topicAlias: 2,
      userProperties: [
        { name: "username", value: "kiosk-user" },
        { name: "appInstanceId", value: "android-123" },
        { name: "custom", value: "one" },
        { name: "custom", value: "two" },
      ],
    });
    expect(reportedMqttSender(metadata)).toEqual({
      username: "kiosk-user",
      appInstanceId: "android-123",
    });
  });

  it("does not guess the sender when the broker does not forward identity", () => {
    expect(reportedMqttSender({ contentType: "application/json" })).toBeNull();
    expect(
      reportedMqttSender({ userProperties: [{ name: "username", value: "" }] }),
    ).toBeNull();
    expect(
      reportedMqttSender({
        userProperties: [{ name: "appInstanceId", value: "device-2" }],
      }),
    ).toEqual({ appInstanceId: "device-2", username: undefined });
    expect(mqttHex(Uint8Array.of(0, 255))).toBe("00ff");
  });

  it("rejects truncated, invalid and duplicate PUBLISH properties", () => {
    for (const property of [
      [0x03, 0, 9, 65], // UTF string longer than property section
      [0x26, 0, 3, 65], // user property incomplete
      [0x09, 0, 2, 255],
      [0x02, 0, 0, 0],
      [0x01, 2],
      [0x0b, 0],
      [0x23, 0, 0],
      [0x01, 1, 0x01, 1],
      [0x99],
      [0x0b, 0x80, 0x80, 0x80, 0x80, 1],
      [0x03, 0, 1, 0xff], // invalid UTF-8
    ]) {
      expect(() =>
        parseMqttPublishProperties(Uint8Array.from(property)),
      ).toThrow(/MQTT/);
    }
    expect(parseMqttPublishProperties(new Uint8Array(0))).toEqual({});
  });
});
