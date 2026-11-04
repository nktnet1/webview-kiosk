import { joinURL } from "ufo";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getBasePath } from "#/lib/basePath";

describe("documentation base paths", () => {
  afterEach(() => vi.unstubAllEnvs());

  it.each([
    ["", "/"],
    ["/", "/"],
    ["//", "/"],
    ["/webview-kiosk", "/webview-kiosk"],
    ["/webview-kiosk/", "/webview-kiosk"],
    ["webview-kiosk/", "/webview-kiosk"],
  ])("normalizes the configured base path %j to %j", (input, expected) => {
    vi.stubEnv("PUBLIC_DOCS_BASE_PATH", input);
    expect(getBasePath()).toBe(expected);
  });

  it("joins root-relative paths without creating protocol-relative URLs", () => {
    expect(joinURL("/", "/docs")).toBe("/docs");
    expect(joinURL("/", "/", "/docs")).toBe("/docs");
    expect(joinURL("/", "", "/docs")).toBe("/docs");
  });

  it.each([
    [
      "",
      "/__tsr/staticServerFnCache/test.json",
      "/__tsr/staticServerFnCache/test.json",
    ],
    [
      "/",
      "/__tsr/staticServerFnCache/test.json",
      "/__tsr/staticServerFnCache/test.json",
    ],
    [
      "/webview-kiosk",
      "/__tsr/staticServerFnCache/test.json",
      "/webview-kiosk/__tsr/staticServerFnCache/test.json",
    ],
    [
      "/webview-kiosk/",
      "__tsr/staticServerFnCache/test.json",
      "/webview-kiosk/__tsr/staticServerFnCache/test.json",
    ],
  ])("joins %j and %j as an absolute path", (basePath, path, expected) => {
    const joined = joinURL("/", basePath, path);
    expect(joined).toBe(expected);
    expect(new URL(joined, "https://example.com/docs/").origin).toBe(
      "https://example.com",
    );
  });
});
