import { describe, expect, it, vi } from "vitest";
import {
  resolveBuildQrInstallationVersions,
  shouldFetchQrInstallationVersions,
} from "#/components/qr/version.build";
import type { discoverQrInstallationVersions } from "#/components/qr/version.discovery";

describe("shouldFetchQrInstallationVersions", () => {
  it.each([undefined, "", "1", "true"])(
    "enables discovery when the env value is %s",
    (value) => {
      expect(shouldFetchQrInstallationVersions(value)).toBe(true);
    },
  );

  it.each(["0", "false", "FALSE"])(
    "disables discovery when the env value is %s",
    (value) => {
      expect(shouldFetchQrInstallationVersions(value)).toBe(false);
    },
  );
});

describe("resolveBuildQrInstallationVersions", () => {
  it("skips external requests when explicitly disabled", async () => {
    const discover = vi.fn<typeof discoverQrInstallationVersions>(async () => {
      throw new Error("Network unavailable");
    });

    await expect(
      resolveBuildQrInstallationVersions({ enableNetwork: false, discover }),
    ).resolves.toEqual({});
    expect(discover).not.toHaveBeenCalled();
  });

  it("discovers versions by default", async () => {
    const discovered = {
      GitHub: {
        code: 901,
        tag: "v9.1.0",
        downloadUrl: "https://example.test/release.apk",
      },
    };
    const discover = vi.fn<typeof discoverQrInstallationVersions>(
      async () => discovered,
    );

    await expect(
      resolveBuildQrInstallationVersions({
        githubToken: "build-token",
        discover,
      }),
    ).resolves.toEqual(discovered);
    expect(discover).toHaveBeenCalledTimes(1);
    expect(discover).toHaveBeenCalledWith({
      githubToken: "build-token",
    });
  });

  it("keeps the build working if version discovery unexpectedly rejects", async () => {
    const discover = vi.fn<typeof discoverQrInstallationVersions>(async () => {
      throw new Error("Registry unavailable");
    });
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    try {
      await expect(
        resolveBuildQrInstallationVersions({ discover }),
      ).resolves.toEqual({});
      expect(discover).toHaveBeenCalledTimes(1);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining("Registry unavailable"),
      );
    } finally {
      warn.mockRestore();
    }
  });
});
