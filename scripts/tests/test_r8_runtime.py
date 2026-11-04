"""Offline contract checks for the release-device instrumentation protocol."""

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts.r8 import r8_runtime as runtime

ROOT = Path(__file__).resolve().parents[2]


def output_for(statuses, result_code=0, include_mqtt_evidence=True):
    lines = []
    for name, status in statuses.items():
        lines.extend([
            f"INSTRUMENTATION_STATUS: r8_check={name}",
            f"INSTRUMENTATION_STATUS: r8_status={status}",
            "INSTRUMENTATION_STATUS: r8_error=Test failure",
        ])
        if name.startswith("mqtt_") and status == "pass" and include_mqtt_evidence:
            lines.extend([
                "INSTRUMENTATION_STATUS: r8_subscriptions=3",
                "INSTRUMENTATION_STATUS: r8_inbound=get_status",
                "INSTRUMENTATION_STATUS: r8_outbound=url_changed",
            ])
        lines.append("INSTRUMENTATION_STATUS_CODE: 0")
    lines.append(f"INSTRUMENTATION_CODE: {result_code}")
    return "\n".join(lines)


class R8RuntimeTest(unittest.TestCase):
    def test_minified_runtime_probes_are_in_smoke_app_not_in_test_apk(self):
        sources = ROOT / "app/src/smokeRelease/java/uk/nktnet/webviewkiosk/r8"
        runner = (sources / "SmokeReleaseInstrumentation.kt").read_text()
        broker = (sources / "Mqtt5LoopbackBroker.kt").read_text()
        for source in (runner, broker):
            self.assertNotIn(".use {", source)
        self.assertNotIn("InboundRequestJsonParser", runner)
        self.assertNotIn("OutboundEventJsonParser", runner)
        self.assertIn("broker.close()", runner)
        self.assertIn("socket.close()", broker)

    def test_java_runner_only_links_to_smoke_variant_entry_point(self):
        test_sources = ROOT / "app/src/androidTest/java/uk/nktnet/webviewkiosk/r8"
        runner = (test_sources / "R8RuntimeInstrumentation.java").read_text()
        self.assertIn("extends SmokeReleaseInstrumentation", runner)
        self.assertEqual([], list(test_sources.glob("*.kt")))
        keep = (ROOT / "app/proguard-smoke-test.pro").read_text()
        # The class exists only in the smoke variant, not in src/main, so an
        # IDE can flag this valid variant-specific shrinker rule as unresolved.
        self.assertTrue(
            (ROOT / "app/src/smokeRelease/java/uk/nktnet/webviewkiosk/r8/SmokeReleaseInstrumentation.kt").is_file()
        )
        self.assertIn(
            "# noinspection ShrinkerUnresolvedReference\n"
            "-keep class uk.nktnet.webviewkiosk.r8.SmokeReleaseInstrumentation "
            "{ public *; }", keep,
        )
        self.assertNotIn("-keep class kotlin.", keep)
        self.assertNotIn("-keep class kotlinx.", keep)
        build = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('proguardFiles("proguard-smoke-test.pro")', build)

    def test_runtime_probes_use_retained_app_api_instead_of_removed_factories(self):
        runner = (ROOT / "app/src/smokeRelease/java/uk/nktnet/webviewkiosk/r8/SmokeReleaseInstrumentation.kt").read_text()
        for missing_factory in (
            "Json {", "Json(base)", "Dispatchers.Default", "scope.cancel()",
            "kotlinx.serialization.decodeFromString", "kotlinx.serialization.encodeToString",
        ):
            with self.subTest(factory=missing_factory):
                # Comments may mention deleted APIs, but executable references must not.
                source_lines = [line.split("//", 1)[0] for line in runner.splitlines()]
                self.assertNotIn(missing_factory, "\n".join(source_lines))
        self.assertIn("UserSettings(sandbox)", runner)
        self.assertIn("settings.importJson", runner)
        self.assertIn("settings.exportJson", runner)
        self.assertIn("EmptyCoroutineContext", runner)
        self.assertIn("FlowCollector<MqttLogEntry>", runner)
        self.assertIn("FlowCollector<", runner)
        self.assertIn("RemoteMessageManager.RemoteMessage<", runner)
        self.assertIn("InboundRequestMessage", runner)

    def test_parses_all_named_probes_and_errors(self):
        statuses = dict.fromkeys(runtime.PROBES, "pass")
        statuses["mqtt_websocket"] = "fail"
        results = runtime.parse_instrumentation_output(output_for(statuses))
        self.assertEqual("pass", results["mqtt_tcp"][0])
        self.assertEqual(3, results["mqtt_tcp"][1]["subscriptionsAcknowledged"])
        self.assertEqual("get_status", results["mqtt_tcp"][1]["inboundDelivery"])
        self.assertEqual("url_changed", results["mqtt_tcp"][1]["outboundDelivery"])
        self.assertEqual("Test failure", results["mqtt_websocket"][1]["error"])

    def test_preserves_primary_mqtt_error_and_reports_cleanup_error(self):
        output = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        output = output.replace(
            "INSTRUMENTATION_STATUS: r8_status=pass\n"
            "INSTRUMENTATION_STATUS: r8_error=Test failure\n"
            "INSTRUMENTATION_STATUS: r8_subscriptions=3",
            "INSTRUMENTATION_STATUS: r8_status=fail\n"
            "INSTRUMENTATION_STATUS: r8_error=IllegalStateException: SUBACK missing\n"
            "INSTRUMENTATION_STATUS: r8_cleanup_error=IllegalStateException: MQTT disconnect timed out\n"
            "INSTRUMENTATION_STATUS: r8_subscriptions=3",
            1,
        )
        results = runtime.parse_instrumentation_output(output)
        self.assertEqual("fail", results["mqtt_tcp"][0])
        self.assertEqual("IllegalStateException: SUBACK missing", results["mqtt_tcp"][1]["error"])
        self.assertEqual(
            "IllegalStateException: MQTT disconnect timed out",
            results["mqtt_tcp"][1]["cleanupError"],
        )
        self.assertNotIn("cleanupError", results["mqtt_websocket"][1])

    def test_reports_disconnect_error_as_failed_mqtt_probe(self):
        output = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        output = output.replace(
            "INSTRUMENTATION_STATUS: r8_status=pass\n"
            "INSTRUMENTATION_STATUS: r8_error=Test failure\n"
            "INSTRUMENTATION_STATUS: r8_subscriptions=3",
            "INSTRUMENTATION_STATUS: r8_status=fail\n"
            "INSTRUMENTATION_STATUS: r8_error=IllegalStateException: MQTT disconnect failed: broker disconnected\n"
            "INSTRUMENTATION_STATUS: r8_subscriptions=3",
            1,
        )
        results = runtime.parse_instrumentation_output(output)
        self.assertEqual("fail", results["mqtt_tcp"][0])
        self.assertEqual(
            "IllegalStateException: MQTT disconnect failed: broker disconnected",
            results["mqtt_tcp"][1]["error"],
        )
        self.assertNotIn("cleanupError", results["mqtt_tcp"][1])

    def test_multiline_webview_errors_and_cleanup_stacks_survive(self):
        output = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        old = (
            "INSTRUMENTATION_STATUS: r8_check=webview_javascript\n"
            "INSTRUMENTATION_STATUS: r8_status=pass\n"
            "INSTRUMENTATION_STATUS: r8_error=Test failure\n"
            "INSTRUMENTATION_STATUS_CODE: 0"
        )
        primary = "IllegalStateException: WebView bridge failed\nsecond message line"
        stack = (
            "java.lang.IllegalStateException: WebView bridge failed\n"
            "\tat uk.nktnet.webviewkiosk.r8.R8RuntimeInstrumentation.webViewJavascript(R8RuntimeInstrumentation.kt:155)\n"
            "\nCaused by: java.lang.IllegalArgumentException: invalid JSON\n"
            "\tat org.json.JSONTokener.nextValue(JSONTokener.java:100)"
        )
        cleanup = "IllegalStateException: WebView destroy failed\nteardown detail"
        cleanup_stack = (
            "java.lang.IllegalStateException: WebView destroy failed\n"
            "\tat android.webkit.WebView.destroy(WebView.java:200)"
        )
        new = "\n".join([
            "INSTRUMENTATION_STATUS: r8_check=webview_javascript",
            "INSTRUMENTATION_STATUS: r8_status=fail",
            f"INSTRUMENTATION_STATUS: r8_error={primary}",
            f"INSTRUMENTATION_STATUS: r8_stack_trace={stack}",
            f"INSTRUMENTATION_STATUS: r8_cleanup_error={cleanup}",
            f"INSTRUMENTATION_STATUS: r8_cleanup_stack_trace={cleanup_stack}",
            "INSTRUMENTATION_STATUS_CODE: 0",
        ])
        self.assertIn(old, output)
        parsed = runtime.parse_instrumentation_output(output.replace(old, new))
        self.assertEqual("fail", parsed["webview_javascript"][0])
        self.assertEqual(primary, parsed["webview_javascript"][1]["error"])
        self.assertEqual(stack, parsed["webview_javascript"][1]["stackTrace"])
        self.assertEqual(cleanup, parsed["webview_javascript"][1]["cleanupError"])
        self.assertEqual(cleanup_stack, parsed["webview_javascript"][1]["cleanupStackTrace"])
        self.assertEqual("pass", parsed["mqtt_tcp"][0])
        self.assertNotIn("stackTrace", parsed["mqtt_tcp"][1])

    def test_long_multiline_error_is_not_truncated(self):
        output = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        long_error = "IllegalStateException: " + "X" * 3500 + "\n\tat example.Probe.run(Probe.kt:42)"
        target = (
            "INSTRUMENTATION_STATUS: r8_check=json_serialization\n"
            "INSTRUMENTATION_STATUS: r8_status=pass\n"
            "INSTRUMENTATION_STATUS: r8_error=Test failure"
        )
        replacement = target.replace("r8_status=pass", "r8_status=fail").replace(
            "r8_error=Test failure", f"r8_error={long_error}"
        )
        parsed = runtime.parse_instrumentation_output(output.replace(target, replacement, 1))
        self.assertEqual(long_error, parsed["json_serialization"][1]["error"])
        self.assertEqual("pass", parsed["webview_javascript"][0])

    def test_instrumentation_footer_is_not_appended_to_stack(self):
        output = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        target = (
            "INSTRUMENTATION_STATUS: r8_check=json_serialization\n"
            "INSTRUMENTATION_STATUS: r8_status=pass\n"
            "INSTRUMENTATION_STATUS: r8_error=Test failure"
        )
        changed = (
            "INSTRUMENTATION_STATUS: r8_check=json_serialization\n"
            "INSTRUMENTATION_STATUS: r8_status=fail\n"
            "INSTRUMENTATION_STATUS: r8_error=primary error\n"
            "additional detail"
        )
        parsed = runtime.parse_instrumentation_output(output.replace(target, changed, 1))
        self.assertEqual("primary error\nadditional detail", parsed["json_serialization"][1]["error"])
        self.assertNotIn("INSTRUMENTATION_CODE", parsed["json_serialization"][1]["error"])

    def test_mqtt_pass_without_suback_or_delivery_evidence_is_rejected(self):
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "omitted subscription evidence"):
            runtime.parse_instrumentation_output(
                output_for(dict.fromkeys(runtime.PROBES, "pass"), include_mqtt_evidence=False)
            )

    def test_mqtt_partial_delivery_evidence_is_rejected(self):
        stdout = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        stdout = stdout.replace("r8_outbound=url_changed", "r8_outbound=missing")
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "incomplete delivery evidence"):
            runtime.parse_instrumentation_output(stdout)

    def test_incomplete_run_is_not_silently_passed(self):
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "did not complete"):
            runtime.parse_instrumentation_output(output_for({"json_serialization": "pass"}))

    def test_duplicate_result_is_rejected(self):
        stdout = output_for(dict.fromkeys(runtime.PROBES, "pass"))
        duplicate = output_for({"mqtt_tcp": "pass"}).split("INSTRUMENTATION_CODE")[0]
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "duplicate"):
            runtime.parse_instrumentation_output(duplicate + stdout)

    def test_unknown_probe_is_rejected(self):
        stdout = output_for({"other": "pass", **dict.fromkeys(runtime.PROBES, "pass")})
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "Unexpected"):
            runtime.parse_instrumentation_output(stdout)

    def test_does_not_run_against_arbitrary_production_apk(self):
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "smokeRelease"):
            runtime.run_runtime_probes(Path("/tmp/app-release.apk"))

    def test_accepts_instrumentation_declared_in_compiled_manifest(self):
        # Real AAPT 36 prints NO `instrumentation:` line in `dump badging`.
        badging = "package: name='app.test' versionCode='' versionName=''\napplication: label='' icon=''"
        xmltree = "\n".join([
            "N: android=http://schemas.android.com/apk/res/android",
            "  E: manifest (line=2)",
            "    A: package=\"app.test\" (Raw: \"app.test\")",
            "    E: uses-sdk (line=5)",
            "      A: android:minSdkVersion(0x0101020c)=(type 0x10)0x15",
            "    E: instrumentation (line=9)",
            '      A: android:label(0x01010001)="Tests for app" (Raw: "Tests for app")',
            f'      A: android:name(0x01010003)="{runtime.RUNNER}" (Raw: "{runtime.RUNNER}")',
            '      A: android:targetPackage(0x01010021)="app" (Raw: "app")',
            "    E: application (line=16)",
        ])
        self.assertEqual(
            f"app.test/{runtime.RUNNER}",
            runtime.validate_instrumentation_component(badging, xmltree, "app"),
        )

    def test_accepts_isolated_smoke_instrumentation_package(self):
        target = "uk.nktnet.webviewkiosk.smoke"
        badging = f"package: name='{target}.test' versionCode='139' versionName='0.26.25'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
            f'      A: android:targetPackage(0x01010021)="{target}"',
        ])
        self.assertEqual(
            f"{target}.test/{runtime.RUNNER}",
            runtime.validate_instrumentation_component(badging, xmltree, target),
        )

    def test_accepts_reordered_xmltree_attributes(self):
        badging = "package: versionCode='1' name='app.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            '      A: android:targetPackage(0x01010021)="app"',
            '      A: android:label(0x01010001)="Tests for app"',
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
        ])
        self.assertEqual(
            f"app.test/{runtime.RUNNER}",
            runtime.validate_instrumentation_component(badging, xmltree, "app"),
        )

    def test_reports_actual_runner_and_target_when_mismatched(self):
        badging = "package: name='app.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            '      A: android:name(0x01010003)="other.Runner"',
            '      A: android:targetPackage(0x01010021)="wrong.app"',
        ])
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "other.Runner") as raised:
            runtime.validate_instrumentation_component(badging, xmltree, "app")
        self.assertIn("wrong.app", str(raised.exception))

    def test_rejects_missing_instrumentation_even_if_application_has_a_name(self):
        badging = "package: name='app.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: application (line=9)",
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
            '      A: android:targetPackage(0x01010021)="app"',
        ])
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "found none"):
            runtime.validate_instrumentation_component(badging, xmltree, "app")

    def test_rejects_wrong_test_package_with_correct_runner(self):
        badging = "package: name='other.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
            '      A: android:targetPackage(0x01010021)="app"',
        ])
        with self.assertRaisesRegex(runtime.RuntimeProbeError, "other.test"):
            runtime.validate_instrumentation_component(badging, xmltree, "app")

    def test_ignores_nonmatching_instrumentation_sibling(self):
        badging = "package: name='app.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            '      A: android:name(0x01010003)="wrong.Runner"',
            '      A: android:targetPackage(0x01010021)="wrong.app"',
            "    E: instrumentation (line=13)",
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
            '      A: android:targetPackage(0x01010021)="app"',
            "    E: application (line=16)",
        ])
        self.assertEqual(
            f"app.test/{runtime.RUNNER}",
            runtime.validate_instrumentation_component(badging, xmltree, "app"),
        )

    def test_runs_instrumentation_when_badging_omits_declaration(self):
        badging = "package: name='app.test' versionCode=''"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            f'      A: android:name(0x01010003)="{runtime.RUNNER}"',
            '      A: android:targetPackage(0x01010021)="app"',
        ])
        calls = []
        commands = []

        def fake_adb(_adb, _serial, *args, **_kwargs):
            calls.append(args)
            if args[0] == "install":
                return SimpleNamespace(returncode=0, stdout="", stderr="")
            return SimpleNamespace(
                returncode=0,
                stdout=output_for(dict.fromkeys(runtime.PROBES, "pass")),
                stderr="",
            )

        def fake_command(args, **_kwargs):
            commands.append(args)
            return SimpleNamespace(stdout=xmltree if "xmltree" in args else badging)

        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "app.apk"
            test_apk = Path(folder) / "test.apk"
            target.write_bytes(b"apk bytes")
            test_apk.write_bytes(b"test apk bytes")
            with (
                patch.object(runtime.smoke, "SMOKE_APK", target),
                patch.object(runtime.smoke, "find_sdk_tool", return_value="aapt"),
                patch.object(runtime.smoke, "select_emulator", return_value="emulator-5554"),
                patch.object(runtime, "build_test_apk", return_value=test_apk),
                patch.object(runtime.smoke, "apk_details", return_value=("app", "Activity", "1.0")),
                patch.object(runtime.smoke, "command", side_effect=fake_command),
                patch.object(runtime.smoke, "adb_command", side_effect=fake_adb),
            ):
                results, apk, serial = runtime.run_runtime_probes(target)
        self.assertEqual(test_apk, apk)
        self.assertEqual("emulator-5554", serial)
        self.assertEqual(set(runtime.PROBES), set(results))
        self.assertTrue(all(status == "pass" for status, _ in results.values()))
        self.assertEqual(
            ["aapt", "dump", "xmltree", test_apk, "AndroidManifest.xml"], commands[1]
        )
        self.assertEqual(("install", "-r", str(test_apk)), calls[0])
        self.assertEqual(("logcat", "-c"), calls[1])
        self.assertEqual(
            ("shell", "am", "instrument", "-w", "-r", f"app.test/{runtime.RUNNER}"),
            calls[2],
        )

    def test_checks_manifest_before_installing_invalid_runner(self):
        badging = "package: name='app.test'"
        xmltree = "\n".join([
            "  E: manifest (line=1)",
            "    E: instrumentation (line=9)",
            '      A: android:name(0x01010003)="wrong.Runner"',
            '      A: android:targetPackage(0x01010021)="app"',
        ])
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "app.apk"
            target.write_bytes(b"apk bytes")
            with (
                patch.object(runtime.smoke, "SMOKE_APK", target),
                patch.object(runtime.smoke, "find_sdk_tool", return_value="aapt"),
                patch.object(runtime.smoke, "select_emulator", return_value="emulator-5554"),
                patch.object(runtime, "build_test_apk", return_value=Path("test.apk")),
                patch.object(runtime.smoke, "apk_details", return_value=("app", "Activity", "1.0")),
                patch.object(runtime.smoke, "command", side_effect=[
                    SimpleNamespace(stdout=badging), SimpleNamespace(stdout=xmltree),
                ]),
                patch.object(runtime.smoke, "adb_command") as adb,
            ):
                with self.assertRaisesRegex(runtime.RuntimeProbeError, "runner/target mismatch"):
                    runtime.run_runtime_probes(target)
                adb.assert_not_called()


