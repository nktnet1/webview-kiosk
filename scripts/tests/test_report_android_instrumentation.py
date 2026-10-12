"""Offline regression tests for the CI instrumentation failure reporter."""

import tempfile
import unittest
from pathlib import Path

from scripts.ci.report_android_instrumentation import report


class ReportAndroidInstrumentationTest(unittest.TestCase):
    def test_renders_test_name_and_failure_stack(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            test_file = reports / "debug" / "pixel" / "TEST-example.xml"
            test_file.parent.mkdir(parents=True)
            test_file.write_text(
                "<testsuite><testcase classname='ExampleDeviceTest' name='myTest'>"
                "<failure message='expected: 1 but was: 0'>assertion at ExampleDeviceTest.kt:42"
                "</failure></testcase></testsuite>",
                encoding="utf-8",
            )
            output = report(reports)
            self.assertIn("FAIL ExampleDeviceTest.myTest (failure)", output)
            self.assertIn("expected: 1 but was: 0", output)
            self.assertIn("ExampleDeviceTest.kt:42", output)

    def test_renders_errors_and_no_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            self.assertIn("No JUnit XML reports", report(reports))
            (reports / "TEST-crash.xml").write_text(
                "<testsuites><testsuite><testcase classname='Example' name='startup'>"
                "<error type='RuntimeException'>crashed</error></testcase>"
                "</testsuite></testsuites>",
                encoding="utf-8",
            )
            output = report(reports)
            self.assertIn("FAIL Example.startup (error): crashed", output)

    def test_malformed_xml_is_reported_without_crashing(self):
        with tempfile.TemporaryDirectory() as directory:
            reports = Path(directory)
            (reports / "TEST-truncated.xml").write_text("<testsuite>", encoding="utf-8")
            self.assertIn("Could not read", report(reports))


if __name__ == "__main__":
    unittest.main()
