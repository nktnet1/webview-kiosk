import * as v from "valibot";
import { sanitizeStoredCredentials } from "#/components/remote-control/credential-json";
import {
  DEFAULT_PREFERENCES,
  EMPTY_MQTT_CREDENTIALS,
  type MqttCredentials,
  type MqttPreferences,
  MqttPreferencesSchema,
  normalizeMqttCredentials,
} from "./validation";

export const MQTT_CREDENTIALS_KEY = "webview-kiosk:mqtt-debug:credentials:v1";
export const MQTT_PREFERENCES_KEY = "webview-kiosk:mqtt-debug:preferences:v1";

export function loadMqttCredentials(raw: string | null): MqttCredentials {
  try {
    return raw
      ? normalizeMqttCredentials(
          sanitizeStoredCredentials(JSON.parse(raw), EMPTY_MQTT_CREDENTIALS),
        )
      : { ...EMPTY_MQTT_CREDENTIALS };
  } catch {
    return { ...EMPTY_MQTT_CREDENTIALS };
  }
}

export function loadMqttPreferences(raw: string | null): MqttPreferences {
  try {
    const result = v.safeParse(
      MqttPreferencesSchema,
      normalizeMqttCredentials,
      JSON.parse(raw ?? "null"),
    );
    if (!result.success)
      return {
        ...DEFAULT_PREFERENCES,
        subscriptions: [...DEFAULT_PREFERENCES.subscriptions],
      };
    return {
      ...result.output,
      subscriptions: result.output.subscriptions.slice(0, 30),
    };
  } catch {
    return {
      ...DEFAULT_PREFERENCES,
      subscriptions: [...DEFAULT_PREFERENCES.subscriptions],
    };
  }
}

export function saveMqttCredentials(
  storage: Pick<Storage, "setItem" | "removeItem">,
  credentials: MqttCredentials,
) {
  if (
    Object.entries(EMPTY_MQTT_CREDENTIALS).every(
      ([key, value]) => credentials[key as keyof MqttCredentials] === value,
    )
  )
    storage.removeItem(MQTT_CREDENTIALS_KEY);
  else storage.setItem(MQTT_CREDENTIALS_KEY, JSON.stringify(credentials));
}

export function saveMqttPreferences(
  storage: Pick<Storage, "setItem">,
  preferences: MqttPreferences,
) {
  storage.setItem(MQTT_PREFERENCES_KEY, JSON.stringify(preferences));
}
