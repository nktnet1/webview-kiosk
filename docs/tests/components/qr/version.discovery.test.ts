import { beforeEach, describe, expect, it, vi } from "vitest";
import { discoverQrInstallationVersions } from "#/components/qr/version.discovery";

const githubReleaseUrl =
  "https://api.github.com/repos/nktnet1/webview-kiosk/releases/latest";
const githubGradleUrl =
  "https://raw.githubusercontent.com/nktnet1/webview-kiosk/v9.1.0/app/build.gradle.kts";
const fdroidUrl = "https://f-droid.org/api/v1/packages/uk.nktnet.webviewkiosk";
const izzyUrl = "https://apt.izzysoft.de/fdroid/repo/index-v1.json";

type Routes = Record<string, () => Response | Promise<Response>>;

const githubRelease = {
  tag_name: "v9.1.0",
  assets: [
    {
      name: "source.zip",
      browser_download_url: "https://example.com/source.zip",
    },
    {
      name: "WebviewKiosk_v9.1.0.apk",
      browser_download_url:
        "https://github.com/nktnet1/webview-kiosk/releases/download/v9.1.0/WebviewKiosk_v9.1.0.apk",
    },
  ],
};

const expectedVersions = {
  GitHub: {
    code: 901,
    tag: "v9.1.0",
    downloadUrl: githubRelease.assets[1].browser_download_url,
  },
  "F-Droid": {
    code: 900,
    tag: "v9.0.0",
    downloadUrl: "https://f-droid.org/repo/uk.nktnet.webviewkiosk_900.apk",
  },
  IzzyOnDroid: {
    code: 899,
    tag: "v8.9.9",
    downloadUrl:
      "https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_899.apk",
  },
};

function createDiscovery() {
  const routes: Routes = {
    [githubReleaseUrl]: () => Response.json(githubRelease),
    [githubGradleUrl]: () =>
      new Response('versionCode = 901\nversionName = "9.1.0"'),
    [fdroidUrl]: () =>
      Response.json({
        packages: [
          { versionCode: 898, versionName: "8.9.8" },
          { versionCode: 900, versionName: "9.0.0" },
          { versionCode: 899, versionName: "8.9.9" },
        ],
      }),
    [izzyUrl]: () =>
      Response.json({
        packages: {
          "other.application": [{ versionCode: 9999, versionName: "99.9.9" }],
          "uk.nktnet.webviewkiosk": [
            {
              versionCode: 899,
              versionName: "8.9.9",
              apkName: "uk.nktnet.webviewkiosk_899.apk",
            },
            {
              versionCode: 898,
              versionName: "8.9.8",
              apkName: "uk.nktnet.webviewkiosk_898.apk",
            },
          ],
        },
      }),
  };
  const fetchImpl = vi.fn<typeof fetch>(async (input) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.href
          : input.url;
    const respond = routes[url];
    if (!respond) throw new Error(`Unexpected request: ${url}`);
    return respond();
  });
  return { routes, fetchImpl };
}

beforeEach(() => {
  vi.spyOn(console, "log").mockImplementation(() => {});
  vi.spyOn(console, "warn").mockImplementation(() => {});
  // Any accidental use of real fetch must fail instead of contacting a service.
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>().mockRejectedValue(new Error("Unexpected real fetch")),
  );
});

