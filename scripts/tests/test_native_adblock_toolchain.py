"""Keep rustup's Android target provisioning aligned with the Gradle/CI ABI matrix."""

import re
import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CRATE = ROOT / "app/native-adblock"
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
RELEASE = (ROOT / ".github/workflows/release.yaml").read_text(encoding="utf-8")


class NativeAdblockToolchainTest(unittest.TestCase):
    def test_rustup_targets_match_gradle_and_release(self):
        with (CRATE / "rust-toolchain.toml").open("rb") as manifest:
            toolchain = tomllib.load(manifest)["toolchain"]
        self.assertEqual("stable", toolchain["channel"])
        self.assertEqual("minimal", toolchain["profile"])

        gradle_targets = set(
            re.findall(r'Triple\("[^"]+", "([^"]+)", "[^"]+"\)', GRADLE)
        )
        self.assertEqual(4, len(gradle_targets))
        self.assertEqual(gradle_targets, set(toolchain["targets"]))

        release_targets = re.search(r"^\s+targets: ([^\n]+)$", RELEASE, re.MULTILINE)
        self.assertIsNotNone(release_targets)
        self.assertEqual(gradle_targets, set(release_targets.group(1).split(",")))

    def test_gradle_tracks_toolchain_file_as_an_input(self):
        self.assertIn('nativeAdblockSource.file("rust-toolchain.toml")', GRADLE)
        self.assertIn("workingDir = crate", GRADLE)


if __name__ == "__main__":
    unittest.main()
