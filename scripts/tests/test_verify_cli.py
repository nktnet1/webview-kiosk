"""Contract tests for the single-command release verifier, without an SDK/AVD."""

import contextlib
import io
import os
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from scripts import verify as VERIFY_MODULE
from scripts.r8 import check_release_dex, r8_audit, r8_runtime, r8_verify, release_startup_smoke

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
        self.assertEqual(ROOT, r8_verify.PROJECT_ROOT)
        self.assertTrue(Path(r8_verify.__file__).is_relative_to(ROOT / "scripts/r8"))
        self.assertEqual(ROOT / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk", VERIFY_MODULE.SMOKE_APK)

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.report_file = Path(self.folder.name) / "summary.json"
        self.report_patch = patch.object(VERIFY_MODULE, "VERIFY_REPORT", self.report_file)
        self.report_patch.start()
        self.addCleanup(self.report_patch.stop)

    def fake_execute(self, calls):
        def execute(label, argv, report, **kwargs):
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
        for index in (0, 3, 4):
            self.assertEqual([sys.executable, "scripts/ci/test_local.py"], calls[index][1][:2])
        self.assertIn("--runtime", calls[-1][1])
        self.assertIn("emulator-5580", calls[-1][1])
        self.assertNotIn("--runtime", calls[2][1])

    def test_preflight_reuses_running_emulator_without_local_system_image(self):
        args = SimpleNamespace(skip_docs=True, skip_avd=False, serial=None)
        tools = []
        def tool(sdk, name, **kwargs):
            tools.append(name)
            return name
        with (
            patch.dict(os.environ, {"PATH": os.environ["PATH"]}),
            patch.object(VERIFY_MODULE, "select_jdk21", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "run_command", return_value=SimpleNamespace(returncode=0, stdout="24.0.0")),
            patch.object(VERIFY_MODULE, "sdk_root", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "sdk_tool", side_effect=tool),
            patch.object(VERIFY_MODULE, "select_running_emulator", return_value="emulator-5554"),
            patch.object(VERIFY_MODULE, "image_package") as image,
        ):
            VERIFY_MODULE.preflight(args)
        image.assert_not_called()
        self.assertEqual("emulator-5554", args.running_serial)
        self.assertNotIn("avdmanager", tools)
        self.assertNotIn("emulator", tools)

    def test_preflight_no_running_emulator_requires_isolated_image(self):
        args = SimpleNamespace(skip_docs=True, skip_avd=False, serial=None)
        with (
            patch.dict(os.environ, {"PATH": os.environ["PATH"]}),
            patch.object(VERIFY_MODULE, "select_jdk21", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "run_command", return_value=SimpleNamespace(returncode=0, stdout="24.0.0")),
            patch.object(VERIFY_MODULE, "sdk_root", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "sdk_tool", return_value="tool"),
            patch.object(VERIFY_MODULE, "select_running_emulator", return_value=None),
            patch.object(VERIFY_MODULE, "image_package", side_effect=VERIFY_MODULE.VerificationError("image missing")),
        ):
            with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, "image missing"):
                VERIFY_MODULE.preflight(args)

    def test_running_emulator_selection_does_not_hide_ambiguity_or_adb_errors(self):
        for message in (
            "More than one compatible emulator is running: emulator-5554, emulator-5556",
            "adb devices failed",
        ):
            with self.subTest(message=message):
                with patch.object(release_startup_smoke, "select_emulator",
                                  side_effect=release_startup_smoke.SmokeTestError(message)):
                    with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, message.split(":")[0]):
                        VERIFY_MODULE.select_running_emulator("adb")
        with patch.object(release_startup_smoke, "select_emulator", side_effect=
                          release_startup_smoke.SmokeTestError("No running Android 16 (API 36) emulator with 16 KiB pages")):
            self.assertIsNone(VERIFY_MODULE.select_running_emulator("adb"))

    def test_default_reuses_running_serial_without_starting_another_avd(self):
        calls = []
        def preflight(args):
            args.running_serial = "emulator-5554"
        with (
            patch.object(VERIFY_MODULE, "preflight", side_effect=preflight),
            patch.object(VERIFY_MODULE, "execute", side_effect=self.fake_execute(calls)),
            patch.object(VERIFY_MODULE, "test_emulator") as boot,
        ):
            self.assertEqual(0, VERIFY_MODULE.main(["--skip-docs"]))
        boot.assert_not_called()
        self.assertEqual("emulator-5554", calls[-1][1][calls[-1][1].index("--serial") + 1])

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
        def fail(label, argv, report, **kwargs):
            report["steps"].append({"name": label, "status": "fail"})
            raise VERIFY_MODULE.VerificationError("Gradle failed")
        with patch.object(VERIFY_MODULE, "preflight"), patch.object(VERIFY_MODULE, "execute", side_effect=fail):
            self.assertEqual(1, VERIFY_MODULE.main(["--skip-avd", "--skip-docs"]))
        self.assertEqual("fail", json.loads(self.report_file.read_text())["status"])

    def test_selects_installed_jdk21_even_if_java_home_is_another_version(self):
        wrong = Path(self.folder.name) / "jdk25"
        correct = Path(self.folder.name) / "jbr21"
        for home in (wrong, correct):
            (home / "bin").mkdir(parents=True)
            (home / "bin/java").touch()
            (home / "bin/javac").touch()

        def java_version(argv, **kwargs):
            version = "21.0.11" if str(correct) in str(argv[0]) else "25.0.1"
            return SimpleNamespace(returncode=0, stdout="", stderr=f'openjdk version "{version}"\n')

        with (
            patch.object(VERIFY_MODULE, "jdk21_candidates", return_value=iter([wrong, correct])),
            patch.object(VERIFY_MODULE, "run_command", side_effect=java_version),
        ):
            self.assertEqual(correct, VERIFY_MODULE.select_jdk21())

    def test_prefers_jetbrains_jbr21_for_gradle_daemon(self):
        home = Path(self.folder.name)
        regular = home / "temurin21"
        jetbrains = home / "jbr21"
        for candidate in (regular, jetbrains):
            (candidate / "bin").mkdir(parents=True)
            (candidate / "bin/java").touch()
            (candidate / "bin/javac").touch()
        with (
            patch.object(VERIFY_MODULE, "jdk21_candidates", return_value=iter([regular, jetbrains])),
            patch.object(VERIFY_MODULE, "run_command", return_value=SimpleNamespace(
                returncode=0, stdout="", stderr='openjdk version "21.0.11"\n'
            )),
        ):
            self.assertEqual(jetbrains, VERIFY_MODULE.select_jdk21())

    def test_invalid_java_candidate_does_not_block_discovery(self):
        folder = Path(self.folder.name)
        bad = folder / "bad"
        good = folder / "good21"
        for home in (bad, good):
            (home / "bin").mkdir(parents=True)
            (home / "bin/java").touch()
            (home / "bin/javac").touch()
        def probe(argv, **kwargs):
            if str(argv[0]).startswith(str(bad)):
                raise VERIFY_MODULE.VerificationError("Cannot execute")
            return SimpleNamespace(returncode=0, stdout="", stderr='openjdk version "21.0.11"\n')
        with (
            patch.object(VERIFY_MODULE, "jdk21_candidates", return_value=iter([bad, good])),
            patch.object(VERIFY_MODULE, "run_command", side_effect=probe),
        ):
            self.assertEqual(good, VERIFY_MODULE.select_jdk21())

    def test_select_jdk21_requires_a_full_jdk_not_just_a_java_runtime(self):
        runtime_only = Path(self.folder.name) / "jre21"
        (runtime_only / "bin").mkdir(parents=True)
        (runtime_only / "bin/java").touch()
        with (
            patch.object(VERIFY_MODULE, "jdk21_candidates", return_value=iter([runtime_only])),
            patch.object(VERIFY_MODULE, "run_command") as run,
        ):
            with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, "JDK 21 not found"):
                VERIFY_MODULE.select_jdk21()
        run.assert_not_called()

    def test_detected_jdk_is_used_by_gradle_child_processes(self):
        jdk = Path(self.folder.name) / "jbr21"
        args = SimpleNamespace(skip_docs=True, skip_avd=True, serial=None)
        with (
            patch.dict(os.environ, {"JAVA_HOME": "/not-the-correct-jdk", "PATH": os.environ["PATH"]}),
            patch.object(VERIFY_MODULE, "select_jdk21", return_value=jdk),
            patch.object(VERIFY_MODULE, "run_command", return_value=SimpleNamespace(returncode=0, stdout="24.1.0")),
            patch.object(VERIFY_MODULE, "sdk_root", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE, "sdk_tool", return_value="tool"),
        ):
            VERIFY_MODULE.preflight(args)
            self.assertEqual(str(jdk), os.environ["JAVA_HOME"])
            self.assertEqual(str(jdk / "bin"), os.environ["PATH"].split(os.pathsep)[0])
        self.assertNotEqual(str(jdk), os.environ.get("JAVA_HOME"))

    def test_jdk21_discovery_respects_android_studio_and_mac_java_home(self):
        with (
            patch.dict(os.environ, {"JAVA_HOME": "/bad/jdk"}),
            patch.object(VERIFY_MODULE.platform, "system", return_value="Darwin"),
            patch.object(VERIFY_MODULE, "run_command", return_value=SimpleNamespace(
                returncode=0, stdout="/Library/Java/JavaVirtualMachines/jetbrains-21/Contents/Home\n"
            )),
            patch.object(VERIFY_MODULE.Path, "home", return_value=Path(self.folder.name)),
            patch.object(VERIFY_MODULE.shutil, "which", return_value=None),
        ):
            homes = list(VERIFY_MODULE.jdk21_candidates())
        self.assertIn(Path("/bad/jdk"), homes)
        self.assertIn(Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"), homes)
        # /usr/libexec/java_home exists on macOS, so the stub is conditional on
        # the host instead of trying to invoke the Linux system's tool here.

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

    def test_colour_is_interactive_only_and_respects_no_color(self):
        class Terminal(io.StringIO):
            def isatty(self):
                return True

        env = {**os.environ, "TERM": "xterm"}
        env.pop("NO_COLOR", None)
        with patch.dict(os.environ, env, clear=True):
            self.assertIn("\033[1;32m", VERIFY_MODULE.colour("PASS", "1;32", stream=Terminal()))
            self.assertEqual("PASS", VERIFY_MODULE.colour("PASS", "1;32", stream=io.StringIO()))
            os.environ["NO_COLOR"] = ""
            self.assertEqual("PASS", VERIFY_MODULE.colour("PASS", "1;32", stream=Terminal()))

    def test_step_headings_and_skips_have_clear_separation(self):
        report = {"requested": {"docs": False}, "steps": []}
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            VERIFY_MODULE.skipped("Documentation suites", "--skip-docs", report)
        self.assertIn("Step 1/5 - Documentation suites", output.getvalue())
        self.assertIn("SKIPPED - Documentation suites (--skip-docs)", output.getvalue())
        self.assertEqual([{"name": "Documentation suites", "status": "skipped",
                           "reason": "--skip-docs"}], report["steps"])

    def test_script_tests_hide_expected_errors_but_keep_full_log(self):
        report = {"requested": {"docs": True}, "steps": []}
        log = Path(self.folder.name) / "script-tests.log"
        output = io.StringIO()

        def test_process(argv, **kwargs):
            self.assertEqual(subprocess.STDOUT, kwargs["stderr"])
            kwargs["stdout"].write("Error: 'magick' command not found\n"
                                   "Ran 184 tests in 5.2s\nOK\n# tests 311\n# pass 311\n")
            return SimpleNamespace(returncode=0)

        with (patch.object(VERIFY_MODULE, "ROOT", Path(self.folder.name)),
              patch.object(VERIFY_MODULE, "SCRIPT_TEST_LOG", log),
              patch.object(VERIFY_MODULE.subprocess, "run", side_effect=test_process),
              contextlib.redirect_stdout(output)):
            VERIFY_MODULE.execute("Script tests (Python + Node)", ["python3", "tests"], report,
                                  capture_output=True)
        self.assertEqual("pass", report["steps"][0]["status"])
        self.assertEqual("script-tests.log", report["steps"][0]["log"])
        self.assertNotIn("magick", output.getvalue())
        self.assertIn("Python: 184 tests | Node: 311 tests", output.getvalue())
        self.assertIn("PASS - Script tests", output.getvalue())
        self.assertIn("Error: 'magick' command not found", log.read_text())

    def test_failed_script_suite_surfaces_errors_and_writes_failure_report(self):
        report = {"requested": {"docs": True}, "steps": []}
        log = Path(self.folder.name) / "script-tests.log"
        output = io.StringIO()
        errors = io.StringIO()

        def test_process(argv, **kwargs):
            kwargs["stdout"].write("AssertionError: real regression\n")
            return SimpleNamespace(returncode=4)

        with (patch.object(VERIFY_MODULE, "ROOT", Path(self.folder.name)),
              patch.object(VERIFY_MODULE, "SCRIPT_TEST_LOG", log),
              patch.object(VERIFY_MODULE.subprocess, "run", side_effect=test_process),
              contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors)):
            with self.assertRaisesRegex(VERIFY_MODULE.VerificationError, "exit 4"):
                VERIFY_MODULE.execute("Script tests (Python + Node)", ["python3", "tests"], report,
                                      capture_output=True)
        self.assertIn("AssertionError: real regression", errors.getvalue())
        self.assertIn("FAIL - Script tests", output.getvalue())
        self.assertEqual("fail", report["steps"][0]["status"])


if __name__ == "__main__":
    unittest.main()
