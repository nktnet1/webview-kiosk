#!/usr/bin/env python3
"""Populate Robolectric's Maven cache before CI starts its unit test JVMs."""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

# Reuse the exact SDK/version table and checksum-verified downloader used by the
# offline Android test asset exporter. Robolectric resolves these JARs outside
# Gradle's dependency cache and otherwise downloads them during test execution.
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from scripts.android.export_android_test_assets import (  # noqa: E402
    ExportError,
    ROBOLECTRIC_SDK_VERSIONS,
    ROBOLECTRIC_MAVEN_URL,
    robolectric_artifact,
)

RETRY_DELAYS = (15, 30)
# Robolectric's own build downloads large Android runtimes from this mirror to
# avoid the 429 rate limits Maven Central applies to frequent large downloads.
# Retain Maven Central as a fallback for recently published or missing files.
GOOGLE_MAVEN_MIRROR_URL = (
    "https://maven-central.storage-download.googleapis.com/maven2/"
    "org/robolectric/android-all-instrumented"
)
RUNTIME_REPOSITORIES = (
    ("Google Maven Central mirror", GOOGLE_MAVEN_MIRROR_URL),
    ("Maven Central", ROBOLECTRIC_MAVEN_URL),
)


def fetch_artifact(repository: Path, version: str, extension: str) -> None:
    """Fetch from the mirror first, retaining verified local Maven artifacts."""
    errors = []
    for name, url in RUNTIME_REPOSITORIES:
        try:
            robolectric_artifact(repository, version, extension, repository_url=url)
            return
        except ExportError as error:
            errors.append(f"{name}: {error}")
    raise ExportError("Both Robolectric repositories failed: " + " | ".join(errors))


def prefetch(repository: Path) -> None:
    """Verify cached artifacts and retry bounded network failures before tests."""
    for sdk, version in ROBOLECTRIC_SDK_VERSIONS.items():
        for extension in ("pom", "jar"):
            for attempt in range(len(RETRY_DELAYS) + 1):
                try:
                    print(f"Robolectric API {sdk}: verifying {extension} ({version})", flush=True)
                    fetch_artifact(repository, version, extension)
                    break
                except ExportError as error:
                    if attempt == len(RETRY_DELAYS):
                        raise
                    delay = RETRY_DELAYS[attempt]
                    print(f"Robolectric API {sdk}: {error}. Retrying in {delay}s", file=sys.stderr,
                          flush=True)
                    time.sleep(delay)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository", type=Path, default=Path.home() / ".m2" / "repository",
        help="Robolectric's local Maven repository (default: ~/.m2/repository)",
    )
    args = parser.parse_args(argv)
    try:
        prefetch(args.repository.expanduser())
    except (ExportError, OSError) as error:
        print(f"Could not prepare Robolectric runtimes: {error}", file=sys.stderr)
        return 1
    print("All Robolectric runtime artifacts are verified and cached", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
