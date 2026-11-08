import { describe, expect, it, vi } from "vitest";
import { createAsyncRequestCache } from "#/lib/async-request-cache";

describe("createAsyncRequestCache", () => {
  it("reuses a successful result without making another request", async () => {
    const cache = createAsyncRequestCache<{ value: string }>();
    const load = vi.fn(async () => ({ value: "documentation" }));

    const first = await cache("/docs/index.json", load);
    const second = await cache("/docs/index.json", load);

    expect(second).toBe(first);
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("deduplicates concurrent requests for the same URL", async () => {
    const cache = createAsyncRequestCache<string>();
    let resolve!: (value: string) => void;
    const load = vi.fn(
      () =>
        new Promise<string>((done) => {
          resolve = done;
        }),
    );

    const first = cache("/docs/install.json", load);
    const second = cache("/docs/install.json", load);

    expect(second).toBe(first);
    await Promise.resolve();
    expect(load).toHaveBeenCalledTimes(1);

    resolve("loaded");
    await expect(first).resolves.toBe("loaded");
    await expect(second).resolves.toBe("loaded");
  });

  it("keeps separate URLs independent", async () => {
    const cache = createAsyncRequestCache<string>();
    const load = vi.fn(async () => "loaded");

    await Promise.all([
      cache("/docs/a.json", load),
      cache("/docs/b.json", load),
    ]);

    expect(load).toHaveBeenCalledTimes(2);
  });

  it("retries failed requests instead of caching rejections", async () => {
    const cache = createAsyncRequestCache<string>();
    const load = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(new Error("HTTP 503"))
      .mockResolvedValueOnce("recovered");

    await expect(cache("/docs/missing.json", load)).rejects.toThrow("HTTP 503");
    await expect(cache("/docs/missing.json", load)).resolves.toBe("recovered");
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("retries when a loader throws synchronously", async () => {
    const cache = createAsyncRequestCache<string>();
    const load = vi
      .fn<() => Promise<string>>()
      .mockImplementationOnce(() => {
        throw new Error("invalid URL");
      })
      .mockResolvedValueOnce("recovered");

    await expect(cache("/docs/invalid.json", load)).rejects.toThrow(
      "invalid URL",
    );
    await expect(cache("/docs/invalid.json", load)).resolves.toBe("recovered");
    expect(load).toHaveBeenCalledTimes(2);
  });
});
