import { describe, expect, it } from "vitest";
import { formatMqttMessagePayload } from "../../../src/components/mqtt/MqttMessageLog";

describe("MQTT message log formatting", () => {
  it("formats JSON responses for Fumadocs code blocks", () => {
    expect(
      formatMqttMessagePayload('{"result":{"ok":true},"ids":[1,2]}'),
    ).toEqual({
      language: "json",
      code: '{\n  "result": {\n    "ok": true\n  },\n  "ids": [\n    1,\n    2\n  ]\n}',
    });
  });

  it("preserves plain text and malformed JSON without throwing", () => {
    expect(formatMqttMessagePayload("MQTT connected")).toEqual({
      language: "text",
      code: "MQTT connected",
    });
    expect(formatMqttMessagePayload("{unclosed")).toEqual({
      language: "text",
      code: "{unclosed",
    });
  });

  it("keeps JSON primitive values as JSON", () => {
    expect(formatMqttMessagePayload("true")).toEqual({
      language: "json",
      code: "true",
    });
    expect(formatMqttMessagePayload("null")).toEqual({
      language: "json",
      code: "null",
    });
  });
});
