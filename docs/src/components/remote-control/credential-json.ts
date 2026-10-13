/**
 * Import credential backups without mixing values from different endpoints.
 * Only keys from the supplied template are accepted and exported.
 */
export function parseCredentialJson<T extends object>(
  raw: string,
  template: T,
): T {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error("The credentials file must contain valid JSON.");
  }

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("The credentials file must contain a JSON object.");
  }

  const values = parsed as Record<string, unknown>;
  const keys = Object.keys(template);
  if (!keys.some((key) => Object.hasOwn(values, key))) {
    throw new Error(
      "The JSON does not contain any recognised credential fields.",
    );
  }

  for (const key of keys) {
    if (Object.hasOwn(values, key) && typeof values[key] !== "string") {
      throw new Error(`Credential field "${key}" must be a string.`);
    }
  }

  return Object.fromEntries(keys.map((key) => [key, values[key] ?? ""])) as T;
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
