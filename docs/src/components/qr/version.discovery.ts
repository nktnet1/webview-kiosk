import urlJoin from "url-join";
import {
  type DiscoveredQrInstallationVersion,
  QR_INSTALLATION_SOURCES,
  type QrInstallationSource,
} from "#/components/qr/version";

const APPLICATION_ID = "uk.nktnet.webviewkiosk";
const GITHUB_REPOSITORY = "nktnet1/webview-kiosk";
const FETCH_TIMEOUT_MS = 30_000;

const GITHUB_LATEST_RELEASE_URL = urlJoin(
  "https://api.github.com/repos/",
  GITHUB_REPOSITORY,
  "/releases/latest",
);
const F_DROID_PACKAGE_URL = urlJoin(
  "https://f-droid.org/api/v1/packages",
  APPLICATION_ID,
);
const IZZY_ON_DROID_REPO_URL = "https://apt.izzysoft.de/fdroid/repo/";
const IZZY_ON_DROID_INDEX_URL = urlJoin(
  IZZY_ON_DROID_REPO_URL,
  "index-v1.json",
);

type FetchLike = typeof fetch;

type DiscoveryContext = {
  fetchImpl: FetchLike;
  githubToken?: string;
};

type GitHubReleaseAsset = {
  name: string;
  browser_download_url: string;
};

type FdroidPackageVersion = {
  versionCode: number;
  versionName: string;
  apkName?: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parsePositiveInteger(value: unknown, label: string): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value <= 0) {
    throw new Error(`${label} must be a positive integer`);
  }

  return value;
}

function parseNonEmptyString(value: unknown, label: string): string {
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`${label} must be a non-empty string`);
  }

  return value;
}

function withTimeout(init: RequestInit = {}): RequestInit {
  return {
    ...init,
    signal: init.signal ?? AbortSignal.timeout(FETCH_TIMEOUT_MS),
  };
}

async function fetchJson(
  fetchImpl: FetchLike,
  url: string,
  init?: RequestInit,
): Promise<unknown> {
  const response = await fetchImpl(url, withTimeout(init));
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }

  return response.json();
}

async function fetchText(
  fetchImpl: FetchLike,
  url: string,
  init?: RequestInit,
): Promise<string> {
  const response = await fetchImpl(url, withTimeout(init));
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }

  return response.text();
}

function parseFdroidVersion(
  value: unknown,
  label: string,
): FdroidPackageVersion {
  if (!isRecord(value)) {
    throw new Error(`${label} must be an object`);
  }

  return {
    versionCode: parsePositiveInteger(
      value.versionCode,
      `${label}.versionCode`,
    ),
    versionName: parseNonEmptyString(value.versionName, `${label}.versionName`),
    apkName:
      typeof value.apkName === "string" && value.apkName.length > 0
        ? value.apkName
        : undefined,
  };
}

function findLatestVersion(
  values: unknown[],
  label: string,
): FdroidPackageVersion {
  if (values.length === 0) {
    throw new Error(`${label} did not contain any published versions`);
  }

  return values
    .map((value, index) => parseFdroidVersion(value, `${label}[${index}]`))
    .reduce((latest, candidate) =>
      candidate.versionCode > latest.versionCode ? candidate : latest,
    );
}

