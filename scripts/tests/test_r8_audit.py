"""Offline tests for the R8 release audit and machine-readable diagnostics."""

import contextlib
import io
import json
import re
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import r8_audit as audit


class R8AuditTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.apk = self.root / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk"
        self.apk.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", "dex1")
            archive.writestr("classes2.dex", "dex2")
            archive.writestr("assets/PublicSuffixDatabase.list", "okhttp suffixes")
            archive.writestr("adblock/publicsuffix-serialized.json", "serialized suffixes")
        self.mapping_path = self.root / "app/build/outputs/mapping/smokeRelease/mapping.txt"
        self.mapping_path.parent.mkdir(parents=True)
        self.mapping_path.write_text("\n".join(
            f"{name} -> {name}:" for name in audit.BRIDGES
        ) + "\n", encoding="utf-8")

    def test_public_suffix_check_accepts_one_raw_list_and_scriptlet_json(self):
        status, details = audit.public_suffix_resources(self.apk)
        self.assertEqual("pass", status)
        self.assertEqual(0, details["duplicateCompressedBytes"])

    def test_public_suffix_check_rejects_duplicate_raw_data(self):
        with zipfile.ZipFile(self.apk, "a") as archive:
            archive.writestr("adblock/publicsuffix.list", "okhttp suffixes")
        status, details = audit.public_suffix_resources(self.apk)
        self.assertEqual("fail", status)
        self.assertGreater(details["duplicateCompressedBytes"], 0)

    def test_public_suffix_check_rejects_missing_runtime_data(self):
        missing = self.root / "incomplete.apk"
        with zipfile.ZipFile(missing, "w") as archive:
            archive.writestr("assets/PublicSuffixDatabase.list", "okhttp suffixes")
        status, details = audit.public_suffix_resources(missing)
        self.assertEqual("fail", status)
        self.assertEqual(["adblock/publicsuffix-serialized.json"], details["missing"])

    def test_public_suffix_check_rejects_missing_okhttp_data(self):
        missing = self.root / "missing-okhttp.apk"
        with zipfile.ZipFile(missing, "w") as archive:
            archive.writestr("adblock/publicsuffix-serialized.json", "serialized suffixes")
        status, details = audit.public_suffix_resources(missing)
        self.assertEqual("fail", status)
        self.assertEqual(["assets/PublicSuffixDatabase.list"], details["missing"])

    def test_public_suffix_check_rejects_broken_apk(self):
        broken = self.root / "broken.apk"
        broken.write_bytes(b"not a zip")
        with self.assertRaisesRegex(audit.AuditError, "Could not inspect"):
            audit.public_suffix_resources(broken)

    def test_finds_mapping_for_matching_variant_only(self):
        self.assertEqual(self.mapping_path, audit.mapping_file(self.apk, None))
        other = self.root / "downloaded.apk"
        self.assertIsNone(audit.mapping_file(other, None))
        self.assertEqual(self.mapping_path, audit.mapping_file(other, self.mapping_path))

    def test_mapping_resolves_obfuscated_nested_class(self):
        original = "uk.nktnet.webviewkiosk.blocking.WebViewRequests$Rules"
        self.mapping_path.write_text(
            "# compiler: R8\n" + f"{original} -> a.b:\n"
            "  boolean shouldBlock(java.lang.String,java.lang.String,java.lang.String) -> shouldBlock\n",
            encoding="utf-8",
        )
        mapping = audit.parse_mapping(self.mapping_path)
        self.assertEqual("a.b", mapping[original])
        self.assertEqual("La/b;", audit.dex_descriptor(mapping[original]))

    def test_missing_mapping_can_be_a_ci_failure(self):
        self.mapping_path.unlink()
        with (
            patch.object(audit.smoke, "find_sdk_tool", return_value="aapt"),
            patch.object(audit.smoke, "apk_details", return_value=("pkg", "Main", "1.0")),
            patch.object(audit.dex, "check_apk", return_value="classes.dex"),
            patch.object(audit, "inspect_bridges", return_value=("warn", {})),
            patch.object(audit, "suppression_report", return_value=("pass", {})),
        ):
            report = audit.run_audit(self.apk, dexdump="dexdump", require_mapping=True)
        self.assertEqual("fail", report["summary"]["status"])
        self.assertEqual("fail", next(c for c in report["checks"] if c["id"] == "mapping")["status"])

    def test_empty_mapping_is_rejected(self):
        self.mapping_path.write_text("# empty\n", encoding="utf-8")
        with self.assertRaisesRegex(audit.AuditError, "no class mappings"):
            audit.parse_mapping(self.mapping_path)

    def test_dexdump_annotation_is_bound_to_the_correct_dex_class(self):
        dexdump_output = (
            "Class #0 annotations:\nAnnotations on method #1 'shouldBlock'\n"
            "  VISIBILITY_RUNTIME Landroid/webkit/JavascriptInterface;\n"
            "Class #1 annotations:\nAnnotations on method #3 'shouldBlock'\n"
            "  VISIBILITY_BUILD Lsome/OtherAnnotation;\n"
            "Class #0            -\n  Class descriptor  : 'Lwanted/Bridge;'\n"
            "  Virtual methods   -\n    #0              : (in Lwanted/Bridge;)\n"
            "      name          : 'shouldBlock'\n      type          : '()Z'\n"
            "Class #1            -\n  Class descriptor  : 'Lother/Bridge;'\n"
            "  Virtual methods   -\n    #0              : (in Lother/Bridge;)\n"
            "      name          : 'shouldBlock'\n      type          : '()Z'\n"
        )
        process = unittest.mock.MagicMock()
        process.__enter__.return_value = process
        process.stdout = io.StringIO(dexdump_output)
        process.wait.return_value = 0
        with patch.object(audit.subprocess, "Popen", return_value=process) as popen:
            found = audit.dump_selected("dexdump", Path("classes.dex"), {"Lwanted/Bridge;", "Lother/Bridge;"})
        self.assertEqual({"shouldBlock"}, found["Lwanted/Bridge;"]["annotated"])
        self.assertEqual(set(), found["Lother/Bridge;"]["annotated"])
        self.assertIn("-a", popen.call_args.args[0])

    def test_streams_selected_classes_only(self):
        data = (
            "Class #1            -\n  Class descriptor  : 'Lunused/A;'\n"
            "Class #2            -\n  Class descriptor  : 'Lwanted/B;'\n"
            "  Virtual methods   -\n    #0              : (in Lwanted/B;)\n"
            "      name          : 'shouldBlock'\n      type          : '(Ljava/lang/String;)Z'\n"
            "Class #3            -\n  Class descriptor  : 'Lunused/C;'\n"
        )
        classes = dict(audit.selected_classes(iter(data.splitlines(True)), {"Lwanted/B;"}))
        self.assertEqual(["Lwanted/B;"], list(classes))
        self.assertEqual(
            {("shouldBlock", "(Ljava/lang/String;)Z")},
            audit.declared_methods(classes["Lwanted/B;"]),
        )

    def test_only_methods_and_exact_signatures_are_accepted(self):
        text = (
            "    #0              : (in Lfoo/Bridge;)\n"
            "      name          : 'error'\n      type          : '(Ljava/lang/String;)V'\n"
            "    #1              : (in Lfoo/Bridge;)\n"
            "      name          : 'error'\n      type          : 'Ljava/lang/String;'\n"
        )
        self.assertEqual({("error", "(Ljava/lang/String;)V")}, audit.declared_methods(text))

    def test_all_bridge_methods_are_verified_across_multidex(self):
        mapping = audit.parse_mapping(self.mapping_path)
        first = next(iter(audit.BRIDGES))
        calls = []

        def fake_dump(tool, dex_path, wanted):
            calls.append(dex_path.name)
            return {
                audit.dex_descriptor(name): {"methods": methods, "annotated": {method for method, _ in methods}}
                for name, methods in audit.BRIDGES.items()
                if (name == first) == (dex_path.name == "classes.dex")
            }

        with patch.object(audit, "dump_selected", side_effect=fake_dump):
            status, details = audit.inspect_bridges(self.apk, "dexdump", mapping)
        self.assertEqual("pass", status)
        self.assertEqual(14, len(details["verified"]))
        self.assertEqual(["classes.dex", "classes2.dex"], calls)

    def test_removed_or_renamed_js_method_fails_when_mapping_is_available(self):
        mapping = audit.parse_mapping(self.mapping_path)
        def fake_dump(tool, dex_path, wanted):
            return {
                audit.dex_descriptor(name): {
                    "methods": methods - {("shouldBlock", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z")},
                    "annotated": {method for method, _ in methods},
                }
                for name, methods in audit.BRIDGES.items()
            } if dex_path.name == "classes.dex" else {}

        with patch.object(audit, "dump_selected", side_effect=fake_dump):
            status, details = audit.inspect_bridges(self.apk, "dexdump", mapping)
        self.assertEqual("fail", status)
        self.assertEqual(1, len(details["missing"]))
        self.assertIn("shouldBlock", details["missing"][0])

    def test_missing_js_annotation_fails_even_if_method_is_present(self):
        mapping = audit.parse_mapping(self.mapping_path)
        def fake_dump(tool, dex_path, wanted):
            return {
                audit.dex_descriptor(name): {
                    "methods": methods,
                    "annotated": {method for method, _ in methods} - {"shouldBlock"},
                }
                for name, methods in audit.BRIDGES.items()
            } if dex_path.name == "classes.dex" else {}
        with patch.object(audit, "dump_selected", side_effect=fake_dump):
            status, details = audit.inspect_bridges(self.apk, "dexdump", mapping)
        self.assertEqual("fail", status)
        self.assertIn("annotation missing", details["missing"][0])

    def test_missing_mapping_returns_inconclusive_instead_of_false_regression(self):
        with patch.object(audit, "dump_selected", return_value={}):
            status, details = audit.inspect_bridges(self.apk, "dexdump", None)
        self.assertEqual("warn", status)
        self.assertIn("--mapping", details["reason"])

    def test_diagnostics_report_missing_rules_without_silencing(self):
        missing = self.mapping_path.parent / "missing_rules.txt"
        missing.write_text("# generated\n-dontwarn library.optional.**\n", encoding="utf-8")
        status, details = audit.r8_diagnostics(self.mapping_path)
        self.assertEqual("warn", status)
        self.assertEqual(["-dontwarn library.optional.**"], details["missingRules"])
        self.assertRegex(details["mappingSha256"], r"^[0-9a-f]{64}$")
        missing.write_text("# no missing classes\n", encoding="utf-8")
        self.assertEqual("pass", audit.r8_diagnostics(self.mapping_path)[0])

    def test_broad_dontwarn_rules_are_not_accepted(self):
        status, details = audit.suppression_report()
        self.assertEqual("pass", status)
        self.assertNotIn("io.netty.**", details["suppressedWarnings"])
        self.assertNotIn("org.slf4j.**", details["suppressedWarnings"])

    def test_hivemq_slf4j_api_is_a_dependency_not_suppressed(self):
        gradle = (audit.ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        catalog = (audit.ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        rules = (audit.ROOT / "app/proguard-rules.pro").read_text(encoding="utf-8")
        self.assertIn("implementation(libs.slf4j.api)", gradle)
        self.assertIn("slf4j-api = {", catalog)
        self.assertNotRegex(rules, r"(?m)^-dontwarn\s+org\.slf4j\.")

    def test_contract_covers_all_existing_javascript_annotations(self):
        source = audit.ROOT / "app/src/main/java"
        annotations = {}
        for file in source.rglob("*.kt"):
            text = file.read_text(encoding="utf-8")
            matches = re.findall(r"@JavascriptInterface\s+fun\s+(\w+)\s*\(", text)
            if matches:
                # Only these app classes actually expose JavaScript methods.
                classes = [key for key in audit.BRIDGES if key.endswith("." + file.stem)]
                if file.name == "WebViewRequests.kt":
                    classes = [key for key in audit.BRIDGES if key.endswith("WebViewRequests$Rules")]
                self.assertEqual(1, len(classes), str(file))
                annotations[classes[0]] = set(matches)
        self.assertEqual(set(audit.BRIDGES), set(annotations))
        for name, methods in audit.BRIDGES.items():
            self.assertEqual(annotations[name], {method for method, _ in methods}, name)

    def test_report_records_all_results_and_explicit_not_run_device(self):
        with (
            patch.object(audit, "git_commit", return_value="abc123"),
            patch.object(audit.smoke, "find_sdk_tool", return_value="aapt"),
            patch.object(audit.smoke, "apk_details", return_value=("pkg", "Main", "1.0 (1)")),
            patch.object(audit.dex, "check_apk", return_value="classes2.dex"),
            patch.object(audit, "inspect_bridges", return_value=("pass", {"verified": []})),
            patch.object(audit, "r8_diagnostics", return_value=("pass", {})),
            patch.object(audit, "suppression_report", return_value=("warn", {})),
        ):
            result = audit.run_audit(self.apk, dexdump="dexdump")
        self.assertEqual("warn", result["summary"]["status"])
        self.assertEqual("abc123", result["gitCommit"])
        self.assertRegex(result["apk"]["sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual("pkg", result["apk"]["package"])
        suffix_check = next(c for c in result["checks"] if c["id"] == "public_suffix_resources")
        self.assertEqual("pass", suffix_check["status"])
        self.assertIn("not_run", [test["status"] for test in result["checks"]])

    def test_missing_apk_still_writes_valid_failure_json(self):
        report_path = self.root / "reports/r8.json"
        with patch.object(audit, "git_commit", return_value=None):
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                exit_code = audit.main([str(self.root / "missing.apk"), "--json", str(report_path)])
        self.assertEqual(1, exit_code)
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual(2, report["schemaVersion"])
        self.assertEqual("fail", report["summary"]["status"])
        self.assertEqual("artifact", report["checks"][0]["id"])

    def test_device_failure_marks_report_failed_not_silent_pass(self):
        with (
            patch.object(audit.smoke, "find_sdk_tool", return_value="aapt"),
            patch.object(audit.smoke, "apk_details", return_value=("pkg", "Main", "1.0")),
            patch.object(audit.dex, "check_apk", return_value="classes.dex"),
            patch.object(audit, "inspect_bridges", return_value=("pass", {"verified": []})),
            patch.object(audit, "r8_diagnostics", return_value=("pass", {})),
            patch.object(audit, "suppression_report", return_value=("pass", {})),
            patch.object(audit.smoke, "smoke_test", side_effect=audit.smoke.SmokeTestError("crashed")),
        ):
            result = audit.run_audit(self.apk, dexdump="dexdump", device=True)
        self.assertEqual("fail", result["summary"]["status"])
        cold_launch = next(c for c in result["checks"] if c["id"] == "emulator_cold_launch")
        self.assertEqual("fail", cold_launch["status"])
        self.assertIn("crashed", cold_launch["details"]["error"])


class R8RuntimeAuditTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.apk = Path(self.folder.name) / "app.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", "dex1")
            archive.writestr("assets/PublicSuffixDatabase.list", "okhttp suffixes")
            archive.writestr("adblock/publicsuffix-serialized.json", "serialized suffixes")

    def common(self):
        return (
            patch.object(audit, "mapping_file", return_value=self.apk),
            patch.object(audit, "parse_mapping", return_value={"pkg.Test": "a"}),
            patch.object(audit.smoke, "find_sdk_tool", return_value="aapt"),
            patch.object(audit.smoke, "apk_details", return_value=("pkg", "Main", "1.0")),
            patch.object(audit.dex, "check_apk", return_value="classes.dex"),
            patch.object(audit, "inspect_bridges", return_value=("pass", {})),
            patch.object(audit, "r8_diagnostics", return_value=("pass", {})),
            patch.object(audit, "suppression_report", return_value=("pass", {})),
        )

    def test_device_probes_not_run_by_default(self):
        from contextlib import ExitStack
        with ExitStack() as stack:
            for mock in self.common():
                stack.enter_context(mock)
            report = audit.run_audit(self.apk, dexdump="dexdump")
        runtime_checks = [c for c in report["checks"] if c["id"].startswith("runtime_")]
        self.assertEqual(len(audit.runtime.PROBES), len(runtime_checks))
        self.assertTrue(all(c["status"] == "not_run" for c in runtime_checks))
        self.assertEqual("partial", report["summary"]["status"])
        self.assertEqual("pass", report["summary"]["static"]["status"])
        self.assertEqual("not_run", report["summary"]["device"]["status"])
        self.assertEqual("not_run", report["summary"]["runtime"]["status"])
        self.assertFalse(report["summary"]["runtime"]["requested"])
        self.assertEqual(len(audit.runtime.PROBES), report["summary"]["runtime"]["expectedProbes"])

    def test_requested_runtime_success_means_all_coverage_was_executed(self):
        from contextlib import ExitStack
        with ExitStack() as stack:
            for mock in self.common():
                stack.enter_context(mock)
            stack.enter_context(patch.object(audit.smoke, "smoke_test"))
            outcomes = {n: ("pass", {"probe": n}) for n in audit.runtime.PROBES}
            stack.enter_context(patch.object(audit.runtime, "run_runtime_probes",
                                           return_value=(outcomes, self.apk, "emulator-5554")))
            report = audit.run_audit(self.apk, dexdump="dexdump", device=True, runtime_probes=True)
        self.assertEqual("pass", report["summary"]["status"])
        self.assertEqual("pass", report["summary"]["static"]["status"])
        self.assertEqual("pass", report["summary"]["device"]["status"])
        self.assertEqual("pass", report["summary"]["runtime"]["status"])
        self.assertTrue(report["summary"]["runtime"]["requested"])
        self.assertEqual(0, report["summary"]["notRun"])

    def test_summary_never_claims_full_pass_when_only_static_checks_run(self):
        report = audit.finalise({
            "requested": {"device": False, "runtime": False},
            "checks": [
                {"id": "artifact", "status": "pass", "details": {}},
                {"id": "emulator_cold_launch", "status": "not_run", "details": {}},
                *({"id": "runtime_" + n, "status": "not_run", "details": {}}
                  for n in audit.runtime.PROBES),
            ],
        })
        self.assertEqual("partial", report["summary"]["status"])
        self.assertEqual("pass", report["summary"]["static"]["status"])
        self.assertEqual("not_run", report["summary"]["runtime"]["status"])

    def test_partial_static_audit_writes_json_and_exits_successfully(self):
        report_path = Path(self.folder.name) / "static-report.json"
        report = audit.finalise({
            "requested": {"device": False, "runtime": False},
            "checks": [
                {"id": "artifact", "status": "pass", "details": {}},
                {"id": "emulator_cold_launch", "status": "not_run", "details": {}},
                *({"id": "runtime_" + n, "status": "not_run", "details": {}}
                  for n in audit.runtime.PROBES),
            ],
        })
        with patch.object(audit, "run_audit", return_value=report):
            with contextlib.redirect_stdout(io.StringIO()) as output:
                exit_code = audit.main(["--json", str(report_path)])
        self.assertEqual(0, exit_code)
        self.assertIn("SUMMARY: partial (static=pass, device=not_run, runtime=not_run)",
                      output.getvalue())
        result = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual("not_run", result["summary"]["runtime"]["status"])

    def test_partial_runtime_does_not_count_as_success(self):
        report = audit.finalise({
            "requested": {"device": True, "runtime": True},
            "checks": [
                {"id": "artifact", "status": "pass", "details": {}},
                {"id": "emulator_cold_launch", "status": "pass", "details": {}},
                {"id": "runtime_mqtt_tcp", "status": "pass", "details": {}},
                {"id": "runtime_mqtt_websocket", "status": "not_run", "details": {}},
            ],
        })
        self.assertEqual("partial", report["summary"]["status"])
        self.assertEqual("partial", report["summary"]["runtime"]["status"])
        self.assertTrue(report["summary"]["runtime"]["requested"])

    def test_runtime_probe_results_are_included_in_json(self):
        from contextlib import ExitStack
        with ExitStack() as stack:
            for mock in self.common():
                stack.enter_context(mock)
            stack.enter_context(patch.object(audit.smoke, "smoke_test"))
            outcomes = {n: ("pass", {"probe": n}) for n in audit.runtime.PROBES}
            outcomes["mqtt_tcp"] = ("fail", {"error": "MQTT failed"})
            stack.enter_context(patch.object(audit.runtime, "run_runtime_probes",
                                           return_value=(outcomes, self.apk, "emulator-5554")))
            report = audit.run_audit(self.apk, dexdump="dexdump", device=True, runtime_probes=True)
        self.assertEqual("fail", report["summary"]["status"])
        self.assertEqual("emulator-5554", report["runtime"]["serial"])
        check = next(c for c in report["checks"] if c["id"] == "runtime_mqtt_tcp")
        self.assertEqual("fail", check["status"])

    def test_emulator_failure_skips_runtime_probe_without_masking_error(self):
        from contextlib import ExitStack
        with ExitStack() as stack:
            for mock in self.common():
                stack.enter_context(mock)
            stack.enter_context(patch.object(audit.smoke, "smoke_test",
                                           side_effect=audit.smoke.SmokeTestError("no AVD")))
            with patch.object(audit.runtime, "run_runtime_probes") as run:
                report = audit.run_audit(self.apk, dexdump="dexdump", device=True,
                                         runtime_probes=True)
                run.assert_not_called()
        self.assertEqual("fail", report["summary"]["status"])
        self.assertTrue(all(c["status"] == "not_run" for c in report["checks"]
                            if c["id"].startswith("runtime_")))


if __name__ == "__main__":
    unittest.main()
