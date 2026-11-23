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
    robolectric_artifact,
)

RETRY_DELAYS = (15, 30)


def prefetch(repository: Path) -> None:
    """Verify cached artifacts and retry bounded network failures before tests."""
    for sdk, version in ROBOLECTRIC_SDK_VERSIONS.items():
        for extension in ("pom", "jar"):
            for attempt in range(len(RETRY_DELAYS) + 1):
                try:
                    print(f"Robolectric API {sdk}: verifying {extension} ({version})", flush=True)
                    robolectric_artifact(repository, version, extension)
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
