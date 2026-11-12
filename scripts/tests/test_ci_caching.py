"""Check the app and documentation CI contracts that affect real builds.

Keep these checks focused on runnable jobs, their dependencies, and the
conditions under which they execute. In particular, job-local setup is not a
workflow-wide singleton: the docs build and browser jobs run on separate runners.
"""

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTS = (ROOT / ".github/workflows/tests.yaml").read_text(encoding="utf-8")
DOCS_CI = (ROOT / ".github/workflows/docs-ci.yaml").read_text(encoding="utf-8")
DOCS_DEPLOY = (ROOT / ".github/workflows/docs.yaml").read_text(encoding="utf-8")
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")


def job_body(workflow: str, name: str) -> str:
    """Extract a top-level job without requiring a YAML dependency in CI."""
    _, separator, jobs = workflow.partition("\njobs:\n")
    if not separator:
        raise AssertionError("Workflow has no jobs section")
    match = re.search(rf"(?m)^  {re.escape(name)}:\s*$", jobs)
    if not match:
        raise AssertionError(f"Workflow is missing the {name!r} job")
    remainder = jobs[match.end():]
    next_job = re.search(r"(?m)^  [A-Za-z_][\w-]*:\s*$", remainder)
    return remainder[:next_job.start()] if next_job else remainder


class CiWorkflowTest(unittest.TestCase):
    def test_android_ci_verifies_app_and_release_uses_the_same_checks(self):
        android = job_body(TESTS, "android")
        for required in (
            "run: python3 scripts/ci/test_local.py checks",
            "uses: gradle/actions/setup-gradle@",
            "sh gradlew checkFormatting",
            "sh gradlew :adblock:test :app:testDebugUnitTest",
            "scripts/r8/r8_audit.py",
        ):
            self.assertIn(required, android)
        self.assertIn("uses: ./.github/workflows/tests.yaml", job_body(RELEASE, "verify"))
        self.assertIn("needs: [verify, build]", job_body(RELEASE, "release"))

    def test_android_and_docs_checks_have_separate_change_scopes(self):
        for pattern in ("app/**", "adblock/**", "gradle/**", "scripts/**"):
            self.assertIn(f"- '{pattern}'", TESTS)
        self.assertNotIn("- 'docs/**'", TESTS)
        for pattern in ("docs/**", ".github/workflows/docs-ci.yaml"):
            self.assertIn(f"- '{pattern}'", DOCS_CI)
        self.assertNotIn("- 'app/**'", DOCS_CI)
        self.assertIn("  workflow_call:", TESTS)
        self.assertIn("  workflow_call:", DOCS_CI)

    def test_docs_build_validates_and_packages_site_without_installing_browser(self):
        build = job_body(DOCS_CI, "build")
        for required in (
            "uses: pnpm/action-setup@",
            "uses: actions/setup-node@",
            "cache: pnpm",
            "cache-dependency-path: docs/pnpm-lock.yaml",
            "run: pnpm install --frozen-lockfile",
            "run: pnpm check",
            "run: pnpm typecheck",
            "run: pnpm test",
            "run: pnpm build",
            "uses: actions/upload-pages-artifact@",
        ):
            self.assertIn(required, build)
        self.assertNotIn("run: pnpm test:e2e", build)
        self.assertNotIn("playwright install", build)
        self.assertIn("if: github.event_name != 'pull_request'", build)

    def test_docs_deployment_reuses_validation_and_does_not_publish_prs(self):
        self.assertIn("  push:\n    branches:\n      - main", DOCS_DEPLOY)
        self.assertNotIn("  pull_request:", DOCS_DEPLOY)
        self.assertIn("uses: ./.github/workflows/docs-ci.yaml", job_body(DOCS_DEPLOY, "build"))
        deploy = job_body(DOCS_DEPLOY, "deploy")
        self.assertIn("needs: build", deploy)
        self.assertIn("uses: actions/deploy-pages@", deploy)
        build = job_body(DOCS_CI, "build")
        self.assertIn(
            "GITHUB_TOKEN: ${{ github.event_name != 'pull_request' && github.token || '' }}",
            build,
        )

    def test_browser_job_is_conditional_and_uses_preinstalled_chromium(self):
        build = job_body(DOCS_CI, "build")
        browser = job_body(DOCS_CI, "browser")
        self.assertIn("uses: dorny/paths-filter@", build)
        self.assertIn("browser_relevant: ${{ steps.browser_changes.outputs.browser }}", build)
        for path in ("docs/src/**", "docs/tests/e2e/**", "docs/config/playwright.config.ts"):
            self.assertIn(f"- '{path}'", build)
        self.assertIn("needs: build", browser)
        self.assertIn(
            "if: github.event_name == 'workflow_dispatch' "
            "|| needs.build.outputs.browser_relevant == 'true'",
            browser,
        )
        for required in (
            "uses: pnpm/action-setup@",
            "uses: actions/setup-node@",
            "run: pnpm install --frozen-lockfile",
            "run: pnpm build",
            "run: pnpm test:e2e",
            "uses: actions/upload-artifact@",
        ):
            self.assertIn(required, browser)
        self.assertNotIn("playwright install", DOCS_CI)

        # A different browser binary from @playwright/test makes real E2E runs fail.
        image = re.search(r"image: mcr\.microsoft\.com/playwright:v([\d.]+)-noble", browser)
        self.assertIsNotNone(image, "Browser job needs a pinned Playwright image")
        package = json.loads((ROOT / "docs/package.json").read_text(encoding="utf-8"))
        self.assertEqual(package["devDependencies"]["@playwright/test"], image.group(1))


if __name__ == "__main__":
    unittest.main()