async function discoverGitHubVersion({
  fetchImpl,
  githubToken,
}: DiscoveryContext): Promise<DiscoveredQrInstallationVersion> {
  const headers: Record<string, string> = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2026-03-10",
  };
  if (githubToken) {
    headers.Authorization = `Bearer ${githubToken}`;
  }

  const releaseValue = await fetchJson(fetchImpl, GITHUB_LATEST_RELEASE_URL, {
    headers,
  });

  if (!isRecord(releaseValue)) {
    throw new Error("GitHub latest release response must be an object");
  }

  const tag = parseNonEmptyString(releaseValue.tag_name, "GitHub tag_name");
  if (!Array.isArray(releaseValue.assets)) {
    throw new Error("GitHub latest release assets must be an array");
  }

  const expectedApkName = `WebviewKiosk_${tag}.apk`;
  const apkAsset = releaseValue.assets.find(
    (asset): asset is GitHubReleaseAsset => {
      return (
        isRecord(asset)
        && asset.name === expectedApkName
        && typeof asset.browser_download_url === "string"
        && asset.browser_download_url.length > 0
      );
    },
  );

  if (!apkAsset) {
    throw new Error(
      `GitHub release ${tag} does not contain ${expectedApkName}`,
    );
  }

  const gradleUrl = `https://raw.githubusercontent.com/${GITHUB_REPOSITORY}/${encodeURIComponent(tag)}/app/build.gradle.kts`;
  const gradle = await fetchText(fetchImpl, gradleUrl);
  const versionCodeMatch = gradle.match(/\bversionCode\s*=\s*(\d+)/);
  const versionNameMatch = gradle.match(/\bversionName\s*=\s*"([^"]+)"/);

  if (!versionCodeMatch || !versionNameMatch) {
    throw new Error(
      `Unable to read version metadata for GitHub release ${tag}`,
    );
  }

  const code = parsePositiveInteger(
    Number.parseInt(versionCodeMatch[1], 10),
    "GitHub versionCode",
  );
  const versionName = versionNameMatch[1];

  if (tag !== `v${versionName}`) {
    throw new Error(
      `GitHub release tag ${tag} does not match Gradle versionName ${versionName}`,
    );
  }

  return {
    code,
    tag,
    downloadUrl: apkAsset.browser_download_url,
  };
}

async function discoverFDroidVersion({
  fetchImpl,
}: DiscoveryContext): Promise<DiscoveredQrInstallationVersion> {
  const packageValue = await fetchJson(fetchImpl, F_DROID_PACKAGE_URL);

  if (!isRecord(packageValue) || !Array.isArray(packageValue.packages)) {
    throw new Error("F-Droid package response must contain a packages array");
  }

  const latest = findLatestVersion(packageValue.packages, "F-Droid packages");

  return {
    code: latest.versionCode,
    tag: `v${latest.versionName}`,
    downloadUrl: `https://f-droid.org/repo/${APPLICATION_ID}_${latest.versionCode}.apk`,
  };
}

async function discoverIzzyOnDroidVersion({
  fetchImpl,
}: DiscoveryContext): Promise<DiscoveredQrInstallationVersion> {
  const indexValue = await fetchJson(fetchImpl, IZZY_ON_DROID_INDEX_URL);

  if (!isRecord(indexValue) || !isRecord(indexValue.packages)) {
    throw new Error("IzzyOnDroid index must contain a packages object");
  }

  const packageVersions = indexValue.packages[APPLICATION_ID];
  if (!Array.isArray(packageVersions)) {
    throw new Error(`IzzyOnDroid does not contain ${APPLICATION_ID}`);
  }

  const latest = findLatestVersion(packageVersions, "IzzyOnDroid packages");
  if (!latest.apkName) {
    throw new Error("IzzyOnDroid latest package is missing apkName");
  }

  return {
    code: latest.versionCode,
    tag: `v${latest.versionName}`,
    downloadUrl: new URL(latest.apkName, IZZY_ON_DROID_REPO_URL).toString(),
  };
}

const discoverers: Record<
  QrInstallationSource,
  (context: DiscoveryContext) => Promise<DiscoveredQrInstallationVersion>
> = {
  GitHub: discoverGitHubVersion,
  "F-Droid": discoverFDroidVersion,
  IzzyOnDroid: discoverIzzyOnDroidVersion,
};

export async function discoverQrInstallationVersions(
  options: { fetchImpl?: FetchLike; githubToken?: string } = {},
): Promise<
  Partial<Record<QrInstallationSource, DiscoveredQrInstallationVersion>>
> {
  const context: DiscoveryContext = {
    fetchImpl: options.fetchImpl ?? fetch,
    githubToken: options.githubToken,
  };
  const entries = await Promise.all(
    QR_INSTALLATION_SOURCES.map(async (source) => {
      try {
        console.log(`Fetching latest version for ${source}...`);
        return [source, await discoverers[source](context)] as const;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.warn(
          `[qr] Failed to discover latest ${source} version; using QR_INSTALLATION_FALLBACK_VERSION: ${message}`,
        );
        return [source, undefined] as const;
      }
    }),
  );

  const result = Object.fromEntries(
    entries.filter(
      (
        entry,
      ): entry is readonly [
        QrInstallationSource,
        DiscoveredQrInstallationVersion,
      ] => entry[1] !== undefined,
    ),
  );

  console.log("Versions:", result);

  return result;
}
