import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import MqttDebugger from "../../../src/components/mqtt/MqttDebugger";

describe("MQTT debugger workspace", () => {
  it("starts with an expanded connection panel, a topics sidebar, and the composer after the history", () => {
    const html = renderToStaticMarkup(createElement(MqttDebugger));

    const connection = html.indexOf('aria-label="Broker connection"');
    const sidebar = html.indexOf('aria-label="MQTT topics"');
    const messages = html.indexOf('aria-label="MQTT messages"');
    const composer = html.indexOf('aria-label="Publish messages"');

    expect(connection).toBeGreaterThanOrEqual(0);
    expect(sidebar).toBeGreaterThan(connection);
    expect(messages).toBeGreaterThan(sidebar);
    expect(composer).toBeGreaterThan(messages);
    expect(html).toContain('aria-controls="mqtt-connection-fields"');
    expect(html).toContain('aria-expanded="true"');
    expect(html).toContain("Disconnected");
    expect(html).toContain("lg:grid-cols-[17rem_minmax(0,1fr)]");
  });
});
