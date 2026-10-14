import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import MqttDebugger from "../../../src/components/mqtt/MqttDebugger";

describe("MQTT debugger workspace", () => {
  it("renders a full-width workspace with the publisher below Topics and alongside Messages", () => {
    const html = renderToStaticMarkup(createElement(MqttDebugger));

    const connection = html.indexOf('aria-label="Broker connection"');
    const sidebar = html.indexOf('aria-label="MQTT topics"');
    const composer = html.indexOf('aria-label="Publish messages"');
    const messages = html.indexOf('aria-label="MQTT messages"');

    expect(connection).toBeGreaterThanOrEqual(0);
    expect(sidebar).toBeGreaterThan(connection);
    expect(composer).toBeGreaterThan(sidebar);
    expect(messages).toBeGreaterThan(composer);
    expect(html).toContain('aria-controls="mqtt-connection-fields"');
    expect(html).toContain('aria-expanded="true"');
    expect(html).toContain("Disconnected");
    expect(html).toContain("lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]");
    expect(html).toContain("Not connected. Connect to a broker");
    expect(html).toContain("Connection settings");
    expect(html).toContain("h-[74vh]");
    expect(html).not.toContain("max-w-6xl");
  });
});
