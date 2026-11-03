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
# Android's raw instrumentation printer emits embedded newlines as
# unprefixed continuation lines. Keep both the concise errors and full traces.
MULTILINE_ERROR_KEYS = frozenset({
    "r8_error", "r8_cleanup_error", "r8_stack_trace", "r8_cleanup_stack_trace",
})


class RuntimeProbeError(Exception):
    """A build, device, or probe failure with optional structured diagnostics."""

    def __init__(self, message, *, details=None):
        super().__init__(message)
        self.details = details or {}


def crash_logcat_excerpt(logcat, target_package):
    """Return the target's Java crash, or recent relevant device diagnostics.

    Instrumentation's `shortMsg=Process crashed.` does not contain the
    exception. The AndroidRuntime stack lives in logcat, so include it in the
    JSON report rather than attempting to guess which R8 class is at fault.
    """
    lines = logcat.splitlines()
    starts = [index for index, line in enumerate(lines) if "FATAL EXCEPTION:" in line]
    process = re.compile(r"\bProcess:\s*" + re.escape(target_package) + r"(?=\s|,|:|$)")
    for start in reversed(starts):
        end = next((index for index in starts if index > start), len(lines))
        if any(process.search(line) for line in lines[start:min(end, start + 15)]):
            return "\n".join(lines[start:min(end, start + 160)])[:24000]
    # Native crashes and runner-load failures sometimes have no Java
    # FATAL EXCEPTION block. Keep only diagnostic lines, not whole logcat.
    markers = (
        target_package, "AndroidRuntime", "ActivityThread", "ActivityManager",
        "DEBUG", "libc", "R8RuntimeInstrumentation",
    )
    relevant = [line for line in lines if any(marker in line for marker in markers)]
    return "\n".join(relevant[-120:])[-24000:]


def instrumentation_failure_details(adb, serial, package, run):
    """Preserve the runner output and a bounded emulator crash excerpt."""
    details = {
        "instrumentationExitCode": run.returncode,
        "instrumentationOutput": (run.stdout + run.stderr)[-24000:],
        "deviceSerial": serial,
    }
    try:
        logcat = smoke.adb_command(
            adb, serial, "logcat", "-d", "-v", "threadtime",
            "-b", "main", "-b", "system", "-b", "crash", timeout=45,
        ).stdout
        excerpt = crash_logcat_excerpt(logcat, package)
        if excerpt:
            details["deviceLogcat"] = excerpt
        else:
            details["deviceLogcatNote"] = "No matching crash found in emulator logcat"
    except smoke.SmokeTestError as error:
        # Never mask the original instrumentation failure with an adb issue.
        details["deviceLogcatError"] = str(error)
    return details


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
    continuation_key = None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, sep, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            continuation_key = None
            if sep:
                frame[key] = value
                if key in MULTILINE_ERROR_KEYS:
                    continuation_key = key
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            continuation_key = None
            name = frame.get("r8_check")
            if name:
                if name not in PROBES or name in results:
                    raise RuntimeProbeError(f"Unexpected or duplicate runtime probe: {name}")
                status = frame.get("r8_status")
                if status not in {"pass", "fail"}:
                    raise RuntimeProbeError(f"Invalid status for runtime probe {name}: {status}")
                details = {"probe": name}
                if status == "fail":
                    details["error"] = frame.get("r8_error", "No error reported")
                    for source, field in (
                        ("r8_stack_trace", "stackTrace"),
                        ("r8_cleanup_error", "cleanupError"),
                        ("r8_cleanup_stack_trace", "cleanupStackTrace"),
                    ):
                        if source in frame:
                            details[field] = frame[source]
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
        elif continuation_key is not None:
            # Do not consume the instrumentation process summary as a trace
            # if it arrives without an expected STATUS_CODE delimiter.
            if line.startswith(("INSTRUMENTATION_CODE:", "INSTRUMENTATION_RESULT:",
                                "INSTRUMENTATION_FAILED:")):
                continuation_key = None
            else:
                frame[continuation_key] += "\n" + line
    if set(results) != set(PROBES):
        raise RuntimeProbeError(
            "Instrumentation did not complete all probes: "
            + ", ".join(sorted(set(PROBES) - set(results)))
        )
    return results


