package uk.nktnet.webviewkiosk.services

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Minimal MQTT 5 TCP peer: real HiveMQ handshakes and callbacks, with gated CONNACKs. */
internal class LoopbackMqttBroker : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val closing = AtomicBoolean()
    private val failure = AtomicReference<Throwable?>()
    private val connections = CopyOnWriteArrayList<Connection>()
    private val connected = LinkedBlockingQueue<Connection>()
    private val acceptor = thread(name = "test-mqtt-accept", isDaemon = true) {
        try {
            while (!closing.get()) {
                val connection = Connection(server.accept())
                connections.add(connection)
                connection.start()
            }
        } catch (e: SocketException) {
            if (!closing.get()) failure.compareAndSet(null, e)
        } catch (e: Throwable) {
            failure.compareAndSet(null, e)
        }
    }

    val port: Int get() = server.localPort
    val connectionCount: Int get() = connections.size

    fun awaitConnection(): Connection {
        val result = connected.poll(5, TimeUnit.SECONDS)
        assertHealthy()
        return checkNotNull(result) { "No MQTT CONNECT reached the loopback broker" }
    }

    fun assertHealthy() {
        failure.get()?.let { throw AssertionError("Loopback MQTT peer failed", it) }
    }

    override fun close() {
        closing.set(true)
        server.close()
        acceptor.join(1_000)
        connections.forEach { it.close() }
        connections.forEach { it.worker.join(1_000) }
        check(!acceptor.isAlive && connections.none { it.worker.isAlive }) {
            "Loopback MQTT threads did not stop"
        }
        assertHealthy()
    }

    data class Publication(val topic: String, val payload: String, val properties: ByteArray)

    fun correlationData(publication: Publication): ByteArray? {
        val input = DataInputStream(ByteArrayInputStream(publication.properties))
        while (input.available() > 0) {
            when (val property = readVariableInt(input)) {
                0x01 -> input.readUnsignedByte()
                0x03, 0x08 -> readString(input)
                0x09 -> return ByteArray(input.readUnsignedShort()).also(input::readFully)
                0x26 -> { readString(input); readString(input) }
                else -> error("Unexpected outbound MQTT property: $property")
            }
        }
        return null
    }

    inner class Connection internal constructor(private val socket: Socket) : AutoCloseable {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private val connectRelease = CountDownLatch(1)
        private var connectReason = 0
        val subscriptions: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val subscriptionCount = AtomicInteger()
        val publications = LinkedBlockingQueue<Publication>()
        val disconnected = CountDownLatch(1)
        lateinit var worker: Thread
            private set

        internal fun start() {
            worker = thread(name = "test-mqtt-peer", isDaemon = true) {
                try {
                    val connect = readPacket(input)
                    check(connect.header == 0x10) { "Expected CONNECT, got ${connect.header}" }
                    val body = DataInputStream(ByteArrayInputStream(connect.body))
                    check(readString(body) == "MQTT" && body.readUnsignedByte() == 5) {
                        "The client did not use MQTT 5"
                    }
                    connected.add(this)
                    check(connectRelease.await(5, TimeUnit.SECONDS)) { "CONNACK gate was not released" }
                    if (closing.get() || socket.isClosed) return@thread
                    send(0x20, byteArrayOf(0, connectReason.toByte(), 0))
                    if (connectReason != 0) return@thread

                    while (!closing.get() && !socket.isClosed) {
                        val packet = readPacket(input)
                        val packetBody = DataInputStream(ByteArrayInputStream(packet.body))
                        when (packet.header shr 4) {
                            3 -> {
                                val topic = readString(packetBody)
                                val qos = (packet.header shr 1) and 3
                                check(qos <= 1) { "Test peer only supports QoS 0/1" }
                                val packetId = if (qos == 1) packetBody.readUnsignedShort() else null
                                val properties = ByteArray(readVariableInt(packetBody))
                                    .also(packetBody::readFully)
                                publications.add(Publication(topic, packetBody.readBytes().toString(Charsets.UTF_8), properties))
                                if (packetId != null) send(0x40, packetIdBytes(packetId))
                            }
                            8 -> {
                                val packetId = packetBody.readUnsignedShort()
                                packetBody.readFully(ByteArray(readVariableInt(packetBody)))
                                val topics = mutableListOf<String>()
                                val grants = mutableListOf<Byte>()
                                while (packetBody.available() > 0) {
                                    topics.add(readString(packetBody))
                                    grants.add((packetBody.readUnsignedByte() and 3).toByte())
                                }
                                send(0x90, packetIdBytes(packetId) + byteArrayOf(0) + grants.toByteArray())
                                subscriptions.addAll(topics)
                                subscriptionCount.addAndGet(topics.size)
                            }
                            12 -> send(0xD0, byteArrayOf())
                            14 -> return@thread
                            else -> error("Unexpected MQTT packet: ${packet.header}")
                        }
                    }
                } catch (_: EOFException) {
                    // A failed/cancelled connection and a normal client disconnect close TCP.
                } catch (e: SocketException) {
                    if (!closing.get() && !socket.isClosed) failure.compareAndSet(null, e)
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                } finally {
                    socket.close()
                    disconnected.countDown()
                }
            }
        }

        fun acknowledgeConnect(reason: Int = 0) {
            connectReason = reason
            connectRelease.countDown()
        }

        fun publish(topic: String, payload: String, properties: ByteArray = byteArrayOf()) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { body ->
                writeString(body, topic)
                writeVariableInt(body, properties.size)
                body.write(properties)
                body.write(payload.toByteArray(Charsets.UTF_8))
            }
            send(0x30, bytes.toByteArray())
        }

        @Synchronized
        private fun send(header: Int, body: ByteArray) {
            output.writeByte(header)
            writeVariableInt(output, body.size)
            output.write(body)
            output.flush()
        }

        override fun close() {
            connectRelease.countDown()
            socket.close()
        }
    }

    private data class Packet(val header: Int, val body: ByteArray)

    private fun readPacket(input: DataInputStream): Packet {
        val header = input.readUnsignedByte()
        val length = readVariableInt(input)
        check(length <= 1_048_576) { "Unexpectedly large test packet" }
        return Packet(header, ByteArray(length).also(input::readFully))
    }

    private fun packetIdBytes(id: Int) = byteArrayOf((id shr 8).toByte(), id.toByte())

    private fun readString(input: DataInputStream): String =
        ByteArray(input.readUnsignedShort()).also(input::readFully).toString(Charsets.UTF_8)

    private fun writeString(output: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        output.writeShort(bytes.size)
        output.write(bytes)
    }

    private fun readVariableInt(input: DataInputStream): Int {
        var value = 0
        for (shift in 0..21 step 7) {
            val byte = input.readUnsignedByte()
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return value
        }
        error("Malformed MQTT variable-length integer")
    }

    private fun writeVariableInt(output: DataOutputStream, value: Int) {
        var remaining = value
        do {
            var byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining > 0) byte = byte or 0x80
            output.writeByte(byte)
        } while (remaining > 0)
    }
}
