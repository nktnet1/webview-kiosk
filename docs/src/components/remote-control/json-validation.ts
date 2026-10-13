import * as v from "valibot";

/** Shared JSON object validation for the MQTT and UnifiedPush playgrounds. */
// Valibot's record schema also accepts arrays (and turns [] into {}). Reject
// them before the record parser runs so only JSON objects reach callers.
export const JsonObjectSchema = v.pipe(
  v.unknown(),
  v.check(
    (value) =>
      value !== null && typeof value === "object" && !Array.isArray(value),
    "Expected a JSON object.",
  ),
  v.record(v.string(), v.unknown()),
);
export type JsonObject = v.InferOutput<typeof JsonObjectSchema>;

// Check the input itself before Valibot parses it: v.record() can turn
// arrays into objects, silently changing the value being validated.
function hasJsonObjectShape(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function isJsonObject(value: unknown): value is JsonObject {
  return hasJsonObjectShape(value) && v.is(JsonObjectSchema, value);
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
  if (!hasJsonObjectShape(value)) throw new Error(invalidObjectMessage);
  const parsed = v.safeParse(JsonObjectSchema, value);
  if (!parsed.success) throw new Error(invalidObjectMessage);
  return parsed.output;
}
