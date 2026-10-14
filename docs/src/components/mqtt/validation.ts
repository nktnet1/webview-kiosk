import * as v from "valibot";

export type MqttQos = 0 | 1 | 2;

export type MqttProtocol = "" | "ws" | "wss";

const BrokerCredentialsSchema = v.object({
  brokerUrl: v.string(),
  // Optional for backups made before connection options were introduced.
  protocol: v.optional(v.picklist(["", "ws", "wss"]), ""),
  port: v.optional(v.string(), ""),
  path: v.optional(v.string(), ""),
  clientId: v.string(),
  username: v.string(),
  password: v.string(),
});

export type MqttCredentials = v.InferOutput<typeof BrokerCredentialsSchema>;
export const EMPTY_MQTT_CREDENTIALS: MqttCredentials = {
  brokerUrl: "",
  protocol: "",
  port: "",
  path: "",
  clientId: "",
  username: "",
  password: "",
};

export const MqttSubscriptionSchema = v.object({
  topic: v.string(),
  qos: v.union([v.literal(0), v.literal(1), v.literal(2)]),
});
export type MqttSubscription = v.InferOutput<typeof MqttSubscriptionSchema>;
export const DEFAULT_SUBSCRIPTIONS: MqttSubscription[] = [
  { topic: "wk/event/#", qos: 0 },
  { topic: "wk/response/#", qos: 0 },
];

export const MqttPreferencesSchema = v.object({
  commandTopic: v.string(),
  settingsTopic: v.string(),
  requestTopic: v.string(),
  customTopic: v.string(),
  subscriptions: v.array(MqttSubscriptionSchema),
});
export type MqttPreferences = v.InferOutput<typeof MqttPreferencesSchema>;
export const DEFAULT_PREFERENCES: MqttPreferences = {
  commandTopic: "wk/command",
  settingsTopic: "wk/settings",
  requestTopic: "wk/request",
  customTopic: "wk/debug",
  subscriptions: DEFAULT_SUBSCRIPTIONS,
};

export function parseMqttCredentials(value: unknown): MqttCredentials {
  const parsed = v.safeParse(BrokerCredentialsSchema, value);
  if (!parsed.success) throw new Error("Invalid MQTT credentials.");
  return parsed.output;
}

/** Resolve a broker address to the WebSocket URL used by the MQTT client.
 * Bare hosts, host:port, and http(s) URLs are accepted. Explicit schemes in
 * the address win unless a protocol override was selected.
 */
export function validateBrokerUrl(
  raw: string,
  options: Pick<MqttCredentials, "protocol" | "port" | "path"> = {
    protocol: "",
    port: "",
    path: "",
  },
): string {
  const value = raw.trim();
  if (!value || (value.startsWith("/") && !value.startsWith("//"))) {
    throw new Error("Enter a valid WebSocket broker address.");
  }

  const explicitScheme = value
    .match(/^([a-z][a-z0-9+.-]*):\/\//i)?.[1]
    ?.toLowerCase();
  if (
    explicitScheme
    && !["http", "https", "ws", "wss"].includes(explicitScheme)
  ) {
    throw new Error("Use a WebSocket broker address (ws:// or wss://).");
  }

  if (!v.is(v.picklist(["", "ws", "wss"]), options.protocol)) {
    throw new Error("Select a valid WebSocket protocol.");
  }

  const inferredProtocol =
    explicitScheme === "https" || explicitScheme === "wss"
      ? "wss"
      : explicitScheme === "http" || explicitScheme === "ws"
        ? "ws"
        : typeof location !== "undefined" && location.protocol === "http:"
          ? "ws"
          : "wss";
  const protocol = options.protocol || inferredProtocol;
  const withoutScheme = explicitScheme
    ? value.slice(explicitScheme.length + 3)
    : value.startsWith("//")
      ? value.slice(2)
      : value;

  let url: URL;
  try {
    url = new URL(`${protocol}://${withoutScheme}`);
  } catch {
    throw new Error("Enter a valid WebSocket broker address.");
  }
  if (!url.hostname || url.username || url.password || url.hash) {
    throw new Error(
      "Broker URLs cannot contain embedded credentials or fragments.",
    );
  }

  const port = options.port.trim();
  if (port) {
    const result = v.safeParse(
      v.pipe(
        v.string(),
        v.regex(/^\d+$/),
        v.transform(Number),
        v.integer(),
        v.minValue(1),
        v.maxValue(65535),
      ),
      port,
    );
    if (!result.success) {
      throw new Error("Port must be a whole number from 1 to 65535.");
    }
    url.port = String(result.output);
  }

  const path = options.path.trim();
  if (path) {
    if (
      !v.is(v.pipe(v.string(), v.regex(/^[^?#]+$/)), path)
      || [...path].some(
        (character) =>
          character.charCodeAt(0) < 32 || character.charCodeAt(0) === 127,
      )
    ) {
      throw new Error("Enter a path without query parameters or fragments.");
    }
    url.pathname = path.startsWith("/") ? path : `/${path}`;
  }

  if (
    typeof location !== "undefined"
    && location.protocol === "https:"
    && url.protocol === "ws:"
  ) {
    throw new Error("HTTPS pages require a secure wss:// broker URL.");
  }
  return url.toString();
}

export function validateTopic(value: string, filter: boolean): string {
  if (
    !v.is(v.pipe(v.string(), v.minLength(1)), value)
    || value.includes("\0")
  ) {
    throw new Error("Enter a valid MQTT topic.");
  }
  if (new TextEncoder().encode(value).length > 65_535) {
    throw new Error("MQTT topic is too long.");
  }
  const parts = value.split("/");
  if (!filter && (value.includes("#") || value.includes("+"))) {
    throw new Error("Publish topics cannot contain wildcards.");
  }
  if (
    filter
    && parts.some(
      (part, index) =>
        (part.includes("#") && (part !== "#" || index !== parts.length - 1))
        || (part.includes("+") && part !== "+"),
    )
  ) {
    throw new Error(
      "Invalid MQTT topic filter: + must occupy one level and # must be the last level.",
    );
  }
  return value;
}

export function validateKeepAlive(value: number): number {
  if (
    !v.is(
      v.pipe(v.number(), v.integer(), v.minValue(0), v.maxValue(65_535)),
      value,
    )
  ) {
    throw new Error(
      "Keep alive must be a whole number from 0 to 65535 seconds.",
    );
  }
  return value;
}
