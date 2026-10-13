import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  addDefaultSetting,
  assertEditablePayload,
  COMMANDS,
  changeCommand,
  changeDataField,
  changeSetting,
  changeTopLevelField,
  createRemotePayload,
  parseRemotePayload,
  splitTargets,
  stringifyPayload,
  validateRemotePayload,
} from "#/components/remote-control/payload";
import { SETTINGS_CATALOG } from "#/components/remote-control/settings-catalog";

describe("shared remote message editor", () => {
  it("builds the same command/settings payloads for MQTT and UnifiedPush except for type", () => {
    for (const kind of ["command", "settings"] as const) {
      const mqtt = createRemotePayload("mqtt", kind);
      const unifiedPush = createRemotePayload("unifiedpush", kind);
      expect(unifiedPush).toEqual({ type: kind, ...mqtt });
      expect(
        validateRemotePayload(
          stringifyPayload(unifiedPush),
          "unifiedpush",
          kind,
        ),
      ).toBe(JSON.stringify(unifiedPush));
      expect(validateRemotePayload(stringifyPayload(mqtt), "mqtt", kind)).toBe(
        JSON.stringify(mqtt),
      );
    }
  });

  it("exposes all documented commands with valid type-specific defaults", () => {
    const documented = readFileSync(
      new URL(
        "../../../content/docs/settings/mqtt/topics/command.mdx",
        import.meta.url,
      ),
      "utf8",
    );
    for (const definition of COMMANDS) {
      expect(documented).toContain(`"command": "${definition.name}"`);
      const message = createRemotePayload(
        "unifiedpush",
        "command",
        definition.name,
      );
      expect(message.command).toBe(definition.name);
      if (definition.name === "toast")
        expect(message.data).toEqual({ message: "Hello from Webview Kiosk!" });
    }
    expect(COMMANDS).toHaveLength(17);
  });

  it("preserves custom metadata and changes only requested command fields", () => {
    const original = {
      ...createRemotePayload("unifiedpush", "command"),
      messageId: "request-1",
      wakeScreen: true,
      arbitrary: { future: true },
    };
    const next = changeDataField(
      changeCommand(original, "go_to_url"),
      "url",
      "https://example.net",
    );
    expect(next).toMatchObject({
      type: "command",
      command: "go_to_url",
      data: { url: "https://example.net" },
      messageId: "request-1",
      wakeScreen: true,
      arbitrary: { future: true },
    });
    expect(next).not.toHaveProperty("data.message");
    expect(
      changeTopLevelField(next, "messageId", undefined),
    ).not.toHaveProperty("messageId");
  });

  it("updates typed settings without dropping other settings, JSON-only keys or message properties", () => {
    const original = {
      ...createRemotePayload("unifiedpush", "settings"),
      messageId: "1",
      data: {
        settings: { "appearance.theme": "LIGHT", "future.custom": [1, 2] },
      },
    };
    const withBoolean = addDefaultSetting(
      original,
      "web_browsing.allow_refresh",
    );
    const withArray = changeSetting(
      withBoolean,
      "web_browsing.address_bar_actions",
      ["BACK", "HOME"],
    );
    const updated = changeSetting(withArray, "appearance.theme", "DARK");
    const parsed = parseRemotePayload(
      stringifyPayload(updated),
      "unifiedpush",
      "settings",
    );
    expect(parsed.data).toEqual({
      settings: {
        "appearance.theme": "DARK",
        "future.custom": [1, 2],
        "web_browsing.allow_refresh": false,
        "web_browsing.address_bar_actions": ["BACK", "HOME"],
      },
    });
    expect(parsed.messageId).toBe("1");
    expect(
      assertEditablePayload(
        stringifyPayload(parsed),
        "unifiedpush",
        "settings",
      ),
    ).toEqual(parsed);
    expect(
      changeSetting(parsed, "future.custom", undefined),
    ).not.toHaveProperty("data.settings.future.custom");
  });

  it("rejects malformed form values without silently replacing JSON data", () => {
    const invalid =
      '{ "type": "settings", "data": { "settings": { "appearance.theme": "INVALID" } } }';
    expect(() =>
      assertEditablePayload(invalid, "unifiedpush", "settings"),
    ).toThrow("appearance.theme");
    expect(() =>
      validateRemotePayload(invalid, "unifiedpush", "settings"),
    ).toThrow("appearance.theme");
    expect(() => parseRemotePayload("{", "unifiedpush", "settings")).toThrow(
      "valid JSON",
    );
    expect(() =>
      parseRemotePayload(
        '{"type":"command","command":"toast"}',
        "unifiedpush",
        "settings",
      ),
    ).toThrow('type must be "settings"');
    expect(() =>
      assertEditablePayload(
        '{"type":"command","command":"future_command"}',
        "unifiedpush",
        "command",
      ),
    ).toThrow("JSON tab");
    expect(
      validateRemotePayload(
        '{"type":"command","command":"future_command"}',
        "unifiedpush",
        "command",
      ),
    ).toContain("future_command");
    expect(() =>
      validateRemotePayload(
        '{"type":"command","command":"launch_package"}',
        "unifiedpush",
        "command",
      ),
    ).toThrow("Package name is required");
    expect(() =>
      validateRemotePayload(
        '{"type":"settings","data":{"settings":{}}}',
        "unifiedpush",
        "settings",
      ),
    ).toThrow("at least one setting");
    expect(() =>
      validateRemotePayload(
        '{"type":"settings","data":{"settings":{"web_browsing.allow_refresh":"yes"}}}',
        "unifiedpush",
        "settings",
      ),
    ).toThrow("invalid value");
    expect(() =>
      validateRemotePayload(
        '{"type":"command","command":"toast","targetInstances":"all"}',
        "unifiedpush",
        "command",
      ),
    ).toThrow("array of strings");
  });

  it("normalises target lists without duplicates or blank entries", () => {
    expect(splitTargets(" abc, def\nabc, \n")).toEqual(["abc", "def"]);
  });
});

