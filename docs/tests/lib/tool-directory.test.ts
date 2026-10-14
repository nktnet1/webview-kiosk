import { describe, expect, it } from "vitest";
import { toolDirectory } from "#/lib/tool-directory";

describe("documentation tools directory", () => {
  it("lists all four tools with unique routes", () => {
    expect(toolDirectory.map((tool) => tool.href)).toEqual([
      "/tools/mqtt",
      "/tools/unifiedpush",
      "/tools/regex",
      "/tools/qr-installation",
    ]);
    expect(new Set(toolDirectory.map((tool) => tool.href)).size).toBe(
      toolDirectory.length,
    );
  });

  it("provides a useful label and description for each tool", () => {
    for (const tool of toolDirectory) {
      expect(tool.title.trim().length).toBeGreaterThan(0);
      expect(tool.description.trim().length).toBeGreaterThan(15);
    }
  });
});
