#!/usr/bin/env python3
"""Run every local CI and R8 release check, including isolated AVD probes.

This command does not install SDK/Node tools or use production signing keys.
Gradle may resolve dependencies online; the AVD lives under build/verify/avd.
"""

import argparse
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SMOKE_APK = ROOT / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk"
VERIFY_REPORT = ROOT / "build/reports/verify/summary.json"
R8_REPORTS = ROOT / "build/reports/r8"
AVD_ROOT = ROOT / "build/verify/avd"
AVD_NAME = "r8-verify-api36-16k"
IMAGE_TAG = "google_apis_ps16k"

# Direct execution adds scripts/ to sys.path, not its parent. Package imports
# must also work via `python3 -m scripts.verify` and the regression suite.
if __name__ == "__main__" and not __package__:
    sys.path.insert(0, str(ROOT))


class VerificationError(Exception):
    """A failed check, missing prerequisite or unsafe emulator state."""


def sdk_root():
    candidates = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(line.split("=", 1)[1].replace("\\:", ":"))
    for candidate in filter(None, candidates):
        root = Path(candidate).expanduser()
        if root.is_dir():
            return root.resolve()
    raise VerificationError("Android SDK not found; set ANDROID_HOME or sdk.dir in local.properties")


def sdk_tool(sdk, name, *, build_tool=False):
    if build_tool:
        paths = sorted(
            (sdk / "build-tools").glob(f"*/{name}"),
            key=lambda path: tuple(int(n) for n in re.findall(r"\d+", path.parent.name)),
            reverse=True,
        )
    elif name == "avdmanager":
        paths = [sdk / "cmdline-tools/latest/bin/avdmanager"]
        paths.extend(sorted((sdk / "cmdline-tools").glob("*/bin/avdmanager"), reverse=True))
    else:
        paths = [sdk / folder / name for folder in ("platform-tools", "emulator")]
    for path in paths:
        if path.is_file() and os.access(path, os.X_OK):
            return str(path)
    found = shutil.which(name)
    if found:
        return found
    raise VerificationError(f"Android SDK tool '{name}' not installed in {sdk}")


def image_package(sdk):
    host = platform.machine().lower()
    arch = "arm64-v8a" if host in ("arm64", "aarch64") else "x86_64" if host in ("x86_64", "amd64") else None
    if arch is None:
        raise VerificationError(f"No supported Android 16 KB emulator image for host architecture: {host}")
    folder = sdk / "system-images/android-36" / IMAGE_TAG / arch
    if not (folder / "source.properties").is_file():
        raise VerificationError(
            "Android 16 (API 36) 16 KB-page emulator image is missing. "
            f"Install with sdkmanager 'system-images;android-36;{IMAGE_TAG};{arch}' "
            "(one time), or explicitly use --serial for an already-running compatible emulator."
        )
    return f"system-images;android-36;{IMAGE_TAG};{arch}"


