"""Offline tests for the Android emulator action's single-command test wrapper."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/run-android-instrumentation.sh"


class RunAndroidInstrumentationTest(unittest.TestCase):
    def run_instrumentation(self, gradle_exit: int, adb_exit: int = 0):
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory)
            (project / "gradlew").write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$@\" > gradle-arguments.txt\n"
                f"exit {gradle_exit}\n",
                encoding="utf-8",
            )
            adb = project / "adb"
            adb.write_text(
                "#!/bin/sh\n"
                "printf 'emulator logcat\\n'\n"
                f"exit {adb_exit}\n",
                encoding="utf-8",
            )
            adb.chmod(0o755)
            env = os.environ.copy()
            env["PATH"] = f"{directory}:{env.get('PATH', '')}"
            result = subprocess.run(
                ["sh", str(SCRIPT)],
                cwd=project,
                env=env,
                capture_output=True,
                text=True,
                check=False,
            )
            log_file = (
                project / "app/build/outputs/androidTest-results/connected/device-logcat.txt"
            )
            return (
                result.returncode,
                (project / "gradle-arguments.txt").read_text(encoding="utf-8"),
                log_file.read_text(encoding="utf-8") if log_file.exists() else None,
            )

    def test_success_skips_logcat(self):
        status, arguments, logcat = self.run_instrumentation(0)
        self.assertEqual(0, status)
        self.assertEqual(":app:connectedDebugAndroidTest\n--stacktrace\n", arguments)
        self.assertIsNone(logcat)

    def test_failure_collects_logcat_and_preserves_gradle_exit(self):
        status, _, logcat = self.run_instrumentation(23)
        self.assertEqual(23, status)
        self.assertEqual("emulator logcat\n", logcat)

    def test_logcat_failure_does_not_mask_gradle_failure(self):
        status, _, _ = self.run_instrumentation(23, adb_exit=7)
        self.assertEqual(23, status)

    def test_runner_script_is_a_single_command(self):
        workflow = (ROOT / ".github/workflows/tests.yaml").read_text(encoding="utf-8")
        self.assertIn("script: sh scripts/ci/run-android-instrumentation.sh", workflow)


if __name__ == "__main__":
    unittest.main()
