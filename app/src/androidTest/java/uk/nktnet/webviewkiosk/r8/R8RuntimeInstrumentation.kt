package uk.nktnet.webviewkiosk.r8

import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.json.JSONTokener
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventJsonParser
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundEventMessage
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundUrlChangedEvent
import uk.nktnet.webviewkiosk.managers.MqttManager
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
        // Test the *application's* HiveMQ/Netty path, not a second unminified
        // copy of the client inside androidTest.
        LoopbackBroker(webSocket).use { broker ->
            val settings = UserSettings(targetContext)
            val enabled = settings.mqttEnabled
            val host = settings.mqttServerHost
            val port = settings.mqttServerPort
            val tls = settings.mqttUseTls
            val ws = settings.mqttUseWebSocket
            val path = settings.mqttWebSocketServerPath
            val reconnect = settings.mqttAutomaticReconnect
            val clientId = settings.mqttClientId
            val completed = CountDownLatch(1)
            val failure = AtomicReference<String?>()
            try {
                settings.mqttEnabled = true
                settings.mqttServerHost = "127.0.0.1"
                settings.mqttServerPort = broker.port
                settings.mqttUseTls = false
                settings.mqttUseWebSocket = webSocket
                settings.mqttWebSocketServerPath = "/mqtt"
                settings.mqttAutomaticReconnect = false
                settings.mqttClientId = "r8-loopback"
                MqttManager.connect(
                    targetContext,
                    onConnected = { completed.countDown() },
                    onError = {
                        failure.set(it ?: "Unknown MQTT connection failure")
                        completed.countDown()
                    },
                )
                check(completed.await(15, TimeUnit.SECONDS)) {
                    "MQTT connect timed out"
                }
                check(failure.get() == null) {
                    "MQTT connect failed: ${failure.get()}"
                }
                check(broker.handshake.await(3, TimeUnit.SECONDS)) {
                    "MQTT broker did not receive CONNECT"
                }
                broker.fault.get()?.let {
                    throw IllegalStateException("Broker error: $it")
                }
            } finally {
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
                    // Even a failed or throwing disconnect must not leave the
                    // kiosk configured to connect to our disposable broker.
                    settings.mqttEnabled = enabled
                    settings.mqttServerHost = host
                    settings.mqttServerPort = port
                    settings.mqttUseTls = tls
                    settings.mqttUseWebSocket = ws
                    settings.mqttWebSocketServerPath = path
                    settings.mqttAutomaticReconnect = reconnect
                    settings.mqttClientId = clientId
                }
            }
        }
    }

    /** Minimal MQTT 5 CONNACK peer, with optional RFC 6455 WebSocket framing. */
    private class LoopbackBroker(private val webSocket: Boolean) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int = listener.localPort
        val handshake = CountDownLatch(1)
        val fault = AtomicReference<String?>()
        private val worker =
            Thread(
                {
                    try {
                        listener.accept().use { socket ->
                            socket.soTimeout = 10_000
                            if (webSocket) {
                                upgradeWebSocket(socket)
                            }
                            val connect = receive(socket.getInputStream())
                            check(
                                connect.isNotEmpty() && connect[0] == 0x10.toByte()
                            ) {
                                "Expected MQTT CONNECT"
                            }
                            check(
                                connect
                                    .toString(Charsets.ISO_8859_1)
                                    .contains("MQTT")
                            ) {
                                "Not an MQTT protocol header"
                            }
                            send(socket, byteArrayOf(0x20, 0x03, 0x00, 0x00, 0x00))
                            handshake.countDown()
                            // Give the application's connected callback time to run.
                            Thread.sleep(1500)
                        }
                    } catch (exception: Exception) {
                        fault.set(
                            "${exception.javaClass.simpleName}: ${exception.message}"
                        )
                        handshake.countDown()
                    }
                },
                "r8-mqtt-loopback",
            )

        init {
            worker.isDaemon = true
            worker.start()
        }

        private fun upgradeWebSocket(socket: Socket) {
            val input = socket.getInputStream()
            val headers = ByteArrayOutputStream()
            while (!headers.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                check(headers.size() < 16_384) { "WebSocket headers too large" }
                val next = input.read()
                check(next >= 0) { "WebSocket upgrade ended early" }
                headers.write(next)
            }
            val request = headers.toString("ISO-8859-1")
            check(request.startsWith("GET /mqtt ")) { "Wrong WebSocket path" }
            val key =
                Regex("(?im)^Sec-WebSocket-Key: *([^\r\n]+)")
                    .find(request)
                    ?.groupValues
                    ?.get(1)
                    ?.trim() ?: error("Missing WebSocket key")
            val digest = MessageDigest.getInstance("SHA-1")
            val accept =
                Base64.encodeToString(
                    digest.digest(
                        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()
                    ),
                    Base64.NO_WRAP,
                )
            socket
                .getOutputStream()
                .write(
                    ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n" +
                            "Sec-WebSocket-Protocol: mqtt\r\n\r\n")
                        .toByteArray(Charsets.US_ASCII)
                )
            socket.getOutputStream().flush()
        }

        private fun receive(input: InputStream): ByteArray {
            if (!webSocket) {
                val header = input.read()
                check(header >= 0) { "Broker socket closed" }
                val remaining = readLength(input)
                return byteArrayOf(header.toByte()) + readBytes(input, remaining)
            }
            val header = input.read()
            check(header >= 0 && (header and 0x0f) == 2) {
                "Expected binary WebSocket frame"
            }
            var length = input.read()
            check(length >= 0 && (length and 0x80) != 0) {
                "Client WebSocket frame not masked"
            }
            length = length and 0x7f
            if (length == 126) {
                length = (input.read() shl 8) or input.read()
            }
            check(length in 1..16_384) { "Invalid WebSocket frame length" }
            val mask = readBytes(input, 4)
            return readBytes(input, length)
                .mapIndexed { index, byte ->
                    (byte.toInt() xor mask[index % 4].toInt()).toByte()
                }
                .toByteArray()
        }

        private fun readLength(input: InputStream): Int {
            var total = 0
            for (shift in 0..21 step 7) {
                val octet = input.read()
                check(octet >= 0) { "Truncated MQTT length" }
                total = total or ((octet and 0x7f) shl shift)
                if ((octet and 0x80) == 0) {
                    return total
                }
            }
            error("MQTT remaining length too large")
        }

        private fun readBytes(input: InputStream, count: Int): ByteArray {
            val bytes = ByteArray(count)
            var consumed = 0
            while (consumed < count) {
                val read = input.read(bytes, consumed, count - consumed)
                check(read > 0) { "Truncated broker request" }
                consumed += read
            }
            return bytes
        }

        private fun send(socket: Socket, payload: ByteArray) {
            val bytes =
                if (webSocket) {
                    byteArrayOf(0x82.toByte(), payload.size.toByte()) + payload
                } else {
                    payload
                }
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }

        override fun close() {
            listener.close()
            worker.join(2000)
        }
    }
}
