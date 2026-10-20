package uk.nktnet.webviewkiosk.config.mqtt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage

class HomeAssistantDiscoveryTest {
    private val discovery = HomeAssistantDiscovery("instance-a", "homeassistant")

    @Test
    fun deviceDiscoveryGroupsEightEntitiesWithStableUniqueIds() {
        val first = discovery.payload("Kitchen", "Example", "Tablet", "1.0")
        val renamed = discovery.payload("Hallway", "Example", "Tablet", "2.0")
        val components = first.getValue("components").jsonObject

        assertEquals(8, components.size)
        assertEquals(
            "Webview Kiosk",
            first.getValue("origin").jsonObject["name"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "webview_kiosk_instance-a",
            first
                .getValue("device")
                .jsonObject
                .getValue("identifiers")
                .jsonArray
                .single()
                .jsonPrimitive
                .content,
        )
        val uniqueIds =
            components.values.map {
                it.jsonObject.getValue("unique_id").jsonPrimitive.content
            }
        assertEquals(uniqueIds.size, uniqueIds.toSet().size)
        assertEquals(components, renamed.getValue("components"))
        assertEquals(
            "homeassistant/device/webview_kiosk_instance-a/config",
            discovery.configTopic,
        )
    }

    @Test
    fun commandsUseTheExistingWireFormatAndTargetOnlyThisInstance() {
        val components =
            discovery.payload("", "", "", "1")["components"]!!.jsonObject
        components.values.forEach { component ->
            val entity = component.jsonObject
            listOf("payload_on", "payload_off", "payload_press").forEach payload@{
                key ->
                val payload = entity[key]?.jsonPrimitive?.content ?: return@payload
                if (
                    entity.getValue("platform").jsonPrimitive.content ==
                        "binary_sensor"
                ) {
                    return@payload
                }
                val command =
                    InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
                        payload
                    )
                assertEquals(setOf("instance-a"), command.targetInstances)
                assertEquals(
                    discovery.commandTopic,
                    entity["command_topic"]?.jsonPrimitive?.content,
                )
                assertEquals("false", entity["retain"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun instanceAndPrefixChangesKeepTransportIsolated() {
        val other = HomeAssistantDiscovery("instance-b", "homeassistant")
        val customPrefix = HomeAssistantDiscovery("instance-a", "custom/discovery")
        assertFalse(discovery.deviceId == other.deviceId)
        assertFalse(discovery.commandTopic == other.commandTopic)
        assertEquals(discovery.stateTopic, customPrefix.stateTopic)
        assertEquals("homeassistant/status", customPrefix.birthTopic)
        assertEquals(
            "custom/ha-status",
            HomeAssistantDiscovery("instance-a", "custom", "custom/ha-status")
                .birthTopic,
        )
        assertEquals(
            "custom/discovery/device/webview_kiosk_instance-a/config",
            customPrefix.configTopic,
        )
        // Device names are JSON encoded, rather than interpolated into a string.
        val name = "Kitchen \"tablet\"\n雪"
        val payload =
            Json.parseToJsonElement(discovery.payload(name, "", "", "1").toString())
        assertEquals(
            name,
            payload.jsonObject["device"]
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun discoveryPrefixesRejectInvalidOrAmbiguousTopics() {
        listOf(
                "",
                " ",
                "/homeassistant",
                "homeassistant/",
                "ha/+",
                "ha/#",
                "ha\u0000",
            )
            .forEach { assertFalse(it, HomeAssistantDiscovery.isValidPrefix(it)) }
        listOf("homeassistant", "custom/discovery").forEach {
            assertTrue(it, HomeAssistantDiscovery.isValidPrefix(it))
        }
    }
}
