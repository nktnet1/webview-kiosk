package uk.nktnet.webviewkiosk.mqtt

import kotlinx.serialization.json.Json
import uk.nktnet.webviewkiosk.config.option.MqttQosOption
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption

data class MqttConfig(
    val enabled: Boolean,
    val clientId: String,
    val serverHost: String,
    val serverPort: Int,
    val username: String,
    val password: String,
    val useTls: Boolean,
    val automaticReconnect: Boolean,
    val cleanStart: Boolean,
    val keepAlive: Int,
    val mqttConnectTimeout: Int,
    val socketConnectTimeout: Int,

    val subscribeCommandTopic: String,
    val subscribeCommandQos: MqttQosOption,
    val subscribeCommandRetainHandling: MqttRetainHandlingOption,
    val subscribeCommandRetainAsPublished: Boolean,

    val subscribeSettingsTopic: String,
    val subscribeSettingsQos: MqttQosOption,
    val subscribeSettingsRetainHandling: MqttRetainHandlingOption,
    val subscribeSettingsRetainAsPublished: Boolean,

    val willTopic: String,
    val willPayload: String,
    val willQos: MqttQosOption,
    val willRetain: Boolean,
    val willMessageExpiryInterval: Int,
    val willDelayInterval: Int
)
