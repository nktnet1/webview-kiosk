package uk.nktnet.webviewkiosk.managers

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.config.mqtt.MqttConfig
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent.DisconnectCause
import uk.nktnet.webviewkiosk.testing.runConcurrently
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exercises the connected-client shutdown path, including delayed and failed acknowledgements. */
@RunWith(RobolectricTestRunner::class)
class MqttShutdownTest {
    private val manager = MqttManager
    private val fieldsToRestore = listOf(
        "client", "config", "pendingDisconnect", "disconnectRequestId",
        "reconnectCancellation", "configurationError",
    )
    private lateinit var savedFields: Map<String, Any?>
    private var savedCancellation = false
    private lateinit var scopeJob: Job
    private lateinit var savedJobs: Set<Job>
    private lateinit var reconnectCancellation: Completion
    private val clients = mutableListOf<RecordingClient>()

    @Before
    fun setUp() {
        savedFields = fieldsToRestore.associateWith { field(it).get(manager) }
        val scope = field("scope").get(manager) as CoroutineScope
        scopeJob = requireNotNull(scope.coroutineContext[Job])
        savedJobs = scopeJob.children.toSet()
        savedCancellation = cancellation().getAndSet(false)
        field("pendingDisconnect").set(manager, null)
        field("disconnectRequestId").setLong(manager, 0)
        reconnectCancellation = completion()
        field("reconnectCancellation").set(manager, reconnectCancellation.api)
        manager.updateConfig(RuntimeEnvironment.getApplication(), rebuildClient = false)
        val config = field("config").get(manager) as MqttConfig
        field("config").set(
            manager,
            config.copy(enabled = true, publishEventTopic = "wk/events"),
        )
    }

    @After
    fun tearDown() {
        try {
            // Resolve every future before restoring singleton fields so no callback reaches a later test.
            clients.forEach { client ->
                client.state.set(MqttClientState.DISCONNECTED)
                client.event.succeed()
                client.shutdown.succeed()
            }
        } finally {
            try {
                // Cancel and join only work created by this fixture; keep the manager's scope usable.
                runBlocking {
                    withTimeout(3_000) {
                        while (true) {
                            val jobs = scopeJob.children.filterNot { it in savedJobs }.toList()
                            if (jobs.isEmpty()) break
                            jobs.forEach { it.cancel() }
                            jobs.forEach { it.join() }
                        }
                    }
                }
            } finally {
                savedFields.forEach { (name, value) -> field(name).set(manager, value) }
                cancellation().set(savedCancellation)
            }
        }
    }

    @Test
    fun shutdownWaitsForBothTheEventAcknowledgementAndTheClientDisconnect() {
        val client = installClient()
        val stopped = StopProbe()

        stop(stopped)

        assertWaitingForEvent(client)
        assertTrue(cancellation().get())
        assertTrue(reconnectCancellation.isDone())
        assertTrue(stopped.outcomes.isEmpty())
        client.event.succeed()
        assertEquals(listOf("publish.build", "publish.send", "disconnect.build", "disconnect.send"), client.operations())
        assertTrue(stopped.outcomes.isEmpty())
        assertFalse(client.shutdown.isDone())

        client.finishShutdown()

        assertOutcome(stopped, "disconnected")
        assertNull(field("pendingDisconnect").get(manager))
        assertFalse(cancellation().get())
    }

    @Test
    fun aFailedEventAcknowledgementStillClosesTheClient() {
        val client = installClient()
        val stopped = StopProbe()
        stop(stopped)
        assertWaitingForEvent(client)

        client.event.fail(IOException("event rejected"))

        assertEquals(1, client.count("disconnect.send"))
        assertTrue(stopped.outcomes.isEmpty())
        client.finishShutdown()
        assertOutcome(stopped, "disconnected")
        assertFalse(cancellation().get())
    }

    @Test
    fun anEventBuilderExceptionFallsBackToClientDisconnect() {
        val client = installClient().apply { publishBuilderFailure = IllegalStateException("cannot build event") }
        val stopped = StopProbe()

        stop(stopped)

        assertEquals(listOf("publish.build", "disconnect.build", "disconnect.send"), client.operations())
        assertFalse(client.event.isDone())
        client.finishShutdown()
        assertOutcome(stopped, "disconnected")
    }

    @Test
    fun anInvalidEventTopicDoesNotLeaveShutdownWaitingForAnAcknowledgement() {
        val client = installClient()
        val config = field("config").get(manager) as MqttConfig
        field("config").set(manager, config.copy(publishEventTopic = "wk/#"))
        val stopped = StopProbe()

        stop(stopped)

        assertEquals(listOf("disconnect.build", "disconnect.send"), client.operations())
        client.finishShutdown()
        assertOutcome(stopped, "disconnected")
    }

