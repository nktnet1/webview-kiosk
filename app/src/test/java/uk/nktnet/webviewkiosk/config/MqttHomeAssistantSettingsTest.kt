@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MqttHomeAssistantSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        restrictions =
            context.getSystemService(Context.RESTRICTIONS_SERVICE)
                as RestrictionsManager
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        settings = UserSettings(context)
    }

    @Test
    fun discoveryIsOptInAndOlderImportsPreserveTheCurrentConfiguration() {
        assertFalse(settings.mqttHomeAssistantEnabled)
        assertEquals("homeassistant", settings.mqttHomeAssistantDiscoveryPrefix)
        assertEquals("homeassistant/status", settings.mqttHomeAssistantStatusTopic)
        assertEquals("", settings.mqttHomeAssistantDeviceName)

        settings.mqttHomeAssistantEnabled = true
        settings.mqttHomeAssistantDeviceName = "Kitchen"
        assertTrue(settings.importJson("{}"))
        assertTrue(settings.mqttHomeAssistantEnabled)
        assertEquals("Kitchen", settings.mqttHomeAssistantDeviceName)
    }

    @Test
    fun discoverySettingsRoundTripThroughExportAndRemoteImport() {
        settings.mqttHomeAssistantEnabled = true
        settings.mqttHomeAssistantDiscoveryPrefix = "custom/discovery"
        settings.mqttHomeAssistantStatusTopic = "custom/ha-status"
        settings.mqttHomeAssistantDeviceName = "Kitchen \"tablet\""
        val exported = settings.exportJson()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertTrue(UserSettings(context).importJson(exported.toString()))

        val restored = UserSettings(context)
        assertTrue(restored.mqttHomeAssistantEnabled)
        assertEquals("custom/discovery", restored.mqttHomeAssistantDiscoveryPrefix)
        assertEquals("custom/ha-status", restored.mqttHomeAssistantStatusTopic)
        assertEquals("Kitchen \"tablet\"", restored.mqttHomeAssistantDeviceName)
        assertTrue(
            restored.importJson(
                JSONObject()
                    .put(UserSettingsKeys.Mqtt.HomeAssistant.ENABLED, false)
                    .toString()
            )
        )
        assertFalse(restored.mqttHomeAssistantEnabled)
    }

    @Test
    fun managedRestrictionsOverrideLocalAndImportedDiscoverySettings() {
        val enabledKey = UserSettingsKeys.Mqtt.HomeAssistant.ENABLED
        val prefixKey = UserSettingsKeys.Mqtt.HomeAssistant.DISCOVERY_PREFIX
        val nameKey = UserSettingsKeys.Mqtt.HomeAssistant.DEVICE_NAME
        val statusKey = UserSettingsKeys.Mqtt.HomeAssistant.STATUS_TOPIC
        shadowOf(restrictions)
            .setApplicationRestrictions(
                Bundle().apply {
                    putBoolean(enabledKey, true)
                    putString(prefixKey, "managed/discovery")
                    putString(nameKey, "Managed kiosk")
                    putString(statusKey, "managed/ha-status")
                }
            )

        settings.importJson(
            JSONObject()
                .put(enabledKey, false)
                .put(prefixKey, "local")
                .put(nameKey, "Local")
                .toString()
        )

        assertTrue(settings.mqttHomeAssistantEnabled)
        assertEquals("managed/discovery", settings.mqttHomeAssistantDiscoveryPrefix)
        assertEquals("Managed kiosk", settings.mqttHomeAssistantDeviceName)
        assertEquals("managed/ha-status", settings.mqttHomeAssistantStatusTopic)
        listOf(enabledKey, prefixKey, nameKey, statusKey).forEach {
            assertTrue(settings.isRestricted(it))
        }
    }
}
