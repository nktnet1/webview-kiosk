export const QR_INSTALLATION_SOURCES = [
  "GitHub",
  "F-Droid",
  "IzzyOnDroid",
] as const;

export type QrInstallationSource = (typeof QR_INSTALLATION_SOURCES)[number];

export type QrInstallationVersion = {
  code: number;
  tag: string;
  adminSignatureChecksum: string;
  downloadUrl: string;
};

export type DiscoveredQrInstallationVersion = Pick<
  QrInstallationVersion,
  "code" | "tag" | "downloadUrl"
>;

export const QR_INSTALLATION_FALLBACK_VERSION = {
  code: 134,
  tag: "v0.26.20",
  adminSignatureChecksum: "L-EN4OxwoH84OoeJLKRWZyFOoTxO7qSjJU86Mxp6axU=",
} as const;

declare const __QR_INSTALLATION_DISCOVERED_VERSIONS__: Partial<
  Record<QrInstallationSource, DiscoveredQrInstallationVersion>
>;

const discoveredVersions =
  typeof __QR_INSTALLATION_DISCOVERED_VERSIONS__ === "undefined"
    ? {}
    : __QR_INSTALLATION_DISCOVERED_VERSIONS__;

function fallbackDownloadUrl(source: QrInstallationSource): string {
  const { code, tag } = QR_INSTALLATION_FALLBACK_VERSION;

  switch (source) {
    case "GitHub":
      return `https://github.com/nktnet1/webview-kiosk/releases/download/${tag}/WebviewKiosk_${tag}.apk`;
    case "F-Droid":
      return `https://f-droid.org/repo/uk.nktnet.webviewkiosk_${code}.apk`;
    case "IzzyOnDroid":
      return `https://apt.izzysoft.de/fdroid/repo/uk.nktnet.webviewkiosk_${code}.apk`;
  }
}

function resolveVersion(source: QrInstallationSource): QrInstallationVersion {
  return {
    ...QR_INSTALLATION_FALLBACK_VERSION,
    downloadUrl: fallbackDownloadUrl(source),
    ...discoveredVersions[source],
  };
}

export const QR_INSTALLATION_VERSIONS = {
  GitHub: resolveVersion("GitHub"),
  "F-Droid": resolveVersion("F-Droid"),
  IzzyOnDroid: resolveVersion("IzzyOnDroid"),
} as const satisfies Record<QrInstallationSource, QrInstallationVersion>;