describe("discoverQrInstallationVersions", () => {
  it("discovers each source independently and selects the highest published version code", async () => {
    const { fetchImpl } = createDiscovery();

    await expect(
      discoverQrInstallationVersions({ fetchImpl }),
    ).resolves.toEqual(expectedVersions);
    expect(fetchImpl).toHaveBeenCalledTimes(4);
    expect(console.warn).not.toHaveBeenCalled();
  });

  it("sends the GitHub token only to the GitHub API and bounds every request", async () => {
    const { fetchImpl } = createDiscovery();
    const signal = new AbortController().signal;
    const timeout = vi.spyOn(AbortSignal, "timeout").mockReturnValue(signal);

    await discoverQrInstallationVersions({
      fetchImpl,
      githubToken: "test-token",
    });

    expect(timeout).toHaveBeenCalledTimes(4);
    for (const [url, init] of fetchImpl.mock.calls) {
      expect(init?.signal).toBe(signal);
      const headers = new Headers(init?.headers);
      expect(headers.get("Authorization")).toBe(
        url === githubReleaseUrl ? "Bearer test-token" : null,
      );
      if (url === githubReleaseUrl) {
        expect(headers.get("Accept")).toBe("application/vnd.github+json");
      }
    }
    expect(
      timeout.mock.calls.every(([milliseconds]) => milliseconds === 30_000),
    ).toBe(true);
  });

  it("does not send an Authorization header without a token", async () => {
    const { fetchImpl } = createDiscovery();

    await discoverQrInstallationVersions({ fetchImpl });

    for (const [, init] of fetchImpl.mock.calls) {
      expect(new Headers(init?.headers).has("Authorization")).toBe(false);
    }
  });

  it("uses the global fetch implementation when none is injected", async () => {
    const { fetchImpl } = createDiscovery();
    vi.stubGlobal("fetch", fetchImpl);

    await expect(discoverQrInstallationVersions()).resolves.toEqual(
      expectedVersions,
    );
  });

  it.each([
    { source: "GitHub", url: githubReleaseUrl, status: 403 },
    { source: "F-Droid", url: fdroidUrl, status: 429 },
    { source: "IzzyOnDroid", url: izzyUrl, status: 503 },
  ] as const)(
    "isolates HTTP $status from $source without discarding other sources",
    async ({ source, url, status }) => {
      const { fetchImpl, routes } = createDiscovery();
      routes[url] = () => new Response("unavailable", { status });
      const expected = { ...expectedVersions };
      Reflect.deleteProperty(expected, source);

      await expect(
        discoverQrInstallationVersions({ fetchImpl }),
      ).resolves.toEqual(expected);
      expect(console.warn).toHaveBeenCalledWith(
        expect.stringContaining(`latest ${source} version`),
      );
      expect(console.warn).toHaveBeenCalledWith(
        expect.stringContaining(`HTTP ${status}`),
      );
    },
  );

  it.each([
    { label: "network failure", error: new TypeError("network failed") },
    {
      label: "timeout",
      error: new DOMException("request timed out", "TimeoutError"),
    },
  ])(
    "falls back for a $label while preserving successful discoveries",
    async ({ error }) => {
      const { fetchImpl, routes } = createDiscovery();
      routes[fdroidUrl] = () => Promise.reject(error);

      await expect(
        discoverQrInstallationVersions({ fetchImpl }),
      ).resolves.toEqual({
        GitHub: expectedVersions.GitHub,
        IzzyOnDroid: expectedVersions.IzzyOnDroid,
      });
      expect(console.warn).toHaveBeenCalledWith(
        expect.stringContaining(error.message),
      );
    },
  );

  it("handles malformed JSON as a source-specific failure", async () => {
    const { fetchImpl, routes } = createDiscovery();
    routes[izzyUrl] = () => new Response("{not-json");

    await expect(
      discoverQrInstallationVersions({ fetchImpl }),
    ).resolves.toEqual({
      GitHub: expectedVersions.GitHub,
      "F-Droid": expectedVersions["F-Droid"],
    });
  });

  it("returns an empty discovery result when all sources fail", async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockRejectedValue("offline");

    await expect(
      discoverQrInstallationVersions({ fetchImpl }),
    ).resolves.toEqual({});
    expect(console.warn).toHaveBeenCalledTimes(3);
  });

  it("starts other source requests while GitHub is still pending", async () => {
    const { fetchImpl, routes } = createDiscovery();
    let releaseGithub: (response: Response) => void = () => {
      throw new Error("GitHub request did not start");
    };
    routes[githubReleaseUrl] = () =>
      new Promise<Response>((resolve) => {
        releaseGithub = resolve;
      });

    const discovery = discoverQrInstallationVersions({ fetchImpl });
    const requestedUrls = fetchImpl.mock.calls.map(([url]) => url);
    // Always settle the pending request, even when an assertion fails.
    releaseGithub(Response.json(githubRelease));
    const versions = await discovery;

    expect(requestedUrls).toEqual(
      expect.arrayContaining([githubReleaseUrl, fdroidUrl, izzyUrl]),
    );
    expect(versions).toEqual(expectedVersions);
  });
});

