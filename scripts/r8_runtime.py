"""Optional device probes against the R8-minified smokeRelease APK.

An isolated androidTest APK invokes real application classes in the target
process. None of the probe code is included in the production release APK.
"""

import hashlib
import os
import re
import subprocess
from pathlib import Path

if __package__:
    from . import release_startup_smoke as smoke
else:
    import release_startup_smoke as smoke

ROOT = Path(__file__).resolve().parents[1]
TEST_APK_DIR = ROOT / "app/build/outputs/apk/androidTest/smokeRelease"
RUNNER = "uk.nktnet.webviewkiosk.r8.R8RuntimeInstrumentation"
PROBES = (
    "json_serialization",
    "webview_javascript",
    "mqtt_tcp",
    "mqtt_websocket",
)


class RuntimeProbeError(Exception):
    """A build, device, or probe protocol failure."""


def build_test_apk():
    """Build only the test APK for the already-audited smokeRelease variant."""
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    try:
        build = subprocess.run(
            [str(wrapper), ":app:assembleSmokeReleaseAndroidTest", "--console=plain"],
            cwd=ROOT, timeout=1200, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RuntimeProbeError(f"Unable to build smokeRelease androidTest APK: {error}") from error
    if build.returncode:
        raise RuntimeProbeError("smokeRelease androidTest build failed; see Gradle output")
    candidates = list(TEST_APK_DIR.glob("*.apk"))
    if len(candidates) != 1:
        raise RuntimeProbeError(
            f"Expected one smokeRelease androidTest APK in {TEST_APK_DIR}; found {len(candidates)}"
        )
    return candidates[0]


def parse_instrumentation_output(output):
    """Parse framed am-instrument STATUS bundles, rejecting missing/duplicate probes."""
    results = {}
    frame = {}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, sep, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if sep:
                frame[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            name = frame.get("r8_check")
            if name:
                if name not in PROBES or name in results:
                    raise RuntimeProbeError(f"Unexpected or duplicate runtime probe: {name}")
                status = frame.get("r8_status")
                if status not in {"pass", "fail"}:
                    raise RuntimeProbeError(f"Invalid status for runtime probe {name}: {status}")
                details = {"probe": name}
                if status == "fail":
                    details["error"] = frame.get("r8_error", "No error reported")[:2000]
                elif name.startswith("mqtt_"):
                    # A successful MQTT probe must prove more than a TCP connect.
                    try:
                        subscriptions = int(frame.get("r8_subscriptions", ""))
                    except ValueError as error:
                        raise RuntimeProbeError(
                            f"MQTT probe {name} omitted subscription evidence"
                        ) from error
                    if (subscriptions != 3 or frame.get("r8_inbound") != "get_status"
                            or frame.get("r8_outbound") != "url_changed"):
                        raise RuntimeProbeError(f"MQTT probe {name} provided incomplete delivery evidence")
                    details.update({"subscriptionsAcknowledged": subscriptions,
                                    "inboundDelivery": "get_status",
                                    "outboundDelivery": "url_changed"})
                results[name] = (status, details)
            frame = {}
    if set(results) != set(PROBES):
        raise RuntimeProbeError(
            "Instrumentation did not complete all probes: "
            + ", ".join(sorted(set(PROBES) - set(results)))
        )
    return results


def run_runtime_probes(apk, *, serial=None):
    """Run test-only instrumentation against the exact smokeRelease APK."""
    if apk.resolve() != smoke.SMOKE_APK.resolve():
        raise RuntimeProbeError(
            "Runtime probes require the locally built smokeRelease APK; "
            "do not instrument a differently minified production APK"
        )
    original_digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    adb = smoke.find_sdk_tool("adb")
    selected = smoke.select_emulator(adb, serial)
    test_apk = build_test_apk()
    if hashlib.sha256(apk.read_bytes()).hexdigest() != original_digest:
        raise RuntimeProbeError("Target APK changed while building instrumentation; rerun the audit")
    aapt = smoke.find_sdk_tool("aapt", build_tool=True)
    package, _, _ = smoke.apk_details(aapt, apk)
    details = smoke.command([aapt, "dump", "badging", test_apk]).stdout
    matcher = re.search(
        r"^instrumentation: name='([^']+)' targetPackage='([^']+)'",
        details, re.MULTILINE,
    )
    if not matcher or matcher.groups() != (RUNNER, package):
        raise RuntimeProbeError("Test APK instrumentation runner/target mismatch")
    test_package = re.search(r"^package: name='([^']+)'", details, re.MULTILINE)
    if not test_package or test_package.group(1) != package + ".test":
        raise RuntimeProbeError("Test APK package is not the expected isolated test package")
    smoke.adb_command(adb, selected, "install", "-r", str(test_apk), timeout=120)
    run = smoke.adb_command(
        adb, selected, "shell", "am", "instrument", "-w", "-r",
        f"{package}.test/{RUNNER}", timeout=180, check=False,
    )
    try:
        results = parse_instrumentation_output(run.stdout)
    except RuntimeProbeError as error:
        # Include the instrumentation process failure/stack trace in the JSON
        # rather than incorrectly reporting a failed launch as skipped tests.
        tail = (run.stdout + run.stderr)[-3000:]
        raise RuntimeProbeError(f"{error}\nInstrumentation output tail:\n{tail}") from error
    if run.returncode or not re.search(r"^INSTRUMENTATION_CODE: 0\s*$", run.stdout, re.MULTILINE):
        if all(status == "pass" for status, _ in results.values()):
            raise RuntimeProbeError("Instrumentation ended without a successful completion code")
    return results, test_apk, selected
