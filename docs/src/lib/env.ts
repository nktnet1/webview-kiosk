import * as v from "valibot";

export const CLIENT_ENV_PREFIX = "PUBLIC_";

const envSchema = v.object({
  PUBLIC_DOCS_BASE_PATH: v.pipe(v.optional(v.string(), "/"), v.startsWith("/")),
});

const rawEnv =
  typeof process !== "undefined" && process.env ? process.env : import.meta.env;

const result = v.safeParse(envSchema, rawEnv);

if (!result.success) {
  throw new Error(v.summarize(result.issues));
}

export const env = result.output;