class InstrumentationCrashDiagnosticsTest(unittest.TestCase):
    def test_selects_app_crash_and_keeps_root_cause(self):
        unrelated = (
            "11-04 08:00:00.000  100 100 E AndroidRuntime: FATAL EXCEPTION: main\n"
            "11-04 08:00:00.001  100 100 E AndroidRuntime: Process: wrong.app, PID: 100\n"
            "11-04 08:00:00.002  100 100 E AndroidRuntime: java.lang.Error: wrong one\n"
        )
        target = (
            "11-04 08:00:01.000  200 200 E AndroidRuntime: FATAL EXCEPTION: Instr: TestRunner\n"
            "11-04 08:00:01.001  200 200 E AndroidRuntime: Process: app, PID: 200\n"
            "11-04 08:00:01.002  200 200 E AndroidRuntime: java.lang.RuntimeException: can't instantiate runner\n"
            "11-04 08:00:01.003  200 200 E AndroidRuntime: Caused by: java.lang.NoClassDefFoundError: kotlin/Unit\n"
        )
        excerpt = runtime.crash_logcat_excerpt(unrelated + target, "app")
        self.assertIn("NoClassDefFoundError: kotlin/Unit", excerpt)
        self.assertNotIn("wrong one", excerpt)

    def test_native_crash_fallback_keeps_process_lines(self):
        logcat = (
            "11-04 08:00:01.000  200 200 F libc : Fatal signal 11 (SIGSEGV)\n"
            "11-04 08:00:01.001  200 200 F DEBUG: pid: 200, tid: 200, name: app  >>> app <<<\n"
        )
        excerpt = runtime.crash_logcat_excerpt(logcat, "app")
        self.assertIn("Fatal signal 11", excerpt)
        self.assertIn(">>> app <<<", excerpt)

    def test_preserves_instrumentation_crash_in_error_details(self):
        badging = "package: name='app.test'"
        xmltree = "\n".join([
            "E: manifest (line=1)",
            "  E: instrumentation (line=9)",
            f'    A: android:name(0x01010003)="{runtime.RUNNER}"',
            '    A: android:targetPackage(0x01010021)="app"',
        ])
        logcat = (
            "11-04 08:00:01.000 200 200 E AndroidRuntime: FATAL EXCEPTION: Instr: runner\n"
            "11-04 08:00:01.001 200 200 E AndroidRuntime: Process: app, PID: 200\n"
            "11-04 08:00:01.002 200 200 E AndroidRuntime: java.lang.NoClassDefFoundError: MissingClass\n"
            "11-04 08:00:01.003 200 200 E AndroidRuntime: Caused by: java.lang.ClassNotFoundException: MissingClass\n"
        )
        calls = []

        def fake_adb(_adb, _serial, *args, **_kwargs):
            calls.append(args)
            if args[:2] == ("shell", "am"):
                return SimpleNamespace(
                    returncode=0,
                    stdout="INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n",
                    stderr="",
                )
            if args[:2] == ("logcat", "-d"):
                return SimpleNamespace(returncode=0, stdout=logcat, stderr="")
            return SimpleNamespace(returncode=0, stdout="", stderr="")

        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "app.apk"
            test_apk = Path(folder) / "test.apk"
            target.write_bytes(b"apk bytes")
            test_apk.write_bytes(b"test apk bytes")
            with (
                patch.object(runtime.smoke, "SMOKE_APK", target),
                patch.object(runtime.smoke, "find_sdk_tool", return_value="aapt"),
                patch.object(runtime.smoke, "select_emulator", return_value="emulator-5554"),
                patch.object(runtime, "build_test_apk", return_value=test_apk),
                patch.object(runtime.smoke, "apk_details", return_value=("app", "Activity", "1.0")),
                patch.object(runtime.smoke, "command", side_effect=[
                    SimpleNamespace(stdout=badging), SimpleNamespace(stdout=xmltree),
                ]),
                patch.object(runtime.smoke, "adb_command", side_effect=fake_adb),
            ):
                with self.assertRaises(runtime.RuntimeProbeError) as caught:
                    runtime.run_runtime_probes(target)
        self.assertIn("Process crashed", str(caught.exception))
        self.assertIn("NoClassDefFoundError: MissingClass", str(caught.exception))
        self.assertIn("ClassNotFoundException", caught.exception.details["deviceLogcat"])
        self.assertEqual("emulator-5554", caught.exception.details["deviceSerial"])
        self.assertEqual(0, caught.exception.details["instrumentationExitCode"])
        self.assertEqual("install", calls[0][0])
        self.assertEqual(("logcat", "-c"), calls[1])
        self.assertEqual(("shell", "am"), calls[2][:2])
        self.assertEqual(("logcat", "-d"), calls[3][:2])

    def test_logcat_failure_does_not_mask_instrumentation_error(self):
        run = SimpleNamespace(
            returncode=0,
            stdout="INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0",
            stderr="",
        )
        with patch.object(
            runtime.smoke, "adb_command", side_effect=runtime.smoke.SmokeTestError("offline")
        ):
            details = runtime.instrumentation_failure_details("adb", "emulator-5554", "app", run)
        self.assertIn("Process crashed", details["instrumentationOutput"])
        self.assertEqual("offline", details["deviceLogcatError"])
