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
    fun deviceDiscoveryGroupsEntitiesWithStableUniqueIds() {
        val first = discovery.payload("Kitchen", "Example", "Tablet", "1.0")
        val renamed = discovery.payload("Hallway", "Example", "Tablet", "2.0")
        val components = first.getValue("components").jsonObject

        assertEquals(37, components.size)
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
    fun onlyTheRetiredNotifiersAreIncludedInTheRemovalMessage() {
        val removal = discovery.notifierRemovalPayload()
        val removed = removal.getValue("components").jsonObject
        val current = discovery.payload("", "", "", "1")
        val components = current.getValue("components").jsonObject

        assertEquals(
            setOf(
                "toast",
                "notify",
                "command_json",
                "settings_json",
                "request_json",
            ),
            removed.keys,
        )
        removed.values.forEach {
            // A platform-only entry explicitly removes a device component in HA.
            assertEquals(setOf("platform"), it.jsonObject.keys)
            assertEquals(
                "notify",
                it.jsonObject.getValue("platform").jsonPrimitive.content,
            )
        }
        assertEquals(
            current.getValue("device").jsonObject.getValue("identifiers"),
            removal.getValue("device").jsonObject.getValue("identifiers"),
        )
        assertTrue(components.keys.intersect(removed.keys).isEmpty())
        assertTrue(
            components.values.none {
                it.jsonObject.getValue("platform").jsonPrimitive.content == "notify"
            }
        )
    }

    @Test
    fun toastAndNotificationAreBlankTextControlsWithFixedCommandEnvelopes() {
        val components =
            discovery.payload("", "", "", "1")["components"]!!.jsonObject
        listOf(
                Triple("toast_message", "toast", "message"),
                Triple("notification_message", "notify", "contentText"),
            )
            .forEach { (key, command, field) ->
                val entity = components.getValue(key).jsonObject
                assertEquals(
                    "text",
                    entity.getValue("platform").jsonPrimitive.content,
                )
                assertEquals("255", entity.getValue("max").jsonPrimitive.content)
                assertEquals(
                    discovery.commandTopic,
                    entity.getValue("command_topic").jsonPrimitive.content,
                )
                assertEquals(
                    discovery.stateTopic,
                    entity.getValue("state_topic").jsonPrimitive.content,
                )
                assertEquals(
                    "{{ '' }}",
                    entity.getValue("value_template").jsonPrimitive.content,
                )
                assertEquals(
                    "false",
                    entity.getValue("retain").jsonPrimitive.content,
                )
                assertEquals(
                    "{{ {\"command\":\"$command\",\"targetInstances\":[\"instance-a\"],\"data\":{\"$field\":value}} | tojson }}",
                    entity.getValue("command_template").jsonPrimitive.content,
                )
            }
    }

    @Test
    fun commandsUseTheExistingWireFormatAndTargetOnlyThisInstance() {
        val components =
            discovery.payload("", "", "", "1")["components"]!!.jsonObject
        components.values.forEach { component ->
            val entity = component.jsonObject
            if (
                entity["command_topic"]?.jsonPrimitive?.content !=
                    discovery.commandTopic
            ) {
                return@forEach
            }
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
