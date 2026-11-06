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
    expect(markup).toContain("Refreshed at");
    expect(markup).not.toContain("Readings refresh every 2 seconds.");
    expect(markup).not.toContain(">Refresh<");
    expect(markup).not.toContain(">Read<");
    expect(markup).not.toContain(">Read battery<");
    expect(markup).not.toContain("Restore");
  });

  it("keeps brightness input and Set button on a single narrow-screen row", () => {
    expect(markup).toContain('id="brightness-value"');
    expect(markup).toContain("Current: ");
    expect(markup).toContain("System default is -1.");
    const systemButton = markup.match(
      /<button[^>]*aria-label="Select system default \(-1\)"[^>]*>.*?<\/button>/s,
    )?.[0];
    expect(systemButton).toContain('title="Select system default (-1)"');
    expect(systemButton).toContain("lucide-monitor-cog");
    expect(systemButton).not.toContain(">Auto</button>");
    expect(
      markup.indexOf('aria-label="Select system default (-1)"'),
    ).toBeLessThan(markup.indexOf(">Set</button>"));
  });

  it("submits brightness and vibration using the keyboard Go key", () => {
    const forms = [...markup.matchAll(/<form\b[^>]*>[\s\S]*?<\/form>/g)].map(
      ([form]) => form,
    );
    expect(forms).toHaveLength(2);

    for (const [form, field, action] of [
      [forms[0], "brightness-value", "Set"],
      [forms[1], "vibration-duration", "Vibrate"],
    ]) {
      expect(form).toContain(`id="${field}"`);
      expect(form).toMatch(/enterKeyHint="go"/i);
      expect(form).toMatch(
        new RegExp(`<button[^>]*type="submit"[^>]*>${action}</button>`),
      );
    }
    expect(forms[0]).toMatch(
      /<button[^>]*type="button"[^>]*aria-label="Select system default \(-1\)"/,
    );
  });

  it("offers a bounded vibration duration next to its action", () => {
    expect(markup).toContain("Duration (1–1000 ms)");
    expect(markup).toContain('id="vibration-duration"');
    expect(
      markup.match(/grid grid-cols-\[minmax\(0,1fr\)_auto\] items-end gap-2/g),
    ).toHaveLength(2);
    expect(markup).toContain(">Vibrate</button>");
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
      /data-slot="card-content" class="px-6"><form class="grid grid-cols-\[minmax\(0,1fr\)_auto\] items-end gap-2">.*?Vibrate<\/button>/s,
    );
  });
});
