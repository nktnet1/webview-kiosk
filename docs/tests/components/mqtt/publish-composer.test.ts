import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  MqttPublishComposer,
  PUBLISH_MODES,
  type PublishMode,
} from "../../../src/components/mqtt/MqttPublishComposer";
import {
  createRemotePayload,
  stringifyPayload,
} from "../../../src/components/remote-control/payload";

const props = {
  onModeChange: () => {},
  commandPayload: stringifyPayload(createRemotePayload("mqtt", "command")),
  onCommandPayloadChange: () => {},
  settingsPayload: stringifyPayload(createRemotePayload("mqtt", "settings")),
  onSettingsPayloadChange: () => {},
  requestPayload: '{"request":"status"}',
  onRequestPayloadChange: () => {},
  customPayload: "hello mqtt",
  onCustomPayloadChange: () => {},
  responseTopic: "",
  onResponseTopicChange: () => {},
  correlationData: "",
  onCorrelationDataChange: () => {},
  topic: "wk/command",
  onTopicChange: () => {},
  qos: 0 as const,
  onQosChange: () => {},
  retain: false,
  onRetainChange: () => {},
  connected: true,
  busy: false,
  onPublish: () => {},
};

describe("MQTT publish modes", () => {
  it("offers Command, Settings, Request and Custom as independent modes", () => {
    expect(PUBLISH_MODES).toEqual(["command", "settings", "request", "custom"]);
  });

  it.each(PUBLISH_MODES)(
    "keeps the %s editor visible in the sidebar",
    (mode: PublishMode) => {
      const html = renderToStaticMarkup(
        createElement(MqttPublishComposer, { ...props, mode }),
      );
      expect(html).toContain('aria-label="Publish messages"');
      expect(html).toContain("Message type");
      expect(html).toContain("Publish topic");
      expect(html).toContain('id="mqtt-publish-editor"');
      expect(html).toContain('aria-label="Message payload editor"');
      expect(html).not.toMatch(/id="mqtt-publish-editor"[^>]*hidden/);
      expect(html).not.toContain("Edit payload");
      expect(html).not.toContain('id="remote-message-kind"');
      expect(html).toContain("Retain");
      expect(html).toContain("QoS");
      if (mode === "command") expect(html).toContain('id="remote-command"');
      if (mode === "settings") expect(html).toContain("Settings to update");
      if (mode === "request")
        expect(html).toContain("MQTT 5 response metadata");
      if (mode === "custom") expect(html).toContain("hello mqtt");
    },
  );

  it("places message type, QoS, and Retain together above the topic and editor", () => {
    const html = renderToStaticMarkup(
      createElement(MqttPublishComposer, { ...props, mode: "command" }),
    );
    expect(html).toContain(
      "grid-cols-[minmax(0,4fr)_minmax(0,3fr)_minmax(0,3fr)] items-end gap-2",
    );
    expect(html).toContain(
      "justify-between gap-1 rounded-md border border-input px-2",
    );
    const messageType = html.indexOf('id="mqtt-publish-mode"');
    const qos = html.indexOf('id="mqtt-publish-qos"');
    const retain = html.indexOf('id="mqtt-retain"');
    const topic = html.indexOf('id="mqtt-publish-topic"');
    expect(messageType).toBeGreaterThanOrEqual(0);
    expect(qos).toBeGreaterThan(messageType);
    expect(retain).toBeGreaterThan(qos);
    expect(topic).toBeGreaterThan(retain);
  });

  it("keeps Publish disabled when disconnected without hiding the editor", () => {
    const html = renderToStaticMarkup(
      createElement(MqttPublishComposer, {
        ...props,
        mode: "custom",
        connected: false,
      }),
    );
    expect(html).toContain("hello mqtt");
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>[^<]*<svg/);
    expect(html).toContain("Connect to a broker to publish.");
    expect(html).toContain('aria-describedby="mqtt-publish-connection-hint"');
  });
});

describe("MQTT publish availability", () => {
  it("does not show a connection warning when connected", () => {
    const html = renderToStaticMarkup(
      createElement(MqttPublishComposer, {
        ...props,
        mode: "command",
        connected: true,
      }),
    );
    expect(html).not.toContain("mqtt-publish-connection-hint");
    expect(html).not.toContain("Connect to a broker to publish.");
  });
});
