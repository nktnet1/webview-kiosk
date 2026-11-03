#!/usr/bin/env python3
"""Audit an actual R8-optimised APK with optional emulator runtime probes.

Without an APK argument, build smokeRelease using test signing. Write a JSON
report even on failures. Runtime probes cover only selected critical paths.
"""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import check_release_dex as dex
    from . import release_startup_smoke as smoke
    from . import r8_runtime as runtime
else:
    import check_release_dex as dex
    import release_startup_smoke as smoke
    import r8_runtime as runtime

ROOT = Path(__file__).resolve().parents[2]
# These entry points are called by JavaScript, not statically by the JVM. R8
# must preserve their original names and signatures. Keep in sync with the
# @JavascriptInterface declarations in app/src/main/java.
BRIDGES = {
    "uk.nktnet.webviewkiosk.blocking.WebViewRequests$Rules": {
        ("shouldBlock", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z"),
    },
    "uk.nktnet.webviewkiosk.utils.webview.interfaces.BatteryInterface": {
        ("getBatteryStatus", "()Ljava/lang/String;"),
    },
    "uk.nktnet.webviewkiosk.utils.webview.interfaces.BrightnessInterface": {
        ("getBrightness", "()I"),
        ("setBrightness", "(I)V"),
    },
    "uk.nktnet.webviewkiosk.utils.webview.interfaces.BlobInterface": {
        ("abortAllDownloads", "()V"),
        ("error", "(Ljava/lang/String;)V"),
        ("startDownload", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z"),
        ("appendChunk", "(Ljava/lang/String;Ljava/lang/String;)Z"),
        ("finishDownload", "(Ljava/lang/String;)Z"),
        ("abortDownload", "(Ljava/lang/String;)V"),
    },
    "uk.nktnet.webviewkiosk.utils.webview.interfaces.NfcInterface": {
        ("scan", "(Ljava/lang/String;)Z"),
        ("stopScan", "()V"),
        ("reset", "()V"),
        ("write", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
    },
}
METHOD_NAME = re.compile(r"^\s+name\s+: '([^']+)'$", re.MULTILINE)
METHOD_TYPE = re.compile(r"^\s+type\s+: '([^']+)'$", re.MULTILINE)
CLASS_MAPPING = re.compile(r"^([\w.$-]+) -> ([\w.$-]+):$")
WARN_DIRECTIVE = re.compile(r"^\s*-dontwarn\s+(\S+)", re.MULTILINE)
CLASS_ANNOTATIONS = re.compile(r"^Class #(\d+) annotations:$")
METHOD_ANNOTATIONS = re.compile(r"^Annotations on method #\d+ '([^']+)'$")
JS_ANNOTATION = "VISIBILITY_RUNTIME Landroid/webkit/JavascriptInterface;"
# These files are loaded at runtime through AssetManager or
# Class.getResourceAsStream, so a Kotlin/DEX check cannot establish that
# resource shrinking and APK packaging retained them. Other redirect payloads
# are discovered from the source resource inventory below.
CORE_RUNTIME_RESOURCES = frozenset({
    "assets/content-blocking/cosmetic.js",
    "assets/content-blocking/requests.js",
    "adblock/scriptlets.js",
    "adblock/scriptlets-extra.js",
    "adblock/scriptlets-network.js",
    "adblock/scriptlets-advanced.js",
})


class AuditError(Exception):
    """An expected audit failure, suitable for a JSON check result."""


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git_commit():
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
            text=True, check=False,
        )
    except OSError:
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def mapping_file(apk, explicit):
    if explicit is not None:
        return explicit
    # Only use the matching variant's build output. Never silently pair an
    # arbitrary downloaded APK with the checkout's possibly unrelated mapping.
    if apk.parent.parent.name == "apk" and apk.parent.parent.parent.name == "outputs":
        variant = apk.parent.name
        return apk.parent.parent.parent / "mapping" / variant / "mapping.txt"
    return None


def parse_mapping(path):
    if path is None or not path.is_file():
        return None
    classes = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = CLASS_MAPPING.fullmatch(line)
        if match:
            classes[match.group(1)] = match.group(2)
    if not classes:
        raise AuditError(f"R8 mapping has no class mappings: {path}")
    return classes


def dex_descriptor(name):
    return "L" + name.replace(".", "/") + ";"


def selected_classes(lines, wanted):
    """Stream only requested classes from dexdump, regardless of DEX size."""
    current = None
    content = []
    for line in lines:
        if dex.CLASS_HEADER.match(line):
            if current is not None:
                yield current, "".join(content)
            current = None
            content = [line]
        descriptor = dex.CLASS_DESCRIPTOR.match(line.rstrip("\r\n"))
        if descriptor and descriptor.group(1) in wanted:
            current = descriptor.group(1)
        if current is not None:
            content.append(line)
    if current is not None:
        yield current, "".join(content)


def declared_methods(class_text):
    """Return class-declared (name, DEX signature) pairs, not inherited calls."""
    members = list(dex.MEMBER_HEADER.finditer(class_text))
    found = set()
    for index, member in enumerate(members):
        end = members[index + 1].start() if index + 1 < len(members) else len(class_text)
        part = class_text[member.end():end]
        name = METHOD_NAME.search(part)
        signature = METHOD_TYPE.search(part)
        if name and signature and signature.group(1).startswith("("):
            found.add((name.group(1), signature.group(1)))
    return found


def dump_selected(tool, dex_path, wanted):
    annotations = {}
    annotation_class = None
    annotation_method = None

    def read_lines(stream):
        # dexdump -a lists method annotations before class definitions. Match
        # them by class index within this DEX, not by names that R8 may change.
        nonlocal annotation_class, annotation_method
        for line in stream:
            match = CLASS_ANNOTATIONS.match(line)
            if match:
                annotation_class = int(match.group(1))
                annotation_method = None
            match = METHOD_ANNOTATIONS.match(line)
            if match:
                annotation_method = match.group(1)
            if JS_ANNOTATION in line and annotation_class is not None and annotation_method:
                annotations.setdefault(annotation_class, set()).add(annotation_method)
            yield line

    try:
        with subprocess.Popen(
            [tool, "-a", "-n", str(dex_path)], stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, errors="replace",
        ) as process:
            found = {}
            for descriptor, class_text in selected_classes(read_lines(process.stdout), wanted):
                match = re.match(r"Class #(\d+)", class_text)
                if match is None:
                    raise AuditError(f"Missing class index in dexdump: {descriptor}")
                found[descriptor] = {
                    "methods": declared_methods(class_text),
                    "annotated": annotations.get(int(match.group(1)), set()),
                }
            status = process.wait()
    except OSError as error:
        raise AuditError(f"Could not execute dexdump: {error}") from error
    if status:
        raise AuditError(f"dexdump failed on {dex_path.name} (exit {status})")
    return found


def inspect_bridges(apk, dexdump, mapping):
    expected = {
        dex_descriptor(mapping.get(name, name) if mapping is not None else name): name
        for name in BRIDGES
    }
    if len(expected) != len(BRIDGES):
        raise AuditError("R8 mapping resolves multiple JavaScript bridges to one class")
    found = {}
    try:
        with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as folder:
            entries = [name for name in archive.namelist() if dex.DEX_NAME.fullmatch(name)]
            if not entries:
                raise AuditError("APK contains no classes*.dex")
            for entry in sorted(entries):
                path = Path(folder) / entry
                with archive.open(entry) as source, path.open("wb") as target:
                    shutil.copyfileobj(source, target)
                matches = dump_selected(dexdump, path, set(expected))
                for descriptor, methods in matches.items():
                    if descriptor in found:
                        raise AuditError(f"Duplicate class in multi-DEX APK: {descriptor}")
                    found[descriptor] = {"dex": entry, **methods}
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        raise AuditError(f"Could not inspect APK: {error}") from error

    missing = []
    verified = []
    for descriptor, original in sorted(expected.items()):
        match = found.get(descriptor)
        if match is None:
            missing.append(f"{original}: class {descriptor} not found")
            continue
        for method, signature in sorted(BRIDGES[original]):
            item = f"{original}.{method}{signature}"
            if (method, signature) not in match["methods"]:
                missing.append(f"{item}: method missing or renamed")
            elif method not in match["annotated"]:
                missing.append(f"{item}: runtime @JavascriptInterface annotation missing")
            else:
                verified.append({"entryPoint": item, "dex": match["dex"]})
    # Without mapping, absent classes may have simply been renamed. Do not
    # report an unprovable R8 regression as a confirmed failure.
    if missing and mapping is None:
        return "warn", {"verified": verified, "unverified": missing,
                        "reason": "Provide --mapping to resolve obfuscated class names"}
    if missing:
        return "fail", {"verified": verified, "missing": missing}
    return "pass", {"verified": verified}


def public_suffix_resources(apk):
    """Detect duplicate PSL data and verify the compact app resource survives."""
    raw_okhttp = "assets/PublicSuffixDatabase.list"
    raw_duplicate = "adblock/publicsuffix.list"
    normalized = "adblock/publicsuffix-serialized.json"
    try:
        with zipfile.ZipFile(apk) as archive:
            entries = set(archive.namelist())
            missing = [name for name in (raw_okhttp, normalized) if name not in entries]
            duplicate_bytes = (
                archive.getinfo(raw_duplicate).compress_size
                if raw_duplicate in entries else 0
            )
    except (OSError, zipfile.BadZipFile) as error:
        raise AuditError(f"Could not inspect public suffix resources: {error}") from error
    details = {
        "okhttpResource": raw_okhttp,
        "scriptletResource": normalized,
        "duplicateResource": raw_duplicate,
        "duplicateCompressedBytes": duplicate_bytes,
    }
    if missing:
        details["missing"] = missing
    if missing or duplicate_bytes or raw_duplicate in entries:
        return "fail", details
    return "pass", details


def packaged_runtime_resources(apk):
    """Verify source assets survive the optimised APK byte-for-byte.

    Content-blocking scripts and redirect payloads are referenced by string
    paths, not Android resource IDs. Include newly added assets automatically,
    while also pinning the core scripts to catch accidental source deletion.
    """
    expected = {}
    for source_root, apk_prefix in (
        (ROOT / "app/src/main/assets", "assets/"),
        (ROOT / "adblock/src/main/resources/adblock", "adblock/"),
    ):
        for source in source_root.rglob("*"):
            if source.is_file():
                expected[apk_prefix + source.relative_to(source_root).as_posix()] = source

    missing_from_source = sorted(CORE_RUNTIME_RESOURCES - expected.keys())
    missing = []
    empty = []
    modified = []
    duplicate = []
    verified = 0
    try:
        with zipfile.ZipFile(apk) as archive:
            counts = Counter(archive.namelist())
            entries = counts.keys()
            missing = sorted(expected.keys() - entries)
            duplicate = sorted(name for name in expected if counts[name] > 1)
            for name, source in sorted(expected.items()):
                if name in missing:
                    continue
                with archive.open(name) as content:
                    digest = hashlib.sha256()
                    size = 0
                    for block in iter(lambda: content.read(1024 * 1024), b""):
                        size += len(block)
                        digest.update(block)
                if size == 0:
                    empty.append(name)
                elif digest.hexdigest() != sha256(source):
                    modified.append(name)
                else:
                    verified += 1
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        raise AuditError(f"Could not verify APK runtime resources: {error}") from error

    details = {
        "expected": len(expected),
        "verified": verified,
        "missingFromSource": missing_from_source,
        "missing": missing,
        "empty": empty,
        "modified": modified,
        "duplicate": duplicate,
    }
    failed = missing_from_source or missing or empty or modified or duplicate
    return ("fail" if failed else "pass"), details


def r8_diagnostics(mapping_path):
    if mapping_path is None or not mapping_path.is_file():
        return "warn", {"reason": "R8 mapping not supplied or found for this APK"}
    folder = mapping_path.parent
    diagnostics = {"mapping": str(mapping_path), "mappingSha256": sha256(mapping_path)}
    for filename in ("missing_rules.txt", "configuration.txt", "usage.txt", "seeds.txt"):
        file = folder / filename
        diagnostics[filename] = {"present": file.is_file()}
        if file.is_file():
            diagnostics[filename]["bytes"] = file.stat().st_size
            diagnostics[filename]["sha256"] = sha256(file)
    missing = folder / "missing_rules.txt"
    if missing.is_file():
        directives = [line.strip() for line in missing.read_text(encoding="utf-8", errors="replace").splitlines()
                      if line.strip() and not line.lstrip().startswith("#")]
        if directives:
            diagnostics["missingRules"] = directives[:30]
            diagnostics["missingRulesTruncated"] = len(directives) > 30
            return "warn", diagnostics
    return "pass", diagnostics


def verify_zip_alignment(apk):
    """Check the actual APK for 16 KiB native-library ZIP alignment.

    This uses Android's official zipalign check, rather than inferring support
    from the Gradle plugin version or from the manifest.
    """
    tool = smoke.find_sdk_tool("zipalign", build_tool=True)
    result = smoke.command([tool, "-c", "-P", "16", "-v", "4", str(apk)],
                           timeout=120, check=False)
    if result.returncode:
        return "fail", {"error": "APK fails zipalign 16 KiB check: " +
                        (result.stdout + result.stderr).strip()[-1200:]}
    return "pass", {"pageSizeBytes": 16384, "tool": str(tool)}


def suppression_report():
    rules = ROOT / "app/proguard-rules.pro"
    text = rules.read_text(encoding="utf-8")
    suppressions = WARN_DIRECTIVE.findall(text)
    broad = [rule for rule in suppressions if "**" in rule or rule == "*"]
    details = {"suppressedWarnings": suppressions, "broadSuppressions": broad}
    return ("warn" if broad else "pass"), details


def put_check(report, ident, status, details):
    report["checks"].append({"id": ident, "status": status, "details": details})


def run_audit(apk=None, *, dexdump=None, mapping=None, device=False, serial=None,
              require_mapping=False, runtime_probes=False):
    report = {
        "schemaVersion": 2,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "gitCommit": git_commit(),
        "requested": {"device": device, "runtime": runtime_probes},
        "apk": None,
        "checks": [],
        "limitations": [
            "DEX entry-point checks cannot prove reflection or runtime behaviour",
            "Without --runtime, no MQTT, JavaScript or serialization calls are executed",
            "--runtime checks MQTT 5 SUBACK, inbound and outbound delivery over TCP/WS, one WebView JavaScript bridge, and sealed JSON round trips",
            "MQTT TLS/WSS and the other JavaScript bridges require additional device scenarios",
        ],
    }
    try:
        if apk is None:
            apk = dex.build_smoke_apk()
        if not apk.is_file():
            raise AuditError(f"APK not found: {apk}")
        report["apk"] = {"path": str(apk), "sha256": sha256(apk), "bytes": apk.stat().st_size}
    except (dex.DexCheckError, AuditError, OSError) as error:
        put_check(report, "artifact", "fail", {"error": str(error)})
        return finalise(report)

    put_check(report, "artifact", "pass", {"sha256": report["apk"]["sha256"]})
    try:
        status, details = packaged_runtime_resources(apk)
        put_check(report, "packaged_resources", status, details)
    except AuditError as error:
        put_check(report, "packaged_resources", "fail", {"error": str(error)})
    try:
        status, details = public_suffix_resources(apk)
        put_check(report, "public_suffix_resources", status, details)
    except AuditError as error:
        put_check(report, "public_suffix_resources", "fail", {"error": str(error)})
    try:
        aapt = smoke.find_sdk_tool("aapt", build_tool=True)
        package, activity, version = smoke.apk_details(aapt, apk)
        report["apk"].update({"package": package, "launchActivity": activity, "version": version})
        put_check(report, "apk_metadata", "pass", {"package": package, "version": version, "debuggable": False})
    except (smoke.SmokeTestError, OSError) as error:
        put_check(report, "apk_metadata", "fail", {"error": str(error)})

    try:
        status, details = verify_zip_alignment(apk)
        put_check(report, "apk_16k_alignment", status, details)
    except (smoke.SmokeTestError, OSError) as error:
        put_check(report, "apk_16k_alignment", "fail", {"error": str(error)})

    mapping_path = mapping_file(apk, mapping)
    try:
        class_mapping = parse_mapping(mapping_path)
        if class_mapping is not None:
            put_check(report, "mapping", "pass", {"path": str(mapping_path), "classes": len(class_mapping)})
        else:
            put_check(report, "mapping", "fail" if require_mapping else "warn",
                      {"reason": "R8 mapping unavailable; bridge checks may be inconclusive"})
    except (OSError, AuditError) as error:
        class_mapping = None
        put_check(report, "mapping", "fail", {"error": str(error)})

    try:
        dexdump = dexdump or dex.find_dexdump()
        source_dex = dex.check_apk(apk, dexdump)
        put_check(report, "retrostreams_guard", "pass", {"classDex": source_dex})
    except (dex.DexCheckError, OSError) as error:
        put_check(report, "retrostreams_guard", "fail", {"error": str(error)})

    if dexdump:
        try:
            status, details = inspect_bridges(apk, dexdump, class_mapping)
            put_check(report, "javascript_bridges", status, details)
        except (AuditError, OSError) as error:
            put_check(report, "javascript_bridges", "fail", {"error": str(error)})
    else:
        put_check(report, "javascript_bridges", "fail", {"error": "dexdump unavailable"})

    try:
        status, details = r8_diagnostics(mapping_path)
        put_check(report, "r8_diagnostics", status, details)
    except OSError as error:
        put_check(report, "r8_diagnostics", "fail", {"error": str(error)})
    try:
        status, details = suppression_report()
        put_check(report, "suppression_review", status, details)
    except OSError as error:
        put_check(report, "suppression_review", "fail", {"error": str(error)})

    launched = False
    if device:
        try:
            # Only use an explicitly selected compatible emulator. Never test
            # against an arbitrary phone, or silently fall back to a debug APK.
            smoke.smoke_test(apk, serial=serial)
            launched = True
            put_check(report, "emulator_cold_launch", "pass", {"serial": serial or "auto"})
        except smoke.SmokeTestError as error:
            put_check(report, "emulator_cold_launch", "fail", {"error": str(error)})
    else:
        put_check(report, "emulator_cold_launch", "not_run", {"reason": "Run with --device on an Android 16 / 16 KiB emulator"})

    if runtime_probes and launched:
        try:
            outcomes, test_apk, selected = runtime.run_runtime_probes(apk, serial=serial)
            report["runtime"] = {"testApkSha256": sha256(test_apk), "serial": selected,
                                 "instrumentationRunner": runtime.RUNNER}
            for name in runtime.PROBES:
                status, details = outcomes[name]
                put_check(report, "runtime_" + name, status, details)
        except (runtime.RuntimeProbeError, smoke.SmokeTestError, OSError) as error:
            details = {"error": str(error)}
            if isinstance(error, runtime.RuntimeProbeError):
                details.update(error.details)
            put_check(report, "runtime_instrumentation", "fail", details)
            for name in runtime.PROBES:
                put_check(report, "runtime_" + name, "not_run", {"reason": "Instrumentation unavailable"})
    else:
        reason = "Run with --device --runtime on an Android 16 / 16 KiB emulator"
        if runtime_probes:
            reason = "A successful emulator cold launch is required"
        for name in runtime.PROBES:
            put_check(report, "runtime_" + name, "not_run", {"reason": reason})
    return finalise(report)


def summarise_checks(checks):
    """Summarise a check group without equating skipped work with success."""
    statuses = [check["status"] for check in checks]
    if "fail" in statuses:
        status = "fail"
    elif "warn" in statuses:
        status = "warn"
    elif not statuses or all(item == "not_run" for item in statuses):
        status = "not_run"
    elif "not_run" in statuses:
        status = "partial"
    else:
        status = "pass"
    return {
        "status": status,
        "passed": statuses.count("pass"),
        "failed": statuses.count("fail"),
        "warnings": statuses.count("warn"),
        "notRun": statuses.count("not_run"),
        "total": len(statuses),
    }


def finalise(report):
    """Separate static validity from optional device/runtime test coverage.

    `partial` means no recorded errors or warnings, but at least one optional
    check was not run. CI still exits nonzero only on a recorded failure.
    """
    checks = report["checks"]
    static_checks = [check for check in checks if not check["id"].startswith("runtime_")
                     and check["id"] != "emulator_cold_launch"]
    device_checks = [check for check in checks if check["id"] == "emulator_cold_launch"]
    runtime_checks = [check for check in checks if check["id"].startswith("runtime_")]
    summary = summarise_checks(checks)
    summary["static"] = summarise_checks(static_checks)
    summary["device"] = {**summarise_checks(device_checks),
                         "requested": report["requested"]["device"]}
    summary["runtime"] = {**summarise_checks(runtime_checks),
                          "requested": report["requested"]["runtime"],
                          "expectedProbes": len(runtime.PROBES)}
    report["summary"] = summary
    return report


def write_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as output:
        temporary = Path(output.name)
        json.dump(report, output, indent=2, sort_keys=True)
        output.write("\n")
    temporary.replace(path)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path, help="Existing APK (default: build smokeRelease)")
    parser.add_argument("--json", type=Path, help="Write a JSON report, including failures")
    parser.add_argument("--mapping", type=Path, help="R8 mapping.txt for the exact APK")
    parser.add_argument("--require-mapping", action="store_true", help="Fail if R8 mapping is absent (CI)")
    parser.add_argument(
        "--fail-on-warnings", action="store_true",
        help="Exit nonzero for R8 review warnings (recommended for CI)",
    )
    parser.add_argument("--dexdump", help="Path to the Android SDK dexdump executable")
    parser.add_argument("--device", action="store_true", help="Also test cold launch on a compatible running emulator")
    parser.add_argument("--serial", help="ADB serial for --device (required if multiple emulators)")
    parser.add_argument("--runtime", action="store_true", help="Run MQTT, JS bridge and JSON instrumentation on smokeRelease (requires --device)")
    args = parser.parse_args(argv)
    if args.serial and not args.device:
        parser.error("--serial requires --device")
    if args.runtime and not args.device:
        parser.error("--runtime requires --device")
    report = run_audit(
        args.apk.expanduser().resolve() if args.apk else None,
        dexdump=args.dexdump,
        mapping=args.mapping.expanduser().resolve() if args.mapping else None,
        device=args.device,
        serial=args.serial,
        require_mapping=args.require_mapping,
        runtime_probes=args.runtime,
    )
    if args.json:
        try:
            write_report(args.json.expanduser().resolve(), report)
            print(f"R8 report: {args.json}")
        except OSError as error:
            print(f"FAIL: cannot write R8 report: {error}", file=sys.stderr)
            return 1
    for check in report["checks"]:
        print(f"{check['status'].upper()}: {check['id']}")
        details = check["details"]
        if check["status"] in ("fail", "warn"):
            message = details.get("error") or details.get("reason")
            if message is None and details.get("missing"):
                message = "; ".join(details["missing"][:5])
            if message is None and details.get("missingFromSource"):
                message = "Missing core runtime sources: " + "; ".join(details["missingFromSource"][:5])
            if message is None and details.get("empty"):
                message = "Empty runtime assets: " + "; ".join(details["empty"][:5])
            if message is None and details.get("modified"):
                message = "Modified runtime assets: " + "; ".join(details["modified"][:5])
            if message is None and details.get("duplicate"):
                message = "Duplicate runtime assets: " + "; ".join(details["duplicate"][:5])
            if message is None and details.get("missingRules"):
                message = "R8 suggested missing class rules; inspect the JSON report"
            if message is None and details.get("broadSuppressions"):
                message = "Broad warning suppression: " + ", ".join(details["broadSuppressions"])
            if message:
                print(f"  {message}")
    summary = report["summary"]
    print("SUMMARY: " + summary["status"] + " (static=" + summary["static"]["status"]
          + ", device=" + summary["device"]["status"]
          + ", runtime=" + summary["runtime"]["status"] + ")")
    if summary["failed"]:
        return 1
    if args.fail_on_warnings and summary["warnings"]:
        print(
            f"FAIL: {summary['warnings']} R8 warning(s) require review (--fail-on-warnings)",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
