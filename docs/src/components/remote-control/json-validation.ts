import * as v from "valibot";

/** Shared JSON object validation for the MQTT and UnifiedPush playgrounds. */
export const JsonObjectSchema = v.record(v.string(), v.unknown());
export type JsonObject = v.InferOutput<typeof JsonObjectSchema>;

export function isJsonObject(value: unknown): value is JsonObject {
  return v.is(JsonObjectSchema, value);
}

export function parseJsonObject(
  raw: string,
  invalidJsonMessage: string,
  invalidObjectMessage: string,
): JsonObject {
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new Error(invalidJsonMessage);
  }
  const parsed = v.safeParse(JsonObjectSchema, value);
  if (!parsed.success) throw new Error(invalidObjectMessage);
  return parsed.output;
}
