"""Keep Android Studio inspections and Git ignore rules consistent across checkouts."""

import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROFILE_DIR = ROOT / ".idea/inspectionProfiles"


class IdeInspectionConfigurationTests(unittest.TestCase):
    def test_shared_profile_is_selected(self):
        profile = ET.parse(PROFILE_DIR / "Project_Default.xml").getroot()
        profile_name = profile.find("./profile/option[@name='myName']")
        self.assertIsNotNone(profile_name)

        settings = ET.parse(PROFILE_DIR / "profiles_settings.xml").getroot()
        selected = settings.find("./settings/option[@name='PROJECT_PROFILE']")
        enabled = settings.find("./settings/option[@name='USE_PROJECT_PROFILE']")
        self.assertIsNotNone(selected)
        self.assertIsNotNone(enabled)
        self.assertEqual(profile_name.get("value"), selected.get("value"))
        self.assertEqual("true", enabled.get("value"))

    def test_noisy_ide_inspections_are_disabled_but_grammar_is_retained(self):
        profile = ET.parse(PROFILE_DIR / "Project_Default.xml").getroot()
        disabled = (
            "CheckImageSize",
            "ClassIndependentOfModule",
            "ClassOnlyUsedInOneModule",
            "GrazieStyle",
            "HtmlDeprecatedAttribute",
            "ModuleWithTooManyClasses",
            "UnusedLibrary",
            "unused",
        )
        for inspection in disabled:
            with self.subTest(inspection=inspection):
                tool = profile.find(f"./profile/inspection_tool[@class='{inspection}']")
                self.assertIsNotNone(tool)
                self.assertEqual("false", tool.get("enabled"))
        spelling = profile.find("./profile/inspection_tool[@class='SpellCheckingInspection']")
        self.assertIsNotNone(spelling)
        self.assertEqual("true", spelling.get("enabled"))
        self.assertIsNone(profile.find("./profile/inspection_tool[@class='GrazieInspection']"))

    def test_no_unsupported_editorconfig_spelling_property(self):
        self.assertNotIn("spelling_language", (ROOT / ".editorconfig").read_text())

    def test_git_ignores_local_secrets_and_diagnostics(self):
        for path in (
            ".env",
            ".env.local",
            "app/hs_err_pid12345.log",
            ".kotlin/errors/errors-12345.log",
            "app/build/reports/lint-results.xml",
            "docs/node_modules/example/module.js",
        ):
            with self.subTest(path=path):
                result = subprocess.run(
                    ["git", "check-ignore", "-q", "--no-index", "--", path],
                    cwd=ROOT,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
