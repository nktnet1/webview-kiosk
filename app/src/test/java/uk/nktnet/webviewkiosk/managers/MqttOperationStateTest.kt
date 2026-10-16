package uk.nktnet.webviewkiosk.managers

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
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
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundErrorRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLaunchablePackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLockTaskPackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.utils.WebviewKioskStatus

/**
 * Checks event, response and subscription entry points against the manager's current
 * lifecycle.
 */
@RunWith(RobolectricTestRunner::class)
class MqttOperationStateTest {
    private val manager = MqttManager
    private val fieldsToRestore = listOf("client", "config", "pendingDisconnect")
    private lateinit var savedFields: Map<String, Any?>
    private var savedCancellation = false
    private lateinit var scopeJob: Job
    private lateinit var savedJobs: Set<Job>

    @Before
    fun setUp() {
        savedFields = fieldsToRestore.associateWith { field(it).get(manager) }
        val scope = field("scope").get(manager) as CoroutineScope
        scopeJob = requireNotNull(scope.coroutineContext[Job])
        savedJobs = scopeJob.children.toSet()
        savedCancellation = cancellation().getAndSet(false)
        field("pendingDisconnect").set(manager, null)
        manager.updateConfig(
            RuntimeEnvironment.getApplication(),
            rebuildClient = false,
        )
        val config = field("config").get(manager) as MqttConfig
        field("config")
            .set(
                manager,
                config.copy(
                    enabled = true,
                    publishEventTopic = "wk/events",
                    publishResponseTopic = "wk/responses",
                    subscribeCommandTopic = "wk/commands",
                    subscribeSettingsTopic = "wk/settings",
                    subscribeRequestTopic = "wk/requests",
                ),
            )
    }

