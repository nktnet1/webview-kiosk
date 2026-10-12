"""Static regression checks for automated test workflow caching semantics.

Uses only the standard library so these checks run before Gradle or pnpm.
"""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTS = (ROOT / ".github/workflows/tests.yaml").read_text(encoding="utf-8")
DOCS = (ROOT / ".github/workflows/docs.yaml").read_text(encoding="utf-8")
RUNNER = (ROOT / "scripts/ci/run-android-instrumentation.sh").read_text(
    encoding="utf-8"
)


class CiCachingTest(unittest.TestCase):
    def test_gradle_cache_is_enabled_without_competing_java_cache(self):
        self.assertEqual(2, TESTS.count("uses: gradle/actions/setup-gradle@v6"))
        self.assertNotIn("cache: gradle", TESTS)
        self.assertIn("cache-read-only:", TESTS)
        self.assertIn("head.repo.full_name != github.repository", TESTS)
        self.assertIn("--build-cache --profile --stacktrace", RUNNER)
        self.assertIn(
            "run: sh gradlew :app:testDebugUnitTest --build-cache --profile --stacktrace",
            TESTS,
        )
        self.assertEqual(2, TESTS.count("build/reports/profile/"))

    def test_each_emulator_variant_keeps_its_own_clean_snapshot(self):
        self.assertIn("api: 23\n            image: default\n            arch: x86", TESTS)
        self.assertIn("api: 35\n            image: google_apis\n            arch: x86_64", TESTS)
        for segment in (
            "${{ runner.os }}",
            "${{ matrix.api }}",
            "${{ matrix.image }}",
            "${{ matrix.arch }}",
            "pixel_2",
        ):
            self.assertIn(segment, TESTS)

        restore = TESTS.index("name: Restore clean Android emulator snapshot")
        seed = TESTS.index("name: Create clean emulator snapshot")
        save = TESTS.index("name: Cache clean emulator snapshot")
        tests = TESTS.index("name: Run installed-APK tests")
        self.assertLess(restore, seed)
        self.assertLess(seed, save)
        self.assertLess(save, tests)
        self.assertIn("uses: actions/cache/restore@v4", TESTS[restore:seed])
        self.assertIn("uses: actions/cache/save@v4", TESTS[save:tests])
        self.assertIn("continue-on-error: true", TESTS[restore:seed])
        self.assertIn("continue-on-error: true", TESTS[save:tests])
        self.assertNotIn("-no-snapshot", TESTS[seed:save])
        self.assertIn("-no-snapshot-save", TESTS[tests:])
        self.assertEqual(2, TESTS.count("force-avd-creation: false"))
        self.assertIn("script: sh scripts/ci/run-android-instrumentation.sh", TESTS)

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
