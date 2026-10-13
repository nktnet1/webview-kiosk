/** Shared command/settings message format used by both MQTT and UnifiedPush. */
import * as v from "valibot";
import {
  isJsonObject,
  type JsonObject,
  JsonObjectSchema,
  parseJsonObject,
} from "./json-validation";
import { defaultSettingValue, SETTINGS_BY_KEY } from "./settings-catalog";

export type { JsonObject } from "./json-validation";
export { isJsonObject };

export type RemoteTransport = "mqtt" | "unifiedpush";
export type MessageKind = "command" | "settings";

export type CommandField = {
  key: string;
  label: string;
  kind: "text" | "textarea" | "boolean" | "integer" | "choice";
  placeholder?: string;
  required?: boolean;
  options?: readonly string[];
};
export type CommandDefinition = {
  name: string;
  label: string;
  description: string;
  fields?: readonly CommandField[];
  defaults?: JsonObject;
};

/** Matches the user-facing commands supported by InboundCommandMessage.kt. */
export const COMMANDS: readonly CommandDefinition[] = [
  {
    name: "toast",
    label: "Toast",
    description: "Display a short on-screen message.",
    fields: [
      {
        key: "message",
        label: "Message",
        kind: "textarea",
        placeholder: "Hello!",
      },
    ],
    defaults: { message: "Hello from Webview Kiosk!" },
  },
  {
    name: "go_to_url",
    label: "Go to URL",
    description: "Navigate to a specific page.",
    fields: [
      {
        key: "url",
        label: "URL",
        kind: "text",
        required: true,
        placeholder: "https://example.com",
      },
    ],
    defaults: { url: "https://webviewkiosk.nktnet.uk" },
  },
  {
    name: "search",
    label: "Search",
    description: "Search using the device's configured search provider.",
    fields: [
      { key: "query", label: "Search query", kind: "text", required: true },
    ],
  },
  {
    name: "go_back",
    label: "Go back",
    description: "Navigate back in history.",
  },
  {
    name: "go_forward",
    label: "Go forward",
    description: "Navigate forward in history.",
  },
  {
    name: "go_home",
    label: "Go home",
    description: "Open the configured home page.",
  },
  {
    name: "refresh",
    label: "Refresh",
    description: "Reload the current page.",
  },
  {
    name: "clear_history",
    label: "Clear history",
    description: "Remove the navigation history.",
  },
  {
    name: "clear_cache",
    label: "Clear cache",
    description: "Clear WebView cache.",
  },
  {
    name: "page_up",
    label: "Page up",
    description: "Scroll up (or all the way to the top).",
    fields: [{ key: "absolute", label: "Scroll to top", kind: "boolean" }],
  },
  {
    name: "page_down",
    label: "Page down",
    description: "Scroll down (or all the way to the bottom).",
    fields: [{ key: "absolute", label: "Scroll to bottom", kind: "boolean" }],
  },
  {
    name: "lock",
    label: "Lock kiosk",
    description: "Start lock task / screen pinning mode.",
  },
  {
    name: "unlock",
    label: "Unlock kiosk",
    description: "Stop lock task / screen pinning mode.",
  },
  {
    name: "reconnect",
    label: "Reconnect MQTT",
    description: "Reconnect and resubscribe to MQTT topics.",
  },
  {
    name: "lock_device",
    label: "Lock device",
    description: "Turn the device screen off (requires privileges).",
  },
  {
    name: "notify",
    label: "Notification",
    description: "Display an Android notification (permission required).",
    fields: [
      {
        key: "contentTitle",
        label: "Title",
        kind: "text",
        placeholder: "MQTT",
      },
      {
        key: "contentText",
        label: "Content",
        kind: "textarea",
        placeholder: "Notify",
      },
      {
        key: "priority",
        label: "Priority",
        kind: "choice",
        options: ["MIN", "LOW", "DEFAULT", "HIGH", "MAX"],
      },
      {
        key: "timeout",
        label: "Auto-dismiss after (milliseconds; 0 to disable)",
        kind: "integer",
      },
      { key: "silent", label: "Silent notification", kind: "boolean" },
      { key: "onGoing", label: "Ongoing notification", kind: "boolean" },
      { key: "autoCancel", label: "Dismiss when tapped", kind: "boolean" },
    ],
    defaults: {
      contentTitle: "MQTT",
      contentText: "Hello from Webview Kiosk!",
      priority: "DEFAULT",
      timeout: 0,
      silent: false,
      onGoing: false,
      autoCancel: true,
    },
  },
  {
    name: "launch_package",
    label: "Launch app",
    description: "Launch an installed Android application.",
    fields: [
      {
        key: "packageName",
        label: "Package name",
        kind: "text",
        required: true,
        placeholder: "com.example.app",
      },
      {
        key: "activityName",
        label: "Activity name (optional)",
        kind: "text",
        placeholder: ".MainActivity",
      },
    ],
  },
];

