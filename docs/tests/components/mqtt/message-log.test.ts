import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  appendMqttMessage,
  expandedMqttMessageIds,
  filterMqttMessages,
  formatMqttMessagePayload,
  type LogEntry,
  MqttMessageLog,
  matchesMqttTopicFilter,
  toggleMqttMessageExpansion,
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

  it("matches MQTT wildcard topic filters without including system topics", () => {
    expect(matchesMqttTopicFilter("wk/event/#", "wk/event")).toBe(true);
    expect(matchesMqttTopicFilter("wk/event/#", "wk/event/device/state")).toBe(
      true,
    );
    expect(matchesMqttTopicFilter("wk/+/state", "wk/node/state")).toBe(true);
    expect(matchesMqttTopicFilter("wk/+/state", "wk/node/sub/state")).toBe(
      false,
    );
    expect(matchesMqttTopicFilter("wk/event", "wk/event/state")).toBe(false);
    expect(matchesMqttTopicFilter("wk/event", "wk/event")).toBe(true);
    expect(matchesMqttTopicFilter("#", "$SYS/broker/state")).toBe(false);
    expect(matchesMqttTopicFilter("+/broker/state", "$SYS/broker/state")).toBe(
      false,
    );
    expect(matchesMqttTopicFilter("$SYS/#", "$SYS/broker/state")).toBe(true);
  });

  it("filters messages by selected subscription and direction", () => {
    const entries = [received, published];
    expect(filterMqttMessages(entries, "all", "wk/event/#")).toEqual([
      received,
    ]);
    expect(filterMqttMessages(entries, "received", "wk/event/#")).toEqual([
      received,
    ]);
    expect(filterMqttMessages(entries, "published", "wk/event/#")).toEqual([]);
    expect(filterMqttMessages(entries, "all", null)).toBe(entries);
  });

  it("expands the newest message by default and independently toggles earlier messages", () => {
    const entries = [received, published];
    expect([...expandedMqttMessageIds(entries, "all", null, null)]).toEqual([
      2,
    ]);

    const withOlder = toggleMqttMessageExpansion(entries, "all", null, null, 1);
    expect([
      ...expandedMqttMessageIds(entries, "all", null, withOlder),
    ]).toEqual([1, 2]);

    const withoutLatest = toggleMqttMessageExpansion(
      entries,
      "all",
      null,
      withOlder,
      2,
    );
    expect([
      ...expandedMqttMessageIds(entries, "all", null, withoutLatest),
    ]).toEqual([1]);

    const withoutEither = toggleMqttMessageExpansion(
      entries,
      "all",
      null,
      withoutLatest,
      1,
    );
    expect(
      expandedMqttMessageIds(entries, "all", null, withoutEither).size,
    ).toBe(0);
  });

  it("supports expand all and collapse all while keeping manual toggles independent", () => {
    const entries = [received, published];
    const expandedAll = {
      filter: "all" as const,
      topicFilter: null,
      mode: "all" as const,
      overrides: new Map<number, boolean>(),
    };
    const collapsedAll = { ...expandedAll, mode: "none" as const };
    expect([
      ...expandedMqttMessageIds(entries, "all", null, expandedAll),
    ]).toEqual([1, 2]);
    expect(
      expandedMqttMessageIds(entries, "all", null, collapsedAll).size,
    ).toBe(0);
    expect([
      ...expandedMqttMessageIds(
        [...entries, { ...received, id: 3 }],
        "all",
        null,
        expandedAll,
      ),
    ]).toEqual([1, 2, 3]);

    const reopened = toggleMqttMessageExpansion(
      entries,
      "all",
      null,
      collapsedAll,
      1,
    );
    expect([...expandedMqttMessageIds(entries, "all", null, reopened)]).toEqual(
      [1],
    );
    const oneClosed = toggleMqttMessageExpansion(
      entries,
      "all",
      null,
      expandedAll,
      2,
    );
    expect([
      ...expandedMqttMessageIds(entries, "all", null, oneClosed),
    ]).toEqual([1]);
  });

  it("resets expansion when filters change and handles subsequent messages", () => {
    const entries = [received, published];
    const olderOpened = toggleMqttMessageExpansion(
      entries,
      "all",
      null,
      null,
      1,
    );
    const next = { ...received, id: 3, time: "10:15:02" };
    expect([
      ...expandedMqttMessageIds([...entries, next], "all", null, olderOpened),
    ]).toEqual([1, 3]);
    expect([
      ...expandedMqttMessageIds([received], "received", null, olderOpened),
    ]).toEqual([1]);
    expect([
      ...expandedMqttMessageIds([received], "all", "wk/event/#", olderOpened),
    ]).toEqual([1]);
    expect(expandedMqttMessageIds([], "all", null, null).size).toBe(0);

    // A deliberate collapse-all survives new messages until the user changes it.
    const collapsedAll = {
      filter: "all" as const,
      topicFilter: null,
      mode: "none" as const,
      overrides: new Map<number, boolean>(),
    };
    expect(
      expandedMqttMessageIds([...entries, next], "all", null, collapsedAll)
        .size,
    ).toBe(0);
  });

  it("prunes expansion overrides for messages outside the visible log", () => {
    const entries = [received, published];
    const prior = {
      filter: "all" as const,
      topicFilter: null,
      mode: "latest" as const,
      overrides: new Map([[999, true]]),
    };
    const next = toggleMqttMessageExpansion(entries, "all", null, prior, 1);
    expect([...next.overrides.keys()]).toEqual([1]);
  });

  it("shows an empty-state message when the selected topic has no messages", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [received, published],
        topicFilter: "other/#",
      }),
    );
    expect(html).not.toContain("justify-start");
    expect(html).not.toContain("justify-end");
    expect(html).toContain("No messages matching this topic and direction.");
  });

  it("renders Fumadocs tabs with a visibly active filter and hides older payloads", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, { entries: [received, published] }),
    );
    expect(html).toContain('aria-label="Filter MQTT messages"');
    expect(html).toContain('role="tablist"');
    expect(html).toContain("flex min-h-0 min-w-0 flex-1 flex-col gap-3");
    expect(html).toContain("lg:h-full lg:min-h-0");
    expect(html).toContain('data-state="active"');
    expect(html).toContain('aria-selected="true"');
    expect(html).toContain('aria-label="Message expansion"');
    expect(html).toContain("Expand all");
    expect(html).toContain("Collapse all");
    expect(html).toContain("data-[state=active]:bg-fd-accent/50");
    expect(html).toContain("justify-start");
    expect(html).toContain("justify-end");
    expect(html).toContain("text-sky-700");
    expect(html).toContain("text-emerald-700");
    expect(html).toContain("wk/event/state");
    expect(html).toContain("wk/command");
    expect(html).toContain("Retained");
    expect(html.indexOf("wk/event/state")).toBeLessThan(
      html.indexOf("wk/command"),
    );
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain('aria-expanded="true"');
    // The older received payload must not be shown, not even as a preview.
    expect(html).not.toContain("online");
    expect(html).toContain("refresh");
  });

  it("identifies MQTT publishers from reported user properties and displays metadata", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [
          {
            ...received,
            properties: {
              contentType: "application/json",
              responseTopic: "wk/reply",
              correlationData: "0a0b",
              userProperties: [
                { name: "username", value: "kiosk-alpha" },
                { name: "appInstanceId", value: "android-999" },
              ],
            },
          },
        ],
      }),
    );
    expect(html).toContain("kiosk-alpha");
    expect(html).toContain("Instance: android-999");
    expect(html).toContain("MQTT 5 properties");
    expect(html).toContain("Response topic");
    expect(html).toContain("Correlation data (hex)");
    expect(html).toContain("application/json");
  });

  it("shows the local username and client ID for published messages", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [
          {
            ...published,
            sender: { username: "debugger", clientId: "wk-docs-1" },
          },
        ],
      }),
    );
    expect(html).toContain("You");
    expect(html).toContain("debugger");
    expect(html).toContain("Client: wk-docs-1");
  });

  it("renders an empty payload only when its message is expanded", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [received, { ...published, payload: "" }],
      }),
    );
    expect(html).toContain("Empty payload");
    expect(html).not.toContain("online");
  });
});
