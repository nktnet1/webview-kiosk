package uk.nktnet.webviewkiosk.instrumentation

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Deterministic on-device HTTP origin: no public network or external server is required. */
internal class LoopbackHttpServer : AutoCloseable {
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val closed = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val executor = Executors.newCachedThreadPool()
    private val hitCounts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val acceptor = thread(name = "http-test-accept", isDaemon = true) {
        while (!closed.get()) {
            try {
                val socket = server.accept()
                clients.add(socket)
                executor.execute {
                    try {
                        handle(socket)
                    } finally {
                        clients.remove(socket)
                        runCatching { socket.close() }
                    }
                }
            } catch (error: SocketException) {
                if (!closed.get()) throw error
            }
        }
    }

    fun url(path: String) = "http://127.0.0.1:${server.localPort}$path"

    fun requests(path: String): Int = hitCounts[path]?.get() ?: 0

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
        val request = reader.readLine() ?: return
        val path = request.split(' ').getOrNull(1)?.substringBefore('?') ?: return
        while (true) {
            if (reader.readLine().isNullOrEmpty()) break
        }
        hitCounts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()

        val (status, body, headers) = when (path) {
            "/redirect" -> Triple("302 Found", "", "Location: /first\r\n")
            "/first" -> Triple("200 OK", page("first"), "")
            "/second" -> Triple("200 OK", page("second"), "")
            "/iframe" -> Triple(
                "200 OK",
                "<html><body><h1 id='marker'>iframe</h1><iframe src='/missing'></iframe></body></html>",
                ""
            )
            else -> Triple("404 Not Found", "missing", "")
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write(
                ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\n" +
                    "Connection: close\r\n$headers\r\n")
                    .toByteArray(Charsets.US_ASCII)
            )
            write(bytes)
            flush()
        }
    }

    private fun page(marker: String) =
        "<html><head><title>$marker</title></head><body><h1 id='marker'>$marker</h1></body></html>"

    override fun close() {
        closed.set(true)
        server.close()
        clients.forEach { runCatching { it.close() } }
        acceptor.join(2_000)
        executor.shutdownNow()
        check(executor.awaitTermination(3, TimeUnit.SECONDS)) { "HTTP workers leaked" }
        check(!acceptor.isAlive) { "HTTP acceptor leaked" }
    }
}
