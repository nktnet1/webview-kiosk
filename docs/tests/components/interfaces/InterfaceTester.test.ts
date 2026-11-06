import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InterfaceTester from "#/components/interfaces/InterfaceTester";

describe("device API tester", () => {
  const markup = renderToStaticMarkup(createElement(InterfaceTester));

  it("shows the exact Android setting for every exposed feature", () => {
    const settings = [...markup.matchAll(/<code[^>]*>(.*?)<\/code>/gs)].map(
      ([, content]) => content.replace(/<[^>]*>/g, ""),
    );

    expect(settings).toEqual([
      "js_scripts.enable_battery_api",
      "js_scripts.enable_brightness_api",
      "device.allow_nfc",
      "js_scripts.enable_vibration_api",
      "web_engine.allow_file_download",
    ]);
  });

  it("uses automatic polling instead of manual reads or refresh", () => {
    expect(markup).toContain("Readings refresh every 2 seconds.");
    expect(markup).not.toContain(">Refresh<");
    expect(markup).not.toContain(">Read<");
    expect(markup).not.toContain(">Read battery<");
    expect(markup).not.toContain("Restore");
  });

  it("keeps brightness input and Set button on a single narrow-screen row", () => {
    expect(markup).toContain("grid-cols-[minmax(0,1fr)_auto]");
    expect(markup).toContain("Current: ");
  });

  it("right-aligns actions and places the primary NFC action last", () => {
    const nfcButtons =
      markup.match(
        /<div class="flex flex-wrap justify-end gap-2">(.*?)<\/div>/s,
      )?.[1] ?? "";
    expect(nfcButtons).toContain(">Clear tags</button>");
    expect(nfcButtons).toContain(">Start scan</button>");
    expect(nfcButtons.indexOf(">Clear tags</button>")).toBeLessThan(
      nfcButtons.indexOf(">Start scan</button>"),
    );
    expect(markup).toMatch(
      /data-slot="card-content" class="px-6 flex justify-end"><button[^>]*>Download sample<\/button>/,
    );
    expect(markup).toMatch(
      /data-slot="card-content" class="px-6 flex justify-end"><button[^>]*>Vibrate<\/button>/,
    );
  });
});
