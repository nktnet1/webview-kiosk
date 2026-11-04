package uk.nktnet.webviewkiosk.r8

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

/**
 * Disposable MQTT 5 peer. Not a full broker: it checks real client SUBACK and
 * PUBLISH paths.
 */
internal class Mqtt5LoopbackBroker(private val webSocket: Boolean) : Closeable {
    companion object {
        const val COMMAND_TOPIC = "r8/probe/command"
        const val SETTINGS_TOPIC = "r8/probe/settings"
        const val REQUEST_TOPIC = "r8/probe/request"
        const val EVENT_TOPIC = "r8/probe/event"
        const val INBOUND_ID = "r8-mqtt-inbound"
        const val EXPECTED_URL = "https://example.invalid/r8/probe"
        val EXPECTED_TOPICS: Set<String> =
            Collections.unmodifiableSet(
                HashSet<String>().apply {
                    add(COMMAND_TOPIC)
                    add(SETTINGS_TOPIC)
                    add(REQUEST_TOPIC)
                }
            )
    }

    private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port: Int = listener.localPort
    val subscriptions = CountDownLatch(1)
    val inboundSent = CountDownLatch(1)
    val outboundPublished = CountDownLatch(1)
    private val fault = AtomicReference<String?>()
    @Volatile private var stopping = false
    @Volatile private var connectedSocket: Socket? = null
    private val worker =
        Thread({ serve() }, "r8-mqtt-loopback").also {
            it.isDaemon = true
            it.start()
        }

    fun checkFault() {
        fault.get()?.let { throw IllegalStateException("MQTT broker error: $it") }
    }

    private fun serve() {
        try {
            val socket = listener.accept()
            try {
                connectedSocket = socket
                socket.soTimeout = 30_000
                if (webSocket) {
                    upgradeWebSocket(socket)
                }
                // Treat RFC 6455 frames as a continuous MQTT byte stream. Netty
                // may pack multiple MQTT control packets into one WS frame.
                val input =
                    if (webSocket) {
                        WebSocketInput(socket.getInputStream())
                    } else {
                        socket.getInputStream()
                    }
                val connect = receive(input)
                check(
                    connect.first == 0x10 &&
                        connect.second.size >= 7 &&
                        connect.second
                            .copyOfRange(0, 6)
                            .toString(Charsets.ISO_8859_1)
                            .contains("MQTT") &&
                        connect.second[6] == 5.toByte()
                ) {
                    "Expected MQTT 5 CONNECT"
                }
                send(socket, mqttPacket(0x20, byteArrayOf(0x00, 0x00, 0x00)))

                val topics = HashSet<String>()
                while (!stopping) {
                    val (header, body) = receive(input)
                    when (header and 0xf0) {
                        0x80 -> {
                            check(header == 0x82) { "Invalid SUBSCRIBE flags" }
                            val packet = PacketReader(body)
                            val packetId = packet.u16()
                            packet.skip(packet.variableLength()) // MQTT 5 properties
                            val filters = ArrayList<String>()
                            while (packet.remaining > 0) {
                                filters.add(packet.utf8())
                                packet.byte() // subscription options
                            }
                            check(filters.isNotEmpty()) { "Empty MQTT SUBSCRIBE" }
                            topics.addAll(filters)
                            check(topics.all { it in EXPECTED_TOPICS }) {
                                "Unexpected MQTT subscription topics: $topics"
                            }
                            val ack =
                                byteArrayOf(
                                    (packetId ushr 8).toByte(),
                                    packetId.toByte(),
                                    0x00,
                                ) +
                                    ByteArray(
                                        filters.size
                                    ) // granted QoS 0 for each filter
                            send(socket, mqttPacket(0x90, ack))
                            if (
                                topics.containsAll(EXPECTED_TOPICS) &&
                                    subscriptions.count > 0
                            ) {
                                subscriptions.countDown()
                                // This validates that HiveMQ's subscribed callback
                                // survives R8
                                // and forwards messages into the app's request flow.
                                val data =
                                    """{"requestType":"get_status","messageId":"$INBOUND_ID"}"""
                                send(
                                    socket,
                                    mqttPacket(
                                        0x30,
                                        utf8(REQUEST_TOPIC) +
                                            byteArrayOf(0x00) +
                                            data.toByteArray(Charsets.UTF_8),
                                    ),
                                )
                                inboundSent.countDown()
                            }
                        }
                        0x30 -> {
                            val packet = PacketReader(body)
                            val topic = packet.utf8()
                            val qos = (header ushr 1) and 0x03
                            check(qos <= 1) { "Unexpected MQTT QoS $qos" }
                            val packetId =
                                if (qos == 1) {
                                    packet.u16()
                                } else {
                                    0
                                }
                            packet.skip(packet.variableLength())
                            val json =
                                JSONObject(packet.rest().toString(Charsets.UTF_8))
                            if (json.optString("eventType") == "url_changed") {
                                check(topic == EVENT_TOPIC) {
                                    "Wrong outbound MQTT event topic: $topic"
                                }
                                check(
                                    json.getJSONObject("data").getString("url") ==
                                        EXPECTED_URL
                                ) {
                                    "MQTT outbound event payload mismatch"
                                }
                                outboundPublished.countDown()
                            }
                            if (qos == 1) {
                                send(
                                    socket,
                                    mqttPacket(
                                        0x40,
                                        byteArrayOf(
                                            (packetId ushr 8).toByte(),
                                            packetId.toByte(),
                                        ),
                                    ),
                                )
                            }
                        }
                        0xc0 -> send(socket, mqttPacket(0xd0, byteArrayOf()))
                        0xe0 -> return // MQTT DISCONNECT
                        else ->
                            error("Unexpected MQTT packet: 0x${header.toString(16)}")
                    }
                }
            } finally {
                socket.close()
            }
        } catch (error: Exception) {
            if (!stopping) {
                fault.set("${error.javaClass.simpleName}: ${error.message}")
            }
        } finally {
            // Wake waiters immediately if the peer fails rather than waiting for a
            // timeout.
            subscriptions.countDown()
            inboundSent.countDown()
            outboundPublished.countDown()
        }
    }

