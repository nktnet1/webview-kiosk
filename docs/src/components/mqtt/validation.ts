import * as v from "valibot";

export type MqttQos = 0 | 1 | 2;

const BrokerCredentialsSchema = v.object({
  brokerUrl: v.string(),
  clientId: v.string(),
  username: v.string(),
  password: v.string(),
});

export type MqttCredentials = v.InferOutput<typeof BrokerCredentialsSchema>;
export const EMPTY_MQTT_CREDENTIALS: MqttCredentials = {
  brokerUrl: "",
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

export function validateBrokerUrl(raw: string): string {
  const value = raw.trim();
  let url: URL;
  try {
    url = new URL(value);
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
      "Use a ws:// or wss:// broker URL without embedded credentials or fragments.",
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
