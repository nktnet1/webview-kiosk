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
  MqttPropertiesDetails,
  matchesMqttTopicFilter,
  mqttPublishPropertyRows,
  pruneMqttMessageExpansion,
  setMqttMessageExpansion,
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

  it("starts with every message expanded and only collapses manually selected messages", () => {
    const entries = [received, published];
    const initiallyCollapsed = new Set<number>();
    expect([...expandedMqttMessageIds(entries, initiallyCollapsed)]).toEqual([
      1, 2,
    ]);

    const oneCollapsed = toggleMqttMessageExpansion(initiallyCollapsed, 1);
    expect([...expandedMqttMessageIds(entries, oneCollapsed)]).toEqual([2]);
    expect([...expandedMqttMessageIds(entries, initiallyCollapsed)]).toEqual([
      1, 2,
    ]);

    const bothCollapsed = toggleMqttMessageExpansion(oneCollapsed, 2);
    expect(expandedMqttMessageIds(entries, bothCollapsed).size).toBe(0);

    const oneReopened = toggleMqttMessageExpansion(bothCollapsed, 1);
    expect([...expandedMqttMessageIds(entries, oneReopened)]).toEqual([1]);
  });

  it("expands and collapses all visible messages without changing hidden messages", () => {
    const entries = [received, published];
    const collapsed = setMqttMessageExpansion(new Set(), [received], false);
    expect([...expandedMqttMessageIds(entries, collapsed)]).toEqual([2]);

    const allCollapsed = setMqttMessageExpansion(collapsed, entries, false);
    expect(expandedMqttMessageIds(entries, allCollapsed).size).toBe(0);

    const receivedReopened = setMqttMessageExpansion(
      allCollapsed,
      [received],
      true,
    );
    expect([...expandedMqttMessageIds(entries, receivedReopened)]).toEqual([1]);
    expect([...expandedMqttMessageIds([published], receivedReopened)]).toEqual(
      [],
    );

    const reopenedAll = setMqttMessageExpansion(
      receivedReopened,
      entries,
      true,
    );
    expect([...expandedMqttMessageIds(entries, reopenedAll)]).toEqual([1, 2]);
    expect(allCollapsed).toEqual(new Set([1, 2]));
  });

  it("opens new messages by default even after Collapse all, and preserves manually collapsed messages across filters", () => {
    const entries = [received, published];
    const collapsed = setMqttMessageExpansion(new Set(), entries, false);
    const next = { ...received, id: 3, time: "10:15:02" };

    // Newly received messages were not present when Collapse all was clicked.
    expect([...expandedMqttMessageIds([...entries, next], collapsed)]).toEqual([
      3,
    ]);
    expect([...expandedMqttMessageIds([received], collapsed)]).toEqual([]);
    expect([...expandedMqttMessageIds([published], collapsed)]).toEqual([]);
    expect(expandedMqttMessageIds([], collapsed).size).toBe(0);

    // Re-expanding a filtered message does not affect other topics or tabs.
    const reopened = setMqttMessageExpansion(collapsed, [received], true);
    expect([...expandedMqttMessageIds(entries, reopened)]).toEqual([1]);
    expect([...expandedMqttMessageIds([published], reopened)]).toEqual([]);
  });

  it("prunes collapsed message IDs that are no longer in the bounded history", () => {
    const old = new Set([1, 999]);
    const entries = [received, published];
    const next = pruneMqttMessageExpansion(old, entries);
    expect(next).toEqual(new Set([1]));
    expect(old).toEqual(new Set([1, 999]));
    expect(pruneMqttMessageExpansion(next, entries)).toBe(next);
    expect(pruneMqttMessageExpansion(new Set(), entries).size).toBe(0);
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

  it("renders Fumadocs tabs with all messages expanded by default", () => {
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
    expect(html).not.toContain('aria-expanded="false"');
    expect((html.match(/aria-expanded="true"/g) ?? []).length).toBe(2);
    expect(html).toContain("online");
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
    expect(html).toContain("android-999");
    expect(html).toContain('aria-label="Message details"');
    expect(html).toContain('aria-label="MQTT 5 properties"');
    // The metadata is in a portal-backed tooltip, not below the payload.
    expect(html).not.toContain("Response topic");
    expect(html).not.toContain("Correlation data (hex)");
    expect(html).not.toContain("application/json");
    expect(html).not.toContain("MQTT 5 metadata");
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
    expect(html).toContain("wk-docs-1");
    expect(html).toContain(">Client<");
    expect(html).toContain(">To<");
    expect(html).toContain(">From<");
  });

  it("formats MQTT 5 tooltip rows, including duplicate user properties and numeric zero", () => {
    expect(
      mqttPublishPropertyRows({
        payloadFormatIndicator: 0,
        messageExpiryInterval: 0,
        contentType: "application/json",
        responseTopic: "wk/response/a",
        correlationData: "0001ff",
        topicAlias: 12,
        subscriptionIdentifiers: [1, 7],
        userProperties: [
          { name: "username", value: "kiosk-a" },
          { name: "username", value: "kiosk-b" },
          { name: "appInstanceId", value: "android-42" },
        ],
      }),
    ).toEqual([
      { label: "Payload format", value: "Binary" },
      { label: "Content type", value: "application/json" },
      { label: "Message expiry", value: "0 seconds" },
      { label: "Response topic", value: "wk/response/a" },
      { label: "Correlation data (hex)", value: "0x0001ff" },
      { label: "Topic alias", value: "12" },
      { label: "Subscription ID", value: "1" },
      { label: "Subscription ID", value: "7" },
      { label: "User property: username", value: "kiosk-a" },
      { label: "User property: username", value: "kiosk-b" },
      { label: "User property: appInstanceId", value: "android-42" },
    ]);
    expect(mqttPublishPropertyRows({})).toEqual([]);
    expect(mqttPublishPropertyRows({ payloadFormatIndicator: 1 })).toEqual([
      { label: "Payload format", value: "UTF-8" },
    ]);
  });

  it("places the MQTT 5 tooltip button outside the payload toggle and keeps details compact", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [
          {
            ...received,
            qos: 2,
            duplicate: true,
            retain: true,
            properties: { topicAlias: 3 },
          },
          { ...published, sender: { username: "docs", clientId: "debug-42" } },
        ],
      }),
    );
    const first = html.slice(
      html.indexOf("<article"),
      html.indexOf("</article>") + 10,
    );
    expect(first).toContain('aria-label="MQTT 5 properties"');
    expect(first).toContain('class="absolute top-3 right-3');
    expect(first.indexOf('aria-label="MQTT 5 properties"')).toBeLessThan(
      first.indexOf('aria-label="Collapse message"'),
    );
    expect(first).toContain('aria-label="Collapse message"');
    expect(first).toContain('aria-label="Message details"');
    expect(first).toContain(">QoS<");
    expect(first).toContain(">From<");
    expect(first).not.toContain(">Instance<");
    expect(first).toContain("Retained");
    expect(first).toContain("Duplicate");
    expect(first).not.toContain("Topic alias");
    expect(html).toContain(">To<");
    expect(html).toContain("debug-42");
  });

  it("separates MQTT 5 user properties into their own labelled section", () => {
    const html = renderToStaticMarkup(
      createElement(MqttPropertiesDetails, {
        properties: {
          contentType: "application/json",
          userProperties: [
            { name: "username", value: "kiosk-a" },
            { name: "username", value: "kiosk-b" },
            { name: "appInstanceId", value: "instance-1" },
          ],
        },
      }),
    );
    expect(html).toContain("MQTT 5 properties");
    expect(html).toContain("Content type");
    expect(html).toContain('aria-label="User properties"');
    expect(html).toContain(">User properties</h3>");
    expect(html).toContain("username</dt>");
    expect(html).not.toContain("User property: username");
    expect(html).toContain("kiosk-a");
    expect(html).toContain("kiosk-b");
    expect(html.indexOf("Content type")).toBeLessThan(
      html.indexOf("User properties</h3>"),
    );
    expect((html.match(/username<\/dt>/g) ?? []).length).toBe(2);

    const userOnly = renderToStaticMarkup(
      createElement(MqttPropertiesDetails, {
        properties: { userProperties: [{ name: "key", value: "value" }] },
      }),
    );
    expect(userOnly).toContain("User properties</h3>");
    expect(userOnly).not.toContain("border-t");
  });

  it("puts the message time and independent expand button in the bottom-right footer", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, { entries: [received] }),
    );
    const message = html.slice(
      html.indexOf("<article"),
      html.indexOf("</article>") + 10,
    );
    const payload = message.indexOf('id="mqtt-message-payload-1"');
    const footer = message.indexOf(
      'class="flex items-center justify-end gap-1.5 px-3 pb-2"',
    );
    const time = message.indexOf("10:15:00");
    const toggle = message.indexOf('aria-label="Collapse message"');
    expect(payload).toBeGreaterThanOrEqual(0);
    expect(footer).toBeGreaterThan(payload);
    expect(time).toBeGreaterThan(footer);
    expect(toggle).toBeGreaterThan(time);
    expect(message).toContain('aria-controls="mqtt-message-payload-1"');
    expect(message).toContain('aria-expanded="true"');
  });

  it("places Fumadocs copy controls in the code block header, outside its scrollable region", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, { entries: [received] }),
    );
    expect(html).toContain("JSON payload</figcaption>");
    expect(html).toContain("Copy Text");
    expect(html.indexOf("JSON payload</figcaption>")).toBeLessThan(
      html.indexOf("Copy Text"),
    );
    expect(html.indexOf("Copy Text")).toBeLessThan(
      html.indexOf('role="region"'),
    );
  });

  it("omits metadata tooltip when no MQTT 5 properties are present", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [received, { ...published, properties: {} }],
      }),
    );
    expect(html).not.toContain('aria-label="MQTT 5 properties"');
    expect(html).toContain('aria-label="Message details"');
    expect(html).toContain("Not reported");
  });

  it("renders an empty payload only when its message is expanded", () => {
    const html = renderToStaticMarkup(
      createElement(MqttMessageLog, {
        entries: [received, { ...published, payload: "" }],
      }),
    );
    expect(html).toContain("Empty payload");
    expect(html).toContain("online");
  });
});