export function commandDefinition(name: string): CommandDefinition | undefined {
  return COMMANDS.find((command) => command.name === name);
}

export function createRemotePayload(
  transport: RemoteTransport,
  kind: MessageKind,
  command = "toast",
): JsonObject {
  const prefix = transport === "unifiedpush" ? { type: kind } : {};
  if (kind === "settings") {
    return { ...prefix, data: { settings: { "appearance.theme": "LIGHT" } } };
  }
  const definition = commandDefinition(command);
  if (!definition) throw new Error(`Unsupported command: ${command}`);
  return {
    ...prefix,
    command,
    ...(definition.defaults ? { data: { ...definition.defaults } } : {}),
  };
}

export function stringifyPayload(message: JsonObject): string {
  return JSON.stringify(message, null, 2);
}

const NonEmptyStringSchema = v.pipe(v.string(), v.nonEmpty());
const NonBlankStringSchema = v.pipe(v.string(), v.trim(), v.nonEmpty());
const SettingsDataSchema = v.looseObject({ settings: JsonObjectSchema });
const NonEmptySettingsSchema = v.pipe(JsonObjectSchema, v.minEntries(1));
const IntegerSchema = v.pipe(v.number(), v.safeInteger());

const CommandMetadataSchema = v.looseObject({
  messageId: v.optional(v.string()),
  targetInstances: v.optional(v.array(v.string())),
  targetUsernames: v.optional(v.array(v.string())),
  interact: v.optional(v.boolean()),
  wakeScreen: v.optional(v.boolean()),
});
const SettingsMetadataSchema = v.looseObject({
  messageId: v.optional(v.string()),
  targetInstances: v.optional(v.array(v.string())),
  targetUsernames: v.optional(v.array(v.string())),
  showToast: v.optional(v.boolean()),
  reloadActivity: v.optional(v.boolean()),
});

/** Parses arbitrary JSON without dropping custom fields, so raw edits are lossless. */
export function parseRemotePayload(
  value: string,
  transport: RemoteTransport,
  kind: MessageKind,
): JsonObject {
  const message = parseJsonObject(
    value,
    "Enter a valid JSON payload.",
    "The payload must be a JSON object.",
  );
  if (transport === "unifiedpush" && !v.is(v.literal(kind), message.type)) {
    throw new Error(`UnifiedPush payload type must be "${kind}".`);
  }
  if (kind === "command" && !v.is(NonEmptyStringSchema, message.command)) {
    throw new Error('A command payload needs a non-empty "command" string.');
  }
  if (kind === "settings" && !v.is(SettingsDataSchema, message.data)) {
    throw new Error('A settings payload needs a "data.settings" object.');
  }
  return message;
}

export function assertEditablePayload(
  value: string,
  transport: RemoteTransport,
  kind: MessageKind,
): JsonObject {
  const message = parseRemotePayload(value, transport, kind);
  if (kind === "command") {
    const definition = commandDefinition(message.command as string);
    if (!definition)
      throw new Error("This command can only be edited in the JSON tab.");
    const data = message.data;
    if (data !== undefined && !isJsonObject(data))
      throw new Error("Command data must be a JSON object to use the form.");
    for (const field of definition.fields ?? []) {
      const fieldValue = isJsonObject(data) ? data[field.key] : undefined;
      if (
        fieldValue !== undefined
        && !isFieldValue(field.kind, fieldValue, field.options)
      ) {
        throw new Error(
          `${field.label} has an unsupported value. Edit it in the JSON tab.`,
        );
      }
    }
  } else {
    const settings = (message.data as JsonObject).settings as JsonObject;
    for (const [key, settingValue] of Object.entries(settings)) {
      const setting = SETTINGS_BY_KEY.get(key);
      if (
        setting
        && !isFieldValue(setting.kind, settingValue, setting.options)
      ) {
        throw new Error(
          `${key} has an invalid value. Edit it in the JSON tab.`,
        );
      }
    }
  }
  return message;
}

