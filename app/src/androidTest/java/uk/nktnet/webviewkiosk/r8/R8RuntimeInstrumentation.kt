package uk.nktnet.webviewkiosk.r8

import android.app.Instrumentation
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.json.JSONTokener
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventJsonParser
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUrlChangedEvent
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BatteryInterface

/**
 * Tests application code inside the R8-minified smokeRelease APK. The tests are kept
 * in a separate instrumentation APK, never in the published APK. Only run this on a
 * disposable emulator: MQTT preferences are changed briefly.
 */
class R8RuntimeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val probes =
            listOf(
                "json_serialization" to ::jsonSerialization,
                "webview_javascript" to ::webViewJavascript,
                "mqtt_tcp" to { mqttLoopback(webSocket = false) },
                "mqtt_websocket" to { mqttLoopback(webSocket = true) },
            )
        var failures = 0
        for ((name, probe) in probes) {
            val result = Bundle()
            result.putString("r8_check", name)
            try {
                probe()
                result.putString("r8_status", "pass")
                if (name.startsWith("mqtt_")) {
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
                result.putString(
                    "r8_error",
                    "${error.javaClass.simpleName}: ${error.message}",
                )
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

    private fun jsonSerialization() {
        val request =
            InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(
                """{"requestType":"get_status","messageId":"r8-test","unknownField":true}"""
            )
        check(request is InboundStatusRequest && request.messageId == "r8-test") {
            "Inbound sealed type or lenient JSON decoding failed"
        }
        val event: OutboundEventMessage =
            OutboundUrlChangedEvent(
                messageId = "r8-test",
                username = "test",
                appInstanceId = "local",
                data =
                    OutboundUrlChangedEvent.UrlData("https://example.invalid/a?x=1"),
            )
        val json = OutboundEventJsonParser.encodeToString(event)
        val decoded =
            OutboundEventJsonParser.decodeFromString<OutboundEventMessage>(json)
        check(
            decoded == event &&
                JSONObject(json).getString("eventType") == "url_changed"
        ) {
            "Outbound sealed type JSON round-trip failed"
        }
    }

    private fun webViewJavascript() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String?>()
        var webView: WebView? = null
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
        } finally {
            runOnMainSync { webView?.destroy() }
        }
    }

    private fun mqttLoopback(webSocket: Boolean) {
        // Exercise the application's minified HiveMQ/Netty client, not a second
        // client embedded in the test APK. Keep the mock broker on loopback.
        Mqtt5LoopbackBroker(webSocket).use { broker ->
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
            val confirmedTopics = Collections.synchronizedSet(mutableSetOf<String>())
            val scope = CoroutineScope(Dispatchers.Default)
            // UNDISPATCHED attaches both SharedFlow observers before connect() can
            // receive broker messages. The request is observed, never executed.
            val inboundJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    RemoteMessageManager.requestsFlow.collect { message ->
                        if (
                            message.source ==
                                RemoteMessageManager.RemoteMessage.Source.MQTT &&
                                message.message is InboundStatusRequest &&
                                message.message.messageId ==
                                    Mqtt5LoopbackBroker.INBOUND_ID
                        ) {
                            inboundReceived.countDown()
                        }
                    }
                }
            val ackJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    MqttManager.debugLog.collect { entry ->
                        if (entry.tag == "subscribe success") {
                            val topic = entry.message?.removePrefix("topic: ")
                            if (
                                topic in Mqtt5LoopbackBroker.EXPECTED_TOPICS &&
                                    confirmedTopics.add(topic)
                            ) {
                                subscribed.countDown()
                            }
                        }
                    }
                }
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
            } finally {
                inboundJob.cancel()
                ackJob.cancel()
                scope.cancel()
                try {
                    val disconnected = CountDownLatch(1)
                    MqttManager.disconnect(
                        uk.nktnet.webviewkiosk.config.remote.outbound
                            .OutboundDisconnectingEvent
                            .DisconnectCause
                            .USER_INITIATED_DISCONNECT,
                        onDisconnected = { disconnected.countDown() },
                        onError = { disconnected.countDown() },
                    )
                    check(disconnected.await(7, TimeUnit.SECONDS)) {
                        "MQTT disconnect timed out"
                    }
                } finally {
                    // Restores the exact pre-test settings, including on errors.
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
