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
    expect(html).toContain("MQTT Broker Connection");
    expect(html).not.toContain("MQTT Debugger</h1>");
    expect(html).not.toContain("Connection options");
    expect(html).toContain("Keep alive (seconds)");
    expect(html).toContain("lg:grid-cols-4");
    const manage = html.indexOf("Manage credentials");
    const connectForm = html.indexOf('id="mqtt-connection-fields"');
    expect(manage).toBeGreaterThan(connection);
    expect(manage).toBeLessThan(connectForm);
    expect(html).toContain("Export JSON");
    expect(html).toContain("Import JSON");
    expect(html).toContain("Clear credentials");
    expect(html).toContain('aria-expanded="true"');
    expect(html).toContain("Disconnected");
    expect(html).toContain("lg:grid-cols-[minmax(16rem,28%)_minmax(0,1fr)]");
    expect(html).toContain("bg-red-500");
    expect(html).not.toContain("Not connected. Connect to a broker");
    expect(html).not.toContain("Connection settings");
    expect(html).toContain(
      'aria-label="MQTT workspace" class="min-w-0 lg:relative lg:min-h-0"',
    );
    expect(html).toContain("lg:absolute lg:inset-0");
    expect(html).toContain("flex min-h-0 min-w-0 flex-1 flex-col");
    expect(html).toContain("lg:h-full lg:min-h-0");
    expect(html).not.toContain("MQTT 5 over WebSockets");
    expect(html).not.toContain("max-w-6xl");
  });
});
