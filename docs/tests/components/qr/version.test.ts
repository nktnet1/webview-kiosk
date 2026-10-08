import { beforeEach, describe, expect, it, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  vi.stubGlobal("__QR_INSTALLATION_DISCOVERED_VERSIONS__", undefined);
});

describe("QR_INSTALLATION_VERSIONS", () => {
  it("provides source-specific fallback download URLs without build-time discovery", async () => {
    const {
      QR_INSTALLATION_FALLBACK_VERSION: fallback,
      QR_INSTALLATION_VERSIONS: versions,
    } = await import("#/components/qr/version");

    expect(versions).toEqual({
      GitHub: {
        ...fallback,
        downloadUrl: `https://github.com/nktnet1/webview-kiosk/releases/download/${fallback.tag}/WebviewKiosk_${fallback.tag}.apk`,
      },
      "F-Droid": {
        ...fallback,
        downloadUrl: `https://f-droid.org/repo/uk.nktnet.webviewkiosk_${fallback.code}.apk`,
      },
      IzzyOnDroid: {
        ...fallback,
        downloadUrl: `https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_${fallback.code}.apk`,
      },
    });
  });

  it("overrides only successfully discovered sources and preserves the signing checksum", async () => {
    const github = {
      code: 901,
      tag: "v9.1.0",
      downloadUrl: "https://example.com/github-release.apk",
    };
    vi.stubGlobal("__QR_INSTALLATION_DISCOVERED_VERSIONS__", {
      GitHub: github,
    });
    const {
      QR_INSTALLATION_FALLBACK_VERSION: fallback,
      QR_INSTALLATION_VERSIONS: versions,
    } = await import("#/components/qr/version");

    expect(versions.GitHub).toEqual({
      ...github,
      adminSignatureChecksum: fallback.adminSignatureChecksum,
    });
    expect(versions["F-Droid"]).toMatchObject(fallback);
    expect(versions.IzzyOnDroid).toMatchObject(fallback);
  });

  it("keeps different published versions for each installation source", async () => {
    const discovered = {
      GitHub: {
        code: 901,
        tag: "v9.1.0",
        downloadUrl: "https://example.com/github.apk",
      },
      "F-Droid": {
        code: 900,
        tag: "v9.0.0",
        downloadUrl: "https://example.com/fdroid.apk",
      },
      IzzyOnDroid: {
        code: 899,
        tag: "v8.9.9",
        downloadUrl: "https://example.com/izzy.apk",
      },
    };
    vi.stubGlobal("__QR_INSTALLATION_DISCOVERED_VERSIONS__", discovered);
    const {
      QR_INSTALLATION_SOURCES: sources,
      QR_INSTALLATION_FALLBACK_VERSION: fallback,
      QR_INSTALLATION_VERSIONS: versions,
    } = await import("#/components/qr/version");

    for (const source of sources) {
      expect(versions[source]).toEqual({
        ...discovered[source],
        adminSignatureChecksum: fallback.adminSignatureChecksum,
      });
    }
  });
});