def validate_instrumentation_component(badging, manifest_tree, target_package):
    """Validate the test APK's actual compiled instrumentation manifest.

    `aapt dump badging` reports the package but omits instrumentation entries,
    even for a valid androidTest APK. Inspect `aapt dump xmltree` instead.
    """

    def attributes(line):
        return dict(re.findall(r"(?<!\S)(\w+)='([^']*)'", line))

    package_line = next(
        (line for line in badging.splitlines() if line.startswith("package: ")), ""
    )
    test_package = attributes(package_line).get("name")
    expected_package = target_package + ".test"
    if test_package != expected_package:
        raise RuntimeProbeError(
            "Test APK package is not the expected isolated test package: "
            f"expected {expected_package!r}; found {test_package!r}"
        )

    # Each E: node ends the previous element. Attributes are scoped to the
    # current instrumentation node, not a sibling application or activity.
    attribute_pattern = re.compile(
        r'^\s*A:\s+android:(name|targetPackage)(?:\([^)]*\))?="([^"]*)"'
    )
    declarations = []
    instrumentation = None
    for line in manifest_tree.splitlines():
        element = re.match(r"^\s*E:\s+([^\s(]+)", line)
        if element:
            if instrumentation is not None:
                declarations.append(
                    (instrumentation.get("name"), instrumentation.get("targetPackage"))
                )
            instrumentation = {} if element.group(1) == "instrumentation" else None
            continue
        if instrumentation is not None:
            match = attribute_pattern.match(line)
            if match:
                instrumentation[match.group(1)] = match.group(2)
    if instrumentation is not None:
        declarations.append(
            (instrumentation.get("name"), instrumentation.get("targetPackage"))
        )

    if (RUNNER, target_package) not in declarations:
        raise RuntimeProbeError(
            "Test APK instrumentation runner/target mismatch in AndroidManifest.xml: "
            f"expected {(RUNNER, target_package)!r}; found {declarations or 'none'}"
        )
    return f"{test_package}/{RUNNER}"


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
    manifest = smoke.command([aapt, "dump", "xmltree", test_apk, "AndroidManifest.xml"]).stdout
    component = validate_instrumentation_component(details, manifest, package)
    smoke.adb_command(adb, selected, "install", "-r", str(test_apk), timeout=120)
    # These probes only run on a disposable emulator. Clearing stale logs
    # ensures the next crash excerpt belongs to this run, not an older test.
    smoke.adb_command(adb, selected, "logcat", "-c")
    run = smoke.adb_command(
        adb, selected, "shell", "am", "instrument", "-w", "-r",
        component, timeout=180, check=False,
    )
    try:
        results = parse_instrumentation_output(run.stdout)
        if (run.returncode or not re.search(
            r"^INSTRUMENTATION_CODE: 0\s*$", run.stdout, re.MULTILINE
        )) and all(status == "pass" for status, _ in results.values()):
            raise RuntimeProbeError("Instrumentation ended without a successful completion code")
    except RuntimeProbeError as error:
        diagnostics = instrumentation_failure_details(adb, selected, package, run)
        diagnostic_lines = [f"{error}", "Instrumentation output tail:",
                            diagnostics["instrumentationOutput"][-3000:]]
        if diagnostics.get("deviceLogcat"):
            diagnostic_lines.extend(("Device crash logcat:", diagnostics["deviceLogcat"][:6000]))
        if diagnostics.get("deviceLogcatError"):
            diagnostic_lines.append("Could not read logcat: " + diagnostics["deviceLogcatError"])
        raise RuntimeProbeError("\n".join(diagnostic_lines), details=diagnostics) from error
    return results, test_apk, selected
