"""Regression tests for the path-independent, default-enabled R8 audit runner."""

import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPT = Path(__file__).resolve().parents[1] / "r8_verify.py"
SPEC = importlib.util.spec_from_file_location("r8_verify", SCRIPT)
assert SPEC and SPEC.loader
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


class FindAuditTests(unittest.TestCase):
    def test_finds_audit_in_moved_subfolder(self):
        with tempfile.TemporaryDirectory() as directory:
            scripts = Path(directory)
            audit = scripts / "r8" / "diagnostics" / "r8_audit.py"
            audit.parent.mkdir(parents=True)
            audit.write_text("# moved\n", encoding="utf-8")
            self.assertEqual(verify.find_audit(scripts), audit)

    def test_finds_original_root_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            scripts = Path(directory)
            audit = scripts / "r8_audit.py"
            audit.write_text("# original\n", encoding="utf-8")
            self.assertEqual(verify.find_audit(scripts), audit)

    def test_rejects_ambiguous_audit_scripts(self):
        with tempfile.TemporaryDirectory() as directory:
            scripts = Path(directory)
            (scripts / "r8_audit.py").touch()
            (scripts / "r8").mkdir()
            (scripts / "r8" / "r8_audit.py").touch()
            with self.assertRaisesRegex(verify.SetupError, "found 2"):
                verify.find_audit(scripts)


class EmulatorTests(unittest.TestCase):
    @mock.patch.object(verify, "adb_output")
    def test_no_compatible_emulators(self, output):
        output.side_effect = lambda *_args: "List of devices attached\nemulator-5554\tdevice"
        with mock.patch.object(verify, "compatible_emulator", return_value=False):
            self.assertIsNone(verify.select_emulator("adb"))

    @mock.patch.object(verify, "adb_output")
    def test_selects_single_compatible_device(self, output):
        output.return_value = "List of devices attached\nemulator-5554\tdevice\nphone\tdevice"
        with mock.patch.object(verify, "compatible_emulator", side_effect=[True, False]):
            self.assertEqual(verify.select_emulator("adb"), "emulator-5554")

    @mock.patch.object(verify, "adb_output")
    def test_rejects_multiple_compatible_devices(self, output):
        output.return_value = "List of devices attached\nemulator-5554\tdevice\nemulator-5556\tdevice"
        with mock.patch.object(verify, "compatible_emulator", return_value=True):
            with self.assertRaisesRegex(verify.SetupError, "Multiple compatible"):
                verify.select_emulator("adb")

    @mock.patch.object(verify, "adb_output")
    def test_checks_api_and_page_size_and_emulator(self, output):
        output.side_effect = ["36", "16384", "0", "1"]
        self.assertTrue(verify.compatible_emulator("adb", "emulator-5554"))


class DefaultCliTests(unittest.TestCase):
    @mock.patch.object(verify, "subprocess")
    def test_default_emulator_runs_all_probes(self, process):
        process.run.return_value.returncode = 0
        with mock.patch.object(verify, "find_audit", return_value=Path("scripts/r8/r8_audit.py")):
            with mock.patch.object(verify, "find_adb", return_value="adb"):
                with mock.patch.object(verify, "select_emulator", return_value="emulator-5554"):
                    self.assertEqual(verify.main([]), 0)
        args = process.run.call_args.args[0]
        self.assertIn("--require-mapping", args)
        self.assertIn("--runtime", args)
        self.assertIn("--device", args)
        self.assertIn("emulator-5554", args)
        self.assertIn("--json", args)

    @mock.patch.object(verify, "subprocess")
    def test_without_emulator_still_writes_json(self, process):
        process.run.return_value.returncode = 0
        with mock.patch.object(verify, "find_audit", return_value=Path("scripts/r8/r8_audit.py")):
            with mock.patch.object(verify, "find_adb", return_value=None):
                self.assertEqual(verify.main([]), 0)
        args = process.run.call_args.args[0]
        self.assertIn("--json", args)
        self.assertNotIn("--device", args)
        self.assertNotIn("--runtime", args)

    @mock.patch.object(verify, "subprocess")
    def test_explicit_args_are_forwarded_unchanged(self, process):
        process.run.return_value.returncode = 7
        with mock.patch.object(verify, "find_audit", return_value=Path("scripts/r8/r8_audit.py")):
            self.assertEqual(verify.main(["--help"]), 7)
        self.assertEqual(process.run.call_args.args[0][-1:], ["--help"])


if __name__ == "__main__":
    unittest.main()