function isFieldValue(
  kind: string,
  value: unknown,
  options?: readonly string[],
): boolean {
  switch (kind) {
    case "text":
    case "textarea":
    case "string":
      return v.is(v.string(), value);
    case "boolean":
      return v.is(v.boolean(), value);
    case "integer":
      return v.is(IntegerSchema, value);
    case "choice":
      return v.is(
        v.pipe(
          v.string(),
          v.check((item) => options?.includes(item) ?? false),
        ),
        value,
      );
    case "multi-select":
      return v.is(
        v.array(
          v.pipe(
            v.string(),
            v.check((item) => options?.includes(item) ?? false),
          ),
        ),
        value,
      );
    default:
      return false;
  }
}

export function changeCommand(message: JsonObject, name: string): JsonObject {
  const definition = commandDefinition(name);
  if (!definition) throw new Error(`Unsupported command: ${name}`);
  const { data: _data, ...rest } = message;
  return {
    ...rest,
    command: name,
    ...(definition.defaults ? { data: { ...definition.defaults } } : {}),
  };
}

export function changeDataField(
  message: JsonObject,
  key: string,
  value: unknown,
): JsonObject {
  const data = isJsonObject(message.data) ? message.data : {};
  const nextData = { ...data };
  if (value === undefined) delete nextData[key];
  else nextData[key] = value;
  return { ...message, data: nextData };
}

export function changeSetting(
  message: JsonObject,
  key: string,
  value: unknown,
): JsonObject {
  const data = isJsonObject(message.data) ? message.data : {};
  const settings = isJsonObject(data.settings) ? data.settings : {};
  const nextSettings = { ...settings };
  if (value === undefined) delete nextSettings[key];
  else nextSettings[key] = value;
  return { ...message, data: { ...data, settings: nextSettings } };
}

export function addDefaultSetting(
  message: JsonObject,
  key: string,
): JsonObject {
  const setting = SETTINGS_BY_KEY.get(key);
  if (!setting) throw new Error(`Unknown setting key: ${key}`);
  return changeSetting(message, key, defaultSettingValue(setting));
}

export function changeTopLevelField(
  message: JsonObject,
  key: string,
  value: unknown,
): JsonObject {
  const next = { ...message };
  if (value === undefined) delete next[key];
  else next[key] = value;
  return next;
}

export function splitTargets(value: string): string[] {
  return [
    ...new Set(
      value
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ];
}

/** Basic preflight for both transports. Unknown commands/keys remain valid via JSON. */
export function validateRemotePayload(
  value: string,
  transport: RemoteTransport,
  kind: MessageKind,
): string {
  const message = parseRemotePayload(value, transport, kind);
  const metadata = v.safeParse(
    kind === "command" ? CommandMetadataSchema : SettingsMetadataSchema,
    message,
  );
  if (!metadata.success) {
    const key = metadata.issues[0]?.path?.[0]?.key;
    if (key === "messageId") throw new Error("Message ID must be a string.");
    if (key === "targetInstances" || key === "targetUsernames") {
      throw new Error(`${key} must be an array of strings.`);
    }
    if (typeof key === "string") throw new Error(`${key} must be a boolean.`);
    throw new Error(v.summarize(metadata.issues));
  }
  if (kind === "command") {
    const command = commandDefinition(message.command as string);
    const data = isJsonObject(message.data) ? message.data : {};
    for (const field of command?.fields ?? []) {
      const fieldValue = data[field.key];
      if (field.required && !v.is(NonBlankStringSchema, fieldValue)) {
        throw new Error(`${field.label} is required for ${command?.label}.`);
      }
      if (
        fieldValue !== undefined
        && !isFieldValue(field.kind, fieldValue, field.options)
      ) {
        throw new Error(`${field.label} has an invalid value.`);
      }
    }
  } else {
    const settings = (message.data as JsonObject).settings;
    // The structural parser already guarantees this object is present.
    if (!v.is(NonEmptySettingsSchema, settings)) {
      throw new Error("Add at least one setting to update.");
    }
    for (const [key, settingValue] of Object.entries(settings)) {
      const setting = SETTINGS_BY_KEY.get(key);
      if (
        setting
        && !isFieldValue(setting.kind, settingValue, setting.options)
      ) {
        throw new Error(`${key} has an invalid value.`);
      }
    }
  }
  return JSON.stringify(message);
}
