import { discoverQrInstallationVersions } from "#/components/qr/version.discovery";

type Discovery = typeof discoverQrInstallationVersions;

/** Explicitly opt out with DOCS_FETCH_QR_VERSIONS=0 (or false). */
export function shouldFetchQrInstallationVersions(value?: string): boolean {
  return value !== "0" && value?.toLowerCase() !== "false";
}

/** Discover fresh QR download versions by default, but never fail the docs build. */
export async function resolveBuildQrInstallationVersions({
  enableNetwork = true,
  githubToken,
  discover = discoverQrInstallationVersions,
}: {
  enableNetwork?: boolean;
  githubToken?: string;
  discover?: Discovery;
} = {}): ReturnType<Discovery> {
  if (!enableNetwork) return {};

  try {
    return await discover({ githubToken });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(
      `[qr] Version discovery failed; using fallback versions: ${message}`,
    );
    return {};
  }
}
