"""Check local CI parity dispatch without an Android SDK or network access."""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / "scripts/ci/test-local.sh"


class LocalTestRunnerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "scripts/ci").mkdir(parents=True)
        (self.root / "docs").mkdir()
        shutil.copyfile(LAUNCHER, self.root / "scripts/ci/test-local.sh")

        (self.root / "gradlew").write_text(
            "#!/bin/sh\n"
            'printf "%s\\n" "$@" > gradle-args.txt\n'
            'exit "${STUB_GRADLE_EXIT:-0}"\n',
            encoding="utf-8",
        )
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name in ("node", "java"):
            command = bin_dir / name
            command.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            command.chmod(0o755)
        pnpm = bin_dir / "pnpm"
        pnpm.write_text(
            '#!/bin/sh\nprintf "%s\\n" "$*" >> pnpm-args.txt\n',
            encoding="utf-8",
        )
        pnpm.chmod(0o755)
        self.env = os.environ.copy()
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
        self.assertIn("JVM/Robolectric", result.stdout)
        self.assertNotIn("android         ", result.stdout)
        self.assertIsNone(self.read_gradle_args())

    def test_jvm_runs_exact_ci_gradle_task(self):
        result = self.run_suite("jvm")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":app:testDebugUnitTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )

    def test_android_suite_is_not_available(self):
        result = self.run_suite("android")
        self.assertEqual(2, result.returncode)
        self.assertIn("Unknown suite 'android'", result.stderr)
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

    def test_other_suites_reject_removed_android_options(self):
        result = self.run_suite("jvm", "--api", "35")
        self.assertEqual(2, result.returncode)
        self.assertIn("Unexpected option '--api'", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_all_dispatches_only_non_avd_suites(self):
        # Avoid installing packages: record a real script dispatch with stubs.
        (self.root / "docs/node_modules").mkdir()
        (self.root / "scripts/tests").mkdir()
        (self.root / "scripts/tests/export-android-test-assets.test.sh").write_text(
            "#!/bin/sh\nexit 0\n", encoding="utf-8"
        )
        # Stub python3 so the offline Python suite does not require copied tests.
        stub = self.root / "bin/python3"
        stub.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        stub.chmod(0o755)
        result = self.run_suite("all")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":app:testDebugUnitTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )
        self.assertEqual(
            "-C docs check\n-C docs test\n-C docs build\n-C docs test:e2e\n",
            (self.root / "pnpm-args.txt").read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
