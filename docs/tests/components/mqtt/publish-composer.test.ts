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
    "renders the %s editor without an extra message-type dropdown",
    (mode: PublishMode) => {
      const html = renderToStaticMarkup(
        createElement(MqttPublishComposer, {
          ...props,
          mode,
        }),
      );
      expect(html).toContain('aria-label="Publish messages"');
      expect(html).toContain('aria-label="Message type"');
      expect(html).toContain("Publish topic");
      expect(html).not.toContain('id="remote-message-kind"');
      if (mode === "command") expect(html).toContain('id="remote-command"');
      if (mode === "settings") expect(html).toContain("Add setting");
      if (mode === "request") expect(html).toContain("Response topic");
      if (mode === "custom") expect(html).toContain("hello mqtt");
    },
  );
});