    /** MQTT frames can cross WebSocket message boundaries or share one message. */
    private fun receive(input: InputStream): Pair<Int, ByteArray> {
        val header = readByte(input)
        val length = readVariableLength(input)
        return header to readExactly(input, length)
    }

    /** Decodes masked client frames into one logical byte stream for MQTT. */
    private inner class WebSocketInput(private val wire: InputStream) :
        InputStream() {
        private var frame = byteArrayOf()
        private var offset = 0

        override fun read(): Int {
            if (offset == frame.size) {
                readFrame()
            }
            return frame[offset++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, start: Int, count: Int): Int {
            if (count == 0) {
                return 0
            }
            val first = read()
            buffer[start] = first.toByte()
            val tail = minOf(count - 1, frame.size - offset)
            if (tail > 0) {
                frame.copyInto(buffer, start + 1, offset, offset + tail)
                offset += tail
            }
            return tail + 1
        }

        private fun readFrame() {
            check(wire.read() == 0x82) {
                "Expected unfragmented binary WebSocket frame"
            }
            val raw = readByte(wire)
            check((raw and 0x80) != 0) { "Unmasked WebSocket client frame" }
            val length =
                when (raw and 0x7f) {
                    126 -> (readByte(wire) shl 8) or readByte(wire)
                    127 -> error("Oversized WebSocket frame")
                    else -> raw and 0x7f
                }
            val mask = readExactly(wire, 4)
            frame =
                readExactly(wire, length)
                    .mapIndexed { index, byte ->
                        (byte.toInt() xor mask[index % 4].toInt()).toByte()
                    }
                    .toByteArray()
            offset = 0
            if (frame.isEmpty()) {
                readFrame()
            }
        }
    }

    private fun upgradeWebSocket(socket: Socket) {
        val input = socket.getInputStream()
        val headers = ByteArrayOutputStream()
        while (!headers.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
            check(headers.size() < 16_384) { "WebSocket headers too large" }
            headers.write(readByte(input))
        }
        val request = headers.toString("ISO-8859-1")
        check(request.startsWith("GET /mqtt ")) { "Wrong WebSocket path" }
        val key =
            Regex("(?im)^Sec-WebSocket-Key: *([^\\r\\n]+)")
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
                ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                        "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n" +
                        "Sec-WebSocket-Protocol: mqtt\r\n\r\n")
                    .toByteArray(Charsets.US_ASCII)
            )
        socket.getOutputStream().flush()
    }

    private fun send(socket: Socket, payload: ByteArray) {
        val bytes =
            if (webSocket) {
                when {
                    payload.size <= 125 ->
                        byteArrayOf(0x82.toByte(), payload.size.toByte()) + payload
                    payload.size <= 65_535 ->
                        byteArrayOf(
                            0x82.toByte(),
                            126,
                            (payload.size ushr 8).toByte(),
                            payload.size.toByte(),
                        ) + payload
                    else -> error("WebSocket broker frame too large")
                }
            } else {
                payload
            }
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    private fun mqttPacket(header: Int, body: ByteArray) =
        byteArrayOf(header.toByte()) + variableLength(body.size) + body

    private fun variableLength(value: Int): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        do {
            var octet = remaining % 128
            remaining /= 128
            if (remaining > 0) {
                octet = octet or 0x80
            }
            output.write(octet)
        } while (remaining > 0)
        return output.toByteArray()
    }

    private fun utf8(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return byteArrayOf((bytes.size ushr 8).toByte(), bytes.size.toByte()) + bytes
    }

    private fun readByte(input: InputStream): Int {
        val octet = input.read()
        check(octet >= 0) { "Broker socket closed unexpectedly" }
        return octet
    }

    private fun readVariableLength(input: InputStream): Int {
        var value = 0
        for (shift in 0..21 step 7) {
            val octet = readByte(input)
            value = value or ((octet and 0x7f) shl shift)
            if ((octet and 0x80) == 0) {
                return value
            }
        }
        error("Invalid MQTT remaining length")
    }

    private fun readExactly(input: InputStream, count: Int): ByteArray {
        check(count in 0..65_535) { "MQTT packet too large" }
        val bytes = ByteArray(count)
        var position = 0
        while (position < count) {
            val n = input.read(bytes, position, count - position)
            check(n > 0) { "Incomplete MQTT packet" }
            position += n
        }
        return bytes
    }

    private class PacketReader(private val bytes: ByteArray) {
        private var position = 0
        val remaining: Int
            get() = bytes.size - position

        fun byte(): Int {
            check(remaining > 0) { "Truncated MQTT packet" }
            return bytes[position++].toInt() and 0xff
        }

        fun u16() = (byte() shl 8) or byte()

        fun variableLength(): Int {
            var value = 0
            for (shift in 0..21 step 7) {
                val octet = byte()
                value = value or ((octet and 0x7f) shl shift)
                if ((octet and 0x80) == 0) {
                    return value
                }
            }
            error("Invalid MQTT property length")
        }

        fun skip(length: Int) {
            check(length in 0..remaining) { "Truncated MQTT properties" }
            position += length
        }

        fun utf8(): String {
            val length = u16()
            check(length in 0..remaining) { "Truncated MQTT string" }
            val result =
                bytes
                    .copyOfRange(position, position + length)
                    .toString(Charsets.UTF_8)
            position += length
            return result
        }

        fun rest() = bytes.copyOfRange(position, bytes.size)
    }

    override fun close() {
        stopping = true
        connectedSocket?.close()
        listener.close()
        worker.join(2000)
    }
}
