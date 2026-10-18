"""Check the release workflow publishes its signed release and stock debug APKs."""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")


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

    def test_draft_release_contains_both_apks(self):
        self.assertIn("name: Create Draft GitHub Release", RELEASE)
        self.assertIn("app/build/outputs/apk/release/*.apk", RELEASE)
        self.assertIn("app/build/outputs/apk/debug/app-debug.apk", RELEASE)
        self.assertIn("artifactErrorsFailBuild: true", RELEASE)
        self.assertIn("needs: [verify, build]", RELEASE)
        self.assertIn("if: startsWith(github.ref, 'refs/tags/v')", RELEASE)


if __name__ == "__main__":
    unittest.main()