describe("GitHub release validation", () => {
  it.each([
    { label: "non-object response", release: null },
    { label: "empty tag", release: { ...githubRelease, tag_name: "" } },
    { label: "non-string tag", release: { ...githubRelease, tag_name: 901 } },
    { label: "non-array assets", release: { ...githubRelease, assets: {} } },
    { label: "missing APK", release: { ...githubRelease, assets: [] } },
    {
      label: "wrong APK name",
      release: {
        ...githubRelease,
        assets: [{ ...githubRelease.assets[1], name: "other.apk" }],
      },
    },
    {
      label: "empty APK URL",
      release: {
        ...githubRelease,
        assets: [{ ...githubRelease.assets[1], browser_download_url: "" }],
      },
    },
  ])(
    "rejects a $label before fetching Gradle metadata",
    async ({ release }) => {
      const { fetchImpl, routes } = createDiscovery();
      routes[githubReleaseUrl] = () => Response.json(release);

      const versions = await discoverQrInstallationVersions({ fetchImpl });

      expect(versions.GitHub).toBeUndefined();
      expect(versions["F-Droid"]).toEqual(expectedVersions["F-Droid"]);
      expect(versions.IzzyOnDroid).toEqual(expectedVersions.IzzyOnDroid);
      expect(fetchImpl.mock.calls.map(([url]) => url)).not.toContain(
        githubGradleUrl,
      );
    },
  );

  it.each([
    { label: "missing versionCode", gradle: 'versionName = "9.1.0"' },
    { label: "missing versionName", gradle: "versionCode = 901" },
    {
      label: "zero versionCode",
      gradle: 'versionCode = 0\nversionName = "9.1.0"',
    },
    {
      label: "mismatched release tag",
      gradle: 'versionCode = 901\nversionName = "9.0.0"',
    },
  ])("falls back when Gradle has a $label", async ({ gradle }) => {
    const { fetchImpl, routes } = createDiscovery();
    routes[githubGradleUrl] = () => new Response(gradle);

    const versions = await discoverQrInstallationVersions({ fetchImpl });

    expect(versions.GitHub).toBeUndefined();
    expect(console.warn).toHaveBeenCalledWith(
      expect.stringContaining("latest GitHub version"),
    );
  });
});

describe("F-Droid and IzzyOnDroid package validation", () => {
  const invalidVersions = [
    { label: "non-object entry", value: null },
    { label: "zero code", value: { versionCode: 0, versionName: "9.0.0" } },
    {
      label: "negative code",
      value: { versionCode: -1, versionName: "9.0.0" },
    },
    {
      label: "fractional code",
      value: { versionCode: 1.5, versionName: "9.0.0" },
    },
    {
      label: "string code",
      value: { versionCode: "900", versionName: "9.0.0" },
    },
    { label: "missing name", value: { versionCode: 900 } },
    { label: "empty name", value: { versionCode: 900, versionName: "" } },
  ];

  describe.each([
    { source: "F-Droid", url: fdroidUrl },
    { source: "IzzyOnDroid", url: izzyUrl },
  ] as const)("$source", ({ source, url }) => {
    it.each(invalidVersions)("rejects a $label", async ({ value }) => {
      const { fetchImpl, routes } = createDiscovery();
      const packages = [value];
      routes[url] = () =>
        Response.json({
          packages:
            source === "F-Droid"
              ? packages
              : { "uk.nktnet.webviewkiosk": packages },
        });

      const versions = await discoverQrInstallationVersions({ fetchImpl });

      expect(versions[source]).toBeUndefined();
      expect(versions.GitHub).toEqual(expectedVersions.GitHub);
      expect(console.warn).toHaveBeenCalledWith(
        expect.stringContaining(`latest ${source} version`),
      );
    });

    it("rejects an empty published-version list", async () => {
      const { fetchImpl, routes } = createDiscovery();
      routes[url] = () =>
        Response.json({
          packages:
            source === "F-Droid" ? [] : { "uk.nktnet.webviewkiosk": [] },
        });

      expect(
        (await discoverQrInstallationVersions({ fetchImpl }))[source],
      ).toBeUndefined();
    });
  });

  it.each([
    { label: "missing packages", index: {} },
    { label: "missing application", index: { packages: {} } },
    {
      label: "non-array application versions",
      index: { packages: { "uk.nktnet.webviewkiosk": {} } },
    },
    {
      label: "missing APK name",
      index: {
        packages: {
          "uk.nktnet.webviewkiosk": [
            { versionCode: 899, versionName: "8.9.9" },
          ],
        },
      },
    },
  ])("rejects an IzzyOnDroid index with $label", async ({ index }) => {
    const { fetchImpl, routes } = createDiscovery();
    routes[izzyUrl] = () => Response.json(index);

    expect(
      (await discoverQrInstallationVersions({ fetchImpl })).IzzyOnDroid,
    ).toBeUndefined();
  });

  it("rejects an F-Droid response without a packages array", async () => {
    const { fetchImpl, routes } = createDiscovery();
    routes[fdroidUrl] = () => Response.json({ packages: {} });

    expect(
      (await discoverQrInstallationVersions({ fetchImpl }))["F-Droid"],
    ).toBeUndefined();
  });
});
