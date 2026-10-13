"""Static regression checks for test workflow scope and caching.

Uses only the standard library so these checks run before Gradle or pnpm.
"""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTS = (ROOT / ".github/workflows/tests.yaml").read_text(encoding="utf-8")
DOCS = (ROOT / ".github/workflows/docs.yaml").read_text(encoding="utf-8")
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")


class CiCachingTest(unittest.TestCase):
    def test_gradle_cache_is_enabled_without_competing_java_cache(self):
        self.assertEqual(1, TESTS.count("uses: gradle/actions/setup-gradle@v6"))
        self.assertNotIn("cache: gradle", TESTS)
        self.assertIn("cache-read-only:", TESTS)
        self.assertIn("head.repo.full_name != github.repository", TESTS)
        self.assertIn(
            "run: sh gradlew :app:testDebugUnitTest --build-cache --profile --stacktrace",
            TESTS,
        )
        self.assertEqual(1, TESTS.count("build/reports/profile/"))

    def test_no_avd_or_installed_apk_automated_jobs_remain(self):
        self.assertNotIn("  emulator:", TESTS)
        self.assertNotIn("  smoke:", RELEASE)
        self.assertNotIn("reactivecircus/android-emulator-runner", TESTS)
        self.assertNotIn("reactivecircus/android-emulator-runner", RELEASE)
        self.assertNotIn("connectedDebugAndroidTest", TESTS)
        self.assertNotIn("avd-cache", TESTS)
        self.assertIn("needs: [verify, build]", RELEASE)
        self.assertIn("  android:\n", TESTS)

    def test_instrumentation_source_and_runner_dependencies_are_retired(self):
        self.assertFalse((ROOT / "app/src/androidTest").exists())
        self.assertFalse((ROOT / "scripts/smoke-release-apk.sh").exists())
        self.assertFalse((ROOT / "scripts/ci/run-android-instrumentation.sh").exists())
        self.assertFalse((ROOT / "scripts/ci/report_android_instrumentation.py").exists())
        gradle_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        versions = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        for obsolete in ("testInstrumentationRunner", "androidTestImplementation"):
            self.assertNotIn(obsolete, gradle_build)
        self.assertNotIn("androidx-test-runner", versions)
        self.assertNotIn("androidx-test-ext-junit", versions)
        self.assertNotIn("androidx-test-core", versions)

    def test_android_release_no_longer_waits_for_docs(self):
        self.assertIn("uses: ./.github/workflows/tests.yaml", RELEASE)
        self.assertNotIn("  docs:\n", TESTS)
        self.assertNotIn("  docs-browser:\n", TESTS)
        self.assertNotIn("cache: pnpm", TESTS)
        self.assertNotIn("playwright", TESTS)

    def test_docs_validation_installs_and_caches_once(self):
        self.assertEqual(1, DOCS.count("uses: pnpm/action-setup@v6"))
        self.assertEqual(1, DOCS.count("uses: actions/setup-node@v6"))
        self.assertEqual(1, DOCS.count("cache: pnpm"))
        self.assertIn("cache-dependency-path: docs/pnpm-lock.yaml", DOCS)
        self.assertLess(
            DOCS.index("uses: pnpm/action-setup@v6"),
            DOCS.index("uses: actions/setup-node@v6"),
        )
        for command in ("pnpm check", "pnpm typecheck", "pnpm test", "pnpm build"):
            self.assertEqual(1, DOCS.count(f"run: {command}\n"))

    def test_docs_validation_runs_on_relevant_prs_and_main_pushes(self):
        self.assertIn("  push:\n    branches:\n      - main", DOCS)
        self.assertIn("  pull_request:\n    paths:\n", DOCS)
        self.assertIn("  workflow_dispatch:\n", DOCS)
        self.assertEqual(2, DOCS.count("      - 'docs/**'"))
        self.assertIn("if: github.event_name != 'pull_request'", DOCS)
        self.assertIn("PUBLIC_DOCS_BASE_PATH: ${{ steps.setup_pages.outputs.base_path || '/' }}", DOCS)
        self.assertIn("    needs: build", DOCS)

    def test_browser_checks_only_when_relevant_or_manual(self):
        self.assertIn("uses: dorny/paths-filter@v3", DOCS)
        self.assertIn("pull-requests: read", DOCS)
        for path in (
            "docs/src/**",
            "docs/tests/e2e/**",
            "docs/vite.config.ts",
            "docs/config/playwright.config.ts",
            "docs/package.json",
            "docs/pnpm-lock.yaml",
        ):
            self.assertIn(f"- '{path}'", DOCS)
        browser_condition = (
            "if: github.event_name == 'workflow_dispatch' "
            "|| steps.browser_changes.outputs.browser == 'true'"
        )
        self.assertEqual(2, DOCS.count(browser_condition))
        self.assertIn("pnpm exec playwright install --with-deps chromium", DOCS)
        self.assertIn("run: pnpm test:e2e", DOCS)


if __name__ == "__main__":
    unittest.main()
