"""Guard the separate smoke APK identity without copying launcher raster assets."""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app"
MAIN = APP / "src/main"
SMOKE = APP / "src/smokeRelease"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def resource_value(path, tag, name):
    resources = ET.parse(path).getroot()
    return next(item.text for item in resources.findall(tag) if item.get("name") == name)


class SmokeVariantIdentityTest(unittest.TestCase):
    def test_smoke_has_separate_package_without_changing_production(self):
        gradle = (APP / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "uk.nktnet.webviewkiosk"', gradle)
        release = gradle.split("        release {", 1)[1].split("        create(\"smokeRelease\") {", 1)[0]
        smoke = gradle.split('        create("smokeRelease") {', 1)[1].split("\n        }", 1)[0]
        self.assertNotIn("applicationIdSuffix", release)
        self.assertIn('initWith(getByName("release"))', smoke)
        self.assertIn('applicationIdSuffix = ".smoke"', smoke)
        self.assertIn("isDebuggable = false", smoke)
        self.assertIn('signingConfigs.getByName("debug")', smoke)

    def test_label_is_overridden_only_for_smoke(self):
        source = MAIN / "res/values/strings.xml"
        override = SMOKE / "res/values/strings.xml"
        self.assertEqual("Webview Kiosk", resource_value(source, "string", "app_name"))
        self.assertEqual("WK Smoke", resource_value(override, "string", "app_name"))
        manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        self.assertEqual("@string/app_name", manifest.find("application").get(ANDROID + "label"))

    def test_adaptive_icon_reuses_production_artwork_with_a_new_colour(self):
        source = MAIN / "res/values/ic_launcher_background.xml"
        override = SMOKE / "res/values/ic_launcher_background.xml"
        self.assertNotEqual(
            resource_value(source, "color", "ic_launcher_background"),
            resource_value(override, "color", "ic_launcher_background"),
        )
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            adaptive = ET.parse(MAIN / "res/mipmap-anydpi-v26" / name).getroot()
            self.assertEqual(
                "@color/ic_launcher_background",
                adaptive.find("background").get(ANDROID + "drawable"),
            )
            self.assertEqual(
                "@drawable/ic_launcher_foreground",
                adaptive.find("foreground").get(ANDROID + "drawable"),
            )

    def test_launcher_uses_stable_main_mipmap_resources_without_invalid_aliases(self):
        # The manifest uses icons available in every variant. API 26+ gets the
        # smoke background from the colour override; older devices use the
        # existing density-specific launcher images instead of a cross-type
        # @mipmap -> @drawable alias that Android lint rejects.
        main_manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        main_app = main_manifest.find("application")
        for name, attribute in (
            ("ic_launcher", "icon"),
            ("ic_launcher_round", "roundIcon"),
        ):
            with self.subTest(icon=name):
                self.assertEqual(f"@mipmap/{name}", main_app.get(ANDROID + attribute))
                self.assertTrue((MAIN / "res/mipmap-anydpi-v26" / f"{name}.xml").is_file())
                for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
                    self.assertTrue((MAIN / f"res/mipmap-{density}" / f"{name}.webp").is_file())
        self.assertFalse((SMOKE / "AndroidManifest.xml").exists())
        # Smoke overlays only the adaptive icon colour, not the launcher
        # resource type. A @mipmap -> @drawable alias is rejected by lint.
        self.assertFalse(list((SMOKE / "res").glob("mipmap*/ic_launcher*")))
        for resource in (SMOKE / "res").glob("values*/*.xml"):
            for item in ET.parse(resource).getroot().findall("item"):
                self.assertNotEqual("mipmap", item.get("type"), str(resource))
        self.assertFalse(list((SMOKE / "res").rglob("smoke_ic_launcher*.xml")))
        self.assertFalse(list((SMOKE / "res").rglob("smoke_mipmap_aliases.xml")))
        self.assertFalse(list((SMOKE / "res").rglob("*.webp")))
        self.assertFalse(list((SMOKE / "res").rglob("*.png")))


if __name__ == "__main__":
    unittest.main()
