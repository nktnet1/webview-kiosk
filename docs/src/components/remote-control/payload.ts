/** Shared command/settings message format used by both MQTT and UnifiedPush. */
import { defaultSettingValue, SETTINGS_BY_KEY } from "./settings-catalog";

export type RemoteTransport = "mqtt" | "unifiedpush";
export type MessageKind = "command" | "settings";
export type JsonObject = Record<string, unknown>;

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

export function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

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

/** Parses arbitrary JSON without dropping custom fields, so raw edits are lossless. */
export function parseRemotePayload(
  value: string,
  transport: RemoteTransport,
  kind: MessageKind,
): JsonObject {
  let message: unknown;
  try {
    message = JSON.parse(value);
  } catch {
    throw new Error("Enter a valid JSON payload.");
  }
  if (!isJsonObject(message))
    throw new Error("The payload must be a JSON object.");
  if (transport === "unifiedpush" && message.type !== kind) {
    throw new Error(`UnifiedPush payload type must be "${kind}".`);
  }
  if (
    kind === "command"
    && (typeof message.command !== "string" || !message.command)
  ) {
    throw new Error('A command payload needs a non-empty "command" string.');
  }
  if (
    kind === "settings"
    && (!isJsonObject(message.data) || !isJsonObject(message.data.settings))
  ) {
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
      return typeof value === "string";
    case "boolean":
      return typeof value === "boolean";
    case "integer":
      return typeof value === "number" && Number.isSafeInteger(value);
    case "choice":
      return typeof value === "string" && (options?.includes(value) ?? false);
    case "multi-select":
      return (
        Array.isArray(value)
        && value.every(
          (item) => typeof item === "string" && options?.includes(item),
        )
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
  if (
    message.messageId !== undefined
    && typeof message.messageId !== "string"
  ) {
    throw new Error("Message ID must be a string.");
  }
  for (const field of ["targetInstances", "targetUsernames"] as const) {
    const targets = message[field];
    if (
      targets !== undefined
      && (!Array.isArray(targets)
        || !targets.every((item) => typeof item === "string"))
    ) {
      throw new Error(`${field} must be an array of strings.`);
    }
  }
  for (const field of kind === "command"
    ? ["interact", "wakeScreen"]
    : ["showToast", "reloadActivity"]) {
    if (message[field] !== undefined && typeof message[field] !== "boolean") {
      throw new Error(`${field} must be a boolean.`);
    }
  }
  if (kind === "command") {
    const command = commandDefinition(message.command as string);
    const data = isJsonObject(message.data) ? message.data : {};
    for (const field of command?.fields ?? []) {
      const fieldValue = data[field.key];
      if (
        field.required
        && (typeof fieldValue !== "string" || !fieldValue.trim())
      ) {
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
    const settings = (message.data as JsonObject).settings as JsonObject;
    if (Object.keys(settings).length === 0)
      throw new Error("Add at least one setting to update.");
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
