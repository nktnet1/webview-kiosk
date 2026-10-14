import * as v from "valibot";

export type MqttQos = 0 | 1 | 2;
export type MqttProtocol = "ws" | "wss";

// Accept the previous (auto / unset) connection options when loading backups.
const StoredBrokerCredentialsSchema = v.object({
  brokerUrl: v.string(),
  protocol: v.optional(v.picklist(["", "ws", "wss"]), ""),
  port: v.optional(v.string(), ""),
  path: v.optional(v.string(), ""),
  clientId: v.string(),
  username: v.string(),
  password: v.string(),
});

type StoredMqttCredentials = v.InferOutput<
  typeof StoredBrokerCredentialsSchema
>;
export type MqttCredentials = Omit<StoredMqttCredentials, "protocol"> & {
  protocol: MqttProtocol;
};

export const EMPTY_MQTT_CREDENTIALS: MqttCredentials = {
  brokerUrl: "",
  protocol: "wss",
  port: "443",
  path: "/mqtt",
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

/** Upgrade previously saved URL-based broker settings without losing their
 * protocol, port or path. Invalid legacy URLs are left untouched so the user
 * can correct them, rather than silently connecting to a different broker.
 */
export function normalizeMqttCredentials(
  credentials: StoredMqttCredentials,
): MqttCredentials {
  let brokerUrl = credentials.brokerUrl.trim();
  let { protocol, port, path } = credentials;
  const legacyScheme = brokerUrl
    .match(/^(https?|wss?):\/\//i)?.[1]
    ?.toLowerCase();
  const hasOtherScheme =
    /^[a-z][a-z0-9+.-]*:\/\//i.test(brokerUrl) && !legacyScheme;
  const needsMigration = legacyScheme || /[/:?#]/.test(brokerUrl);

  if (needsMigration && !hasOtherScheme) {
    try {
      const url = new URL(legacyScheme ? brokerUrl : `wss://${brokerUrl}`);
      if (
        ["http:", "https:", "ws:", "wss:"].includes(url.protocol)
        && url.hostname
        && !url.username
        && !url.password
        && !url.search
        && !url.hash
      ) {
        const inferredProtocol =
          url.protocol === "http:" || url.protocol === "ws:" ? "ws" : "wss";
        const nextProtocol = protocol || inferredProtocol;
        brokerUrl = url.hostname;
        protocol = nextProtocol;
        port = port || url.port || (nextProtocol === "ws" ? "80" : "443");
        path = path || (url.pathname === "/" ? "/mqtt" : url.pathname);
      }
    } catch {
      // Invalid legacy addresses must be fixed in the broker-domain field.
    }
  }

  return {
    ...credentials,
    brokerUrl,
    protocol: protocol || "wss",
    port: port || "443",
    path: path || "/mqtt",
  };
}

export function parseMqttCredentials(value: unknown): MqttCredentials {
  const parsed = v.safeParse(StoredBrokerCredentialsSchema, value);
  if (!parsed.success) throw new Error("Invalid MQTT credentials.");
  return normalizeMqttCredentials(parsed.output);
}

const BrokerPortSchema = v.pipe(
  v.string(),
  v.regex(/^\d+$/),
  v.transform(Number),
  v.integer(),
  v.minValue(1),
  v.maxValue(65535),
);

export function validateBrokerDomain(raw: string): string {
  const domain = raw.trim();
  if (!domain || /[/\\?#@\s%]/.test(domain)) {
    throw new Error("Enter a broker domain without a scheme, port or path.");
  }
  // IPv6 addresses must be bracketed. A colon elsewhere denotes a port.
  if (domain.includes(":") && !/^\[[\da-f:.]+\]$/i.test(domain)) {
    throw new Error("Enter a broker domain without a scheme, port or path.");
  }

  let url: URL;
  try {
    url = new URL(`wss://${domain}/`);
  } catch {
    throw new Error("Enter a valid broker domain or hostname.");
  }
  const hostname = url.hostname;
  if (!hostname || url.port || url.username || url.password) {
    throw new Error("Enter a broker domain without a scheme, port or path.");
  }
  if (!hostname.startsWith("[")) {
    // Allow DNS names, localhost, IPv4 and international domains (punycode).
    const labels = hostname.replace(/\.$/, "").split(".");
    if (
      hostname.length > 254
      || labels.some(
        (label) =>
          !v.is(v.pipe(v.string(), v.regex(/^[a-z0-9-]{1,63}$/i)), label)
          || label.startsWith("-")
          || label.endsWith("-"),
      )
    ) {
      throw new Error("Enter a valid broker domain or hostname.");
    }
  }
  return hostname;
}

export function validateBrokerPort(raw: string): number {
  const result = v.safeParse(BrokerPortSchema, raw.trim());
  if (!result.success) {
    throw new Error("Port is required (1–65535).");
  }
  return result.output;
}

export function validateBrokerPath(raw: string): string {
  const path = raw.trim();
  if (
    !v.is(v.pipe(v.string(), v.regex(/^\/[^?#]*$/)), path)
    || [...path].some(
      (character) =>
        character.charCodeAt(0) < 32 || character.charCodeAt(0) === 127,
    )
  ) {
    throw new Error(
      "Enter a path starting with / (without a query or fragment).",
    );
  }
  return path;
}

/** Construct the MQTT WebSocket endpoint from the required broker fields. */
export function validateBrokerUrl(
  raw: string,
  options: Pick<
    MqttCredentials,
    "protocol" | "port" | "path"
  > = EMPTY_MQTT_CREDENTIALS,
): string {
  const domain = validateBrokerDomain(raw);
  if (!v.is(v.picklist(["ws", "wss"]), options.protocol)) {
    throw new Error("Select WS or WSS for the broker protocol.");
  }
  const port = validateBrokerPort(options.port);
  const path = validateBrokerPath(options.path);
  const url = new URL(`${options.protocol}://${domain}/`);
  url.port = String(port);
  url.pathname = path;
  return validateWebSocketUrl(url.toString());
}

/** The transport consumes a complete URL; domain-only validation belongs to the form. */
export function validateWebSocketUrl(raw: string): string {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("Enter a valid WebSocket broker URL.");
  }
  if (
    (url.protocol !== "ws:" && url.protocol !== "wss:")
    || !url.hostname
    || url.username
    || url.password
    || url.hash
  ) {
    throw new Error(
      "Enter a valid WebSocket broker URL without embedded credentials or fragments.",
    );
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
