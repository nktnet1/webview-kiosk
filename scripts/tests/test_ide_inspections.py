"""Keep Android Studio inspections and Git ignore rules consistent across checkouts."""

import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROFILE_DIR = ROOT / ".idea/inspectionProfiles"
PROFILE_FILE = PROFILE_DIR / "Webview_Kiosk_Australian_English.xml"
PROFILE_NAME = "Webview Kiosk - Australian English"
SCOPE_FILE = ROOT / ".idea/scopes/Maintained_Markdown.xml"


class IdeInspectionConfigurationTests(unittest.TestCase):
    def test_shared_profile_is_selected(self):
        profile = ET.parse(PROFILE_FILE).getroot()
        profile_name = profile.find("./profile/option[@name='myName']")
        self.assertIsNotNone(profile_name)

        settings = ET.parse(PROFILE_DIR / "profiles_settings.xml").getroot()
        selected = settings.find("./settings/option[@name='PROJECT_PROFILE']")
        enabled = settings.find("./settings/option[@name='USE_PROJECT_PROFILE']")
        self.assertIsNotNone(selected)
        self.assertIsNotNone(enabled)
        self.assertEqual(PROFILE_NAME, profile_name.get("value"))
        self.assertEqual(PROFILE_NAME, selected.get("value"))
        self.assertFalse((PROFILE_DIR / "Project_Default.xml").exists())
        self.assertEqual("true", enabled.get("value"))

    def test_noisy_java_html_and_locale_inspections_are_disabled(self):
        profile = ET.parse(PROFILE_FILE).getroot()
        disabled = (
            "CheckImageSize",
            "ClassIndependentOfModule",
            "ClassOnlyUsedInOneModule",
            "GrazieStyle",
            "GrazieInspection",
            "HtmlDeprecatedAttribute",
            "ModuleWithTooManyClasses",
            "UnusedLibrary",
            "unused",
            "CyclomaticComplexity",
            "DesignForExtension",
            "MultipleReturnPointsPerMethod",
            "NestingDepth",
            "ParametersPerMethod",
        )
        for inspection in disabled:
            with self.subTest(inspection=inspection):
                tool = profile.find(f"./profile/inspection_tool[@class='{inspection}']")
                self.assertIsNotNone(tool)
                self.assertEqual("false", tool.get("enabled"))
        # Safety and code correctness inspections remain enabled.
        for inspection in ("UnsecureRandomNumberGeneration", "JDBCExecuteWithNonConstantString"):
            with self.subTest(inspection=inspection):
                tool = profile.find(f"./profile/inspection_tool[@class='{inspection}']")
                self.assertIsNotNone(tool)
                self.assertEqual("true", tool.get("enabled"))

    def test_spelling_is_scoped_to_maintained_markdown(self):
        profile = ET.parse(PROFILE_FILE).getroot()
        spelling = profile.find("./profile/inspection_tool[@class='SpellCheckingInspection']")
        self.assertIsNotNone(spelling)
        self.assertEqual("true", spelling.get("enabled"))
        self.assertEqual("false", spelling.get("enabled_by_default"))
        scope = spelling.find("./scope")
        self.assertIsNotNone(scope)
        self.assertEqual("Maintained Markdown", scope.get("name"))
        self.assertEqual("true", scope.get("enabled"))
        self.assertEqual("false", spelling.find("./option[@name='processCode']").get("value"))

        scope_xml = ET.parse(SCOPE_FILE).getroot()
        definition = scope_xml.find("./scope[@name='Maintained Markdown']")
        self.assertIsNotNone(definition)
        pattern = definition.get("pattern")
        for fragment in ("file:README.md", "file:docs//*.md", "file:adblock/docs//*.md"):
            self.assertIn(fragment, pattern)
        for forbidden in (".env", "*.xml", "*.kt", "META-INF", "node_modules"):
            self.assertNotIn(forbidden, pattern)

    def test_dictionary_accepts_project_specific_markdown_terms(self):
        words = {
            word.text
            for word in ET.parse(ROOT / ".idea/dictionaries/project.xml").findall(
                "./dictionary/words/w"
            )
        }
        for word in ("Adblock", "Lefthook", "canonicalisation", "malitchuk", "subacks"):
            with self.subTest(word=word):
                self.assertIn(word, words)

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
