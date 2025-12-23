package uk.nktnet.webviewkiosk.config.mqtt

data class MqttConfig(
    val appInstanceId: String,

    val enabled: Boolean,
    val clientId: String,
    val serverHost: String,
    val serverPort: Int,
    val username: String,
    val password: String,
    val useTls: Boolean,
    val cleanStart: Boolean,
    val keepAlive: Int,
    val mqttConnectTimeout: Int,
    val socketConnectTimeout: Int,
    val automaticReconnect: Boolean,
    val sessionExpiryInterval: Int,
    val useWebSocket: Boolean,
    val webSocketServerPath: String,

    val publishEventTopic: String,
    val publishEventQos: MqttQosOption,
    val publishEventRetain: Boolean,

    val publishResponseTopic: String,
    val publishResponseQos: MqttQosOption,
    val publishResponseRetain: Boolean,

    val subscribeCommandTopic: String,
    val subscribeCommandQos: MqttQosOption,
    val subscribeCommandRetainHandling: MqttRetainHandlingOption,
    val subscribeCommandRetainAsPublished: Boolean,

    val subscribeSettingsTopic: String,
    val subscribeSettingsQos: MqttQosOption,
    val subscribeSettingsRetainHandling: MqttRetainHandlingOption,
    val subscribeSettingsRetainAsPublished: Boolean,

    val subscribeRequestTopic: String,
    val subscribeRequestQos: MqttQosOption,
    val subscribeRequestRetainHandling: MqttRetainHandlingOption,
    val subscribeRequestRetainAsPublished: Boolean,

    val willTopic: String,
    val willPayload: String,
    val willQos: MqttQosOption,
    val willRetain: Boolean,
    val willMessageExpiryInterval: Int,
    val willDelayInterval: Int,

    val restrictionsReceiveMaximum: Int,
    val restrictionsSendMaximum: Int,
    val restrictionsMaximumPacketSize: Int,
    val restrictionsSendMaximumPacketSize: Int,
    val restrictionsTopicAliasMaximum: Int,
    val restrictionsSendTopicAliasMaximum: Int,
    val restrictionsRequestProblemInformation: Boolean,
    val restrictionsRequestResponseInformation: Boolean,
)
