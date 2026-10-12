import { fileURLToPath } from "node:url";
import { defineConfig, devices } from "@playwright/test";

/** Exercise the built documentation site, not mocked QR helper functions. */
const docsRoot = fileURLToPath(new URL("../", import.meta.url));
const docsBasePath = (process.env.PUBLIC_DOCS_BASE_PATH ?? "").replace(
  /\/$/,
  "",
);
const previewUrl = `http://127.0.0.1:4173${docsBasePath}/`;
export default defineConfig({
  testDir: `${docsRoot}/tests/e2e`,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [
        ["list"],
        [
          "html",
          { open: "never", outputFolder: `${docsRoot}/playwright-report` },
        ],
      ]
    : "list",
  outputDir: `${docsRoot}/test-results`,
  use: {
    ...devices["Desktop Chrome"],
    baseURL: previewUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "pnpm preview --host 127.0.0.1 --port 4173 --strictPort",
    cwd: docsRoot,
    url: `${previewUrl}provision-device/`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