    @Test
    fun eventTimeoutDisconnectsOnceAndIgnoresALateAcknowledgement() {
        val client = installClient()
        val stopped = StopProbe()
        stop(stopped)
        assertWaitingForEvent(client)

        assertTrue("Event timeout never started client shutdown", client.shutdownStarted.await(8, TimeUnit.SECONDS))

        assertFalse(client.event.isDone())
        assertEquals(1, client.count("disconnect.send"))
        assertTrue(stopped.outcomes.isEmpty())
        client.event.succeed()
        assertEquals(1, client.count("disconnect.send"))
        client.finishShutdown()
        assertOutcome(stopped, "disconnected")
        assertNull(field("pendingDisconnect").get(manager))
    }

    @Test
    fun concurrentStopCallersShareOneEventAndOneClientDisconnect() {
        val client = installClient()
        val first = StopProbe()
        val second = StopProbe()

        runConcurrently({ stop(first) }, { stop(second) })

        assertWaitingForEvent(client)
        assertTrue(first.outcomes.isEmpty())
        assertTrue(second.outcomes.isEmpty())
        client.event.succeed()
        assertEquals(1, client.count("disconnect.send"))
        client.finishShutdown()
        client.event.succeed()
        client.shutdown.succeed()

        assertOutcome(first, "disconnected")
        assertOutcome(second, "disconnected")
        assertEquals(1, client.count("disconnect.send"))
        assertNull(field("pendingDisconnect").get(manager))
    }

    @Test
    fun failedClientShutdownReportsAnErrorAndKeepsConnectionCancellationActive() {
        val client = installClient()
        val stopped = StopProbe()
        stop(stopped)
        assertWaitingForEvent(client)
        client.event.succeed()

        client.shutdown.fail(IOException("client shutdown failed"))

        assertOutcome(stopped, "error:client shutdown failed")
        assertNull(field("pendingDisconnect").get(manager))
        assertTrue(cancellation().get())
        assertEquals(MqttClientState.CONNECTED, client.state.get())
        val connectOutcomes = mutableListOf<String>()
        manager.connect(
            RuntimeEnvironment.getApplication(),
            onConnected = { connectOutcomes.add("connected") },
            onError = { connectOutcomes.add("error:$it") },
        )
        assertEquals(
            listOf("error:An MQTT connection or cancellation is already in progress."),
            connectOutcomes,
        )
        assertEquals(1, client.count("disconnect.send"))
    }

    @Test
    fun observedDisconnectedStateWinsOverAFailedShutdownAcknowledgement() {
        val client = installClient()
        val stopped = StopProbe()
        stop(stopped)
        assertWaitingForEvent(client)
        client.event.succeed()

        // Make the wire acknowledgement win the race with the polling coroutine.
        synchronized(manager) {
            client.state.set(MqttClientState.DISCONNECTED)
            client.shutdown.fail(IOException("connection already closed"))
        }

        assertOutcome(stopped, "disconnected")
        assertNull(field("pendingDisconnect").get(manager))
        assertFalse(cancellation().get())
    }

    @Test
    fun clientDisconnectBuilderFailureNotifiesEveryWaitingCaller() {
        val client = installClient().apply { disconnectBuilderFailure = IllegalStateException("cannot build disconnect") }
        val first = StopProbe()
        val second = StopProbe()
        stop(first)
        stop(second)
        assertWaitingForEvent(client)

        client.event.succeed()

        assertEquals(1, client.count("disconnect.build"))
        assertEquals(0, client.count("disconnect.send"))
        assertOutcome(first, "error:cannot build disconnect")
        assertOutcome(second, "error:cannot build disconnect")
        assertNull(field("pendingDisconnect").get(manager))
        assertTrue(cancellation().get())
    }

    @Test
    fun lateShutdownCompletionCannotClearAReplacementClientsPendingOperation() {
        val original = installClient()
        val originalStop = StopProbe()
        stop(originalStop)
        assertWaitingForEvent(original)
        original.event.succeed()
        assertEquals(1, original.count("disconnect.send"))
        // The state poll can observe a closed connection before the wire future completes.
        original.state.set(MqttClientState.DISCONNECTED)
        assertTrue("State polling never completed shutdown", originalStop.completed.await(3, TimeUnit.SECONDS))
        assertFalse(original.shutdown.isDone())
        val replacement = installClient()
        val replacementStop = StopProbe()
        stop(replacementStop)
        assertWaitingForEvent(replacement)
        val replacementOperation = field("pendingDisconnect").get(manager)

        original.shutdown.fail(IOException("late failure from old client"))

        assertSame(replacement.api, field("client").get(manager))
        assertSame(replacementOperation, field("pendingDisconnect").get(manager))
        assertTrue(cancellation().get())
        assertOutcome(originalStop, "disconnected")
        assertTrue(replacementStop.outcomes.isEmpty())
        assertEquals(0, replacement.count("disconnect.send"))
        replacement.event.succeed()
        replacement.finishShutdown()

        assertOutcome(replacementStop, "disconnected")
        assertEquals(1, original.count("disconnect.send"))
        assertEquals(1, replacement.count("disconnect.send"))
        assertNull(field("pendingDisconnect").get(manager))
        assertFalse(cancellation().get())
    }

