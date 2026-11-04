#!/usr/bin/env python3
"""Run the R8 audit with sensible defaults, regardless of scripts/ subfolders.

Without arguments, build smokeRelease, require the R8 mapping, and write a JSON
report. If exactly one Android 16 / 16 KiB emulator is running, also exercise
cold launch and the MQTT, WebView, and serialization runtime probes. Otherwise
run the static audit without touching any connected devices.

Explicit arguments are forwarded to the underlying r8_audit.py unchanged.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
REPORT_PATH = PROJECT_ROOT / "build/reports/r8/smokeRelease-runtime.json"


class SetupError(Exception):
    """User-actionable audit setup failure."""


def find_audit(scripts: Path) -> Path:
    """Discover the real audit after script reorganisation, without guessing paths."""
    matches = sorted(
        path for path in scripts.rglob("r8_audit.py")
        if path.is_file() and not any(part in {"__pycache__", ".venv"} for part in path.parts)
    )
    if len(matches) != 1:
        raise SetupError(
            f"Expected exactly one r8_audit.py beneath {scripts}; "
            f"found {len(matches)}: {', '.join(map(str, matches)) or '(none)'}"
        )
    return matches[0]


def find_adb(root: Path) -> str | None:
    located = shutil.which("adb")
    if located:
        return located
    sdk_paths = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    properties = root / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk_paths.append(line.split("=", 1)[1].replace("\\:", ":"))
    for sdk in filter(None, sdk_paths):
        candidate = Path(sdk).expanduser() / "platform-tools" / "adb"
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None


def adb_output(adb: str, *args: str) -> str:
    result = subprocess.run(
        [adb, *args], capture_output=True, text=True, errors="replace", timeout=12,
        check=False,
    )
    if result.returncode:
        raise SetupError(f"ADB {args!r} failed: {(result.stderr or result.stdout).strip()}")
    return result.stdout.strip()


def compatible_emulator(adb: str, serial: str) -> bool:
    def shell(*args: str) -> str:
        return adb_output(adb, "-s", serial, "shell", *args)

    if shell("getprop", "ro.build.version.sdk") != "36":
        return False
    if shell("getconf", "PAGESIZE") != "16384":
        return False
    if (shell("getprop", "ro.kernel.qemu") == "1" or
            shell("getprop", "ro.boot.qemu") == "1"):
        return True
    fingerprint = shell("getprop", "ro.build.fingerprint")
    return "sdk_gphone" in fingerprint or "generic/" in fingerprint


def select_emulator(adb: str | None) -> str | None:
    if adb is None:
        return None
    candidates = []
    for line in adb_output(adb, "devices").splitlines():
        match = re.fullmatch(r"(\S+)\s+device(?:\s+.*)?", line.strip())
        if not match:
            continue
        serial = match.group(1)
        try:
            if compatible_emulator(adb, serial):
                candidates.append(serial)
        except (SetupError, OSError, subprocess.TimeoutExpired):
            # Ignore disconnected/unresponsive devices, never instrument them.
            continue
    if len(candidates) > 1:
        raise SetupError(
            "Multiple compatible emulators are running: " + ", ".join(candidates) +
            ". Select one explicitly with --device --runtime --serial."
        )
    return candidates[0] if candidates else None


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    try:
        script = find_audit(PROJECT_ROOT / "scripts")
        if argv:
            command = [sys.executable, str(script), *argv]
        else:
            command = [
                sys.executable, str(script), "--require-mapping", "--json", str(REPORT_PATH),
            ]
            serial = select_emulator(find_adb(PROJECT_ROOT))
            if serial:
                print(f"Using compatible emulator {serial}; running R8 runtime probes.", flush=True)
                command.extend(["--device", "--runtime", "--serial", serial])
            else:
                print("No compatible emulator running: static R8 audit only.", flush=True)
        return subprocess.run(command, cwd=PROJECT_ROOT, check=False).returncode
    except (SetupError, OSError, subprocess.TimeoutExpired) as error:
        print(f"R8 audit setup error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
