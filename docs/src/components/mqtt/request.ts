/** MQTT-only request payloads. Command/settings payloads use RemoteMessageEditor. */
import * as v from "valibot";
import {
  isJsonObject,
  type JsonObject,
  parseJsonObject,
} from "#/components/remote-control/json-validation";
import { splitTargets } from "#/components/remote-control/payload";

export const REQUEST_TYPES = [
  { name: "get_status", label: "Get status" },
  { name: "get_settings", label: "Get settings" },
  { name: "get_system_info", label: "Get system info" },
  { name: "get_launchable_packages", label: "Get launchable apps" },
  { name: "get_lock_task_packages", label: "Get lock-task packages" },
] as const;

const RequestMetadataSchema = v.looseObject({
  requestType: v.pipe(v.string(), v.minLength(1)),
  messageId: v.optional(v.string()),
  targetInstances: v.optional(v.array(v.string())),
  targetUsernames: v.optional(v.array(v.string())),
  responseTopic: v.optional(v.string()),
  correlationData: v.optional(v.string()),
});

export function createRequestPayload(requestType = "get_status"): JsonObject {
  return { requestType };
}

export function parseRequestPayload(raw: string): JsonObject {
  const message = parseJsonObject(
    raw,
    "Enter valid request JSON.",
    "Request payload must be a JSON object.",
  );
  const result = v.safeParse(RequestMetadataSchema, message);
  if (!result.success) {
    throw new Error(
      "A request requires a non-empty requestType and valid metadata fields.",
    );
  }
  if (message.data !== undefined && !isJsonObject(message.data)) {
    throw new Error("Request data must be a JSON object.");
  }
  return message;
}

export function changeRequestType(
  message: JsonObject,
  requestType: string,
): JsonObject {
  const { data: _data, ...rest } = message;
  return { ...rest, requestType };
}

export function setRequestSettingKeys(
  message: JsonObject,
  value: string,
): JsonObject {
  const data = isJsonObject(message.data) ? message.data : {};
  const settings = splitTargets(value);
  const nextData = { ...data };
  if (settings.length) nextData.settings = settings;
  else delete nextData.settings;
  return { ...message, data: nextData };
}

/** Advanced get_settings entries with flags are JSON-only so the form cannot silently discard them. */
export function assertEditableRequest(raw: string): JsonObject {
  const message = parseRequestPayload(raw);
  if (!REQUEST_TYPES.some((item) => item.name === message.requestType)) {
    throw new Error("Custom requests can only be edited in the JSON tab.");
  }
  if (message.requestType === "get_settings" && isJsonObject(message.data)) {
    const settings = message.data.settings;
    if (
      settings !== undefined
      && (!Array.isArray(settings)
        || !settings.every((item) => typeof item === "string"))
    ) {
      throw new Error(
        "Advanced setting selectors can only be edited in the JSON tab.",
      );
    }
  }
  return message;
}
