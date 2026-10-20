package uk.nktnet.webviewkiosk.config.mqtt

import android.content.RestrictionEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
    val settingsCommandTopic = "$baseTopic/settings/set"
    val settingsStateTopic = "$baseTopic/settings"
    val requestTopic = "$baseTopic/request"
    val controlTopics
        get() = setOf(commandTopic, settingsCommandTopic, requestTopic)

    fun responseTopic(request: String) = "$baseTopic/response/$request"

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
        settings: List<HomeAssistantSetting> = emptyList(),
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
        put(
            "availability",
            buildJsonArray {
                add(buildJsonObject { put("topic", availabilityTopic) })
            },
        )
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
                        put("json_attributes_topic", stateTopic)
                        put(
                            "json_attributes_template",
                            "{{ {\"url\":value_json.currentUrl} | tojson }}",
                        )
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
                put(
                    "lock_state",
                    statusSensor(
                        "lock_state",
                        "Lock state",
                        "{{ value_json.lockStateType }}",
                    ),
                )
                put(
                    "last_interaction",
                    statusSensor(
                        "last_interaction",
                        "Last interaction",
                        "{{ (value_json.lastInteractionTime / 1000) | timestamp_utc }}",
                    ) {
                        put("device_class", "timestamp")
                    },
                )
                put(
                    "app_brightness",
                    statusSensor(
                        "app_brightness",
                        "App brightness",
                        "{{ value_json.appBrightnessPercentage }}",
                    ) {
                        put("unit_of_measurement", "%")
                    },
                )
                put(
                    "system_brightness",
                    statusSensor(
                        "system_brightness",
                        "System brightness",
                        "{{ value_json.systemBrightness }}",
                    ),
                )
                commandComponents().forEach { (key, value) -> put(key, value) }
                requestComponents().forEach { (key, value) -> put(key, value) }
                settings.forEach { put(it.componentId, settingComponent(it)) }
            },
        )
    }

    private fun statusSensor(
        key: String,
        name: String,
        template: String,
        configure: JsonObjectBuilder.() -> Unit = {},
    ) =
        component("sensor", key, name) {
            put("state_topic", stateTopic)
            put("value_template", template)
            put("entity_category", "diagnostic")
            configure()
        }

    private fun commandComponents() = buildJsonObject {
        listOf(
                "go_home" to "Go home",
                "refresh" to "Refresh",
                "go_back" to "Go back",
                "go_forward" to "Go forward",
                "clear_history" to "Clear history",
                "clear_cache" to "Clear cache",
                "reconnect" to "Reconnect MQTT",
                "lock_device" to "Lock device",
                "page_up" to "Page up",
                "page_down" to "Page down",
            )
            .forEach { (command, name) ->
                put(command, commandButton(command, name))
            }
        listOf("page_up" to "Go to top", "page_down" to "Go to bottom").forEach {
            (command, name) ->
            val key = "${command}_absolute"
            put(
                key,
                commandButton(
                    command,
                    name,
                    key,
                    buildJsonObject { put("absolute", true) },
                ),
            )
        }
        listOf(
                Triple("go_to_url", "Go to URL", "url"),
                Triple("search", "Search", "query"),
                Triple("toast", "Toast", "message"),
                Triple("notify", "Notification", "contentText"),
                Triple("launch_package", "Launch package", "packageName"),
            )
            .forEach { (command, name, field) ->
                put(
                    command,
                    component(
                        if (command == "notify" || command == "toast") {
                            "notify"
                        } else {
                            "text"
                        },
                        command,
                        name,
                    ) {
                        put("command_topic", commandTopic)
                        put(
                            "command_template",
                            "{{ {\"command\":${quote(command)},\"targetInstances\":$target,\"data\":{${quote(field)}:value}} | tojson }}",
                        )
                        if (command != "notify" && command != "toast") {
                            put("max", 255)
                            // Retained state initialises the inputs on discovery.
                            put("state_topic", stateTopic)
                            put(
                                "value_template",
                                if (command == "go_to_url") {
                                    "{{ value_json.currentUrl if value_json.currentUrl | length <= 255 else '' }}"
                                } else {
                                    "{{ '' }}"
                                },
                            )
                        }
                        put("retain", false)
                    },
                )
            }
        put(
            "settings_json",
            component("notify", "settings_json", "Settings JSON") {
                put("command_topic", settingsCommandTopic)
                put(
                    "command_template",
                    "{{ dict(value | from_json, targetInstances=$target) | tojson }}",
                )
                put("entity_category", "config")
                put("retain", false)
            },
        )
        put(
            "command_json",
            component("notify", "command_json", "Command JSON") {
                put("command_topic", commandTopic)
                put(
                    "command_template",
                    "{{ dict(value | from_json, targetInstances=$target) | tojson }}",
                )
                put("retain", false)
            },
        )
    }

    private fun commandButton(
        command: String,
        name: String,
        key: String = command,
        data: JsonObject? = null,
    ) =
        component("button", key, name) {
            put("command_topic", commandTopic)
            put("payload_press", commandPayload(command, data))
            put("retain", false)
        }

    private fun requestComponents() = buildJsonObject {
        requestNames.forEach { (request, name) ->
            put(request, requestButton(request, "Refresh $name"))
            val key = "${request}_result"
            put(
                key,
                component("sensor", key, "$name result") {
                    put("state_topic", responseTopic(request))
                    put(
                        "value_template",
                        "{{ (value_json.updatedAt / 1000) | timestamp_utc }}",
                    )
                    put("device_class", "timestamp")
                    put("json_attributes_topic", responseTopic(request))
                    put("entity_category", "diagnostic")
                },
            )
        }
        put(
            "request_error",
            component("sensor", "request_error", "Request error") {
                put("state_topic", responseTopic("error"))
                put(
                    "value_template",
                    "{{ (value_json.updatedAt / 1000) | timestamp_utc }}",
                )
                put("device_class", "timestamp")
                put("json_attributes_topic", responseTopic("error"))
                put("entity_category", "diagnostic")
            },
        )
        put(
            "get_launchable_packages_permitted",
            requestButton(
                "get_launchable_packages",
                "Refresh permitted launchable packages",
                "get_launchable_packages_permitted",
                buildJsonObject { put("filterLockTaskPermitted", true) },
            ),
        )
        put(
            "request_json",
            component("notify", "request_json", "Request JSON") {
                put("command_topic", requestTopic)
                put(
                    "command_template",
                    "{{ dict(value | from_json, targetInstances=$target) | tojson }}",
                )
                put("entity_category", "diagnostic")
                put("retain", false)
            },
        )
    }

    private fun requestButton(
        request: String,
        name: String,
        key: String = request,
        data: JsonObject? = null,
    ) =
        component("button", key, name) {
            put("command_topic", requestTopic)
            put(
                "payload_press",
                buildJsonObject {
                    put("requestType", request)
                    put("targetInstances", targets)
                    data?.let { put("data", it) }
                }
                    .toString(),
            )
            put("entity_category", "diagnostic")
            put("retain", false)
        }

    private fun settingComponent(setting: HomeAssistantSetting): JsonObject {
        val key = quote(setting.key)
        val platform =
            when (setting.type) {
                RestrictionEntry.TYPE_BOOLEAN -> "switch"
                RestrictionEntry.TYPE_INTEGER -> "number"
                RestrictionEntry.TYPE_CHOICE -> "select"
                else -> "text"
            }
        val expression =
            when (setting.type) {
                RestrictionEntry.TYPE_INTEGER -> "value | int"
                RestrictionEntry.TYPE_MULTI_SELECT -> "value | from_json"
                else -> "value"
            }
        fun settingsPayload(value: String) =
            "{{ {\"targetInstances\":$target,\"showToast\":false,\"data\":{\"settings\":{$key:$value}}} | tojson }}"
        return component(platform, setting.componentId, setting.name) {
            put("command_topic", settingsCommandTopic)
            put("retain", false)
            put("entity_category", "config")
            // An administrator-managed setting is visible but cannot be edited.
            put(
                "availability",
                buildJsonArray {
                    add(buildJsonObject { put("topic", availabilityTopic) })
                    add(
                        buildJsonObject {
                            put("topic", settingsStateTopic)
                            put(
                                "value_template",
                                "{{ 'offline' if value_json.managed[$key] else 'online' }}",
                            )
                        }
                    )
                },
            )
            put("availability_mode", "all")
            if (platform == "switch") {
                fun booleanPayload(value: Boolean) = buildJsonObject {
                    put("targetInstances", targets)
                    put("showToast", false)
                    put(
                        "data",
                        buildJsonObject {
                            put(
                                "settings",
                                buildJsonObject { put(setting.key, value) },
                            )
                        },
                    )
                }
                    .toString()
                put("payload_on", booleanPayload(true))
                put("payload_off", booleanPayload(false))
                put("state_on", "True")
                put("state_off", "False")
            } else {
                put("command_template", settingsPayload(expression))
            }
            if (platform == "number") {
                put("min", setting.range.first)
                put("max", setting.range.last)
                put("step", 1)
                put("mode", "box")
            }
            if (platform == "select") {
                put("options", JsonArray(setting.options.map(::JsonPrimitive)))
            }
            if (platform == "text") {
                put("max", 255)
            }
            if (setting.isPassword) {
                put("mode", "password")
                // Initialise a known, blank input without echoing credentials.
                put("state_topic", settingsStateTopic)
                put("value_template", "{{ '' }}")
            } else {
                put("state_topic", settingsStateTopic)
                val value =
                    "value_json.settings[$key]" +
                        if (setting.type == RestrictionEntry.TYPE_MULTI_SELECT) {
                            " | tojson"
                        } else {
                            ""
                        }
                put(
                    "value_template",
                    if (platform == "text") {
                        "{% set v = $value %}{{ v if v | length <= 255 else '' }}"
                    } else {
                        "{{ $value }}"
                    },
                )
                put("json_attributes_topic", settingsStateTopic)
                put(
                    "json_attributes_template",
                    "{{ {\"value\":value_json.settings[$key],\"managed\":value_json.managed[$key]} | tojson }}",
                )
            }
        }
    }

    private fun component(
        platform: String,
        key: String,
        name: String,
        configure: JsonObjectBuilder.() -> Unit = {},
    ) = buildJsonObject {
        put("platform", platform)
        put("unique_id", "${deviceId}_$key")
        put("name", name)
        put("enabled_by_default", true)
        configure()
    }

    private val targets
        get() = JsonArray(listOf(JsonPrimitive(appInstanceId)))

    private val target
        get() = targets.toString()

    private fun quote(value: String) = JsonPrimitive(value).toString()

    private fun commandPayload(command: String, data: JsonObject? = null): String =
        buildJsonObject {
            put("command", command)
            put("targetInstances", targets)
            data?.let { put("data", it) }
        }
        .toString()

    companion object {
        val requestNames =
            linkedMapOf(
                "get_status" to "Status",
                "get_settings" to "Settings",
                "get_system_info" to "System info",
                "get_launchable_packages" to "Launchable packages",
                "get_lock_task_packages" to "Lock task packages",
            )

        fun isValidPrefix(prefix: String): Boolean =
            prefix.isNotBlank() &&
                prefix == prefix.trim().trim('/') &&
                isValidMqttPublishTopic(prefix)
    }
}
