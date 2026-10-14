import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  type RemoteTransport,
  stringifyPayload,
} from "#/components/remote-control/payload";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import {
  SETTINGS_BY_KEY,
  SETTINGS_CATALOG,
} from "#/components/remote-control/settings-catalog";

const MULTILINE_KEYS = [
  "appearance.blocked_message",
  "appearance.custom_block_page_html",
  "js_scripts.custom_script_on_page_finish",
  "js_scripts.custom_script_on_page_start",
  "mqtt.will.payload",
  "web_content.website_blacklist",
  "web_content.website_bookmarks",
  "web_content.website_whitelist",
  "web_engine.mutual_tls",
];

describe("shared remote settings controls", () => {
  it("uses multiline inputs for the same text settings as Android", () => {
    expect(
      SETTINGS_CATALOG.filter((setting) => setting.multiline).map(
        (setting) => setting.key,
      ),
    ).toEqual(MULTILINE_KEYS);
    for (const key of MULTILINE_KEYS) {
      expect(SETTINGS_BY_KEY.get(key)?.kind).toBe("string");
    }
    expect(
      SETTINGS_BY_KEY.get("web_content.home_url")?.multiline,
    ).toBeUndefined();
  });

  it.each(["mqtt", "unifiedpush"] as const)(
    "renders multiline textareas in the %s form without altering payload strings",
    (transport: RemoteTransport) => {
      const bookmarks =
        "https://example.org | Example\nhttps://example.com | Site";
      const htmlContent = "<h1>Blocked</h1>\n<p>Access denied</p>";
      const settings = {
        "web_content.website_bookmarks": bookmarks,
        "appearance.custom_block_page_html": htmlContent,
        "web_content.home_url": "https://example.org",
      };
      const payload = stringifyPayload({
        ...(transport === "unifiedpush" ? { type: "settings" } : {}),
        data: { settings },
      });
      const markup = renderToStaticMarkup(
        createElement(RemoteMessageEditor, {
          transport,
          kind: "settings",
          value: payload,
          onChange: () => {},
          onKindChange: () => {},
        }),
      );

      expect(markup).toMatch(
        /<textarea[^>]*id="remote-setting-web_content-website_bookmarks"/,
      );
      expect(markup).toMatch(
        /<textarea[^>]*id="remote-setting-appearance-custom_block_page_html"/,
      );
      expect(markup).toMatch(
        /<input[^>]*id="remote-setting-web_content-home_url"/,
      );
      expect(markup).toContain("https://example.com | Site");
      expect(markup).toContain("&lt;h1&gt;Blocked&lt;/h1&gt;");
      expect(JSON.parse(payload).data.settings).toEqual(settings);
    },
  );
});
