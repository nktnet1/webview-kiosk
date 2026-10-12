"""Check local CI parity dispatch without an Android SDK or network access."""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / "scripts/ci/test-local.sh"
INSTRUMENTATION = ROOT / "scripts/ci/run-android-instrumentation.sh"


class LocalTestRunnerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "scripts/ci").mkdir(parents=True)
        (self.root / "docs").mkdir()
        shutil.copyfile(LAUNCHER, self.root / "scripts/ci/test-local.sh")
        shutil.copyfile(
            INSTRUMENTATION, self.root / "scripts/ci/run-android-instrumentation.sh"
        )

        (self.root / "gradlew").write_text(
            "#!/bin/sh\n"
            'printf "%s\\n" "$@" > gradle-args.txt\n'
            'exit "${STUB_GRADLE_EXIT:-0}"\n',
            encoding="utf-8",
        )
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        adb = bin_dir / "adb"
        adb.write_text(
            "#!/bin/sh\n"
            'case "$1" in\n'
            '  devices) printf "List of devices attached\\\\nemulator-5554\\\\tdevice\\\\n" ;;\n'
            '  -s) printf "${STUB_API:-35}\\\\n" ;;\n'
            '  logcat) printf "diagnostic log\\\\n" ;;\n'
            'esac\n',
            encoding="utf-8",
        )
        adb.chmod(0o755)
        pnpm = bin_dir / "pnpm"
        pnpm.write_text(
            '#!/bin/sh\nprintf "%s\\n" "$*" >> pnpm-args.txt\n',
            encoding="utf-8",
        )
        pnpm.chmod(0o755)
        self.env = os.environ.copy()
        self.env.pop("ANDROID_SERIAL", None)
        self.env.pop("ANDROID_TEST_CLASS", None)
        self.env["PATH"] = f"{bin_dir}:{self.env.get('PATH', '')}"

    def run_suite(self, *args, env=None):
        return subprocess.run(
            ["sh", str(self.root / "scripts/ci/test-local.sh"), *args],
            cwd=self.root,
            env=env or self.env,
            capture_output=True,
            text=True,
            check=False,
        )

    def read_gradle_args(self):
        path = self.root / "gradle-args.txt"
        return path.read_text(encoding="utf-8") if path.exists() else None

    def test_help_is_accessible_without_dependencies(self):
        result = self.run_suite("--help")
        self.assertEqual(0, result.returncode)
        self.assertIn("--class NAME", result.stdout)
        self.assertIsNone(self.read_gradle_args())

    def test_jvm_runs_exact_ci_gradle_task(self):
        result = self.run_suite("jvm")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":app:testDebugUnitTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )

    def test_android_filters_one_test_method_on_api_35(self):
        result = self.run_suite(
            "android",
            "--api",
            "35",
            "--class",
            "AuthenticationRouteDeviceTest#settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("emulator-5554 (API 35)", result.stdout)
        self.assertEqual(
            ":app:connectedDebugAndroidTest\n--build-cache\n--profile\n--stacktrace\n"
            "-Pandroid.testInstrumentationRunnerArguments.class="
            "uk.nktnet.webviewkiosk.instrumentation.AuthenticationRouteDeviceTest"
            "#settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation\n",
            self.read_gradle_args(),
        )

    def test_android_without_filter_runs_entire_suite(self):
        result = self.run_suite("android", "--api", "35")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":app:connectedDebugAndroidTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )

    def test_android_failure_preserves_gradle_status_and_diagnostics(self):
        reporter = self.root / "scripts/ci/report_android_instrumentation.py"
        reporter.write_text(
            'print("JUnit failure report")\n', encoding="utf-8"
        )
        env = self.env.copy()
        env["STUB_GRADLE_EXIT"] = "17"
        result = self.run_suite("android", "--api", "35", env=env)
        self.assertEqual(17, result.returncode)
        self.assertIn("JUnit failure report", result.stdout)
        logcat = (
            self.root
            / "app/build/outputs/androidTest-results/connected/device-logcat.txt"
        )
        self.assertEqual("diagnostic log\n", logcat.read_text(encoding="utf-8"))

    def test_android_rejects_empty_filter_before_running(self):
        result = self.run_suite("android", "--class", "")
        self.assertEqual(2, result.returncode)
        self.assertIn("Missing value for --class", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_android_rejects_wrong_api_before_starting_gradle(self):
        result = self.run_suite("android", "--api", "23")
        self.assertEqual(2, result.returncode)
        self.assertIn("has API 35", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_android_rejects_invalid_class_and_multiple_devices(self):
        result = self.run_suite("android", "--class", "invalid;touch /tmp/oops")
        self.assertEqual(2, result.returncode)
        self.assertIn("Invalid --class", result.stderr)
        self.assertIsNone(self.read_gradle_args())

        # Simulate the real adb output for multiple connected devices.
        adb = self.root / "bin/adb"
        text = adb.read_text(encoding="utf-8")
        text = text.replace(
            "emulator-5554\\\\tdevice\\\\n",
            "emulator-5554\\\\tdevice\\\\nphysical-device\\\\tdevice\\\\n",
        )
        adb.write_text(text, encoding="utf-8")
        result = self.run_suite("android", "--api", "35")
        self.assertEqual(2, result.returncode)
        self.assertIn("exactly one", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_docs_browser_requires_preinstalled_dependencies(self):
        missing = self.run_suite("docs-browser")
        self.assertEqual(2, missing.returncode)
        self.assertIn("docs/node_modules is missing", missing.stderr)
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("docs-browser")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("-C docs build\n-C docs test:e2e\n", (self.root / "pnpm-args.txt").read_text())

    def test_docs_runs_checks_and_vitest(self):
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("docs")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("-C docs check\n-C docs test\n", (self.root / "pnpm-args.txt").read_text())

    def test_non_android_suite_rejects_android_filters(self):
        result = self.run_suite("jvm", "--class", "AuthenticationRouteDeviceTest")
        self.assertEqual(2, result.returncode)
        self.assertIn("only valid with the android suite", result.stderr)
        self.assertIsNone(self.read_gradle_args())


if __name__ == "__main__":
    unittest.main()
