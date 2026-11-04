#!/usr/bin/env python3
"""Build and cold-launch a minified APK on an Android 16 / 16 KiB emulator.

Without an APK argument, build the release-equivalent smokeRelease variant, which
uses a local Android debug signing key but keeps release R8/shrinking enabled.
It does not change production release signing or package already installed.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SMOKE_APK = ROOT / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk"
EXPECTED_API = "36"
EXPECTED_PAGE_SIZE = "16384"
SETTLE_SECONDS = 3


class SmokeTestError(Exception):
    """Expected test/setup failure; print a clear diagnostic without traceback."""


def command(args, *, timeout=30, check=True):
    try:
        result = subprocess.run(
            [str(arg) for arg in args],
            capture_output=True,
            text=True,
            errors="replace",
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise SmokeTestError(f"Could not run {args[0]}: {error}") from error
    if check and result.returncode != 0:
        details = (result.stdout + result.stderr).strip()
        raise SmokeTestError(f"Command failed: {' '.join(map(str, args))}\n{details}")
    return result


def find_sdk_tool(name, *, build_tool=False):
    direct = shutil.which(name)
    if direct:
        return direct

    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    local_properties = ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                roots.append(line.split("=", 1)[1].replace("\\:", ":"))

    for root in filter(None, roots):
        sdk = Path(root).expanduser()
        if build_tool:
            # Prefer the highest installed build-tools version.
            candidates = sorted(
                (sdk / "build-tools").glob(f"*/{name}"),
                key=lambda path: tuple(map(int, re.findall(r"\d+", path.parent.name))),
                reverse=True,
            )
        else:
            candidates = [sdk / "platform-tools" / name]
        for candidate in candidates:
            if candidate.is_file() and os.access(candidate, os.X_OK):
                return str(candidate)
    raise SmokeTestError(
        f"Missing {name}. Install Android SDK {'Build Tools' if build_tool else 'Platform Tools'} "
        "and set ANDROID_HOME (or add the tool to PATH)."
    )


def build_smoke_apk():
    """Build a release-optimised APK without requiring production signing secrets."""
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise SmokeTestError(f"Gradle wrapper not found: {wrapper}")
    print("Building R8-minified smokeRelease APK (signed with local debug key)...", flush=True)
    try:
        result = subprocess.run(
            [str(wrapper), ":app:assembleSmokeRelease", "--console=plain"],
            cwd=ROOT,
            timeout=1200,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise SmokeTestError(f"Could not build smokeRelease APK: {error}") from error
    if result.returncode:
        raise SmokeTestError(
            "smokeRelease build failed. See Gradle output above. "
            "The test variant does not require a production keystore."
        )
    if not SMOKE_APK.is_file():
        raise SmokeTestError(f"Gradle completed but smokeRelease APK is missing: {SMOKE_APK}")
    return SMOKE_APK


def select_emulator(adb, serial):
    """Find exactly one already-running compatible emulator, without touching others."""
    result = adb_command(adb, None, "devices")
    devices = []
    for line in result.stdout.splitlines():
        match = re.fullmatch(r"(\S+)\s+device(?:\s+.*)?", line.strip())
        if match:
            devices.append(match.group(1))
    if serial is not None:
        if serial not in devices:
            raise SmokeTestError(f"ADB device {serial!r} is not connected and ready.")
        validate_emulator(adb, serial)
        return serial

    compatible = []
    for device in devices:
        try:
            validate_emulator(adb, device, announce=False)
        except SmokeTestError:
            continue
        compatible.append(device)
    if not compatible:
        raise SmokeTestError(
            "No running Android 16 (API 36) emulator with 16 KiB pages. "
            "Start a compatible AVD in Android Studio before running this test. "
            f"Connected devices: {', '.join(devices) if devices else '(none)'}."
        )
    if len(compatible) > 1:
        raise SmokeTestError(
            "More than one compatible emulator is running: "
            + ", ".join(compatible) + ". Choose one with --serial."
        )
    validate_emulator(adb, compatible[0])
    return compatible[0]


def apk_details(aapt, apk):
    result = command([aapt, "dump", "badging", apk])
    text = result.stdout
    package = re.search(r"^package: name='([^']+)'", text, re.MULTILINE)
    activity = re.search(r"^launchable-activity: name='([^']+)'", text, re.MULTILINE)
    if not package or not activity:
        raise SmokeTestError("APK has no package name or launchable activity in aapt badging.")
    if re.search(r"^application-debuggable(?:\s|$)", text, re.MULTILINE):
        raise SmokeTestError("Refusing a debuggable APK: test a minified release APK instead.")
    # Include the release version in the output so the Play build is recognisable.
    package_line = next(
        (line for line in text.splitlines() if line.startswith("package: ")), ""
    )
    version_code = re.search(r"\bversionCode='([^']+)'", package_line)
    version_name = re.search(r"\bversionName='([^']+)'", package_line)
    version = (
        f"{version_name.group(1)} ({version_code.group(1)})"
        if version_name and version_code else "unknown"
    )
    return package.group(1), activity.group(1), version


def adb_command(adb, serial, *args, timeout=30, check=True):
    prefix = [adb]
    if serial:
        prefix.extend(["-s", serial])
    return command([*prefix, *args], timeout=timeout, check=check)


def validate_emulator(adb, serial, *, announce=True):
    def prop(name):
        return adb_command(adb, serial, "shell", "getprop", name).stdout.strip()

    api = prop("ro.build.version.sdk")
    pagesize = adb_command(adb, serial, "shell", "getconf", "PAGESIZE").stdout.strip()
    fingerprint = prop("ro.build.fingerprint")
    qemu = prop("ro.kernel.qemu") == "1" or prop("ro.boot.qemu") == "1"
    emulator = qemu or "sdk_gphone" in fingerprint or "generic/" in fingerprint
    if api != EXPECTED_API or pagesize != EXPECTED_PAGE_SIZE or not emulator:
        raise SmokeTestError(
            "Requires an Android 16 (API 36), 16 KiB-page emulator; "
            f"found API {api}, {pagesize}-byte pages, "
            f"fingerprint {fingerprint or '(unknown)'}."
        )
    if announce:
        print(f"Device: {serial} (API {api}, {pagesize}-byte pages, {fingerprint})")


def fatal_crash(logcat, package):
    """Extract an AndroidRuntime fatal crash belonging to the APK, not another app."""
    blocks = re.split(r"(?=FATAL EXCEPTION:)", logcat)
    process_line = re.compile(r"\bProcess:\s*" + re.escape(package) + r"(?=\s|,|$)")
    for block in blocks:
        if block.startswith("FATAL EXCEPTION:") and process_line.search(block):
            return block.strip()
    return None


def smoke_test(apk=None, serial=None):
    adb = find_sdk_tool("adb")
    # Fail early if there is no suitable emulator: don't start a costly build.
    serial = select_emulator(adb, serial)
    if apk is None:
        apk = build_smoke_apk()
    if not apk.is_file():
        raise SmokeTestError(f"APK not found: {apk}. Supply an existing signed APK.")

    aapt = find_sdk_tool("aapt", build_tool=True)
    package, activity, version = apk_details(aapt, apk)
    print(f"APK: {apk} (package {package}, version {version})")
    adb_command(adb, serial, "install", "-r", str(apk), timeout=120)
    adb_command(adb, serial, "shell", "am", "force-stop", package)
    adb_command(adb, serial, "logcat", "-c")

    print(f"Launching once: {package}/{activity}")
    started = adb_command(
        adb, serial, "shell", "am", "start", "-W", "-n", f"{package}/{activity}",
        timeout=45, check=False,
    )
    time.sleep(SETTLE_SECONDS)

    # The AndroidRuntime tag carries the original ExceptionInInitializerError trace.
    logs = adb_command(
        adb, serial, "logcat", "-d", "-b", "main", "-b", "crash",
        "-s", "AndroidRuntime:E", timeout=30,
    ).stdout
    crashed = fatal_crash(logs, package)
    pid = adb_command(adb, serial, "shell", "pidof", package, check=False)
    launch_output = (started.stdout + started.stderr).strip()
    launched = started.returncode == 0 and "Status: ok" in started.stdout
    alive = pid.returncode == 0 and bool(pid.stdout.strip())

    if crashed or not launched or not alive:
        details = ["FAIL: Release APK did not survive cold launch."]
        if crashed:
            details.append(f"AndroidRuntime crash:\n{crashed}")
        if not launched:
            details.append(f"Activity launch failed:\n{launch_output or '(no output)'}")
        if not alive:
            details.append(f"No running process for {package} after launch.")
        raise SmokeTestError("\n".join(details))

    print(f"PASS: {package} launched and remained alive; no AndroidRuntime fatal crash.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "apk", nargs="?", type=Path,
        help="Optional signed release APK; by default build an isolated, minified smokeRelease APK",
    )
    parser.add_argument("--serial", help="ADB device serial (required if multiple devices)")
    args = parser.parse_args(argv)
    try:
        smoke_test(args.apk.expanduser().resolve() if args.apk else None, serial=args.serial)
    except SmokeTestError as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
