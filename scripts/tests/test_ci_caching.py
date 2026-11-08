"""Static regression checks for test workflow scope and caching.

Uses only the standard library so these checks run before Gradle or pnpm.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTS = (ROOT / ".github/workflows/tests.yaml").read_text(encoding="utf-8")
DOCS_CI = (ROOT / ".github/workflows/docs-ci.yaml").read_text(encoding="utf-8")
DOCS_DEPLOY = (ROOT / ".github/workflows/docs.yaml").read_text(encoding="utf-8")
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")


class CiCachingTest(unittest.TestCase):
    def test_gradle_cache_is_enabled_without_competing_java_cache(self):
        self.assertEqual(1, TESTS.count("uses: gradle/actions/setup-gradle@v6"))
        self.assertNotIn("cache: gradle", TESTS)
        self.assertIn("cache-read-only:", TESTS)
        self.assertIn("head.repo.full_name != github.repository", TESTS)
        self.assertIn(
            "run: sh gradlew :adblock:test :app:testDebugUnitTest "
            "--build-cache --profile --stacktrace",
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

    def test_old_instrumentation_suite_stays_retired_and_r8_probes_are_opt_in(self):
        self.assertTrue((ROOT / "app/src/androidTest/java/uk/nktnet/webviewkiosk/r8/R8RuntimeInstrumentation.java").is_file())
        self.assertFalse((ROOT / "scripts/smoke-release-apk.sh").exists())
        self.assertFalse((ROOT / "scripts/ci/run-android-instrumentation.sh").exists())
        self.assertFalse((ROOT / "scripts/ci/test-local.sh").exists())
        self.assertFalse((ROOT / "scripts/maintenance/update-graphics.sh").exists())
        self.assertFalse((ROOT / "scripts/ci/report_android_instrumentation.py").exists())
        gradle_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        versions = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        self.assertRegex(gradle_build, r'testInstrumentationRunner\s*=\s*"uk\.nktnet\.webviewkiosk\.r8\.R8RuntimeInstrumentation"')
        self.assertIn('testBuildType = "smokeRelease"', gradle_build)
        # AGP 9 defaults unit tests to just testBuildType. Keep debug JVM
        # tests in CI and Compose Preview resources for other build types.
        self.assertRegex(
            gradle_build,
            r'androidComponents\s*\{[^}]*beforeVariants\(selector\(\)'
            r'\.all\(\)\)\s*\{\s*'
            r'it\.hostTests\[HostTestBuilder\.UNIT_TEST_TYPE\]\?\.enable\s*=\s*true',
        )
        self.assertNotIn("android.onlyEnableUnitTestForTheTestedBuildType=false", gradle_build)
        self.assertNotIn("androidTestImplementation", gradle_build)
        self.assertNotIn("--runtime", TESTS)
        self.assertNotIn("--runtime", RELEASE)
        self.assertNotIn("connectedSmokeReleaseAndroidTest", TESTS)
        self.assertNotIn("androidx-test-runner", versions)
        self.assertNotIn("androidx-test-ext-junit", versions)
        self.assertNotIn("androidx-test-core", versions)

    def test_android_release_no_longer_waits_for_docs(self):
        self.assertIn("uses: ./.github/workflows/tests.yaml", RELEASE)
        self.assertNotIn("  docs:\n", TESTS)
        self.assertNotIn("  docs-browser:\n", TESTS)
        self.assertNotIn("cache: pnpm", TESTS)
        self.assertNotIn("playwright", TESTS)

    def test_android_ci_runs_only_for_relevant_changes_on_pushes_and_prs(self):
        # Keep one identical allowlist for push and pull_request events.
        # workflow_call and workflow_dispatch must remain unfiltered.
        triggers = TESTS.split("\nconcurrency:", 1)[0]
        match = re.search(
            r"  push:\n    branches:\n      - main\n"
            r"(?:    #[^\n]*\n)*"
            r"    paths: &android_paths\n"
            r"(?P<patterns>(?:      - '[^']+'\n)+)"
            r"  pull_request:\n    paths: \*android_paths\n",
            triggers,
        )
        self.assertIsNotNone(match)
        paths = re.findall(r"^      - '([^']+)'$", match.group("patterns"), re.MULTILINE)
        self.assertEqual(
            [
                "app/**",
                "adblock/**",
                "config/**",
                "gradle/**",
                "scripts/**",
                "build.gradle.kts",
                "settings.gradle.kts",
                "gradle.properties",
                "gradlew",
                "gradlew.bat",
                ".editorconfig",
                ".github/workflows/tests.yaml",
                ".github/workflows/release.yaml",
            ],
            paths,
        )
        self.assertIn("  workflow_dispatch:\n", triggers)
        self.assertIn("  workflow_call:\n", triggers)

        # These simple patterns are directory/** or exact filenames.
        def matches(changed_files):
            return any(
                file.startswith(path[:-3] + "/") if path.endswith("/**")
                else file == path
                for file in changed_files
                for path in paths
            )

        for changed_files in (
            ["docs/content/docs/index.mdx"],
            ["docs/src/components/example.tsx", "README.md"],
            ["metadata/en-US/changelogs/123.txt"],
            ["misc/icons/foreground.svg"],
            [".github/CONTRIBUTING.md"],
        ):
            with self.subTest(ignored=changed_files):
                self.assertFalse(matches(changed_files))
        for changed_files in (
            ["app/src/main/AndroidManifest.xml"],
            ["adblock/src/main/kotlin/Filter.kt"],
            ["config/detekt/braces.yml"],
            ["gradle/libs.versions.toml"],
            ["scripts/tests/test_ci_caching.py"],
            ["gradle.properties"],
            [".editorconfig"],
            [".github/workflows/tests.yaml"],
            [".github/workflows/release.yaml"],
            ["docs/content/docs/index.mdx", "app/build.gradle.kts"],
        ):
            with self.subTest(included=changed_files):
                self.assertTrue(matches(changed_files))

    def test_docs_validation_installs_and_caches_once(self):
        self.assertEqual(1, DOCS_CI.count("uses: pnpm/action-setup@v6"))
        self.assertEqual(1, DOCS_CI.count("uses: actions/setup-node@v6"))
        self.assertEqual(1, DOCS_CI.count("cache: pnpm"))
        self.assertIn("cache-dependency-path: docs/pnpm-lock.yaml", DOCS_CI)
        self.assertLess(
            DOCS_CI.index("uses: pnpm/action-setup@v6"),
            DOCS_CI.index("uses: actions/setup-node@v6"),
        )
        for command in ("pnpm check", "pnpm typecheck", "pnpm test", "pnpm build"):
            self.assertEqual(1, DOCS_CI.count(f"run: {command}\n"))
        # Deployment reuses the exact same checks instead of maintaining a copy.
        self.assertNotIn("pnpm install", DOCS_DEPLOY)

    def test_docs_ci_runs_on_prs_and_deployment_is_main_only(self):
        self.assertIn("  pull_request:\n    paths:\n", DOCS_CI)
        self.assertIn("  workflow_call:\n", DOCS_CI)
        self.assertNotIn("  push:\n", DOCS_CI)
        self.assertNotIn("  workflow_dispatch:\n", DOCS_CI)
        for path in (
            "docs/**",
            ".github/workflows/docs-ci.yaml",
            ".github/workflows/docs.yaml",
        ):
            self.assertIn(f"      - '{path}'", DOCS_CI)
        self.assertIn("group: docs-ci-${{ github.ref }}", DOCS_CI)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", DOCS_CI)

        self.assertIn("  push:\n    branches:\n      - main", DOCS_DEPLOY)
        self.assertIn("  workflow_dispatch:\n", DOCS_DEPLOY)
        self.assertNotIn("  pull_request:\n", DOCS_DEPLOY)
        self.assertIn("uses: ./.github/workflows/docs-ci.yaml", DOCS_DEPLOY)
        self.assertIn("    needs: build", DOCS_DEPLOY)
        self.assertIn("group: pages", DOCS_DEPLOY)
        self.assertIn("pages: write", DOCS_DEPLOY)
        self.assertIn("id-token: write", DOCS_DEPLOY)

        # A PR must neither configure Pages nor upload a deployment artifact.
        self.assertIn("if: github.event_name != 'pull_request'", DOCS_CI)
        self.assertIn(
            "GITHUB_TOKEN: ${{ github.event_name != 'pull_request' && github.token || '' }}",
            DOCS_CI,
        )
        self.assertIn(
            "PUBLIC_DOCS_BASE_PATH: ${{ steps.setup_pages.outputs.base_path || '/' }}", DOCS_CI
        )

    def test_browser_checks_only_when_relevant_or_manual(self):
        self.assertIn("uses: dorny/paths-filter@v3", DOCS_CI)
        self.assertIn("pull-requests: read", DOCS_CI)
        for path in (
            "docs/src/**",
            "docs/tests/e2e/**",
            "docs/vite.config.ts",
            "docs/config/playwright.config.ts",
            "docs/package.json",
            "docs/pnpm-lock.yaml",
            ".github/workflows/docs-ci.yaml",
            ".github/workflows/docs.yaml",
        ):
            self.assertIn(f"- '{path}'", DOCS_CI)
        browser_condition = (
            "if: github.event_name == 'workflow_dispatch' "
            "|| steps.browser_changes.outputs.browser == 'true'"
        )
        self.assertEqual(2, DOCS_CI.count(browser_condition))
        self.assertIn("pnpm exec playwright install --with-deps chromium", DOCS_CI)
        self.assertIn("run: pnpm test:e2e", DOCS_CI)


if __name__ == "__main__":
    unittest.main()