    @After
    fun tearDown() {
        try {
            // Join only this test's log-emission jobs before restoring singleton
            // fields.
            runBlocking {
                withTimeout(3_000) {
                    while (true) {
                        val jobs =
                            scopeJob.children.filterNot { it in savedJobs }.toList()
                        if (jobs.isEmpty()) {
                            break
                        }
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

    @Test
    fun aReadyConnectedClientSendsEventsResponsesAndAttemptsAllThreeSubscriptions() {
        assertOperationsAreReady(installClient())
    }

    @Test
    fun disabledMqttSkipsOperationsEvenWhenTheClientIsConnected() {
        val client = installClient()
        setEnabled(false)

        assertOperationsAreSkipped(client)
    }

    @Test
    fun aMissingClientSkipsOperations() {
        val unusedClient = installClient()
        field("client").set(manager, null)

        assertOperationsAreSkipped(unusedClient)
    }

    @Test
    fun everyNonConnectedStateSkipsOperations() {
        val client = installClient()
        for (state in MqttClientState.values().filterNot { it.isConnected }) {
            client.state.set(state)

            assertOperationsAreSkipped(client, state.name)
        }
    }

    @Test
    fun pendingCancellationSkipsOperationsOnAStillConnectedClient() {
        val client = installClient()
        cancellation().set(true)

        assertOperationsAreSkipped(client)
    }

    @Test
    fun pendingDisconnectIndependentlySkipsOperationsOnAStillConnectedClient() {
        val client = installClient()
        setPendingDisconnect(client)
        assertFalse(cancellation().get())

        assertOperationsAreSkipped(client)
    }

    @Test
    fun disablingMqttDuringTheStateReadSkipsOperations() {
        val client = installClient()
        for (operation in operations()) {
            setEnabled(true)
            client.onStateRead = { setEnabled(false) }

            operation.run()

            assertEquals(operation.name, emptyList<String>(), client.operations())
        }
    }

    @Test
    fun cancellationOrDisconnectStartedDuringTheStateReadSkipsOperations() {
        val client = installClient()
        val transitions =
            listOf<Pair<String, () -> Unit>>(
                "cancel" to { cancellation().set(true) },
                "disconnect" to { setPendingDisconnect(client) },
            )
        for ((transitionName, transition) in transitions) {
            for (operation in operations()) {
                cancellation().set(false)
                field("pendingDisconnect").set(manager, null)
                client.onStateRead = transition

                operation.run()

                assertEquals(
                    "$transitionName / ${operation.name}",
                    emptyList<String>(),
                    client.operations(),
                )
            }
        }
    }

    @Test
    fun aClientReplacedDuringTheStateReadIsNotUsedByAnyOperation() {
        val original = installClient()
        val replacement = RecordingClient()
        for (operation in operations()) {
            field("client").set(manager, original.api)
            original.onStateRead = { field("client").set(manager, replacement.api) }

            operation.run()

            assertEquals(operation.name, emptyList<String>(), original.operations())
            assertSame(operation.name, replacement.api, field("client").get(manager))
            // A separate subscription batch on the replacement is still permitted.
            replacement.clearOperations()
            operation.run()
            assertEquals(
                operation.name,
                operation.expectedCalls,
                replacement.operations(),
            )
            replacement.clearOperations()
        }
    }

    @Test
    fun subscriptionSetupCannotSwitchToReplacementAfterItsFirstTopic() {
        val original = installClient()
        val replacement = RecordingClient()
        original.onSubscribeAttempt = {
            field("client").set(manager, replacement.api)
        }

        subscribeToTopics()

        assertEquals(listOf("subscribe.build"), original.operations())
        assertEquals(emptyList<String>(), replacement.operations())
        assertSame(replacement.api, field("client").get(manager))
    }

    @Test
    fun subscriptionSetupCannotSwitchToReplacementDuringTheInitialStateRead() {
        val original = installClient()
        val replacement = RecordingClient()
        original.onStateRead = { field("client").set(manager, replacement.api) }

        subscribeToTopics()

        assertEquals(emptyList<String>(), original.operations())
        assertEquals(emptyList<String>(), replacement.operations())
        assertSame(replacement.api, field("client").get(manager))
    }

    @Test
    fun currentSubscriptionAcknowledgementsAreLoggedOnce() {
        val client = installClient().apply { acceptSubscriptions = true }
        val successesBefore = logCount("subscribe success")
        val errorsBefore = logCount("subscribe error")

        subscribeToTopics()
        assertEquals(3, client.subscriptionAcks.size)
        assertEquals(3, client.operations().count { it == "subscribe.send" })

        assertTrue(
            client.subscriptionAcks[0].complete(
                subAck(Mqtt5SubAckReasonCode.GRANTED_QOS_0)
            )
        )
        assertFalse(client.subscriptionAcks[0].complete(null))
        assertTrue(
            client.subscriptionAcks[1].completeExceptionally(
                IOException("suback rejected")
            )
        )

        assertEquals(successesBefore + 1, logCount("subscribe success"))
        assertEquals(errorsBefore + 1, logCount("subscribe error"))
    }

    @Test
    fun rejectedSubAckReasonCodeIsNotLoggedAsASuccess() {
        val client = installClient().apply { acceptSubscriptions = true }
        val successesBefore = logCount("subscribe success")
        val errorsBefore = logCount("subscribe error")
        subscribeToTopics()

        client.subscriptionAcks[0].complete(
            subAck(Mqtt5SubAckReasonCode.NOT_AUTHORIZED)
        )
        client.subscriptionAcks[1].complete(
            subAck(Mqtt5SubAckReasonCode.GRANTED_QOS_1)
        )

        assertEquals(successesBefore + 1, logCount("subscribe success"))
        assertEquals(errorsBefore + 1, logCount("subscribe error"))
        assertTrue(
            manager.debugLogHistory.any {
                it.tag == "subscribe error" &&
                    it.message.orEmpty().contains("NOT_AUTHORIZED")
            }
        )
    }

    @Test
    fun oldSessionSubAckIsDiscardedAfterTheSameClientReconnects() {
        val client = installClient().apply { acceptSubscriptions = true }
        val epoch = field("subscriptionEpoch").get(manager) as AtomicLong
        val beforeEpoch = epoch.get()
        try {
            subscribeToTopics()
            val successesBefore = logCount("subscribe success")
            val errorsBefore = logCount("subscribe error")

            // An accepted reconnect reuses the client object but advances its
            // session.
            epoch.incrementAndGet()
            client.subscriptionAcks[0].complete(
                subAck(Mqtt5SubAckReasonCode.GRANTED_QOS_0)
            )
            client.subscriptionAcks[1].complete(
                subAck(Mqtt5SubAckReasonCode.NOT_AUTHORIZED)
            )
            assertEquals(successesBefore, logCount("subscribe success"))
            assertEquals(errorsBefore, logCount("subscribe error"))

            subscribeToTopics()
            client.subscriptionAcks
                .last()
                .complete(subAck(Mqtt5SubAckReasonCode.GRANTED_QOS_0))
            assertEquals(successesBefore + 1, logCount("subscribe success"))
        } finally {
            epoch.set(beforeEpoch)
        }
    }

    @Test
    fun retiredSubscriptionAcknowledgementsCannotReportSuccessOrFailure() {
        val original = installClient().apply { acceptSubscriptions = true }
        val successesBefore = logCount("subscribe success")
        val errorsBefore = logCount("subscribe error")
        subscribeToTopics()
        assertEquals(3, original.subscriptionAcks.size)

        field("client").set(manager, RecordingClient().api)
        original.subscriptionAcks[0].complete(null)
        original.subscriptionAcks[1].completeExceptionally(
            IOException("late suback")
        )
        original.subscriptionAcks[2].complete(null)

        assertEquals(successesBefore, logCount("subscribe success"))
        assertEquals(errorsBefore, logCount("subscribe error"))
    }

    @Test
    fun subscriptionAcknowledgementsAreIgnoredDuringDisconnectOrDisable() {
        val client = installClient().apply { acceptSubscriptions = true }
        val successesBefore = logCount("subscribe success")
        val errorsBefore = logCount("subscribe error")
        subscribeToTopics()
        assertEquals(3, client.subscriptionAcks.size)

        setPendingDisconnect(client)
        client.subscriptionAcks[0].complete(null)
        field("pendingDisconnect").set(manager, null)
        setEnabled(false)
        client.subscriptionAcks[1].completeExceptionally(IOException("late suback"))
        cancellation().set(true)
        setEnabled(true)
        client.subscriptionAcks[2].complete(null)

        assertEquals(successesBefore, logCount("subscribe success"))
        assertEquals(errorsBefore, logCount("subscribe error"))
    }

    private fun subscribeToTopics() {
        manager.javaClass
            .getDeclaredMethod("subscribeToTopics")
            .apply { isAccessible = true }
            .invoke(manager)
    }

    private fun logCount(tag: String) =
        manager.debugLogHistory.count { it.tag == tag }

    @Test
    fun operationsResumeAfterCancellationAndDisconnectGuardsAreCleared() {
        val client = installClient()
        cancellation().set(true)
        setPendingDisconnect(client)
        assertOperationsAreSkipped(client)

        cancellation().set(false)
        assertOperationsAreSkipped(client)
        field("pendingDisconnect").set(manager, null)

        assertOperationsAreReady(client)
    }

    private fun assertOperationsAreSkipped(
        client: RecordingClient,
        reason: String = "guarded",
    ) {
        for (operation in operations()) {
            operation.run()
            assertEquals(
                "$reason / ${operation.name}",
                emptyList<String>(),
                client.operations(),
            )
        }
    }

    private fun assertOperationsAreReady(client: RecordingClient) {
        for (operation in operations()) {
            client.clearOperations()
            operation.run()
            assertEquals(
                operation.name,
                operation.expectedCalls,
                client.operations(),
            )
        }
    }

    private fun subAck(reason: Mqtt5SubAckReasonCode): Mqtt5SubAck =
        Proxy.newProxyInstance(
            Mqtt5SubAck::class.java.classLoader,
            arrayOf(Mqtt5SubAck::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getReasonCodes" -> listOf(reason)
                else ->
                    throw AssertionError(
                        "Unexpected SUBACK property: ${method.name}"
                    )
            }
        } as Mqtt5SubAck

    private class Operation(
        val name: String,
        val run: () -> Unit,
        val expectedCalls: List<String> = listOf("publish.build", "publish.send"),
    )

    private fun operations(): List<Operation> {
        val status =
            WebviewKioskStatus(
                currentUrl = "https://example.test/",
                isLocked = false,
                lockStateType = LockStateType.NONE,
                lastInteractionTime = 0,
                batteryPercentage = 80,
                appBrightnessPercentage = 100,
                systemBrightness = 255,
                isDeviceInteractive = true,
            )
        return listOf(
            Operation(
                "URL event",
                { manager.publishUrlChangedEvent(status.currentUrl) },
            ),
            Operation(
                "lock event",
                { manager.publishLockEvent(LockStateType.LOCK_TASK) },
            ),
            Operation("unlock event", { manager.publishUnlockEvent() }),
            Operation("foreground event", { manager.publishAppForegroundEvent() }),
            Operation("background event", { manager.publishAppBackgroundEvent() }),
            Operation("screen-on event", { manager.publishScreenOnEvent() }),
            Operation("screen-off event", { manager.publishScreenOffEvent() }),
            Operation("user-present event", { manager.publishUserPresentEvent() }),
            Operation("power-plugged event", { manager.publishPowerPluggedEvent() }),
            Operation(
                "power-unplugged event",
                { manager.publishPowerUnpluggedEvent() },
            ),
            Operation(
                "restrictions event",
                { manager.publishApplicationRestrictionsChangedEvent() },
            ),
            Operation(
                "status response",
                { manager.publishStatusResponse(InboundStatusRequest(), status) },
            ),
            Operation(
                "settings response",
                {
                    manager.publishSettingsResponse(
                        InboundSettingsRequest(),
                        JSONObject(),
                    )
                },
            ),
            Operation(
                "packages response",
                {
                    manager.publishLaunchablePackagesResponse(
                        InboundLaunchablePackagesRequest(),
                        listOf("example.app"),
                    )
                },
            ),
            Operation(
                "lock-task packages response",
                {
                    manager.publishLockTaskPermittedPackagesResponse(
                        InboundLockTaskPackagesRequest(),
                        listOf("example.app"),
                    )
                },
            ),
            Operation(
                "error response",
                {
                    manager.publishErrorResponse(
                        InboundErrorRequest(
                            payloadStr = "invalid",
                            error = "bad request",
                        )
                    )
                },
            ),
            Operation(
                "command, settings and request subscriptions",
                {
                    manager.javaClass
                        .getDeclaredMethod("subscribeToTopics")
                        .apply { isAccessible = true }
                        .invoke(manager)
                },
                List(3) { "subscribe.build" },
            ),
        )
    }

    private fun installClient() =
        RecordingClient().also { field("client").set(manager, it.api) }

    private fun setEnabled(enabled: Boolean) {
        val config = field("config").get(manager) as MqttConfig
        field("config").set(manager, config.copy(enabled = enabled))
    }

    private fun setPendingDisconnect(client: RecordingClient) {
        val pendingField = field("pendingDisconnect")
        // Install the real operation marker without starting shutdown or a
        // state-polling coroutine.
        val operation =
            pendingField.type
                .getDeclaredConstructor(Mqtt5AsyncClient::class.java)
                .apply { isAccessible = true }
                .newInstance(client.api)
        pendingField.set(manager, operation)
    }

    private fun cancellation() =
        field("pendingCancelConnect").get(manager) as AtomicBoolean

    private fun field(name: String) =
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun completedFuture(): Any {
        // Android Retrofix can rewrite the manager and MQTT API to java9
        // CompletableFuture.
        val type = field("reconnectCancellation").type
        return type.getDeclaredConstructor().newInstance().also {
            type.getMethod("complete", Any::class.java).invoke(it, null)
        }
    }

    private inner class RecordingClient {
        val state = AtomicReference(MqttClientState.CONNECTED)
        var onStateRead: (() -> Unit)? = null
        var onSubscribeAttempt: (() -> Unit)? = null
        var acceptSubscriptions = false
        val subscriptionAcks = mutableListOf<CompletableFuture<Any?>>()
        private val calls = mutableListOf<String>()

        val api: Mqtt5AsyncClient =
            Proxy.newProxyInstance(
                Mqtt5AsyncClient::class.java.classLoader,
                arrayOf(Mqtt5AsyncClient::class.java),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "getState" -> {
                        val onRead = onStateRead
                        onStateRead = null
                        onRead?.invoke()
                        state.get()
                    }
                    "equals" -> proxy === arguments?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "RecordingOperationMqttClient"
                    "publishWith" -> {
                        calls.add("publish.build")
                        builder(method.returnType, "publish")
                    }
                    "subscribeWith" -> {
                        calls.add("subscribe.build")
                        onSubscribeAttempt?.also { callback ->
                            onSubscribeAttempt = null
                            callback()
                        }
                        if (acceptSubscriptions) {
                            builder(method.returnType, "subscribe")
                        } else {
                            // State-gate cases stop at the first subscription
                            // builder method.
                            throw IllegalStateException(
                                "Subscription attempt recorded by state-gate fixture"
                            )
                        }
                    }
                    else ->
                        throw AssertionError(
                            "Unexpected MQTT client operation: ${method.name}"
                        )
                }
            } as Mqtt5AsyncClient

        fun operations(): List<String> = calls.toList()

        fun clearOperations() = calls.clear()

        private fun builder(
            type: Class<*>,
            operation: String,
            parent: Any? = null,
        ): Any {
            check(type.isInterface) {
                "Expected a fluent MQTT builder interface: $type"
            }
            // publish.topic() returns a more specific Complete stage.
            val proxyType =
                type.declaredClasses.singleOrNull {
                    it.simpleName == "Complete" && type.isAssignableFrom(it)
                } ?: type
            return Proxy.newProxyInstance(
                proxyType.classLoader,
                arrayOf(proxyType),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "equals" -> proxy === arguments?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "RecordingOperationMqttBuilder/$operation"
                    "topic",
                    "topicFilter",
                    "correlationData",
                    "qos",
                    "retain",
                    "retainHandling",
                    "retainAsPublished",
                    "noLocal",
                    "payloadFormatIndicator",
                    "contentType",
                    "payload",
                    "add" -> proxy
                    "callback" -> builder(method.returnType, operation)
                    "userProperties" ->
                        builder(method.returnType, operation, parent = proxy)
                    "applyUserProperties" -> requireNotNull(parent)
                    "send" -> {
                        check(parent == null) {
                            "Nested user-property builder must not send"
                        }
                        calls.add("$operation.send")
                        if (operation == "subscribe") {
                            CompletableFuture<Any?>().also(subscriptionAcks::add)
                        } else {
                            completedFuture()
                        }
                    }
                    else ->
                        throw AssertionError(
                            "Unexpected MQTT builder operation: ${method.name}"
                        )
                }
            }
        }
    }
}
