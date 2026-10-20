package uk.nktnet.webviewkiosk.config.mqtt

import android.content.RestrictionEntry
import java.io.File
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeAssistantCatalogTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    private val discovery = HomeAssistantDiscovery("instance-a", "homeassistant")

    private fun payload() =
        discovery.payload(
            "Kitchen",
            "Example",
            "Tablet",
            "1",
            HomeAssistantSetting.load(context),
        )

    @Test
    fun everyExportedSettingHasATypedControlWithTheRealEnumValues() {
        val settings = HomeAssistantSetting.load(context)
        val exported = UserSettings(context).exportJson()
        assertEquals(153, settings.size)
        assertEquals(
            exported.keys().asSequence().toSet() + HomeAssistantSetting.passwordKeys,
            settings.map { it.key }.toSet(),
        )
        val payload = payload()
        val components = payload.getValue("components").jsonObject
        assertEquals(193, components.size)
        val ids =
            components.values.map {
                it.jsonObject.getValue("unique_id").jsonPrimitive.content
            }
        assertEquals(ids.size, ids.toSet().size)
        components.values.forEach {
            assertEquals(
                "true",
                it.jsonObject["enabled_by_default"]?.jsonPrimitive?.content,
            )
        }
        settings.forEach { setting ->
            val entity = components.getValue(setting.componentId).jsonObject
            val platform = entity.getValue("platform").jsonPrimitive.content
            when (setting.type) {
                RestrictionEntry.TYPE_BOOLEAN -> assertEquals("switch", platform)
                RestrictionEntry.TYPE_INTEGER -> assertEquals("number", platform)
                RestrictionEntry.TYPE_CHOICE -> {
                    assertEquals("select", platform)
                    assertTrue(
                        setting.options.contains(exported.getString(setting.key))
                    )
                }
                RestrictionEntry.TYPE_STRING,
                RestrictionEntry.TYPE_MULTI_SELECT -> assertEquals("text", platform)
                else -> throw AssertionError("Unmapped setting type: ${setting.key}")
            }
        }
        // Optional fixture for validating the actual generated Jinja with HA tools.
        System.getProperty("haDiscoveryReport")?.let { path ->
            File(path).apply {
                parentFile?.mkdirs()
                writeText(payload.toString())
            }
        }
    }

    @Test
    fun allWireCommandsAndRequestsHaveDiscoveredControls() {
        val components = payload().getValue("components").jsonObject
        val commandNames = variants(InboundCommandMessage.serializer().descriptor)
        val requestNames = variants(InboundRequestMessage.serializer().descriptor)
        assertEquals(17, commandNames.size)
        assertEquals(5, requestNames.size)
        assertTrue(
            components.keys.containsAll(commandNames - setOf("lock", "unlock"))
        )
        assertTrue(components.containsKey("kiosk_lock"))
        assertTrue(components.keys.containsAll(requestNames))
        requestNames.forEach { assertTrue(components.containsKey("${it}_result")) }
    }

    @Test
    fun passwordInputsHaveBlankStateWithoutCredentialAttributes() {
        val components = payload().getValue("components").jsonObject
        HomeAssistantSetting.load(context)
            .filter { it.isPassword }
            .forEach {
                val entity: JsonObject =
                    components.getValue(it.componentId).jsonObject
                assertEquals(
                    "password",
                    entity.getValue("mode").jsonPrimitive.content,
                )
                assertEquals(
                    discovery.settingsStateTopic,
                    entity["state_topic"]?.jsonPrimitive?.content,
                )
                assertEquals(
                    "{{ '' }}",
                    entity["value_template"]?.jsonPrimitive?.content,
                )
                assertFalse(entity.containsKey("json_attributes_topic"))
            }
    }

    @Test
    fun numberBoundsMatchTheEffectiveSettingsImportBounds() {
        val settings = UserSettings(context)
        HomeAssistantSetting.load(context)
            .filter { it.type == RestrictionEntry.TYPE_INTEGER }
            .forEach {
                assertTrue(
                    settings.importJson(
                        JSONObject().put(it.key, Int.MIN_VALUE).toString()
                    )
                )
                assertEquals(
                    it.key,
                    it.range.first,
                    settings.exportJson().getInt(it.key),
                )
                assertTrue(
                    settings.importJson(
                        JSONObject().put(it.key, Int.MAX_VALUE).toString()
                    )
                )
                assertEquals(
                    it.key,
                    it.range.last,
                    settings.exportJson().getInt(it.key),
                )
            }
    }

    private fun variants(descriptor: SerialDescriptor): Set<String> {
        val types = descriptor.getElementDescriptor(1)
        return (0 until types.elementsCount)
            .map { types.getElementDescriptor(it).serialName }
            .toSet() - "error"
    }
}
