"""Static regression checks for automated test workflow caching semantics.

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
        self.assertIn("  docs-browser:", TESTS)
        self.assertIn("  android:\n", TESTS)
        self.assertIn("  docs:\n", TESTS)

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

    def test_docs_test_gate_caches_pnpm_after_installing_pnpm(self):
        docs_tests = TESTS.split("  docs:\n", 1)[1].split(
            "  docs-browser:\n", 1
        )[0]
        docs_browser = TESTS.split("  docs-browser:\n", 1)[1]
        for section in (docs_tests, docs_browser, DOCS):
            self.assertIn("uses: pnpm/action-setup@v6", section)
            self.assertIn("cache: pnpm", section)
            self.assertIn("cache-dependency-path: docs/pnpm-lock.yaml", section)
            self.assertLess(
                section.index("uses: pnpm/action-setup@v6"),
                section.index("uses: actions/setup-node@v6"),
            )
        self.assertEqual(2, TESTS.count("cache: pnpm"))
        self.assertNotIn("ms-playwright", TESTS)


if __name__ == "__main__":
    unittest.main()
