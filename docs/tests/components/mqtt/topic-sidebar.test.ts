import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { LogEntry } from "../../../src/components/mqtt/MqttMessageLog";
import { MqttTopicSidebar } from "../../../src/components/mqtt/MqttTopicSidebar";

const received: LogEntry = {
  id: 1,
  time: "11:00:00",
  direction: "received",
  topic: "wk/event/status",
  payload: '{"online":true}',
  qos: 0,
  retain: false,
};

const props = {
  subscriptions: [
    { topic: "wk/event/#", qos: 0 as const },
    { topic: "wk/response/#", qos: 1 as const },
  ],
  activeTopics: ["wk/event/#"],
  entries: [received],
  selectedTopic: "wk/event/#",
  onSelectTopic: () => {},
  subscribeTopic: "",
  onSubscribeTopicChange: () => {},
  subscribeQos: 0 as const,
  onSubscribeQosChange: () => {},
  subscriptionBusy: false,
  connected: true,
  onSubscribe: () => {},
  onUnsubscribe: () => {},
};

describe("MQTT topics sidebar", () => {
  it("renders subscribed filters and uses the active topic selection", () => {
    const html = renderToStaticMarkup(createElement(MqttTopicSidebar, props));
    expect(html).toContain('aria-label="MQTT topics"');
    expect(html).toContain('aria-label="Filter messages by topic"');
    expect(html).toContain("All messages");
    expect(html).toContain("wk/event/#");
    expect(html).toContain("wk/response/#");
    expect(html).toContain("1 messages");
    expect(html).toContain("0 messages");
    expect(html).toContain('aria-label="Subscription active"');
    expect(html).toContain('aria-label="Subscription inactive"');
    expect(html).toContain('aria-pressed="true"');
    expect(html).toContain('aria-label="Unsubscribe wk/event/#"');
    expect(html).toContain("Subscribe to topic");
  });

  it("keeps topic selection available while disconnected but disables new subscriptions", () => {
    const html = renderToStaticMarkup(
      createElement(MqttTopicSidebar, {
        ...props,
        activeTopics: [],
        selectedTopic: null,
        connected: false,
      }),
    );
    expect(html).toContain("All messages");
    expect(html).toContain('aria-pressed="true"');
    expect(html).toMatch(/type="submit"[^>]*disabled=""/);
  });
});
