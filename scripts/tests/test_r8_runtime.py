"""Offline contract checks for the release-device instrumentation protocol."""

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import r8_runtime as runtime


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

    def test_checks_test_apk_runner_and_target_package_before_running(self):
        class Result:
            returncode = 0
            stdout = "package: name='app.other.test'\ninstrumentation: name='wrong' targetPackage='app'"
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "app.apk"
            target.write_bytes(b"apk bytes")
            with (
            patch.object(runtime.smoke, "SMOKE_APK", target),
            patch.object(runtime.smoke, "find_sdk_tool", return_value="adb"),
            patch.object(runtime.smoke, "select_emulator", return_value="emulator-5554"),
            patch.object(runtime, "build_test_apk", return_value=Path("test.apk")),
            patch.object(runtime.smoke, "apk_details", return_value=("app", "Activity", "1.0")),
            patch.object(runtime.smoke, "command", return_value=Result()),
            patch.object(runtime.smoke, "adb_command") as adb,
            ):
                with self.assertRaisesRegex(runtime.RuntimeProbeError, "runner/target mismatch"):
                    runtime.run_runtime_probes(target)
                adb.assert_not_called()
