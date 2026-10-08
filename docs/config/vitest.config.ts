import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// Keep tests independent of the site's build plugins and release-discovery requests.
export default defineConfig({
  root: fileURLToPath(new URL("../", import.meta.url)),
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
    restoreMocks: true,
    unstubGlobals: true,
  },
});
