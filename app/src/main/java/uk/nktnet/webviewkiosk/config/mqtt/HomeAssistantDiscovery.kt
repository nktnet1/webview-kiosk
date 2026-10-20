package uk.nktnet.webviewkiosk.config.mqtt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

/**
 * Stable device discovery and transport topics, independent of shared MQTT topics.
 */
class HomeAssistantDiscovery(
    val appInstanceId: String,
    val discoveryPrefix: String,
    val birthTopic: String = "homeassistant/status",
) {
    val deviceId = "webview_kiosk_$appInstanceId"
    val configTopic = "$discoveryPrefix/device/$deviceId/config"
    private val baseTopic = "wk/$appInstanceId/ha"
    val stateTopic = "$baseTopic/state"
    val availabilityTopic = "$baseTopic/availability"
    val commandTopic = "$baseTopic/command"

    init {
        require(isValidPrefix(discoveryPrefix)) {
            "Invalid Home Assistant discovery prefix."
        }
        require(isValidMqttPublishTopic(birthTopic)) {
            "Invalid Home Assistant status topic."
        }
    }

    fun payload(
        deviceName: String,
        manufacturer: String,
        model: String,
        version: String,
    ): JsonObject = buildJsonObject {
        put(
            "device",
            buildJsonObject {
                put("identifiers", JsonArray(listOf(JsonPrimitive(deviceId))))
                put("name", deviceName.ifBlank { "Webview Kiosk $appInstanceId" })
                put("manufacturer", manufacturer)
                put("model", model)
                put("sw_version", version)
            },
        )
        put(
            "origin",
            buildJsonObject {
                put("name", "Webview Kiosk")
                put("sw_version", version)
                put("support_url", Constants.SOURCE_CODE_URL)
            },
        )
        put("availability_topic", availabilityTopic)
        put("qos", 1)
        put(
            "components",
            buildJsonObject {
                put(
                    "battery",
                    component("sensor", "battery", "Battery") {
                        put("state_topic", stateTopic)
                        put("value_template", "{{ value_json.batteryPercentage }}")
                        put("device_class", "battery")
                        put("state_class", "measurement")
                        put("unit_of_measurement", "%")
                        put("entity_category", "diagnostic")
                    },
                )
                put(
                    "current_url",
                    component("sensor", "current_url", "Current URL") {
                        put("state_topic", stateTopic)
                        // Entity states are limited to 255 characters in Home
                        // Assistant.
                        put("value_template", "{{ value_json.currentUrl[:255] }}")
                    },
                )
                put(
                    "screen",
                    component("binary_sensor", "screen", "Screen") {
                        put("state_topic", stateTopic)
                        put("value_template", "{{ value_json.isDeviceInteractive }}")
                        put("payload_on", "True")
                        put("payload_off", "False")
                    },
                )
                put(
                    "kiosk_lock",
                    component("switch", "kiosk_lock", "Kiosk lock") {
                        put("state_topic", stateTopic)
                        put("value_template", "{{ value_json.isLocked }}")
                        put("state_on", "True")
                        put("state_off", "False")
                        put("command_topic", commandTopic)
                        put("payload_on", commandPayload("lock"))
                        put("payload_off", commandPayload("unlock"))
                        put("retain", false)
                    },
                )
                listOf(
                        "go_home" to "Go home",
                        "refresh" to "Refresh",
                        "go_back" to "Go back",
                        "go_forward" to "Go forward",
                    )
                    .forEach { (command, name) ->
                        put(
                            command,
                            component("button", command, name) {
                                put("command_topic", commandTopic)
                                put("payload_press", commandPayload(command))
                                put("retain", false)
                            },
                        )
                    }
            },
        )
    }

    private fun component(
        platform: String,
        key: String,
        name: String,
        configure: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) = buildJsonObject {
        put("platform", platform)
        put("unique_id", "${deviceId}_$key")
        put("name", name)
        configure()
    }

    private fun commandPayload(command: String): String = buildJsonObject {
        put("command", command)
        put("targetInstances", JsonArray(listOf(JsonPrimitive(appInstanceId))))
    }
        .toString()

    companion object {
        fun isValidPrefix(prefix: String): Boolean =
            prefix.isNotBlank() &&
                prefix == prefix.trim().trim('/') &&
                isValidMqttPublishTopic(prefix)
    }
}