def run_command(argv, *, env=None, input_text=None, timeout=None):
    try:
        return subprocess.run(
            [str(arg) for arg in argv], cwd=ROOT, env=env, input=input_text,
            text=True, encoding="utf-8", errors="replace", capture_output=True,
            timeout=timeout, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise VerificationError(f"Unable to run {' '.join(map(str, argv))}: {error}") from error


def checked(argv, *, env=None, input_text=None, timeout=60):
    result = run_command(argv, env=env, input_text=input_text, timeout=timeout)
    if result.returncode:
        tail = (result.stdout + result.stderr)[-1800:].strip()
        raise VerificationError(f"{' '.join(map(str, argv))} failed (exit {result.returncode}):\n{tail}")
    return result.stdout.strip()


def available_port(adb):
    devices = checked([adb, "devices"], timeout=30)
    connected = set(re.findall(r"^(emulator-\d+)\s+", devices, re.MULTILINE))
    for port in range(5580, 5680, 2):
        if f"emulator-{port}" in connected:
            continue
        with socket.socket() as console, socket.socket() as adb_socket:
            console.settimeout(0.1)
            adb_socket.settimeout(0.1)
            if console.connect_ex(("127.0.0.1", port)) == 0:
                continue
            if adb_socket.connect_ex(("127.0.0.1", port + 1)) == 0:
                continue
        return port
    raise VerificationError("No unused Android emulator console/ADB port pair available")


def adb_output(adb, serial, *arguments, timeout=20):
    return checked([adb, "-s", serial, *arguments], timeout=timeout)


def boot_emulator(adb, serial, name, process, timeout):
    deadline = time.monotonic() + timeout
    last_notice = time.monotonic()
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise VerificationError(f"Emulator exited during boot (exit {process.returncode})")
        state = run_command([adb, "-s", serial, "get-state"], timeout=15)
        if state.returncode == 0 and state.stdout.strip() == "device":
            avd = run_command([adb, "-s", serial, "emu", "avd", "name"], timeout=15)
            if avd.returncode == 0:
                avd_names = [line.strip() for line in avd.stdout.splitlines() if line.strip() != "OK"]
                if avd_names and avd_names[0] != name:
                    raise VerificationError(f"ADB serial {serial} belongs to {avd_names[0]}, not {name}")
                if avd_names and avd_names[0] == name:
                    ready = run_command([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"], timeout=15)
                    if ready.returncode == 0 and ready.stdout.strip() == "1":
                        from scripts.r8 import release_startup_smoke as smoke
                        try:
                            smoke.validate_emulator(adb, serial)
                        except smoke.SmokeTestError as error:
                            raise VerificationError(str(error)) from error
                        return
        if time.monotonic() - last_notice >= 30:
            print(f"[AVD] Still booting {serial}...", flush=True)
            last_notice = time.monotonic()
        time.sleep(2)
    raise VerificationError(f"Timed out booting test AVD {name} ({serial}) after {timeout}s")


@contextmanager
def test_emulator(name, timeout):
    """Boot and clean up only a project-private disposable AVD."""
    sdk = sdk_root()
    adb = sdk_tool(sdk, "adb")
    emulator = sdk_tool(sdk, "emulator")
    avdmanager = sdk_tool(sdk, "avdmanager")
    image = image_package(sdk)
    AVD_ROOT.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["ANDROID_AVD_HOME"] = str(AVD_ROOT)
    env.setdefault("ANDROID_HOME", str(sdk))
    avd_path = AVD_ROOT / f"{name}.avd"
    if not (avd_path / "config.ini").is_file():
        print(f"[AVD] Creating isolated {name} from {image}", flush=True)
        checked([avdmanager, "create", "avd", "-n", name, "-k", image,
                 "-p", str(avd_path)], env=env, input_text="no\n", timeout=120)
    port = available_port(adb)
    serial = f"emulator-{port}"
    log_path = ROOT / "build/reports/verify/emulator.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"[AVD] Starting disposable {name} ({serial}); log: {log_path.relative_to(ROOT)}", flush=True)
    with log_path.open("w", encoding="utf-8") as log:
        try:
            process = subprocess.Popen(
                [emulator, "-avd", name, "-port", str(port), "-no-window",
                 "-no-audio", "-no-boot-anim", "-no-snapshot", "-wipe-data"],
                cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=log,
                stderr=subprocess.STDOUT,
            )
        except OSError as error:
            raise VerificationError(f"Could not start Android emulator: {error}") from error
        try:
            boot_emulator(adb, serial, name, process, timeout)
            yield serial
        finally:
            if process.poll() is None:
                ours = False
                # Never kill someone else's AVD if the port was raced/reused.
                try:
                    identity = run_command([adb, "-s", serial, "emu", "avd", "name"], timeout=10)
                    ours = identity.returncode == 0 and name in identity.stdout.splitlines()
                    if ours:
                        run_command([adb, "-s", serial, "emu", "kill"], timeout=15)
                except VerificationError:
                    pass
                try:
                    process.wait(timeout=15 if ours else 1)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)
            print(f"[AVD] Stopped disposable {name}", flush=True)


def preflight(args):
    if sys.version_info < (3, 10):
        raise VerificationError("Python 3.10+ required")
    for name in ("sh", "node"):
        if not shutil.which(name):
            raise VerificationError(f"Required executable not found: {name}")
    if not (ROOT / "gradlew").is_file():
        raise VerificationError("Gradle wrapper not found")
    node_version = checked(["node", "-p", "process.versions.node"], timeout=10)
    if int(node_version.split(".", 1)[0]) < 24:
        raise VerificationError(f"Node.js 24+ required for the script suite; found {node_version}")
    # java -version writes to stderr; require JDK 21 to match CI and Kotlin.
    java_bin = Path(os.environ["JAVA_HOME"]) / "bin/java" if os.environ.get("JAVA_HOME") else "java"
    if not (java_bin.is_file() if isinstance(java_bin, Path) else shutil.which(java_bin)):
        raise VerificationError("JDK 21 executable not found; check JAVA_HOME and PATH")
    java_probe = run_command([java_bin, "-version"], timeout=10)
    java_info = java_probe.stdout + java_probe.stderr
    if java_probe.returncode or not re.search(r'\bversion "21(?:\.|"|\+)', java_info):
        raise VerificationError("JDK 21 required; set JAVA_HOME to the JetBrains 21 JDK used by CI")
    sdk = sdk_root()
    sdk_tool(sdk, "aapt", build_tool=True)
    sdk_tool(sdk, "dexdump", build_tool=True)
    sdk_tool(sdk, "zipalign", build_tool=True)
    if not args.skip_docs:
        if not shutil.which("pnpm"):
            raise VerificationError("pnpm is required for docs; enable it with Corepack")
        if not (ROOT / "docs/node_modules").is_dir():
            raise VerificationError("docs/node_modules missing; install locked docs dependencies first")
    if not args.skip_avd:
        if args.serial:
            from scripts.r8 import release_startup_smoke as smoke
            try:
                smoke.select_emulator(sdk_tool(sdk, "adb"), args.serial)
            except smoke.SmokeTestError as error:
                raise VerificationError(str(error)) from error
        else:
            sdk_tool(sdk, "adb")
            sdk_tool(sdk, "emulator")
            sdk_tool(sdk, "avdmanager")
            image_package(sdk)


def execute(label, argv, report):
    print(f"\n[verify] {label}: {' '.join(map(str, argv))}", flush=True)
    started = time.monotonic()
    try:
        result = subprocess.run([str(x) for x in argv], cwd=ROOT, check=False)
    except OSError as error:
        result = None
        details = str(error)
    else:
        details = f"exit {result.returncode}"
    duration = round(time.monotonic() - started, 2)
    success = result is not None and result.returncode == 0
    report["steps"].append({"name": label, "status": "pass" if success else "fail", "seconds": duration,
                            "command": [str(x) for x in argv], "result": details})
    print(f"[verify] {label}: {'PASS' if success else 'FAIL'} ({duration}s)", flush=True)
    if not success:
        raise VerificationError(f"{label} failed ({details})")


def verify(args, report):
    preflight(args)
    python = sys.executable
    execute("Script tests (Python + Node)", ["sh", "scripts/ci/test-local.sh", "checks"], report)
    execute("Android format, lint, JVM and minified APK", ["sh", "gradlew", "checkFormatting", "lint",
            ":adblock:test", ":app:testDebugUnitTest", ":app:assembleSmokeRelease",
            "--warning-mode=all", "--build-cache", "--stacktrace"], report)
    execute("R8 static audit", [python, "scripts/r8/r8_audit.py", SMOKE_APK,
            "--require-mapping", "--fail-on-warnings", "--json", R8_REPORTS / "smokeRelease.json"], report)
    if args.skip_docs:
        report["steps"].append({"name": "Documentation suites", "status": "skipped", "reason": "--skip-docs"})
    else:
        execute("Documentation lint, types and unit tests", ["sh", "scripts/ci/test-local.sh", "docs"], report)
        if args.skip_browser:
            report["steps"].append({"name": "Documentation browser tests", "status": "skipped", "reason": "--skip-browser"})
        else:
            execute("Documentation browser tests", ["sh", "scripts/ci/test-local.sh", "docs-browser"], report)
    if args.skip_avd:
        report["steps"].append({"name": "Minified APK emulator + runtime tests", "status": "skipped",
                                "reason": "--skip-avd"})
        return
    if args.serial:
        # The explicit --serial is already checked by preflight. It may be a
        # user-managed AVD: only use it with the user's deliberate opt-in.
        serial = args.serial
        execute("Minified APK emulator + runtime tests", [python, "scripts/r8/r8_audit.py", SMOKE_APK,
                "--require-mapping", "--fail-on-warnings", "--device", "--runtime", "--serial", serial,
                "--json", R8_REPORTS / "smokeRelease-runtime.json"], report)
    else:
        with test_emulator(args.avd, args.boot_timeout) as serial:
            execute("Minified APK emulator + runtime tests", [python, "scripts/r8/r8_audit.py", SMOKE_APK,
                    "--require-mapping", "--fail-on-warnings", "--device", "--runtime", "--serial", serial,
                    "--json", R8_REPORTS / "smokeRelease-runtime.json"], report)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-avd", action="store_true", help="Explicitly omit the AVD/runtime checks (partial verification)")
    parser.add_argument("--skip-docs", action="store_true", help="Explicitly omit documentation suites (partial verification)")
    parser.add_argument("--skip-browser", action="store_true", help="Explicitly omit Playwright (partial verification)")
    parser.add_argument("--serial", help="Use an existing compatible test emulator; it will not be stopped")
    parser.add_argument("--avd", default=AVD_NAME, help="Name of the isolated disposable AVD to auto-create")
    parser.add_argument("--boot-timeout", type=int, default=420, help="AVD boot timeout in seconds (default: 420)")
    args = parser.parse_args(argv)
    if args.serial and args.skip_avd:
        parser.error("--serial cannot be combined with --skip-avd")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.avd):
        parser.error("--avd must contain only letters, digits, hyphens and underscores")
    if args.boot_timeout < 10:
        parser.error("--boot-timeout must be at least 10 seconds")
    report = {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).isoformat(),
              "steps": [], "status": "fail", "error": None,
              "requested": {"avd": not args.skip_avd, "docs": not args.skip_docs,
                            "browser": not args.skip_docs and not args.skip_browser}}
    try:
        verify(args, report)
        report["status"] = "partial" if any(s["status"] == "skipped" for s in report["steps"]) else "pass"
    except (VerificationError, KeyboardInterrupt) as error:
        report["error"] = str(error) or "Interrupted"
        print(f"\n[verify] FAIL: {report['error']}", file=sys.stderr, flush=True)
    finally:
        VERIFY_REPORT.parent.mkdir(parents=True, exist_ok=True)
        VERIFY_REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"[verify] {report['status'].upper()} - report: {VERIFY_REPORT}", flush=True)
    return 1 if report["status"] == "fail" else 0


if __name__ == "__main__":
    sys.exit(main())
