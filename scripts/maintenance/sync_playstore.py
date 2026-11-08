#!/usr/bin/env python3
"""Recreate the Play Store branch with its dedicated application ID."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

APPLICATION_ID = re.compile(
    r'(?m)^([ \t]*)applicationId = "uk\.nktnet\.webviewkiosk"(?=\r?$)'
)
REPLACEMENT = r'\1applicationId = "com.nktnet.webview_kiosk"'


def main() -> int:
    try:
        repo = Path(
            subprocess.check_output(
                ["git", "rev-parse", "--show-toplevel"], text=True
            ).strip()
        )

        def git(*args: str) -> None:
            subprocess.run(["git", *args], cwd=repo, check=True)

        git("switch", "playstore")
        git("reset", "--hard", "main")

        gradle_file = repo / "app" / "build.gradle.kts"
        with gradle_file.open(encoding="utf-8", newline="") as stream:
            original = stream.read()
        updated, matches = APPLICATION_ID.subn(REPLACEMENT, original)
        if matches != 1:
            print(
                f"Error: expected exactly one original applicationId in "
                f"{gradle_file}, found {matches}; not committing or pushing.",
                file=sys.stderr,
            )
            return 1

        with gradle_file.open("w", encoding="utf-8", newline="") as stream:
            stream.write(updated)

        git("add", "--", "app/build.gradle.kts")
        git("commit", "-m", "fix: playstore application ID")
        git("push", "--force")
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
