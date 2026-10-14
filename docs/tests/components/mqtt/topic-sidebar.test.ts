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
  onUpdateSubscription: async () => true,
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
    expect(html).toContain('aria-label="Actions for wk/event/#"');
    expect(html).toContain("Subscribe to topic");
    expect(html).toContain("grid-cols-[minmax(0,1fr)_minmax(6rem,35%)]");
    const topic = html.indexOf('id="mqtt-subscription-topic"');
    const qos = html.indexOf('id="mqtt-subscription-qos"');
    const subscribe = html.indexOf(">Subscribe</button>");
    expect(topic).toBeGreaterThanOrEqual(0);
    expect(qos).toBeGreaterThan(topic);
    expect(subscribe).toBeGreaterThan(qos);
    expect(
      (html.match(/aria-label="More information"/g) ?? []).length,
    ).toBeGreaterThanOrEqual(2);
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
    expect(html).toContain("Connect to a broker to subscribe.");
    expect(html).toContain('aria-describedby="mqtt-subscribe-connection-hint"');
    expect(html).toContain(
      'class="flex min-w-0 items-center justify-end gap-2"',
    );
    expect(html.indexOf('id="mqtt-subscribe-connection-hint"')).toBeLessThan(
      html.indexOf(">Subscribe</button>"),
    );
  });
});

describe("MQTT topic list size", () => {
  it("caps the scrolling list to three fixed-height topics without dropping subscriptions", () => {
    const subscriptions = Array.from({ length: 7 }, (_, index) => ({
      topic: `wk/event/${index}`,
      qos: 0 as const,
    }));
    const html = renderToStaticMarkup(
      createElement(MqttTopicSidebar, { ...props, subscriptions }),
    );
    expect(html).toContain('aria-label="Subscriptions"');
    expect(html).toContain("h-[11rem]");
    expect(html).not.toContain("max-h-[11rem]");
    expect(html).toContain("[scrollbar-gutter:stable]");
    expect(html).toContain("overflow-x-hidden");
    expect(html).toContain("overflow-y-auto");
    expect(
      html.match(/class="flex h-14 min-w-0 items-center gap-1"/g),
    ).toHaveLength(7);
    expect(html).toContain('aria-label="Actions for wk/event/0"');
    expect(html).toContain('aria-label="Actions for wk/event/6"');
  });
});

describe("MQTT topic list layout", () => {
  it.each([0, 1, 2, 3, 7])(
    "retains its width and three-row viewport with %i subscriptions",
    (length) => {
      const subscriptions = Array.from({ length }, (_, index) => ({
        topic: `a/very/long/topic/name/${index}`,
        qos: 0 as const,
      }));
      const html = renderToStaticMarkup(
        createElement(MqttTopicSidebar, { ...props, subscriptions }),
      );
      expect(html).toContain('aria-label="MQTT topics" class="w-full min-w-0"');
      expect(html).toContain('aria-label="Subscriptions"');
      expect(html).toContain("h-[11rem]");
      expect(html).toContain("[scrollbar-gutter:stable]");
      expect(html).toContain("overflow-y-auto");
      expect(
        html.match(/class="flex h-14 min-w-0 items-center gap-1"/g) ?? [],
      ).toHaveLength(length);
    },
  );
});
