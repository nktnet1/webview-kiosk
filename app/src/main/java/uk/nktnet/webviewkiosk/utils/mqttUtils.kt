package com.nktnet.webview_kiosk.utils

import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.managers.MqttManager
import com.nktnet.webview_kiosk.services.MqttForegroundService

fun isValidMqttPublishTopic(topic: String): Boolean {
    return topic.matches(Regex("^[^\\u0000+#]+$"))
}

fun isValidMqttSubscribeTopic(topic: String): Boolean {
    if (topic.isEmpty()) {
        return false
    }
    if (topic.contains('\u0000')) {
        return false
    }

    val levels = topic.split('/')
    levels.forEachIndexed { i, level ->
        if (level.contains('#') && i != levels.lastIndex) {
            return false
        }
        if (level != "#" && level.contains('#')) {
            return false
        }
        if (level != "+" && level.contains('+')) {
            return false
        }
    }
    return true
}

private val ALLOW_PARSED_AS_ARRAY_SETTING_KEYS = setOf(
    UserSettingsKeys.WebContent.WEBSITE_BLACKLIST,
    UserSettingsKeys.WebContent.WEBSITE_WHITELIST,
    UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS,
)

private val ALLOW_EVALUATE_VARIABLES_SETTING_KEYS = setOf(
    UserSettingsKeys.Mqtt.Connection.CLIENT_ID,
    UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC,
    UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC,
    UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC,
    UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC,
    UserSettingsKeys.Mqtt.Will.TOPIC,
    UserSettingsKeys.Mqtt.Will.PAYLOAD,
)

fun filterSettingsJson(
    settings: JSONObject,
    filterKeys: Array<JsonElement> = emptyArray()
): JsonObject {
    fun getJsonValue(value: Any?): JsonElement = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    if (filterKeys.isEmpty()) {
        return buildJsonObject {
            for (key in settings.keys()) {
                val value = settings.get(key)
                put(key, getJsonValue(value))
            }
        }
    }

    return buildJsonObject {
        for (keyElem in filterKeys) {
            val keyStr = when (keyElem) {
                is JsonPrimitive -> keyElem.content
                is JsonObject -> keyElem["key"]?.jsonPrimitive?.content ?: continue
                else -> continue
            }

            if (!settings.has(keyStr)) {
                continue
            }

            val evaluateVariables = (
                (keyElem as? JsonObject)
                    ?.get("evaluateVariables")
                    ?.jsonPrimitive
                    ?.booleanOrNull ?: false
            ) && keyStr in ALLOW_EVALUATE_VARIABLES_SETTING_KEYS

            val value = if (evaluateVariables) {
                MqttManager.mqttVariableReplacement(settings.getString(keyStr))
            } else {
                settings.get(keyStr)
            }

            val parseAsArray = (
                (keyElem as? JsonObject)
                    ?.get("parseAsArray")
                    ?.jsonPrimitive
                    ?.booleanOrNull ?: false
                && value is String
                && keyStr in ALLOW_PARSED_AS_ARRAY_SETTING_KEYS
            )

            if (parseAsArray) {
                val array = value.split("\n")
                    .filter { it.isNotEmpty() }
                    .map { JsonPrimitive(it) }
                put(keyStr, JsonArray(array))
            } else {
                put(keyStr, getJsonValue(value))
            }
        }
    }
}

fun initMqttForegroundService(context: Context, start: Boolean) {
    val intent = Intent(
        context,
        MqttForegroundService::class.java
    )
    if (start) {
        context.startService(intent)
    } else {
        context.stopService(intent)
    }
}
