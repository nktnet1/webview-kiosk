package uk.nktnet.webviewkiosk.managers

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
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
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exercises connection-result transitions without relying on HiveMQ builders or a broker. */
@RunWith(RobolectricTestRunner::class)
class MqttConnectedCallbacksTest {
    private val manager = MqttManager
    private val originalFields = listOf(
        "client", "config", "pendingDisconnect", "disconnectRequestId",
        "reconnectCancellation", "configurationError",
    )
    private lateinit var savedFields: Map<String, Any?>
    private var savedCancellation = false

    @Before
    fun setUp() {
        savedFields = originalFields.associateWith { field(it).get(manager) }
        val cancellation = field("pendingCancelConnect").get(manager) as AtomicBoolean
        savedCancellation = cancellation.getAndSet(false)
        field("pendingDisconnect").set(manager, null)
        manager.updateConfig(RuntimeEnvironment.getApplication(), rebuildClient = false)
        val config = field("config").get(manager) as MqttConfig
        field("config").set(
            manager,
            config.copy(
                enabled = true,
                publishEventTopic = "wk/events",
                subscribeCommandTopic = "wk/commands",
                subscribeSettingsTopic = "wk/settings",
                subscribeRequestTopic = "wk/requests",
            ),
        )
    }

    @After
    fun tearDown() {
        savedFields.forEach { (name, value) -> field(name).set(manager, value) }
        (field("pendingCancelConnect").get(manager) as AtomicBoolean).set(savedCancellation)
    }

    @Test
    fun successfulCompletionRunsSubscriptionsBeforeNotifyingTheCaller() {
        val fake = FakeClient()
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        beginConnect(fake, callbacks)

        assertTrue(fake.operations().isEmpty())
        assertTrue(callbacks.isEmpty())

        fake.state.set(MqttClientState.CONNECTED)
        fake.connectResult.complete(null)

        assertEquals(listOf("connected"), callbacks)
        assertEquals(listOf("subscribe", "callback/connected"), fake.operations())
    }

    @Test
    fun successfulCompletionAfterCancellationNeverSubscribes() {
        val fake = FakeClient()
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        beginConnect(fake, callbacks)
        (field("pendingCancelConnect").get(manager) as AtomicBoolean).set(true)

        fake.state.set(MqttClientState.CONNECTED)
        fake.connectResult.complete(null)

        assertEquals(listOf("error:MQTT connection cancelled."), callbacks)
        assertFalse(fake.operations().contains("subscribe"))
    }

    @Test
    fun successfulCompletionForSupersededClientNeverTouchesReplacement() {
        val stale = FakeClient()
        val replacement = FakeClient(MqttClientState.CONNECTED)
        field("client").set(manager, stale.api)
        val callbacks = mutableListOf<String>()
        beginConnect(stale, callbacks)
        field("client").set(manager, replacement.api)

        stale.state.set(MqttClientState.CONNECTED)
        stale.connectResult.complete(null)

        assertEquals(listOf("error:MQTT connection cancelled."), callbacks)
        assertFalse(stale.operations().contains("subscribe"))
        assertTrue(replacement.operations().isEmpty())
        assertSame(replacement.api, field("client").get(manager))
    }

    @Test
    fun disablingMqttDuringConnectSuppressesTheLateSuccess() {
        val fake = FakeClient()
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        beginConnect(fake, callbacks)
        updateEnabled(false)

        fake.state.set(MqttClientState.CONNECTED)
        fake.connectResult.complete(null)

        assertEquals(listOf("error:MQTT connection cancelled."), callbacks)
        assertFalse(fake.operations().contains("subscribe"))
    }

    @Test
    fun successAcknowledgementWithoutConnectedClientStateIsRejected() {
        val fake = FakeClient(MqttClientState.CONNECTING)
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        beginConnect(fake, callbacks)

        fake.connectResult.complete(null)

        assertEquals(listOf("error:MQTT connection cancelled."), callbacks)
        assertFalse(fake.operations().contains("subscribe"))
    }

    @Test
    fun failedConnectCompletesWithAnErrorAndNoSubscriptions() {
        val fake = FakeClient()
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        beginConnect(fake, callbacks)

        fake.connectResult.completeExceptionally(IOException("broker rejected connection"))

        assertEquals(listOf("error:broker rejected connection"), callbacks)
        assertFalse(fake.operations().contains("subscribe"))
    }

    @Test
    fun connectingAnAlreadyConnectedClientDoesNotStartAnotherHandshake() {
        val fake = FakeClient(MqttClientState.CONNECTED)
        field("client").set(manager, fake.api)
        var called = 0
        val errors = mutableListOf<String?>()

        manager.connect(RuntimeEnvironment.getApplication(), onConnected = { called++ }, onError = errors::add)

        assertEquals(1, called)
        assertTrue(errors.isEmpty())
        assertTrue(fake.operations().isEmpty())
    }

    @Test
    fun readyClientRequiresMqttEnabledAndConnected() {
        val fake = FakeClient(MqttClientState.CONNECTED)
        field("client").set(manager, fake.api)
        val getReadyClient = manager.javaClass.getDeclaredMethod("getReadyClient").apply {
            isAccessible = true
        }

        assertSame(fake.api, getReadyClient.invoke(manager))
        updateEnabled(false)
        assertNull(getReadyClient.invoke(manager))
        updateEnabled(true)
        fake.state.set(MqttClientState.DISCONNECTED)
        assertNull(getReadyClient.invoke(manager))
        assertTrue(fake.operations().isEmpty())
    }

    @Test
    fun subscriptionExceptionReportsErrorWithoutCallingConnected() {
        val fake = FakeClient()
        field("client").set(manager, fake.api)
        val callbacks = mutableListOf<String>()
        fake.connectResult.whenComplete { _, failure ->
            manager.handleConnectResult(
                fake.api,
                failure,
                onConnected = { callbacks.add("connected") },
                onError = { callbacks.add("error:$it") },
                subscribe = { throw IOException("subscription failed") },
            )
        }

        fake.state.set(MqttClientState.CONNECTED)
        fake.connectResult.complete(null)

        assertEquals(listOf("error:subscription failed"), callbacks)
    }

    private fun updateEnabled(enabled: Boolean) {
        val previous = field("config").get(manager) as MqttConfig
        field("config").set(manager, previous.copy(enabled = enabled))
    }

    private fun beginConnect(fake: FakeClient, callbacks: MutableList<String>) {
        fake.connectResult.whenComplete { _, failure ->
            manager.handleConnectResult(
                fake.api,
                failure,
                onConnected = {
                    fake.record("callback/connected")
                    callbacks.add("connected")
                },
                onError = { message -> callbacks.add("error:$message") },
                subscribe = { fake.record("subscribe") },
            )
        }
    }

    private fun field(name: String) = manager.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private class FakeClient(initialState: MqttClientState = MqttClientState.CONNECTING) {
        val state = AtomicReference(initialState)
        val connectResult = CompletableFuture<Any?>()
        private val calls = Collections.synchronizedList(mutableListOf<String>())

        val api: Mqtt5AsyncClient = Proxy.newProxyInstance(
            Mqtt5AsyncClient::class.java.classLoader,
            arrayOf(Mqtt5AsyncClient::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "getState" -> state.get()
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordingMqttClient"
                else -> throw AssertionError("Unexpected MQTT client method: ${method.name}")
            }
        } as Mqtt5AsyncClient

        fun operations(): List<String> = synchronized(calls) { calls.toList() }

        fun record(operation: String) {
            calls.add(operation)
        }
    }
}