    private fun stop(probe: StopProbe) {
        manager.disconnect(
            DisconnectCause.USER_INITIATED_DISCONNECT,
            onDisconnected = { probe.record("disconnected") },
            onError = { probe.record("error:$it") },
        )
    }

    private fun assertOutcome(probe: StopProbe, expected: String) {
        assertTrue("MQTT shutdown did not report its result", probe.completed.await(3, TimeUnit.SECONDS))
        assertEquals(listOf(expected), probe.outcomes.toList())
    }

    private fun assertWaitingForEvent(client: RecordingClient) {
        assertEquals(listOf("publish.build", "publish.send"), client.operations())
        assertFalse(client.event.isDone())
        assertFalse(client.shutdown.isDone())
    }

    private fun installClient(): RecordingClient = RecordingClient().also {
        clients.add(it)
        field("client").set(manager, it.api)
    }

    private fun cancellation() = field("pendingCancelConnect").get(manager) as AtomicBoolean

    private fun field(name: String) = manager.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun completion() = Completion(field("reconnectCancellation").type)

    private class StopProbe {
        val outcomes = CopyOnWriteArrayList<String>()
        val completed = CountDownLatch(1)

        fun record(outcome: String) {
            outcomes.add(outcome)
            completed.countDown()
        }
    }

    /** Use the manager's runtime future class, including Android Retrofix's rewritten class. */
    private class Completion(private val type: Class<*>) {
        val api: Any = type.getDeclaredConstructor().newInstance()

        fun succeed() {
            type.getMethod("complete", Any::class.java).invoke(api, null)
        }

        fun fail(error: Throwable) {
            type.getMethod("completeExceptionally", Throwable::class.java).invoke(api, error)
        }

        fun isDone(): Boolean = type.getMethod("isDone").invoke(api) as Boolean
    }

    private inner class RecordingClient {
        val state = AtomicReference(MqttClientState.CONNECTED)
        val event = completion()
        val shutdown = completion()
        val shutdownStarted = CountDownLatch(1)
        var publishBuilderFailure: RuntimeException? = null
        var disconnectBuilderFailure: RuntimeException? = null
        private val calls = Collections.synchronizedList(mutableListOf<String>())

        val api: Mqtt5AsyncClient = Proxy.newProxyInstance(
            Mqtt5AsyncClient::class.java.classLoader,
            arrayOf(Mqtt5AsyncClient::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "getState" -> state.get()
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordingShutdownMqttClient"
                "publishWith" -> {
                    calls.add("publish.build")
                    publishBuilderFailure?.let { throw it }
                    builder(method.returnType, "publish")
                }
                "disconnectWith" -> {
                    calls.add("disconnect.build")
                    disconnectBuilderFailure?.let { throw it }
                    builder(method.returnType, "disconnect")
                }
                else -> throw AssertionError("Unexpected MQTT client operation: ${method.name}")
            }
        } as Mqtt5AsyncClient

        fun operations(): List<String> = synchronized(calls) { calls.toList() }

        fun count(operation: String): Int = synchronized(calls) { calls.count { it == operation } }

        fun finishShutdown() {
            state.set(MqttClientState.DISCONNECTED)
            shutdown.succeed()
        }

        private fun builder(type: Class<*>, operation: String, parent: Any? = null): Any {
            check(type.isInterface) { "Expected a fluent MQTT builder interface: $type" }
            // topic() returns Send.Complete; a proxy for Send alone fails that runtime cast.
            val proxyType = type.declaredClasses.singleOrNull {
                it.simpleName == "Complete" && type.isAssignableFrom(it)
            } ?: type
            return Proxy.newProxyInstance(proxyType.classLoader, arrayOf(proxyType)) { proxy, method, arguments ->
                when (method.name) {
                    "equals" -> proxy === arguments?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "RecordingMqttBuilder/$operation"
                    "topic", "correlationData", "qos", "retain", "payloadFormatIndicator",
                    "contentType", "payload", "add" -> proxy
                    "userProperties" -> builder(method.returnType, operation, parent = proxy)
                    "applyUserProperties" -> requireNotNull(parent)
                    "send" -> {
                        check(parent == null) { "Nested user-property builder must not send" }
                        calls.add("$operation.send")
                        if (operation == "publish") {
                            event.api
                        } else {
                            shutdownStarted.countDown()
                            shutdown.api
                        }
                    }
                    else -> throw AssertionError("Unexpected MQTT builder operation: ${method.name}")
                }
            }
        }
    }
}
