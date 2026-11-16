"""Validate the shared IDE inspection profile metadata and ignored paths."""

import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROFILE_DIR = ROOT / ".idea/inspectionProfiles"
PROFILE = PROFILE_DIR / "WebviewKiosk.xml"
SCOPE = ROOT / ".idea/scopes/Maintained_Markdown.xml"


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

    def test_only_reported_noisy_inspections_disabled(self):
        profile = ET.parse(PROFILE).getroot()
        disabled = {
            tool.get("class") for tool in profile.findall("./profile/inspection_tool")
            if tool.get("enabled") == "false"
        }
        self.assertEqual(
            {
                "CheckImageSize", "ClassIndependentOfModule",
                "ClassOnlyUsedInOneModule", "GrazieStyle",
                "HtmlDeprecatedAttribute", "KotlinMisorderedAssertEqualsArguments",
                "ModuleWithTooManyClasses", "UnusedLibrary", "unused",
            },
            disabled,
        )

    def test_proofreading_only_in_maintained_markdown(self):
        profile = ET.parse(PROFILE).getroot()
        for name in ("GrazieInspection", "SpellCheckingInspection"):
            with self.subTest(inspection=name):
                tool = profile.find(f"./profile/inspection_tool[@class='{name}']")
                self.assertIsNotNone(tool)
                self.assertEqual("true", tool.get("enabled"))
                self.assertEqual("false", tool.get("enabled_by_default"))
                scopes = tool.findall("scope")
                self.assertEqual(1, len(scopes))
                self.assertEqual("Maintained Markdown", scopes[0].get("name"))
                self.assertEqual("true", scopes[0].get("enabled"))

        spelling = profile.find("./profile/inspection_tool[@class='SpellCheckingInspection']")
        options = {o.get("name"): o.get("value") for o in spelling.findall("option")}
        scoped = spelling.find("scope")
        scope_options = {o.get("name"): o.get("value") for o in scoped.findall("option")}
        self.assertEqual(
            {"processCode": "false", "processLiterals": "false", "processComments": "false"},
            options,
        )
        self.assertEqual(
            {"processCode": "false", "processLiterals": "true", "processComments": "true"},
            scope_options,
        )

    def test_proofreading_scope_only_contains_maintained_markdown(self):
        scope = ET.parse(SCOPE).getroot().find("./scope")
        self.assertIsNotNone(scope)
        self.assertEqual("Maintained Markdown", scope.get("name"))
        patterns = set(scope.get("pattern").split("||"))
        self.assertEqual(
            {
                "file:README.md", "file:.github/CONTRIBUTING.md",
                "file:.github/ISSUE_TEMPLATE/*.md", "file:docs//*.md",
                "file:adblock/docs//*.md",
            },
            patterns,
        )
        self.assertFalse(any("*.xml" in pattern or ".env" in pattern for pattern in patterns))

    def test_correctness_inspections_preserved(self):
        profile = ET.parse(PROFILE).getroot()
        for inspection in (
            "UnsecureRandomNumberGeneration", "JDBCExecuteWithNonConstantString",
            "RuntimeExecWithNonConstantString", "ComposePreviewNeedsComposableAnnotation",
        ):
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
