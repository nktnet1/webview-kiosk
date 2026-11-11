import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workflow = readFileSync(
  new URL("../../../.github/workflows/docs-ci.yaml", import.meta.url),
  "utf8",
);
const manifest = JSON.parse(
  readFileSync(new URL("../../package.json", import.meta.url), "utf8"),
) as { devDependencies: Record<string, string> };

describe("documentation browser CI", () => {
  it("keeps the Playwright container pinned to the package version", () => {
    const imageVersion = workflow.match(
      /image: mcr\.microsoft\.com\/playwright:v([\d.]+)-noble/,
    )?.[1];
    expect(imageVersion).toBe(manifest.devDependencies["@playwright/test"]);
  });
});
