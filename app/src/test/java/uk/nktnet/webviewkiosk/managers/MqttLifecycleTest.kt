package uk.nktnet.webviewkiosk.managers

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.config.mqtt.MqttConfig
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent.DisconnectCause
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real manager's pending-connect/disconnect coordination without a broker. */
@RunWith(RobolectricTestRunner::class)
class MqttLifecycleTest {
    private val manager = MqttManager
    private val state = AtomicReference(MqttClientState.DISCONNECTED)
    private val calledClientMethods = mutableListOf<String>()
    private val fieldsToRestore = listOf(
        "client", "config", "pendingDisconnect", "disconnectRequestId",
        "reconnectCancellation", "configurationError",
    )
    private lateinit var savedFields: Map<String, Any?>
    private var savedPendingCancellation = false
    private lateinit var fakeClient: Mqtt5AsyncClient

    @Before
    fun setUp() {
        savedFields = fieldsToRestore.associateWith { field(it).get(manager) }
        val pendingCancellation = field("pendingCancelConnect").get(manager) as AtomicBoolean
        savedPendingCancellation = pendingCancellation.getAndSet(false)
        val context = RuntimeEnvironment.getApplication()
        manager.updateConfig(context, rebuildClient = false)
        val config = field("config").get(manager) as MqttConfig
        field("config").set(manager, config.copy(enabled = true))
        fakeClient = Proxy.newProxyInstance(
            Mqtt5AsyncClient::class.java.classLoader,
            arrayOf(Mqtt5AsyncClient::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getState" -> state.get()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "FakeConnectingMqttClient"
                else -> {
                    synchronized(calledClientMethods) { calledClientMethods.add(method.name) }
                    throw AssertionError("Unexpected MQTT client operation: ${method.name}")
                }
            }
        } as Mqtt5AsyncClient
        field("client").set(manager, fakeClient)
    }

    @After
    fun tearDown() {
        // A failed assertion must not leave a polling disconnect touching later tests.
        state.set(MqttClientState.DISCONNECTED)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (field("pendingDisconnect").get(manager) != null && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        savedFields.forEach { (name, value) -> field(name).set(manager, value) }
        (field("pendingCancelConnect").get(manager) as AtomicBoolean).set(savedPendingCancellation)
    }

    @Test
    fun cancellingAnIdleClientDoesNotStartADisconnect() {
        assertFalse(manager.cancelConnect())
        assertEquals(MqttClientState.DISCONNECTED, manager.getState())
        assertFalse(manager.isConnected())
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun cancellingAConnectingClientWaitsForItToBecomeDisconnected() {
        state.set(MqttClientState.CONNECTING)
        val finished = CountDownLatch(1)

        assertTrue(manager.cancelConnect())
        manager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT, onDisconnected = { finished.countDown() })
        assertFalse("Disconnect must wait for the connection attempt", finished.await(75, TimeUnit.MILLISECONDS))

        state.set(MqttClientState.DISCONNECTED)
        assertTrue("Pending disconnect never completed", finished.await(3, TimeUnit.SECONDS))
        assertFalse(manager.cancelConnect())
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun concurrentDisconnectCallersShareTheSamePendingOperation() {
        state.set(MqttClientState.CONNECTING)
        val callbacks = mutableListOf<String>()
        val finished = CountDownLatch(2)
        val cause = DisconnectCause.USER_INITIATED_DISCONNECT

        manager.disconnect(cause, onDisconnected = {
            synchronized(callbacks) { callbacks.add("first") }
            finished.countDown()
        })
        manager.disconnect(cause, onDisconnected = {
            synchronized(callbacks) { callbacks.add("second") }
            finished.countDown()
        })

        assertFalse(finished.await(75, TimeUnit.MILLISECONDS))
        state.set(MqttClientState.DISCONNECTED)
        assertTrue(finished.await(3, TimeUnit.SECONDS))
        assertEquals(setOf("first", "second"), synchronized(callbacks) { callbacks.toSet() })
        assertEquals(2, synchronized(callbacks) { callbacks.size })
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun laterStopCancelsAConnectQueuedBehindAnEarlierDisconnect() {
        state.set(MqttClientState.CONNECTING)
        val context = RuntimeEnvironment.getApplication()
        val stopped = CountDownLatch(2)
        val cancelled = CountDownLatch(1)
        val messages = mutableListOf<String?>()
        val cause = DisconnectCause.USER_INITIATED_DISCONNECT

        manager.disconnect(cause, onDisconnected = { stopped.countDown() })
        manager.connect(context, onError = {
            synchronized(messages) { messages.add(it) }
            cancelled.countDown()
        })
        manager.disconnect(cause, onDisconnected = { stopped.countDown() })

        state.set(MqttClientState.DISCONNECTED)
        assertTrue(stopped.await(3, TimeUnit.SECONDS))
        assertTrue(cancelled.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("MQTT connection cancelled."), synchronized(messages) { messages.toList() })
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun aLaterStopPreventsAQueuedRestart() {
        state.set(MqttClientState.CONNECTING)
        val stopped = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val errors = mutableListOf<String?>()
        val context = RuntimeEnvironment.getApplication()

        manager.restart(
            context,
            DisconnectCause.USER_INITIATED_RESTART,
            onError = {
                synchronized(errors) { errors.add(it) }
                cancelled.countDown()
            },
        )
        manager.disconnect(
            DisconnectCause.USER_INITIATED_SETTINGS_DISABLED,
            onDisconnected = { stopped.countDown() },
        )

        state.set(MqttClientState.DISCONNECTED)
        assertTrue(stopped.await(3, TimeUnit.SECONDS))
        assertTrue(cancelled.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("MQTT restart cancelled."), synchronized(errors) { errors.toList() })
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun configurationUpdateDoesNotReplaceClientDuringPendingConnectOrDisconnect() {
        val context = RuntimeEnvironment.getApplication()
        state.set(MqttClientState.CONNECTING)
        manager.updateConfig(context)
        assertSame(fakeClient, field("client").get(manager))

        val completed = CountDownLatch(1)
        manager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT, onDisconnected = { completed.countDown() })
        manager.updateConfig(context)
        assertSame(fakeClient, field("client").get(manager))

        state.set(MqttClientState.DISCONNECTED)
        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    @Test
    fun publishEventsAreIgnoredUntilTheClientIsFullyConnected() {
        for (disconnectedState in listOf(MqttClientState.DISCONNECTED, MqttClientState.CONNECTING)) {
            state.set(disconnectedState)
            manager.publishUrlChangedEvent("https://example.com")
            manager.publishScreenOnEvent()
            manager.publishUnlockEvent()
        }
        assertEquals(emptyList<String>(), calledClientMethods)
    }

    private fun field(name: String) = manager.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