describe("Android-managed settings catalogue", () => {
  it("covers every app restriction with the correct type and enum values", () => {
    const restrictionsXml = readFileSync(
      new URL(
        "../../../../app/src/main/res/xml/app_restrictions.xml",
        import.meta.url,
      ),
      "utf8",
    );
    const arraysXml = readFileSync(
      new URL(
        "../../../../app/src/main/res/values/arrays.xml",
        import.meta.url,
      ),
      "utf8",
    );
    const arrayValues = new Map<string, string[]>();
    for (const match of arraysXml.matchAll(
      /<string-array name="([^"]+)">([\s\S]*?)<\/string-array>/g,
    )) {
      arrayValues.set(
        match[1],
        [...match[2].matchAll(/<item>(.*?)<\/item>/g)].map((item) => item[1]),
      );
    }
    const restrictions = [
      ...restrictionsXml.matchAll(/<restriction\s+([\s\S]*?)\/>/g),
    ].map((match) => {
      const attributes = Object.fromEntries(
        [...match[1].matchAll(/android:([\w]+)="([^"]+)"/g)].map(
          (attribute) => [attribute[1], attribute[2]],
        ),
      );
      return attributes;
    });
    expect(SETTINGS_CATALOG).toHaveLength(restrictions.length);
    const byKey = new Map(
      SETTINGS_CATALOG.map((setting) => [setting.key, setting]),
    );
    for (const restriction of restrictions) {
      const setting = byKey.get(restriction.key);
      expect(setting, restriction.key).toBeDefined();
      expect(setting?.kind).toBe(
        {
          bool: "boolean",
          string: "string",
          integer: "integer",
          choice: "choice",
          "multi-select": "multi-select",
        }[restriction.restrictionType],
      );
      if (restriction.entryValues) {
        expect(setting?.options).toEqual(
          arrayValues.get(restriction.entryValues.replace("@array/", "")),
        );
      }
    }
  });
});
