package uk.nktnet.webviewkiosk.config

import android.content.RestrictionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantSetting
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.config.option.SettingsAuthRequirementOption
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

/** Protects the Android managed-configuration contract, not its XML formatting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManagedRestrictionResourcesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    @Test
    fun managedChoicesResolveToMatchingDisplayLabelsAndImmutableOptionCodes() {
        var checked = 0
        var restrictions = 0
        val loaded = HomeAssistantSetting.load(context).associateBy { it.key }
        context.resources.getXml(R.xml.app_restrictions).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (
                    xml.eventType != XmlPullParser.START_TAG ||
                        xml.name != "restriction"
                ) {
                    continue
                }
                val key = xml.getAttributeValue(androidNamespace, "key")
                // Local-only security controls intentionally have no HA entity.
                if (key == UserSettingsKeys.Device.SETTINGS_AUTH_REQUIREMENT) {
                    continue
                }
                restrictions++
                val valuesId =
                    xml.getAttributeResourceValue(androidNamespace, "entryValues", 0)
                if (valuesId == 0) {
                    continue
                }
                val labelsId =
                    xml.getAttributeResourceValue(androidNamespace, "entries", 0)
                assertTrue("Missing display labels for $key", labelsId != 0)
                val labels = context.resources.getStringArray(labelsId)
                val values = context.resources.getStringArray(valuesId)
                assertTrue("Missing options for $key", values.isNotEmpty())
                assertEquals("Option/label count for $key", labels.size, values.size)
                assertTrue(
                    "Blank option code for $key",
                    values.all { it.isNotBlank() },
                )
                assertEquals(
                    "Duplicate option codes for $key",
                    values.size,
                    values.toSet().size,
                )
                assertEquals(
                    "Catalogue values for $key",
                    values.toList(),
                    loaded[key]?.options,
                )
                checked++
            }
        }
        assertEquals("Managed restriction count", restrictions, loaded.size)
        assertTrue("Expected managed choice restrictions", checked > 0)
    }

    @Test
    fun settingsAuthRequirementManagedRestrictionIsAChoiceNotARemoteControl() {
        val key = UserSettingsKeys.Device.SETTINGS_AUTH_REQUIREMENT
        var found = false
        context.resources.getXml(R.xml.app_restrictions).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (
                    xml.eventType != XmlPullParser.START_TAG ||
                        xml.name != "restriction" ||
                        xml.getAttributeValue(androidNamespace, "key") != key
                ) {
                    continue
                }
                found = true
                assertEquals(
                    RestrictionEntry.TYPE_CHOICE,
                    xml.getAttributeIntValue(
                        androidNamespace,
                        "restrictionType",
                        RestrictionEntry.TYPE_NULL,
                    ),
                )
                assertEquals(
                    SettingsAuthRequirementOption.entries.map {
                        context.getString(it.displayLabelRes)
                    },
                    context.resources
                        .getStringArray(
                            xml.getAttributeResourceValue(
                                androidNamespace,
                                "entries",
                                0,
                            )
                        )
                        .toList(),
                )
                assertEquals(
                    SettingsAuthRequirementOption.entries.map { it.name },
                    context.resources
                        .getStringArray(
                            xml.getAttributeResourceValue(
                                androidNamespace,
                                "entryValues",
                                0,
                            )
                        )
                        .toList(),
                )
                assertEquals(
                    "REQUIRE",
                    xml.getAttributeValue(androidNamespace, "defaultValue"),
                )
            }
        }
        assertTrue("Missing Settings Auth Requirement MDM choice", found)
        assertFalse(HomeAssistantSetting.load(context).any { it.key == key })
    }

    @Test
    fun homeAssistantCatalogueResolvesAndroidRestrictionCodes() {
        val settings = HomeAssistantSetting.load(context)
        assertTrue(settings.isNotEmpty())
        val overrideAction = settings.single {
            it.key == UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION
        }
        assertEquals(
            OverrideUrlLoadingBlockActionOption.entries.map { it.name },
            overrideAction.options,
        )
        assertFalse(overrideAction.name.isBlank())
    }
}
