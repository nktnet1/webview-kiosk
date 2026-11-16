"""Validate the shared IDE inspection profile metadata and ignored paths."""

import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROFILE_DIR = ROOT / ".idea/inspectionProfiles"
PROFILE = PROFILE_DIR / "WebviewKiosk.xml"


class IdeInspectionConfigurationTests(unittest.TestCase):
    def test_active_profile_matches_file_and_internal_name(self):
        profile = ET.parse(PROFILE).getroot()
        declared = profile.find("./profile/option[@name='myName']")
        settings = ET.parse(PROFILE_DIR / "profiles_settings.xml").getroot()
        selected = settings.find("./settings/option[@name='PROJECT_PROFILE']")
        active = settings.find("./settings/option[@name='USE_PROJECT_PROFILE']")
        self.assertIsNotNone(declared)
        self.assertIsNotNone(selected)
        self.assertIsNotNone(active)
        self.assertEqual(PROFILE.stem, declared.get("value"))
        self.assertEqual(PROFILE.stem, selected.get("value"))
        self.assertEqual("true", active.get("value"))

    def test_noisy_inspections_disabled_but_correctness_checks_kept(self):
        profile = ET.parse(PROFILE).getroot()
        for inspection in (
            "CheckImageSize", "ClassIndependentOfModule",
            "ClassOnlyUsedInOneModule", "GrazieInspection", "GrazieStyle",
            "HtmlDeprecatedAttribute", "ModuleWithTooManyClasses",
            "SpellCheckingInspection", "UnusedLibrary", "unused",
        ):
            with self.subTest(inspection=inspection):
                tool = profile.find(f"./profile/inspection_tool[@class='{inspection}']")
                self.assertIsNotNone(tool)
                self.assertEqual("false", tool.get("enabled"))
        for inspection in ("UnsecureRandomNumberGeneration", "JDBCExecuteWithNonConstantString"):
            with self.subTest(inspection=inspection):
                tool = profile.find(f"./profile/inspection_tool[@class='{inspection}']")
                self.assertIsNotNone(tool)
                self.assertEqual("true", tool.get("enabled"))

    def test_git_ignore_includes_secrets_and_build_outputs(self):
        for path in (
            ".env", ".env.local", "app/hs_err_pid12345.log",
            ".kotlin/errors/errors-12345.log", "app/build/reports/lint-results.xml",
        ):
            with self.subTest(path=path):
                result = subprocess.run(
                    ["git", "check-ignore", "-q", "--no-index", "--", path],
                    cwd=ROOT, check=False, capture_output=True,
                )
                self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
