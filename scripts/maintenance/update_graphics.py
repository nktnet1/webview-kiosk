#!/usr/bin/env python3
"""Regenerate F-Droid metadata images and the GitHub social preview."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Preserve the ImageMagick arguments and output sizes from the original helper.
COMMANDS = (
    ("docs/src/assets/icon.svg", "-background", "none", "-size", "512x512",
     "metadata/en-US/images/icon.png"),
    ("misc/featureGraphic.svg", "-background", "none", "-size", "1024x500",
     "metadata/en-US/images/featureGraphic.png"),
    ("misc/featureGraphic.svg", "-background", "none", "-resize", "x640",
     "-gravity", "center", "-crop", "1280x640+0+0", "+repage",
     "misc/githubSocialPreview.png"),
)


def main() -> int:
    magick = shutil.which("magick")
    if magick is None:
        print("Error: 'magick' command not found. Please install ImageMagick.", file=sys.stderr)
        return 1
    for args in COMMANDS:
        try:
            result = subprocess.run([magick, *args], cwd=ROOT, check=False)
        except OSError as error:
            print(f"Error: unable to run ImageMagick: {error}", file=sys.stderr)
            return 1
        if result.returncode:
            return result.returncode
    return 0


if __name__ == "__main__":
    sys.exit(main())
