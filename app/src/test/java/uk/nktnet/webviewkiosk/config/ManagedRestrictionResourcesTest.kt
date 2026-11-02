package uk.nktnet.webviewkiosk.config

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
                restrictions++
                val key = xml.getAttributeValue(androidNamespace, "key")
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
