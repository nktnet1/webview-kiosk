"""Check the release workflow publishes its signed release and stock debug APKs."""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")
APP_BUILD = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")


class ReleaseArtifactsTest(unittest.TestCase):
    def test_builds_both_variants_once(self):
        self.assertIn(
            "run: sh gradlew :app:assembleRelease :app:assembleDebug --stacktrace",
            RELEASE,
        )

    def test_debug_apk_is_uploaded_and_downloaded_without_renaming(self):
        self.assertIn("name: Store debug APK for publication", RELEASE)
        self.assertIn("name: debug-apk", RELEASE)
        self.assertIn("path: app/build/outputs/apk/debug/app-debug.apk", RELEASE)
        self.assertIn("name: Download built debug APK", RELEASE)
        self.assertIn("path: app/build/outputs/apk/debug/", RELEASE)
        self.assertIn("if-no-files-found: error", RELEASE)
        self.assertNotIn('mv app/build/outputs/apk/debug', RELEASE)

    def test_release_apk_keeps_stock_filename_and_versioned_alias(self):
        self.assertIn("name: Store release APK for publication", RELEASE)
        self.assertIn("name: release-apk", RELEASE)
        self.assertIn("path: app/build/outputs/apk/release/", RELEASE)
        self.assertIn("name: Download built release APK", RELEASE)
        self.assertIn(
            'cp app/build/outputs/apk/release/app-release.apk \\\n'
            '            "app/build/outputs/apk/release/WebviewKiosk_v$VERSION_NAME.apk"',
            RELEASE,
        )
        self.assertNotIn("mv app/build/outputs/apk/release/", RELEASE)

    def test_release_signing_always_sets_keystore_path(self):
        signing_configs = APP_BUILD.split("    signingConfigs {", 1)[1].split(
            "    buildTypes {", 1
        )[0]
        self.assertIn('storeFile = file("keystore.jks")', signing_configs)
        self.assertNotIn("keystore.exists()", signing_configs)

    def test_release_is_always_signed_and_smoke_uses_debug_key(self):
        build_types = APP_BUILD.split("    buildTypes {", 1)[1]
        production = build_types.split("        release {", 1)[1].split(
            '        create("smokeRelease")', 1
        )[0]
        smoke = build_types.split('        create("smokeRelease")', 1)[1]
        self.assertIn('signingConfig = signingConfigs.getByName("release")', production)
        self.assertNotIn("keystore.jks", production)
        self.assertIn('signingConfig = signingConfigs.getByName("debug")', smoke)

    def test_missing_release_credentials_report_environment_variables(self):
        self.assertIn('it.name == "validateSigningRelease"', APP_BUILD)
        for variable in ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertIn(f'"{variable}"', APP_BUILD)
        self.assertIn("Gradle does not load .env automatically.", APP_BUILD)

    def test_draft_release_contains_both_apks(self):
        self.assertIn("name: Create Draft GitHub Release", RELEASE)
        self.assertIn("app/build/outputs/apk/release/*.apk", RELEASE)
        self.assertIn("app/build/outputs/apk/debug/app-debug.apk", RELEASE)
        self.assertIn("artifactErrorsFailBuild: true", RELEASE)
        self.assertIn("needs: [verify, build]", RELEASE)
        self.assertIn("if: startsWith(github.ref, 'refs/tags/v')", RELEASE)


if __name__ == "__main__":
    unittest.main()
