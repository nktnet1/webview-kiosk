"""Check Python local CI parity dispatch without an Android SDK or network access."""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / "scripts/ci/test_local.py"


class LocalTestRunnerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "scripts/ci").mkdir(parents=True)
        (self.root / "scripts/tests").mkdir()
        (self.root / "docs").mkdir()
        shutil.copyfile(LAUNCHER, self.root / "scripts/ci/test_local.py")
        for name in ("alpha.test.mjs", "beta.test.mjs"):
            (self.root / "scripts/tests" / name).touch()

        (self.root / "gradlew").write_text(
            "#!/bin/sh\n"
            'printf "%s\\n" "$@" > gradle-args.txt\n'
            'exit "${STUB_GRADLE_EXIT:-0}"\n',
            encoding="utf-8",
        )
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name in ("python3", "node", "java"):
            command = bin_dir / name
            command.write_text(
                '#!/bin/sh\nprintf "%s\\n" "$@" >> ' + name + '-args.txt\n'
                + 'exit "${STUB_' + name.upper() + '_EXIT:-0}"\n',
                encoding="utf-8",
            )
            command.chmod(0o755)
        pnpm = bin_dir / "pnpm"
        pnpm.write_text(
            '#!/bin/sh\nprintf "%s\\n" "$@" >> pnpm-args.txt\nexit "${STUB_PNPM_EXIT:-0}"\n',
            encoding="utf-8",
        )
        pnpm.chmod(0o755)
        self.env = os.environ.copy()
        self.env["PATH"] = f"{bin_dir}:{self.env.get('PATH', '')}"

    def run_suite(self, *args, env=None, cwd=None):
        return subprocess.run(
            [sys.executable, str(self.root / "scripts/ci/test_local.py"), *args],
            cwd=cwd or self.root,
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
        self.assertEqual(2, self.run_suite().returncode)

    def test_jvm_runs_exact_ci_gradle_task(self):
        result = self.run_suite("jvm")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":adblock:test\n:app:testDebugUnitTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )

    def test_android_suite_is_not_available(self):
        result = self.run_suite("android")
        self.assertEqual(2, result.returncode)
        self.assertIn("Unknown suite 'android'", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_checks_runs_python_and_expands_node_glob_without_shell(self):
        result = self.run_suite("checks")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            "-m\nunittest\ndiscover\n-s\nscripts/tests\n-p\ntest_*.py\n",
            (self.root / "python3-args.txt").read_text(encoding="utf-8"),
        )
        self.assertEqual(
            "--test\nscripts/tests/alpha.test.mjs\nscripts/tests/beta.test.mjs\n",
            (self.root / "node-args.txt").read_text(encoding="utf-8"),
        )

    def test_checks_stops_before_node_when_python_fails(self):
        result = self.run_suite("checks", env={**self.env, "STUB_PYTHON3_EXIT": "42"})
        self.assertEqual(42, result.returncode)
        self.assertFalse((self.root / "node-args.txt").exists())

    def test_node_failure_propagates_to_ci(self):
        result = self.run_suite("checks", env={**self.env, "STUB_NODE_EXIT": "31"})
        self.assertEqual(31, result.returncode)
        self.assertTrue((self.root / "python3-args.txt").exists())

    def test_checks_fails_clearly_if_node_tests_are_missing(self):
        for test in (self.root / "scripts/tests").glob("*.test.mjs"):
            test.unlink()
        result = self.run_suite("checks")
        self.assertEqual(2, result.returncode)
        self.assertIn("No Node script tests found", result.stderr)
        self.assertFalse((self.root / "node-args.txt").exists())

    def test_docs_browser_requires_preinstalled_dependencies(self):
        missing = self.run_suite("docs-browser")
        self.assertEqual(2, missing.returncode)
        self.assertIn("docs/node_modules is missing", missing.stderr)
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("docs-browser")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            "-C\ndocs\nbuild\n-C\ndocs\ntest:e2e\n", (self.root / "pnpm-args.txt").read_text()
        )

    def test_docs_runs_checks_and_vitest(self):
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("docs")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("-C\ndocs\ncheck\n-C\ndocs\ntypecheck\n-C\ndocs\ntest\n",
                         (self.root / "pnpm-args.txt").read_text(encoding="utf-8"))

    def test_other_suites_reject_removed_android_options(self):
        result = self.run_suite("jvm", "--api", "35")
        self.assertEqual(2, result.returncode)
        self.assertIn("Unexpected option '--api'", result.stderr)
        self.assertIsNone(self.read_gradle_args())

    def test_failure_propagates_and_stops_before_next_suite(self):
        env = {**self.env, "STUB_GRADLE_EXIT": "17"}
        result = self.run_suite("all", env=env)
        self.assertEqual(17, result.returncode)
        self.assertFalse((self.root / "pnpm-args.txt").exists())
        self.assertEqual(
            "--test\nscripts/tests/alpha.test.mjs\nscripts/tests/beta.test.mjs\n",
            (self.root / "node-args.txt").read_text(encoding="utf-8"),
        )

    def test_docs_failure_stops_before_typecheck_or_vitest(self):
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("docs", env={**self.env, "STUB_PNPM_EXIT": "23"})
        self.assertEqual(23, result.returncode)
        self.assertEqual("-C\ndocs\ncheck\n", (self.root / "pnpm-args.txt").read_text())

    def test_path_resolution_does_not_depend_on_cwd(self):
        result = self.run_suite("checks", cwd=self.root.parent)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue((self.root / "node-args.txt").exists())

    def test_all_dispatches_only_non_avd_suites(self):
        (self.root / "docs/node_modules").mkdir()
        result = self.run_suite("all")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ":adblock:test\n:app:testDebugUnitTest\n--build-cache\n--profile\n--stacktrace\n",
            self.read_gradle_args(),
        )
        self.assertEqual(
            "-C\ndocs\ncheck\n-C\ndocs\ntypecheck\n-C\ndocs\ntest\n-C\ndocs\nbuild\n-C\ndocs\ntest:e2e\n",
            (self.root / "pnpm-args.txt").read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
