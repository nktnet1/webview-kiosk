package uk.nktnet.webviewkiosk.services

import android.app.Application
import android.app.Service
import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import com.hivemq.client.mqtt.MqttClientState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundToastCommand
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent.DisconnectCause
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Actual activity/Compose and service collectors, with real HiveMQ TCP deliveries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MqttActivityHandoffTest {
    private lateinit var context: Application
    private lateinit var settings: UserSettings
    private lateinit var broker: LoopbackMqttBroker
    private lateinit var connection: LoopbackMqttBroker.Connection
    private val activities = mutableListOf<ActivityHost>()
    private var serviceController: ServiceController<MqttForegroundService>? = null
    private lateinit var savedFields: Map<String, Any?>
    private var savedCancellation = false
    private var savedMonitoringJob: Job? = null
    private var savedMonitoringStarted = false
    private val publications = mutableListOf<LoopbackMqttBroker.Publication>()
    private val appliedSettings = CopyOnWriteArrayList<RemoteMessage<InboundSettingsMessage>>()
    private val observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        savedFields = listOf("client", "config", "configurationError", "pendingDisconnect",
            "disconnectRequestId", "reconnectCancellation")
            .associateWith { mqttField(it).get(MqttManager) }
        savedCancellation = (mqttField("pendingCancelConnect").get(MqttManager) as AtomicBoolean)
            .getAndSet(false)
        listOf("client", "config", "configurationError", "pendingDisconnect")
            .forEach { mqttField(it).set(MqttManager, null) }
        mqttField("disconnectRequestId").set(MqttManager, 0L)
        mqttField("reconnectCancellation").let {
            it.set(MqttManager, it.type.getConstructor().newInstance())
        }
        savedMonitoringJob = lockField("monitoringJob").get(LockStateSingleton) as Job?
        savedMonitoringStarted = lockField("isStarted").getBoolean(LockStateSingleton)
        MqttManager.clearLogs()
        broker = LoopbackMqttBroker()
        settings = UserSettings(context).apply {
            mqttEnabled = true
            mqttUseForegroundService = false
            mqttServerHost = "127.0.0.1"
            mqttServerPort = broker.port
            mqttClientId = "activity-handoff-test"
            mqttUseTls = false
            mqttUseWebSocket = false
            mqttAutomaticReconnect = false
            mqttConnectTimeout = 5
            mqttSocketConnectTimeout = 5
            mqttPublishEventTopic = "handoff/events"
            mqttPublishEventQos = MqttQosOption.AT_LEAST_ONCE
            mqttPublishResponseTopic = "handoff/responses"
            mqttPublishResponseQos = MqttQosOption.AT_LEAST_ONCE
            mqttSubscribeCommandTopic = "handoff/commands"
            mqttSubscribeSettingsTopic = "handoff/settings"
            mqttSubscribeRequestTopic = "handoff/requests"
            homeUrl = "about:blank"
            supportPdfRendering = false
            lockOnLaunch = false
            resetOnLaunch = false
            resetOnInactivitySeconds = 0
        }
        observationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsAppliedFlow.collect { appliedSettings.add(it) }
        }
    }

    @After
    fun tearDown() {
        try {
            activities.asReversed().forEach { it.destroy() }
            serviceController?.destroy()
            MqttManager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT)
            if (::broker.isInitialized) {
                broker.close()
                awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
                awaitSubscriptions(0)
            }
        } finally {
            observationScope.cancel()
            if (::savedFields.isInitialized) {
                savedFields.forEach { (name, value) -> mqttField(name).set(MqttManager, value) }
                (mqttField("pendingCancelConnect").get(MqttManager) as AtomicBoolean)
                    .set(savedCancellation)
            }
            val monitoringJob = lockField("monitoringJob").get(LockStateSingleton) as Job?
            if (monitoringJob !== savedMonitoringJob) monitoringJob?.cancel()
            lockField("monitoringJob").set(LockStateSingleton, savedMonitoringJob)
            lockField("isStarted").setBoolean(LockStateSingleton, savedMonitoringStarted)
            ToastManager.cancel()
        }
    }

    @Test
    fun actualActivityCollectorsReplyToARealMqttRequestWithoutAService() {
        createActivity()
        connect()

        publishRequest("activity-request")
        awaitResponse("activity-request")

        assertSubscriptions(1)
        assertEquals(1, responses("activity-request").size)
        assertEquals(1, broker.connectionCount)
    }

    @Test
    fun firstTransportDeliveriesAreHandledBeforeTheActivityUiComposes() {
        val host = createActivity(visible = false)
        assertSubscriptions(1)
        connect()

        publishSettings("before-ui")
        publishRequest("before-ui")
        publishToastAndAwait("before-ui-toast")
        awaitSettings("before-ui")
        awaitResponse("before-ui")
        assertEquals(1, appliedSettings.count { it.message.messageId == "before-ui" })
        assertEquals(1, responses("before-ui").size)
        assertTrue(ShadowToast.showedToast("before-ui-toast"))

        host.controller.visible()
        pumpFrames()
        assertSubscriptions(1)
        assertBatchHandledOnce("after-ui")
    }

    @Test
    fun acceptedServiceTakesOverWithBothActualHostsAliveAndNoDuplicateDeliveries() {
        createActivity()
        connect()
        assertBatchHandledOnce("activity")

        settings.mqttUseForegroundService = true
        startService()
        awaitSubscriptions(2)
        repeat(3) {
            assertEquals(Service.START_STICKY, serviceController!!.get().onStartCommand(null, 0, it + 2))
        }
        assertBatchHandledOnce("service")

        assertSubscriptions(2)
        assertConnectionReused()
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun disablingForegroundModeHandsMessagesBackToTheExistingActivity() {
        settings.mqttUseForegroundService = true
        createActivity()
        connect()
        startService()
        awaitSubscriptions(2)
        assertBatchHandledOnce("service")

        settings.mqttUseForegroundService = false
        assertEquals(Service.START_NOT_STICKY,
            serviceController!!.get().onStartCommand(null, 0, 2))
        awaitSubscriptions(1)
        assertBatchHandledOnce("activity")

        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertConnectionReused()
    }

    @Test
    fun stoppingAndRestartingTheActivityKeepsAnAcceptedServiceConnectionAndOnePairOfCollectors() {
        settings.mqttUseForegroundService = true
        val host = createActivity()
        connect()
        startService()
        awaitSubscriptions(2)

        host.stop()
        assertBatchHandledOnce("stopped")
        host.controller.start().resume()
        pumpFrames()
        assertBatchHandledOnce("restarted")

        assertSubscriptions(2)
        assertConnectionReused()
    }

    @Test
    @Config(sdk = [28, 29])
    fun destroyingTheLastActivityKeepsTheOptedInServiceAndConnectionAlive() {
        settings.mqttUseForegroundService = true
        val host = createActivity()
        connect()
        startService()
        awaitSubscriptions(2)
        clearStoppedServiceIntents()

        host.destroy()
        awaitSubscriptions(1)
        assertNull(shadowOf(context).nextStoppedService)
        assertBatchHandledOnce("destroyed")

        assertConnectionReused()
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun destroyingTheActivityDuringConnectDoesNotCancelTheServiceHandshake() {
        settings.mqttUseForegroundService = true
        val host = createActivity()
        connection = broker.awaitConnection()
        startService()
        awaitSubscriptions(2)
        clearStoppedServiceIntents()

        host.destroy()
        awaitSubscriptions(1)
        assertNull(shadowOf(context).nextStoppedService)
        connection.acknowledgeConnect()
        awaitCondition { connection.subscriptions.size == 3 }
        assertBatchHandledOnce("pending-connect")

        assertConnectionReused()
    }

    @Test
    fun configurationRecreationSubscribesBeforeReusingTheExistingConnection() {
        val oldHost = createActivity()
        connect()
        ReflectionHelpers.setField(oldHost.controller.get(), "mChangingConfigurations", true)
        oldHost.destroy()
        awaitSubscriptions(0)
        assertTrue(MqttManager.isConnected())

        val replacement = createActivity(visible = false)
        assertSubscriptions(1)
        assertBatchHandledOnce("replacement-before-ui")
        replacement.controller.visible()
        pumpFrames()

        assertSubscriptions(1)
        assertConnectionReused()
    }

    @Test
    fun aDisabledActivityDoesNotClaimQueuedSettingsRequestsOrNativeCommands() {
        val host = createActivity()
        connect()
        settings.mqttEnabled = false

        assertInactiveDeliveriesAreUnclaimed("disabled", RemoteMessage.Source.MQTT)
        settings.mqttUseForegroundService = true
        host.stop()
        awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
    }

    @Test
    fun aStoppedActivityWithoutAServiceDoesNotClaimQueuedDeliveries() {
        val host = createActivity()
        connect()
        host.stop()

        assertInactiveDeliveriesAreUnclaimed("stopped", RemoteMessage.Source.MQTT)
    }

    @Test
    fun activityMqttCollectorsLeaveUnifiedPushEnvelopesUnclaimed() {
        createActivity()
        connect()

        assertInactiveDeliveriesAreUnclaimed("unifiedpush", RemoteMessage.Source.UNIFIEDPUSH)
    }

    @Test
    fun repeatedStartsSubscribeOnceAndDoNotDuplicateActivityDeliveries() {
        val host = createActivity()
        connect()
        repeat(3) { i ->
            // Preserve the connection across this stop so subscription ownership is isolated.
            ReflectionHelpers.setField(host.controller.get(), "mChangingConfigurations", true)
            host.stop()
            host.controller.start().resume()
            ReflectionHelpers.setField(host.controller.get(), "mChangingConfigurations", false)
            pumpFrames()
            assertBatchHandledOnce("restart-$i")
            assertSubscriptions(1)
        }

        assertConnectionReused()
    }

    @Test
    fun destroyingANonForegroundActivityDisconnectsAndRemovesItsCommandHost() {
        val initialCommandHosts = commandHostCount()
        val host = createActivity()
        connect()
        assertEquals(initialCommandHosts + 1, commandHostCount())
        val toastCount = ShadowToast.shownToastCount()

        host.destroy()
        assertEquals(initialCommandHosts, commandHostCount())
        awaitSubscriptions(0)
        awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
        dispatchToastAndAwait("after-destroy", RemoteMessage.Source.MQTT)

        assertEquals(toastCount, ShadowToast.shownToastCount())
    }

    @Test
    fun overlappingActivitiesShareOneClaimAndDestroyingTheOldHostKeepsTheNewHost() {
        val oldHost = createActivity()
        connect()
        createActivity()
        awaitSubscriptions(2)
        assertBatchHandledOnce("overlap")

        oldHost.destroy()
        awaitSubscriptions(1)
        assertBatchHandledOnce("remaining")

        assertConnectionReused()
    }

    private fun createActivity(visible: Boolean = true): ActivityHost {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val content = controller.get().findViewById<ViewGroup>(android.R.id.content)
        val view = content.getChildAt(0) as ComposeView
        val frameClock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + frameClock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setParentCompositionContext(recomposer)
        val host = ActivityHost(controller, view, frameClock, scope, recomposer)
        activities.add(host)
        controller.start().resume()
        if (visible) {
            controller.visible()
            pumpFrames()
        }
        return host
    }

    private fun connect() {
        connection = broker.awaitConnection()
        connection.acknowledgeConnect()
        awaitCondition { connection.subscriptions.size == 3 }
        assertTrue(MqttManager.isConnected())
    }

    private fun startService() {
        check(serviceController == null)
        serviceController = Robolectric.buildService(MqttForegroundService::class.java).create()
        assertEquals(Service.START_STICKY, serviceController!!.get().onStartCommand(null, 0, 1))
    }

    private fun assertConnectionReused() {
        assertTrue(MqttManager.isConnected())
        assertEquals(1, broker.connectionCount)
        assertEquals(3, connection.subscriptionCount.get())
    }

    private fun publishRequest(id: String) {
        connection.publish("handoff/requests", JSONObject()
            .put("requestType", "get_settings").put("messageId", id).toString())
    }

    private fun publishSettings(id: String) {
        connection.publish("handoff/settings", JSONObject()
            .put("messageId", id).put("showToast", false).put("reloadActivity", false)
            .put("data", JSONObject().put("settings",
                JSONObject().put(UserSettingsKeys.WebContent.HOME_URL, "https://example.test/$id")))
            .toString())
    }

    private fun assertBatchHandledOnce(prefix: String) {
        val ids = (0..3).map { "$prefix-$it" }
        ids.forEach { id ->
            publishSettings(id)
            publishRequest(id)
        }
        ids.forEach { awaitSettings(it); awaitResponse(it) }
        val toastCount = ShadowToast.shownToastCount()
        publishToastAndAwait("$prefix-toast")
        // Drain the main-thread activity collectors after the eligible handlers finish.
        pumpFrames()
        assertEquals(ids, appliedSettings.filter { it.message.messageId in ids }
            .map { it.message.messageId })
        ids.forEach { id ->
            assertEquals("settings $id", 1, appliedSettings.count { it.message.messageId == id })
            assertEquals("response $id", 1, responses(id).size)
        }
        assertTrue(appliedSettings.filter { it.message.messageId in ids }
            .all { it.source == RemoteMessage.Source.MQTT })
        assertEquals("https://example.test/${ids.last()}", settings.homeUrl)
        assertEquals(toastCount + 1, ShadowToast.shownToastCount())
        assertTrue(ShadowToast.showedToast("$prefix-toast"))
    }

    private fun publishToastAndAwait(id: String) = runBlocking {
        val dispatched = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.commandsFlow.first { it.message.messageId == id }
        }
        try {
            connection.publish("handoff/commands", JSONObject()
                .put("command", "toast").put("messageId", id).put("interact", false)
                .put("data", JSONObject().put("message", id)).toString())
            awaitCondition { dispatched.isCompleted }
            assertEquals(RemoteMessage.Source.MQTT, dispatched.await().source)
            pumpFrames()
        } finally {
            dispatched.cancel()
        }
    }

    private fun dispatchToastAndAwait(id: String, source: RemoteMessage.Source) = runBlocking {
        val dispatched = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.commandsFlow.first { it.message.messageId == id }
        }
        try {
            RemoteMessageManager.emitCommand(InboundToastCommand(
                messageId = id, interact = false, data = InboundToastCommand.ToastData(id)
            ), source)
            awaitCondition { dispatched.isCompleted }
            assertEquals(source, dispatched.await().source)
        } finally {
            dispatched.cancel()
        }
    }

    private fun assertInactiveDeliveriesAreUnclaimed(id: String, source: RemoteMessage.Source) {
        val initialHome = settings.homeUrl
        val initialToasts = ShadowToast.shownToastCount()
        val settingsEnvelope = RemoteMessage(InboundSettingsMessage(
            messageId = id, showToast = false, reloadActivity = false,
            data = InboundSettingsMessage.SettingsUpdateData(buildJsonObject {
                put(UserSettingsKeys.WebContent.HOME_URL, "https://example.test/rejected")
            })
        ), source)
        val requestEnvelope = RemoteMessage<InboundRequestMessage>(
            InboundSettingsRequest(messageId = id), source
        )
        // Inject an already-decoded delivery to test a late queued event after stop/disable.
        runBlocking {
            (RemoteMessageManager.settingsFlow as MutableSharedFlow<RemoteMessage<InboundSettingsMessage>>)
                .emit(settingsEnvelope)
            (RemoteMessageManager.requestsFlow as MutableSharedFlow<RemoteMessage<InboundRequestMessage>>)
                .emit(requestEnvelope)
        }
        pumpFrames()
        dispatchToastAndAwait("$id-toast", source)

        assertTrue("inactive settings must stay unclaimed", settingsEnvelope.tryClaim())
        assertTrue("inactive request must stay unclaimed", requestEnvelope.tryClaim())
        assertEquals(initialHome, settings.homeUrl)
        assertEquals(initialToasts, ShadowToast.shownToastCount())
        assertTrue(appliedSettings.none { it.message.messageId == id })
        assertTrue(responses(id).isEmpty())
    }

    private fun responses(id: String): List<LoopbackMqttBroker.Publication> {
        connection.publications.drainTo(publications)
        return publications.filter {
            it.topic == "handoff/responses"
                && JSONObject(it.payload).optString("requestMessageId") == id
        }
    }

    private fun awaitResponse(id: String) = awaitCondition { responses(id).isNotEmpty() }

    private fun awaitSettings(id: String) = awaitCondition {
        appliedSettings.any { it.message.messageId == id }
    }

    private fun assertSubscriptions(count: Int) {
        assertEquals("settings collectors", count,
            (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertEquals("request collectors", count,
            (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value)
    }

    private fun awaitSubscriptions(count: Int) = awaitCondition {
        (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value == count
            && (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value == count
    }

    private fun clearStoppedServiceIntents() {
        while (shadowOf(context).nextStoppedService != null) { /* drain prior onStart cleanup */ }
    }

    private fun pumpFrames() {
        repeat(6) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            activities.filter { !it.destroyed }.forEach { it.nextFrame() }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        try {
            runBlocking {
                withTimeout(5_000) {
                    while (!condition()) {
                        broker.assertHealthy()
                        pumpFrames()
                        delay(1)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("MQTT handoff timed out; state=${MqttManager.getState()}; logs=${MqttManager.debugLogHistory}", e)
        }
    }

    private fun mqttField(name: String) = MqttManager.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }

    private fun commandHostCount() =
        (RemoteMessageManager.javaClass.getDeclaredField("mqttCommandHosts")
            .apply { isAccessible = true }.get(RemoteMessageManager) as Collection<*>).size

    private fun lockField(name: String) = LockStateSingleton.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }

    private class ActivityHost(
        val controller: ActivityController<MainActivity>,
        private val view: ComposeView,
        private val frameClock: BroadcastFrameClock,
        private val scope: CoroutineScope,
        private val recomposer: Recomposer,
    ) {
        var destroyed = false
            private set
        private var frameNanos = 0L

        fun nextFrame() {
            frameNanos += 16_000_000L
            frameClock.sendFrame(frameNanos)
        }

        fun stop() {
            if (controller.get().lifecycle.currentState == Lifecycle.State.RESUMED) controller.pause()
            if (controller.get().lifecycle.currentState == Lifecycle.State.STARTED) controller.stop()
        }

        fun destroy() {
            if (destroyed) return
            stop()
            controller.destroy()
            view.disposeComposition()
            recomposer.cancel()
            scope.cancel()
            destroyed = true
        }
    }
}
