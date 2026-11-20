import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  addDefaultSetting,
  assertEditablePayload,
  changeSetting,
  createRemotePayload,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
import { RemoteMessageEditor } from "#/components/remote-control/RemoteMessageEditor";
import {
  defaultSettingValue,
  SETTINGS_BY_KEY,
} from "#/components/remote-control/settings-catalog";

const KIOSK_SETTINGS = [
  {
    key: "web_lifecycle.relock_on_unexpected_exit",
    label: "Re-lock After Unexpected Exit",
    documentation: "../../../content/docs/settings/web-lifecycle.mdx",
  },
  {
    key: "device.owner.disable_status_bar_outside_lock_task",
    label: "Disable Status Bar Outside Lock Task",
    documentation: "../../../content/docs/settings/device.mdx",
  },
] as const;

describe("kiosk hardening settings in remote tools and documentation", () => {
  it.each(KIOSK_SETTINGS)(
    "publishes $key as an optional boolean with matching documentation",
    ({ key, label, documentation }) => {
      const setting = SETTINGS_BY_KEY.get(key);
      if (!setting) throw new Error(`Missing setting: ${key}`);
      expect(setting.kind).toBe("boolean");
      expect(setting.label).toBe(label);
      expect(setting.description).toBeTruthy();
      expect(defaultSettingValue(setting)).toBe(false);

      const docs = readFileSync(
        new URL(documentation, import.meta.url),
        "utf8",
      );
      expect(docs).toContain(label);
      expect(docs).toContain(`\`${key}\``);
      expect(docs).toContain("**Default:** false");
    },
  );

  it.each(["mqtt", "unifiedpush"] as const)(
    "allows %s to enable both kiosk hardening settings in the shared editor",
    (transport) => {
      let message = createRemotePayload(transport, "settings");
      for (const { key } of KIOSK_SETTINGS) {
        message = addDefaultSetting(message, key);
        message = changeSetting(message, key, true);
      }
      const payload = stringifyPayload(message);
      const parsed = assertEditablePayload(payload, transport, "settings");
      expect(validateRemotePayload(payload, transport, "settings")).toBe(
        JSON.stringify(message),
      );
      const appliedSettings = (
        parsed.data as {
          settings: Record<string, unknown>;
        }
      ).settings;
      for (const { key } of KIOSK_SETTINGS) {
        expect(appliedSettings[key]).toBe(true);
      }

      const markup = renderToStaticMarkup(
        createElement(RemoteMessageEditor, {
          transport,
          kind: "settings",
          value: payload,
          onChange: () => {},
          onKindChange: () => {},
        }),
      );
      for (const { label } of KIOSK_SETTINGS) {
        expect(markup).toContain(label);
      }
    },
  );

  it("documents the keys for MQTT settings and UnifiedPush messages", () => {
    for (const page of [
      "../../../content/docs/settings/mqtt/topics/settings.mdx",
      "../../../content/docs/settings/unifiedpush/messages.mdx",
    ]) {
      const docs = readFileSync(new URL(page, import.meta.url), "utf8");
      for (const { key } of KIOSK_SETTINGS) {
        expect(docs).toContain(`"${key}": true`);
      }
      expect(docs).toContain('"reloadActivity": true');
    }
  });
});
