import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  appendMqttMessage,
  filterMqttMessages,
  formatMqttMessagePayload,
  type LogEntry,
  MqttMessageLog,
  previewMqttMessage,
} from "../../../src/components/mqtt/MqttMessageLog";

const received: LogEntry = {
  id: 1,
  time: "10:15:00",
  direction: "received",
  topic: "wk/event/state",
  payload: '{"online":true}',
  qos: 1,
  retain: false,
};
const published: LogEntry = {
  id: 2,
  time: "10:15:01",
  direction: "published",
  topic: "wk/command",
  payload: '{"action":"refresh"}',
  qos: 0,
  retain: true,
};

describe("MQTT message log", () => {
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

  it("keeps the newest 100 messages in chronological order", () => {
    const entries = Array.from({ length: 105 }, (_, index) => ({
      ...received,
      id: index,
    }));
    const items = entries.reduce<LogEntry[]>(appendMqttMessage, []);
    expect(items).toHaveLength(100);
    expect(items[0]?.id).toBe(5);
    expect(items.at(-1)?.id).toBe(104);
  });

  it("filters by message direction without mutating the stream", () => {
    const entries = [received, published];
    expect(filterMqttMessages(entries, "received")).toEqual([received]);
    expect(filterMqttMessages(entries, "published")).toEqual([published]);
    expect(filterMqttMessages(entries, "all")).toEqual(entries);
    expect(entries).toEqual([received, published]);
  });

  it("previews messages safely without dumping entire large payloads", () => {
    expect(previewMqttMessage("")).toBe("Empty payload");
    expect(previewMqttMessage("simple text")).toBe("simple text");
    expect(previewMqttMessage("x".repeat(500))).toBe(`${"x".repeat(320)}…`);
  });

  it("displays received left and published right with distinct icon colours and message previews", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, { entries: [received, published] }),
    );
    expect(html).toContain("Filter MQTT messages");
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain("justify-start");
    expect(html).toContain("justify-end");
    expect(html).toContain("text-sky-700");
    expect(html).toContain("text-emerald-700");
    expect(html).toContain("wk/event/state");
    expect(html).toContain("wk/command");
    expect(html).toContain("{&quot;online&quot;:true}");
    expect(html).toContain("Retained");
    expect(html.indexOf("wk/event/state")).toBeLessThan(
      html.indexOf("wk/command"),
    );
  });
});
