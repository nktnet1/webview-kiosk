package uk.nktnet.webviewkiosk.managers

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantSetting
import uk.nktnet.webviewkiosk.config.mqtt.MqttConfig
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundApplicationRestrictionsChangedEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundErrorResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundLockEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundResponseJsonParser
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundResponseMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundScreenOffEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundScreenOnEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundSettingsResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundStatusResponse
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUnlockEvent
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUrlChangedEvent
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus
import uk.nktnet.webviewkiosk.utils.getStatus

/**
 * One broker session; no Activity references or callbacks survive its retirement.
 */
internal class HomeAssistantMqttSession(
    context: Context,
    val client: Mqtt5AsyncClient,
    private val config: MqttConfig,
    private val scope: CoroutineScope,
    private val isActive: () -> Boolean,
    private val log: (String, String) -> Unit,
) {
    private val context = context.applicationContext as Application

    val discovery =
        if (config.homeAssistantEnabled) {
            HomeAssistantDiscovery(
                config.appInstanceId,
                config.homeAssistantDiscoveryPrefix,
                config.homeAssistantStatusTopic,
            )
        } else {
            null
        }
    private val prefs =
        context.getSharedPreferences("mqtt_home_assistant", Context.MODE_PRIVATE)
    // Remember each broker separately so changing brokers does not lose cleanup
    // information for a retained config on the previous broker.
    private val brokerKey =
        JsonArray(
                listOf(
                        config.serverHost,
                        config.serverPort.toString(),
                        config.useTls.toString(),
                        config.useWebSocket.toString(),
                        config.webSocketServerPath,
                        config.appInstanceId,
                    )
                    .map(::JsonPrimitive)
            )
            .toString()
    private val settingsCatalog by lazy { HomeAssistantSetting.load(context) }
    private val userSettings = UserSettings(context)
    private val userPrefs =
        context.getSharedPreferences(
            UserSettingsKeys.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    private var settingsUpdate: Job? = null
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            // Importing a settings message may change many preferences together.
            if (settingsUpdate?.isActive != true && isActive()) {
                settingsUpdate =
                    scope.launch(Dispatchers.Main) {
                        delay(100)
                        refreshSettings()
                    }
            }
        }
    private var status: WebviewKioskStatus? = null
    private var polling: Job? = null
    private var birthAnnouncement: Job? = null

    fun start() {
        val previous =
            synchronized(registryLock) {
                prefs.getStringSet(brokerKey, emptySet()).orEmpty().toSet()
            }
        previous
            .filter { it != discovery?.configTopic }
            .forEach { topic ->
                publish(topic, "") { success ->
                    if (success && isActive()) {
                        updateRegistry(topic, remove = true)
                    }
                }
            }
        if (discovery == null) {
            return
        }
        userPrefs.registerOnSharedPreferenceChangeListener(settingsListener)
        announce()
        polling =
            scope.launch(Dispatchers.Main) {
                while (isActive()) {
                    delay(30_000)
                    refreshState()
                    refreshSettings()
                }
            }
    }

    fun onHomeAssistantBirth() {
        birthAnnouncement?.cancel()
        birthAnnouncement = scope.launch {
            // Spread announcements when many kiosks share a broker.
            delay(Random.nextLong(1_000))
            announce()
        }
    }

    fun announce() {
        val ha = discovery ?: return
        if (!isActive()) {
            return
        }
        // Record before sending: a disconnect before PUBACK can still leave a
        // retained configuration that must be removed after changing settings.
        updateRegistry(ha.configTopic)
        publish(
            ha.configTopic,
            ha.payload(
                    config.homeAssistantDeviceName,
                    Build.MANUFACTURER,
                    Build.MODEL,
                    BuildConfig.VERSION_NAME,
                    settingsCatalog,
                )
                .toString(),
        )
        scope.launch(Dispatchers.Main) {
            if (isActive()) {
                refreshState()
                refreshSettings()
                publish(ha.availabilityTopic, "online")
            }
        }
    }

    private fun refreshState() {
        if (!isActive()) {
            return
        }
        runCatching { getStatus(context) }
            .onSuccess { snapshot ->
                status = snapshot
                publishState(snapshot)
            }
            .onFailure { log("HA state failed", it.toString()) }
    }

    fun onEvent(event: OutboundEventMessage) {
        if (discovery == null || !isActive()) {
            return
        }
        scope.launch(Dispatchers.Main) {
            if (event is OutboundApplicationRestrictionsChangedEvent) {
                refreshSettings()
                return@launch
            }
            if (!isActive()) {
                return@launch
            }
            val current =
                status
                    ?: runCatching { getStatus(context) }
                        .getOrElse {
                            log("HA state failed", it.toString())
                            return@launch
                        }
            val next =
                when (event) {
                    is OutboundUrlChangedEvent ->
                        current.copy(currentUrl = event.data.url)
                    is OutboundLockEvent ->
                        current.copy(
                            isLocked = true,
                            lockStateType = event.data.lockStateType,
                        )
                    is OutboundUnlockEvent ->
                        current.copy(
                            isLocked = false,
                            lockStateType = LockStateType.NONE,
                        )
                    is OutboundScreenOnEvent ->
                        current.copy(isDeviceInteractive = true)
                    is OutboundScreenOffEvent ->
                        current.copy(isDeviceInteractive = false)
                    else -> return@launch
                }
            status = next
            publishState(next)
        }
    }

    private fun publishState(snapshot: WebviewKioskStatus) {
        discovery?.let { publish(it.stateTopic, BaseJson.encodeToString(snapshot)) }
    }

    private fun refreshSettings() {
        val ha = discovery ?: return
        if (!isActive()) {
            return
        }
        runCatching {
            val settings =
                BaseJson.parseToJsonElement(userSettings.exportJson().toString())
                    .jsonObject
            buildJsonObject {
                put("settings", publicSettings(settings))
                put(
                    "managed",
                    buildJsonObject {
                        settingsCatalog.forEach {
                            put(it.key, userSettings.isRestricted(it.key))
                        }
                    },
                )
            }
        }
            .onSuccess { publish(ha.settingsStateTopic, it.toString()) }
            .onFailure { log("HA settings failed", it.toString()) }
    }

    /** HA results have fixed topics and never retain password values. */
    fun onResponse(
        response: OutboundResponseMessage,
        request: InboundRequestMessage,
    ): Boolean {
        val ha = discovery ?: return false
        val topic = request.responseTopic ?: return false
        if (
            topic !in
                (HomeAssistantDiscovery.requestNames.keys + "error").map(
                    ha::responseTopic
                )
        ) {
            return false
        }
        if (!isActive()) {
            return true
        }
        val encoded =
            OutboundResponseJsonParser.encodeToJsonElement<OutboundResponseMessage>(
                    response
                )
                .jsonObject
        val data =
            when (response) {
                is OutboundSettingsResponse ->
                    buildJsonObject {
                        put("settings", publicSettings(response.data.settings))
                    }
                else -> encoded["data"]
            }
        publish(
            topic,
            buildJsonObject {
                put("updatedAt", System.currentTimeMillis())
                put("messageId", response.messageId)
                put("requestMessageId", response.requestMessageId)
                put("correlationData", response.correlationData)
                data?.let { put("result", it) }
                if (response is OutboundErrorResponse) {
                    put("error", "Request failed; see MQTT debug logs.")
                }
            }
                .toString(),
        )
        if (response is OutboundStatusResponse) {
            scope.launch(Dispatchers.Main) {
                if (isActive()) {
                    status = response.data
                    publishState(response.data)
                }
            }
        }
        if (response is OutboundSettingsResponse) {
            scope.launch(Dispatchers.Main) { refreshSettings() }
        }
        return true
    }

    private fun publicSettings(settings: JsonObject) =
        JsonObject(settings.filterKeys { it !in HomeAssistantSetting.passwordKeys })

    fun stop() {
        userPrefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        settingsUpdate?.cancel()
        settingsUpdate = null
        polling?.cancel()
        polling = null
        birthAnnouncement?.cancel()
        birthAnnouncement = null
    }

    private fun updateRegistry(topic: String, remove: Boolean = false) {
        synchronized(registryLock) {
            if (!isActive()) {
                return
            }
            val topics =
                prefs.getStringSet(brokerKey, emptySet()).orEmpty().toMutableSet()
            if (remove) {
                topics.remove(topic)
            } else {
                topics.add(topic)
            }
            prefs.edit(commit = true) { putStringSet(brokerKey, topics) }
        }
    }

    fun disconnect(onComplete: () -> Unit) {
        stop()
        val ha = discovery
        if (ha == null) {
            onComplete()
        } else {
            // The manager has already gated normal operations during shutdown.
            publish(ha.availabilityTopic, "offline", shutdown = true) {
                onComplete()
            }
        }
    }

    @SuppressLint("NewApi")
    private fun publish(
        topic: String,
        payload: String,
        shutdown: Boolean = false,
        complete: (Boolean) -> Unit = {},
    ) {
        if (!shutdown && !isActive()) {
            return
        }
        try {
            client
                .publishWith()
                .topic(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(payload.toByteArray(Charsets.UTF_8))
                .send()
                .whenComplete { result, failure ->
                    val error = failure ?: result?.error?.orElse(null)
                    if (isActive() || shutdown) {
                        log(
                            if (error == null) {
                                "HA publish success"
                            } else {
                                "HA publish failed"
                            },
                            "topic: $topic" +
                                if (error == null) {
                                    ""
                                } else {
                                    "\nerror: $error"
                                },
                        )
                    }
                    complete(error == null)
                }
        } catch (e: Exception) {
            log("HA publish failed", "topic: $topic\nerror: $e")
            complete(false)
        }
    }

    companion object {
        private val registryLock = Any()
    }
}
