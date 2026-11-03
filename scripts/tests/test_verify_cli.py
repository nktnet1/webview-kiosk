"""Contract tests for the single-command release verifier, without an SDK/AVD."""

import contextlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from scripts import verify as VERIFY_MODULE
from scripts.r8 import check_release_dex, r8_audit, r8_runtime, release_startup_smoke

ROOT = Path(__file__).resolve().parents[2]


class VerifyCliTest(unittest.TestCase):
    def test_entrypoint_is_in_scripts_and_supports_direct_and_module_execution(self):
        self.assertFalse((ROOT / "verify").exists())
        self.assertEqual(ROOT, VERIFY_MODULE.ROOT)
        for command in ([sys.executable, "scripts/verify.py", "--help"],
                        [sys.executable, "-m", "scripts.verify", "--help"]):
            with self.subTest(command=command):
                result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                                        timeout=20, check=False)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("--skip-avd", result.stdout)

    def test_moved_r8_scripts_resolve_project_root(self):
        for module in (check_release_dex, r8_audit, r8_runtime, release_startup_smoke):
            with self.subTest(module=module.__name__):
                self.assertEqual(ROOT, module.ROOT)
                self.assertTrue(Path(module.__file__).is_relative_to(ROOT / "scripts/r8"))
        self.assertEqual(ROOT / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk", VERIFY_MODULE.SMOKE_APK)

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.report_file = Path(self.folder.name) / "summary.json"
        self.report_patch = patch.object(VERIFY_MODULE, "VERIFY_REPORT", self.report_file)
        self.report_patch.start()
        self.addCleanup(self.report_patch.stop)

    def fake_execute(self, calls):
        def execute(label, argv, report):
            calls.append((label, list(map(str, argv))))
            report["steps"].append({"name": label, "status": "pass"})
        return execute

    def test_default_runs_every_suite_and_uses_disposable_avd(self):
        calls = []
        @contextlib.contextmanager
        def fake_avd(name, timeout):
            self.assertEqual(VERIFY_MODULE.AVD_NAME, name)
            self.assertEqual(420, timeout)
            yield "emulator-5580"
        with (
            patch.object(VERIFY_MODULE, "preflight"),
            patch.object(VERIFY_MODULE, "execute", side_effect=self.fake_execute(calls)),
            patch.object(VERIFY_MODULE, "test_emulator", side_effect=fake_avd),
        ):
            self.assertEqual(0, VERIFY_MODULE.main([]))
        self.assertEqual("pass", json.loads(self.report_file.read_text())["status"])
        self.assertEqual([
            "Script tests (Python + Node)",
            "Android format, lint, JVM and minified APK",
            "R8 static audit",
            "Documentation lint, types and unit tests",
            "Documentation browser tests",
            "Minified APK emulator + runtime tests",
        ], [name for name, _ in calls])
        android = calls[1][1]
        for check in ("checkFormatting", "lint", ":adblock:test",
                      ":app:testDebugUnitTest", ":app:assembleSmokeRelease", "--warning-mode=all"):
            self.assertIn(check, android)
        self.assertIn("--fail-on-warnings", calls[2][1])
        self.assertIn("--runtime", calls[-1][1])
        self.assertIn("emulator-5580", calls[-1][1])
        self.assertNotIn("--runtime", calls[2][1])

    def test_skipped_avd_and_docs_are_explicitly_partial(self):
        calls = []
        with (
            patch.object(VERIFY_MODULE, "preflight"),
            patch.object(VERIFY_MODULE, "execute", side_effect=self.fake_execute(calls)),
            patch.object(VERIFY_MODULE, "test_emulator") as boot,
        ):
            self.assertEqual(0, VERIFY_MODULE.main(["--skip-avd", "--skip-docs"]))
        boot.assert_not_called()
        self.assertEqual(3, len(calls))
        report = json.loads(self.report_file.read_text())
        self.assertEqual("partial", report["status"])
        self.assertEqual(2, len([s for s in report["steps"] if s["status"] == "skipped"]))
        self.assertFalse(report["requested"]["avd"])

    def test_explicit_serial_never_starts_or_stops_an_avd(self):
        calls = []
        with (
            patch.object(VERIFY_MODULE, "preflight"),
            patch.object(VERIFY_MODULE, "execute", side_effect=self.fake_execute(calls)),
            patch.object(VERIFY_MODULE, "test_emulator") as boot,
        ):
            self.assertEqual(0, VERIFY_MODULE.main(["--serial", "emulator-5554", "--skip-docs"]))
        boot.assert_not_called()
        self.assertIn("emulator-5554", calls[-1][1])

    def test_boot_failure_is_recorded_without_claiming_success(self):
        calls = []
        @contextlib.contextmanager
        def broken_avd(name, timeout):
            raise VERIFY_MODULE.VerificationError("No 16 KB image")
            yield "not-reached"
        with (
            patch.object(VERIFY_MODULE, "preflight"),
            patch.object(VERIFY_MODULE, "execute", side_effect=self.fake_execute(calls)),
            patch.object(VERIFY_MODULE, "test_emulator", side_effect=broken_avd),
        ):
            self.assertEqual(1, VERIFY_MODULE.main(["--skip-docs"]))
        report = json.loads(self.report_file.read_text())
        self.assertEqual("fail", report["status"])
        self.assertIn("No 16 KB image", report["error"])

    def test_failed_test_command_is_recorded(self):
        calls = []
        def fail(label, argv, report):
            report["steps"].append({"name": label, "status": "fail"})
            raise VERIFY_MODULE.VerificationError("Gradle failed")
        with patch.object(VERIFY_MODULE, "preflight"), patch.object(VERIFY_MODULE, "execute", side_effect=fail):
            self.assertEqual(1, VERIFY_MODULE.main(["--skip-avd", "--skip-docs"]))
        self.assertEqual("fail", json.loads(self.report_file.read_text())["status"])

    def test_avd_image_is_required_not_silently_downgraded(self):
        with patch.object(VERIFY_MODULE.platform, "machine", return_value="arm64"):
            with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, "google_apis_ps16k"):
                VERIFY_MODULE.image_package(Path(self.folder.name))
        image = Path(self.folder.name) / "system-images/android-36/google_apis_ps16k/arm64-v8a"
        image.mkdir(parents=True)
        (image / "source.properties").write_text("Pkg.Revision=1\n")
        with patch.object(VERIFY_MODULE.platform, "machine", return_value="arm64"):
            self.assertEqual("system-images;android-36;google_apis_ps16k;arm64-v8a",
                             VERIFY_MODULE.image_package(Path(self.folder.name)))

    def test_avd_boot_refuses_mismatched_serial(self):
        process = SimpleNamespace(poll=lambda: None)
        def output(argv, **kwargs):
            if "get-state" in argv:
                return SimpleNamespace(returncode=0, stdout="device\n")
            return SimpleNamespace(returncode=0, stdout="personal-avd\nOK\n")
        with patch.object(VERIFY_MODULE, "run_command", side_effect=output):
            with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, "belongs to personal-avd"):
                VERIFY_MODULE.boot_emulator("adb", "emulator-5580", "r8-verify-api36-16k", process, 30)

    def test_owned_emulator_is_stopped_on_failed_runtime_test(self):
        avd = Path(self.folder.name) / "avd"
        name = "test-only"
        (avd / f"{name}.avd").mkdir(parents=True)
        (avd / f"{name}.avd/config.ini").write_text("image.sysdir.1=...\n")
        process = MagicMock()
        process.poll.return_value = None
        calls = []
        def output(argv, **kwargs):
            calls.append(argv)
            if "name" in argv:
                return SimpleNamespace(returncode=0, stdout=name + "\nOK\n")
            return SimpleNamespace(returncode=0, stdout="OK\n")
        with (
            patch.object(VERIFY_MODULE, "AVD_ROOT", avd),
            patch.object(VERIFY_MODULE, "ROOT", Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "sdk_root", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "sdk_tool", side_effect=lambda sdk, name: name),
            patch.object(VERIFY_MODULE, "image_package", return_value="system-images;android-36;google_apis_ps16k;x86_64"),
            patch.object(VERIFY_MODULE, "available_port", return_value=5580),
            patch.object(VERIFY_MODULE, "boot_emulator"),
            patch.object(VERIFY_MODULE.subprocess, "Popen", return_value=process) as launch,
            patch.object(VERIFY_MODULE, "run_command", side_effect=output),
        ):
            with self.assertRaisesRegex(RuntimeError, "test failed"):
                with VERIFY_MODULE.test_emulator(name, 30):
                    raise RuntimeError("test failed")
        self.assertIn(["adb", "-s", "emulator-5580", "emu", "kill"], calls)
        self.assertIn("-wipe-data", launch.call_args.args[0])
        process.wait.assert_called_once()

    def test_invalid_avd_name_is_rejected(self):
        with self.assertRaises(SystemExit):
            VERIFY_MODULE.main(["--avd", "../personal", "--skip-avd"])


if __name__ == "__main__":
    unittest.main()
