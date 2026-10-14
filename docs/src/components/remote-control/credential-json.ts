import * as v from "valibot";
import { parseJsonObject } from "./json-validation";

/**
 * Import credential backups without mixing values from different endpoints.
 * Only keys from the supplied template are accepted and exported.
 */
export function parseCredentialJson<T extends object>(
  raw: string,
  template: T,
): T {
  const values = parseJsonObject(
    raw,
    "The credentials file must contain valid JSON.",
    "The credentials file must contain a JSON object.",
  );
  const keys = Object.keys(template);
  if (!keys.some((key) => Object.hasOwn(values, key))) {
    throw new Error(
      "The JSON does not contain any recognised credential fields.",
    );
  }

  // The dynamic schema accepts partial imports, defaults absent fields to
  // empty strings, and strips unknown keys rather than merging old secrets.
  const schema = v.object(
    Object.fromEntries(keys.map((key) => [key, v.optional(v.string(), "")])),
  );
  const result = v.safeParse(schema, values);
  if (!result.success) {
    const key = result.issues[0]?.path?.[0]?.key;
    throw new Error(`Credential field "${String(key)}" must be a string.`);
  }
  return result.output as T;
}

/** Lenient local storage loading, retaining valid fields from older backups. */
export function sanitizeStoredCredentials<T extends object>(
  value: unknown,
  template: T,
): T {
  const schema = v.object(
    Object.fromEntries(
      Object.keys(template).map((key) => [
        key,
        v.fallback(v.optional(v.string(), ""), ""),
      ]),
    ),
  );
  const result = v.safeParse(schema, value);
  return result.success ? (result.output as T) : { ...template };
}

/** Export the known credential fields only, including empty values. */
export function serializeCredentialJson<T extends object>(
  credentials: T,
  template: T,
): string {
  const values = credentials as Record<string, unknown>;
  return `${JSON.stringify(
    Object.fromEntries(Object.keys(template).map((key) => [key, values[key]])),
    null,
    2,
  )}\n`;
}

export function credentialsDiffer<T extends object>(
  previous: T,
  next: T,
  template: T,
): boolean {
  return Object.keys(template).some(
    (key) =>
      (previous as Record<string, unknown>)[key]
      !== (next as Record<string, unknown>)[key],
  );
}
