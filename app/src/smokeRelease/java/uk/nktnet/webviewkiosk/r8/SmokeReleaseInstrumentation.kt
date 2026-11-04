package uk.nktnet.webviewkiosk.r8

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.PrintWriter
import java.io.StringWriter
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.managers.MqttLogEntry
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BatteryInterface

/**
 * Runs inside the R8-minified smokeRelease APK, so R8 sees every Kotlin and
 * coroutine call made by the probes. This class is absent from published APKs. Only
 * run on a disposable emulator: MQTT preferences are changed briefly.
 */
open class SmokeReleaseInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        // Probe code is compiled into smokeRelease and shrunk with the app,
        // not independently with the test APK.
        val probes =
            arrayOf(
                "json_serialization",
                "webview_javascript",
                "mqtt_tcp",
                "mqtt_websocket",
            )
        var failures = 0
        for (name in probes) {
            val result = Bundle()
            result.putString("r8_check", name)
            try {
                when (name) {
                    "json_serialization" -> jsonSerialization()
                    "webview_javascript" -> webViewJavascript()
                    "mqtt_tcp" -> mqttLoopback(webSocket = false)
                    "mqtt_websocket" -> mqttLoopback(webSocket = true)
                    else -> throw IllegalArgumentException("Unknown R8 probe: $name")
                }
                result.putString("r8_status", "pass")
                if (name == "mqtt_tcp" || name == "mqtt_websocket") {
                    result.putInt(
                        "r8_subscriptions",
                        Mqtt5LoopbackBroker.EXPECTED_TOPICS.size,
                    )
                    result.putString("r8_inbound", "get_status")
                    result.putString("r8_outbound", "url_changed")
                }
            } catch (error: Throwable) {
                failures++
                result.putString("r8_status", "fail")
                result.putString("r8_error", describeError(error))
                result.putString("r8_stack_trace", trace(error))
                val cleanup = error.suppressed
                if (cleanup.isNotEmpty()) {
                    val descriptions = StringBuilder()
                    val traces = StringBuilder()
                    for (item in cleanup) {
                        if (descriptions.isNotEmpty()) {
                            descriptions.append("; ")
                            traces.append("\n\n")
                        }
                        descriptions.append(describeError(item))
                        traces.append(trace(item))
                    }
                    result.putString("r8_cleanup_error", descriptions.toString())
                    result.putString("r8_cleanup_stack_trace", traces.toString())
                }
            }
            sendStatus(0, result)
        }
        finish(
            if (failures == 0) {
                0
            } else {
                1
            },
            Bundle().apply { putInt("r8_failures", failures) },
        )
    }

    private fun describeError(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message}"

    private fun trace(error: Throwable): String {
        val output = StringWriter()
        val writer = PrintWriter(output)
        error.printStackTrace(writer)
        writer.flush()
        return output.toString()
    }

    private fun jsonSerialization() {
        // Exercise the actual settings JSON import/export. The MQTT probes
        // also exercise production request decoding and event encoding.
        val preferenceName = "r8-verify-json-probe"
        val sandbox =
            object : ContextWrapper(targetContext) {
                override fun getSharedPreferences(
                    name: String?,
                    mode: Int,
                ): SharedPreferences =
                    targetContext.getSharedPreferences(preferenceName, mode)
            }
        try {
            val settings = UserSettings(sandbox)
            val key = UserSettingsKeys.WebContent.HOME_URL
            val expectedUrl = "https://example.invalid/r8/json-roundtrip"
            val exported = JSONObject(settings.exportJson().toString())
            exported.put(key, expectedUrl)
            check(settings.importJson(exported.toString())) {
                "Settings JSON import failed"
            }
            val restored = UserSettings(sandbox)
            check(
                restored.homeUrl == expectedUrl &&
                    restored.exportJson().getString(key) == expectedUrl
            ) {
                "Settings JSON import/export round-trip failed"
            }
        } finally {
            // Keep test cleanup compatible with the app's minimum SDK.
            targetContext
                .getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    private fun webViewJavascript() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String?>()
        var webView: WebView? = null
        var probeFailure: Throwable? = null
        try {
            runOnMainSync {
                val view = WebView(targetContext)
                webView = view
                view.settings.javaScriptEnabled = true
                view.addJavascriptInterface(
                    BatteryInterface(targetContext),
                    BatteryInterface.NAME,
                )
                view.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(
                                "window.${BatteryInterface.NAME}.getBatteryStatus()"
                            ) { answer ->
                                result.set(answer)
                                completed.countDown()
                            }
                        }
                    }
                view.loadDataWithBaseURL(
                    "https://example.invalid/",
                    "<html><body>R8 bridge probe</body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            check(completed.await(15, TimeUnit.SECONDS)) {
                "WebView JS invocation timed out"
            }
            val value = result.get()
            check(!value.isNullOrEmpty() && value != "null") {
                "WebView did not expose the battery JavaScript method"
            }
            val decoded = JSONTokener(value).nextValue() as? String
            check(decoded != null && JSONObject(decoded).has("percentage")) {
                "WebView bridge returned an invalid battery status"
            }
        } catch (error: Throwable) {
            probeFailure = error
            throw error
        } finally {
            // A WebView teardown failure must not replace the original JS
            // bridge failure; report it as a suppressed cleanup exception.
            try {
                runOnMainSync { webView?.destroy() }
            } catch (cleanupError: Throwable) {
                val originalError = probeFailure
                if (originalError != null) {
                    originalError.addSuppressed(cleanupError)
                } else {
                    throw cleanupError
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private fun mqttLoopback(webSocket: Boolean) {
        // Exercise the application's minified HiveMQ/Netty client, not a second
        // client embedded in the test APK. Keep the mock broker on loopback.
        val broker = Mqtt5LoopbackBroker(webSocket)
        var brokerFailure: Throwable? = null
        try {
            val settings = UserSettings(targetContext)
            val original =
                MqttProbeSettings(
                    enabled = settings.mqttEnabled,
                    host = settings.mqttServerHost,
                    port = settings.mqttServerPort,
                    tls = settings.mqttUseTls,
                    websocket = settings.mqttUseWebSocket,
                    path = settings.mqttWebSocketServerPath,
                    reconnect = settings.mqttAutomaticReconnect,
                    clientId = settings.mqttClientId,
                    homeAssistant = settings.mqttHomeAssistantEnabled,
                    commandTopic = settings.mqttSubscribeCommandTopic,
                    settingsTopic = settings.mqttSubscribeSettingsTopic,
                    requestTopic = settings.mqttSubscribeRequestTopic,
                    eventTopic = settings.mqttPublishEventTopic,
                    eventQos = settings.mqttPublishEventQos,
                )
            val connected = CountDownLatch(1)
            val failure = AtomicReference<String?>()
            val inboundReceived = CountDownLatch(1)
            val subscribed = CountDownLatch(Mqtt5LoopbackBroker.EXPECTED_TOPICS.size)
            val confirmedTopics = Collections.synchronizedSet(HashSet<String>())
            // Empty context lets the coroutine builder select its default
            // dispatcher while we attach the collectors before connecting.
            val scope =
                object : CoroutineScope {
                    override val coroutineContext: CoroutineContext =
                        EmptyCoroutineContext
                }
            // UNDISPATCHED attaches observers before connect().
            val inboundJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    RemoteMessageManager.requestsFlow.collect(
                        object :
                            FlowCollector<
                                RemoteMessageManager.RemoteMessage<
                                    InboundRequestMessage
                                >
                            > {
                            override suspend fun emit(
                                message:
                                    RemoteMessageManager.RemoteMessage<
                                        InboundRequestMessage
                                    >
                            ) {
                                if (
                                    message.source ==
                                        RemoteMessageManager.RemoteMessage.Source
                                            .MQTT &&
                                        message.message is InboundStatusRequest &&
                                        message.message.messageId ==
                                            Mqtt5LoopbackBroker.INBOUND_ID
                                ) {
                                    inboundReceived.countDown()
                                }
                            }
                        }
                    )
                }
            val ackJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    MqttManager.debugLog.collect(
                        object : FlowCollector<MqttLogEntry> {
                            override suspend fun emit(entry: MqttLogEntry) {
                                if (entry.tag == "subscribe success") {
                                    val topic =
                                        entry.message?.removePrefix("topic: ")
                                    if (
                                        topic in
                                            Mqtt5LoopbackBroker.EXPECTED_TOPICS &&
                                            confirmedTopics.add(topic)
                                    ) {
                                        subscribed.countDown()
                                    }
                                }
                            }
                        }
                    )
                }
            var probeFailure: Throwable? = null
            try {
                settings.mqttEnabled = true
                settings.mqttServerHost = "127.0.0.1"
                settings.mqttServerPort = broker.port
                settings.mqttUseTls = false
                settings.mqttUseWebSocket = webSocket
                settings.mqttWebSocketServerPath = "/mqtt"
                settings.mqttAutomaticReconnect = false
                settings.mqttClientId = "r8-loopback"
                settings.mqttHomeAssistantEnabled = false
                settings.mqttSubscribeCommandTopic =
                    Mqtt5LoopbackBroker.COMMAND_TOPIC
                settings.mqttSubscribeSettingsTopic =
                    Mqtt5LoopbackBroker.SETTINGS_TOPIC
                settings.mqttSubscribeRequestTopic =
                    Mqtt5LoopbackBroker.REQUEST_TOPIC
                settings.mqttPublishEventTopic = Mqtt5LoopbackBroker.EVENT_TOPIC
                settings.mqttPublishEventQos = MqttQosOption.AT_MOST_ONCE

                MqttManager.connect(
                    targetContext,
                    onConnected = { connected.countDown() },
                    onError = {
                        failure.set(it ?: "Unknown MQTT connection failure")
                        connected.countDown()
                    },
                )
                check(connected.await(15, TimeUnit.SECONDS)) {
                    "MQTT connect timed out"
                }
                check(failure.get() == null) {
                    "MQTT connect failed: ${failure.get()}"
                }
                check(broker.subscriptions.await(10, TimeUnit.SECONDS)) {
                    "Broker did not acknowledge all three MQTT 5 subscriptions"
                }
                broker.checkFault()
                check(subscribed.await(10, TimeUnit.SECONDS)) {
                    "HiveMQ did not accept all three SUBACK responses: $confirmedTopics"
                }
                check(broker.inboundSent.await(5, TimeUnit.SECONDS)) {
                    "Broker did not send inbound MQTT PUBLISH"
                }
                check(inboundReceived.await(10, TimeUnit.SECONDS)) {
                    "Inbound MQTT request was not delivered to RemoteMessageManager"
                }
                MqttManager.publishUrlChangedEvent(Mqtt5LoopbackBroker.EXPECTED_URL)
                check(broker.outboundPublished.await(10, TimeUnit.SECONDS)) {
                    "Outbound MQTT url_changed PUBLISH did not reach broker"
                }
                broker.checkFault()
            } catch (error: Throwable) {
                probeFailure = error
                throw error
            } finally {
                // Never replace a probe failure with a disconnect or preference
                // restoration failure. Both failures are available in JSON.
                val cleanupFailures = ArrayList<Throwable>()
                runCatching {
                        inboundJob.cancel()
                        ackJob.cancel()
                    }
                    .exceptionOrNull()
                    ?.let(cleanupFailures::add)
                runCatching {
                        val disconnected = CountDownLatch(1)
                        val disconnectError = AtomicReference<String?>()
                        MqttManager.disconnect(
                            uk.nktnet.webviewkiosk.config.remote.outbound
                                .OutboundDisconnectingEvent
                                .DisconnectCause
                                .USER_INITIATED_DISCONNECT,
                            onDisconnected = { disconnected.countDown() },
                            onError = { message ->
                                disconnectError.set(
                                    message?.takeIf { it.isNotBlank() }
                                        ?: "Unknown MQTT disconnect failure"
                                )
                                disconnected.countDown()
                            },
                        )
                        check(disconnected.await(7, TimeUnit.SECONDS)) {
                            "MQTT disconnect timed out"
                        }
                        disconnectError.get()?.let { error ->
                            throw IllegalStateException(
                                "MQTT disconnect failed: $error"
                            )
                        }
                    }
                    .exceptionOrNull()
                    ?.let(cleanupFailures::add)
                runCatching {
                        // Restore pre-test settings even if disconnect fails.
                        settings.mqttEnabled = original.enabled
                        settings.mqttServerHost = original.host
                        settings.mqttServerPort = original.port
                        settings.mqttUseTls = original.tls
                        settings.mqttUseWebSocket = original.websocket
                        settings.mqttWebSocketServerPath = original.path
                        settings.mqttAutomaticReconnect = original.reconnect
                        settings.mqttClientId = original.clientId
                        settings.mqttHomeAssistantEnabled = original.homeAssistant
                        settings.mqttSubscribeCommandTopic = original.commandTopic
                        settings.mqttSubscribeSettingsTopic = original.settingsTopic
                        settings.mqttSubscribeRequestTopic = original.requestTopic
                        settings.mqttPublishEventTopic = original.eventTopic
                        settings.mqttPublishEventQos = original.eventQos
                    }
                    .exceptionOrNull()
                    ?.let(cleanupFailures::add)
                val originalError = probeFailure
                if (originalError != null) {
                    cleanupFailures.forEach(originalError::addSuppressed)
                } else {
                    cleanupFailures.firstOrNull()?.let { first ->
                        cleanupFailures.drop(1).forEach(first::addSuppressed)
                        throw first
                    }
                }
            }
        } catch (error: Throwable) {
            brokerFailure = error
            throw error
        } finally {
            try {
                broker.close()
            } catch (cleanupError: Throwable) {
                val originalError = brokerFailure
                if (originalError != null) {
                    originalError.addSuppressed(cleanupError)
                } else {
                    throw cleanupError
                }
            }
        }
    }

    private data class MqttProbeSettings(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val tls: Boolean,
        val websocket: Boolean,
        val path: String,
        val reconnect: Boolean,
        val clientId: String,
        val homeAssistant: Boolean,
        val commandTopic: String,
        val settingsTopic: String,
        val requestTopic: String,
        val eventTopic: String,
        val eventQos: MqttQosOption,
    )
}
