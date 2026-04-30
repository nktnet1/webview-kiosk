import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([
    ".next/**",
    ".source/**",
    "dist/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  {
    settings: {
      react: {
        // Use '19' instead of 'detect' as a workaround for Eslint v10:
        // https://gist.github.com/OscarGauss/1f305edf5b7c103bb2ee32ba479f4261
        version: "19"
      }
    }
  }
]);

export default eslintConfig;
