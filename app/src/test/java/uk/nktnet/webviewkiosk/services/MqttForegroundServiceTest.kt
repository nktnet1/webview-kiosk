package uk.nktnet.webviewkiosk.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowPowerManager
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundClearHistoryCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsRequest
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source
import uk.nktnet.webviewkiosk.managers.ToastManager
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MqttForegroundServiceTest {
    private lateinit var context: Context
    private lateinit var userSettings: UserSettings
    private lateinit var settings: SystemSettings
    private lateinit var controller: ServiceController<MqttForegroundService>
    private lateinit var service: MqttForegroundService
    private var destroyed = false
    private lateinit var savedMqttFields: Map<String, Any?>
    private val publications = LinkedBlockingQueue<Pair<String, ByteArray>>()
    private val mqttState = AtomicReference(MqttClientState.CONNECTED)
    private val stateReadCount = AtomicInteger()
    private val stateReadGates = ConcurrentHashMap<Int, StateReadGate>()
    private val observedPollJobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        userSettings = UserSettings(context)
        userSettings.mqttEnabled = true
        userSettings.mqttUseForegroundService = true
        settings = SystemSettings(context)
        settings.historyStack = listOf(
            HistoryEntry("first", "https://example.com/first", 100),
            HistoryEntry("second", "https://example.com/second", 200),
        )
        settings.historyIndex = 1
        savedMqttFields = listOf("client", "config", "configurationError")
            .associateWith { mqttField(it).get(MqttManager) }
        MqttManager.updateConfig(context, rebuildClient = false)
        val client = Proxy.newProxyInstance(
            Mqtt5AsyncClient::class.java.classLoader,
            arrayOf(Mqtt5AsyncClient::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getState" -> {
                    val gate = stateReadGates[stateReadCount.incrementAndGet()]
                    val state = gate?.result ?: mqttState.get()
                    if (gate != null) {
                        gate.started.countDown()
                        check(gate.release.await(5, TimeUnit.SECONDS)) { "Status read was not released" }
                    }
                    state
                }
                "publishWith" -> publicationBuilder(method.returnType)
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "ServiceTestMqttClient"
                else -> throw AssertionError("Unexpected MQTT operation: ${method.name}")
            }
        } as Mqtt5AsyncClient
        mqttField("client").set(MqttManager, client)
        controller = Robolectric.buildService(MqttForegroundService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        currentPollJob()?.let { observedPollJobs.add(it) }
        if (!destroyed) controller.destroy()
        stateReadGates.values.forEach { it.release.countDown() }
        observedPollJobs.forEach(::awaitPollCompletion)
        awaitSubscriptions(0)
        savedMqttFields.forEach { (name, value) -> mqttField(name).set(MqttManager, value) }
        ToastManager.cancel()
    }

    @Test
    fun creationDoesNotAcquireResourcesBeforeAnAcceptedStart() {
        assertNoPowerOrReceiver()
    }

    @Test
    fun creationDoesNotTakeNativeCommandOwnership() {
        dispatchClearHistory()

        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun disabledMqttRejectsAStickyRestartWithoutLeavingResources() {
        userSettings.mqttEnabled = false

        assertEquals(Service.START_NOT_STICKY, start(7))
        assertEquals(7, shadowOf(service).stopSelfId)
        assertNoPowerOrReceiver()
    }

    @Test
    fun disabledForegroundSettingRejectsAStartWithoutLeavingResources() {
        userSettings.mqttUseForegroundService = false

        assertEquals(Service.START_NOT_STICKY, start(8))
        assertNoPowerOrReceiver()
    }

    @Test
    fun foregroundDenialReleasesResourcesBeforeDestroy() {
        shadowOf(service).setThrowInStartForeground(SecurityException("foreground denied"))

        assertEquals(Service.START_NOT_STICKY, start(9))
        assertEquals(9, shadowOf(service).stopSelfId)
        assertNoPowerOrReceiver()
        dispatchClearHistory()
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun acceptedNullIntentInitialisesTheChannelNotificationAndWakeLock() {
        assertEquals(Service.START_STICKY, start())

        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(CustomNotificationType.MQTT_SERVICE, shadowOf(service).lastForegroundNotificationId)
        assertEquals(CustomNotificationChannel.MqttService.ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notification.contentIntent)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(CustomNotificationChannel.MqttService.ID))
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(hasScreenReceiver())
        awaitSubscriptions(1)
    }

    @Test
    @Config(sdk = [29])
    fun nullIntentRestartAlsoUsesTheApi29ForegroundPath() {
        assertEquals(Service.START_STICKY, start())
        assertEquals(CustomNotificationChannel.MqttService.ID, shadowOf(service).lastForegroundNotification.channelId)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun repeatedStartsKeepOneWakeLockReceiverAndPairOfCollectors() {
        start()
        awaitSubscriptions(1)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()

        assertEquals(Service.START_STICKY, start(2))

        assertSame(wakeLock, ShadowPowerManager.getLatestWakeLock())
        assertEquals(1, screenReceiverCount())
        awaitSubscriptions(1)
        dispatchClearHistory()
        assertEquals(listOf("second"), settings.historyStack.map { it.id })
    }

    @Test
    fun acceptedServiceExecutesNativeCommandsWithoutAnActivity() {
        start()
        dispatchClearHistory()

        assertEquals(listOf("second"), settings.historyStack.map { it.id })
        assertEquals(0, settings.historyIndex)
    }

    @Test
    fun acceptedStartSubscribesBeforeReturningToTheCaller() {
        start()

        assertEquals(1, (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertEquals(1, (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value)
    }

    @Test
    fun aLaterEnabledStartCanReacquireResourcesAfterARejectedStart() {
        userSettings.mqttEnabled = false
        assertEquals(Service.START_NOT_STICKY, start())
        userSettings.mqttEnabled = true

        assertEquals(Service.START_STICKY, start(2))
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        awaitSubscriptions(1)
        dispatchClearHistory()
        assertEquals(listOf("second"), settings.historyStack.map { it.id })
    }

    @Test
    fun changingToActivityOwnershipImmediatelyDisablesTheServiceCommandHost() {
        start()
        userSettings.mqttUseForegroundService = false
        dispatchClearHistory()

        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun disablingMqttImmediatelyDisablesTheServiceCommandHost() {
        start()
        userSettings.mqttEnabled = false
        dispatchClearHistory()

        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun disabledRepeatedStartReleasesAnAlreadyRunningServiceBeforeDestroy() {
        start()
        userSettings.mqttUseForegroundService = false

        assertEquals(Service.START_NOT_STICKY, start(3))
        assertNoPowerOrReceiver()
        awaitSubscriptions(0)
        assertTrue(shadowOf(service).isForegroundStopped)
        dispatchClearHistory()
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun denialOfARepeatedStartReleasesThePreviousStartBeforeDestroy() {
        start()
        shadowOf(service).setThrowInStartForeground(SecurityException("foreground revoked"))

        assertEquals(Service.START_NOT_STICKY, start(4))
        assertNoPowerOrReceiver()
        awaitSubscriptions(0)
        dispatchClearHistory()
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun destructionReleasesPowerReceiversCollectorsAndNativeOwnership() {
        start()
        controller.destroy()
        destroyed = true

        assertNoPowerOrReceiver()
        awaitSubscriptions(0)
        dispatchClearHistory()
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun acceptedServiceAppliesSettingsThroughItsActualCollector() = runBlocking {
        start()
        awaitSubscriptions(1)
        val message = InboundSettingsMessage(
            messageId = "service-settings",
            reloadActivity = false,
            showToast = false,
            data = InboundSettingsMessage.SettingsUpdateData(buildJsonObject {
                put(UserSettingsKeys.WebContent.HOME_URL, "https://example.com/remote")
            }),
        )
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsAppliedFlow.first { it.message.messageId == message.messageId }
        }
        try {
            RemoteMessageManager.emitSettings(message, Source.MQTT)
            val applied = withTimeout(5_000) { received.await() }

            assertEquals("https://example.com/remote", userSettings.homeUrl)
            assertEquals(Source.MQTT, applied.source)
        } finally {
            received.cancel()
        }
    }

    @Test
    fun acceptedServiceAnswersRequestsThroughItsActualCollector() {
        start()
        awaitSubscriptions(1)

        RemoteMessageManager.emitRequest(
            InboundSettingsRequest(messageId = "service-request", responseTopic = "test/replies"),
            Source.MQTT,
        )
        val publication = publications.poll(5, TimeUnit.SECONDS)

        assertNotNull("The service did not publish the requested settings", publication)
        assertEquals("test/replies", publication!!.first)
        assertEquals("service-request", JSONObject(publication.second.toString(Charsets.UTF_8))
            .getString("requestMessageId"))
    }

    @Test
    fun statusUpdatesKeepOneNotificationWithItsChannelActionAndOngoingFlag() {
        start()
        awaitMainCondition { notificationText() == "Status: CONNECTED" }
        val contentIntent = serviceNotification()!!.contentIntent

        for (state in listOf(MqttClientState.DISCONNECTED, MqttClientState.CONNECTING)) {
            mqttState.set(state)
            awaitMainCondition { notificationText() == "Status: ${state.name}" }

            val notification = serviceNotification()!!
            assertEquals(CustomNotificationChannel.MqttService.ID, notification.channelId)
            assertEquals(contentIntent, notification.contentIntent)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(1, context.getSystemService(NotificationManager::class.java).activeNotifications.size)
        }
    }

    @Test
    fun aPollStartedBeforeARepeatedStartCannotOverwriteTheNewStartupStatus() {
        val oldRead = StateReadGate()
        val nextRead = StateReadGate()
        stateReadGates[2] = oldRead
        stateReadGates[4] = nextRead
        start()
        oldRead.awaitStarted()

        mqttState.set(MqttClientState.DISCONNECTED)
        assertEquals(Service.START_STICKY, start(2))
        oldRead.release.countDown()
        // Reaching the next poll proves the old result has finished publication.
        awaitMainCondition { nextRead.started.count == 0L }

        assertEquals("Status: DISCONNECTED", notificationText())
    }

    @Test
    fun aBlockedPollCannotRecreateTheNotificationAfterDestruction() {
        val read = StateReadGate()
        stateReadGates[2] = read
        start()
        read.awaitStarted()
        val job = pollingJob()

        controller.destroy()
        destroyed = true
        read.release.countDown()
        awaitPollCompletion(job)

        assertNull(serviceNotification())
        assertNoPowerOrReceiver()
    }

    @Test
    fun aBlockedPollCannotRecreateTheNotificationAfterARejectedStart() {
        val read = StateReadGate()
        stateReadGates[2] = read
        start()
        read.awaitStarted()
        val job = pollingJob()
        shadowOf(service).setThrowInStartForeground(SecurityException("foreground revoked"))

        assertEquals(Service.START_NOT_STICKY, start(2))
        read.release.countDown()
        awaitPollCompletion(job)

        assertNull(serviceNotification())
        assertNoPowerOrReceiver()
    }

    @Test
    fun aCancelledPollCannotOverwriteAReenabledHostsNotification() {
        val oldRead = StateReadGate()
        val newRead = StateReadGate()
        stateReadGates[2] = oldRead
        stateReadGates[4] = newRead
        start()
        oldRead.awaitStarted()
        val oldJob = pollingJob()
        userSettings.mqttEnabled = false
        assertEquals(Service.START_NOT_STICKY, start(2))

        userSettings.mqttEnabled = true
        mqttState.set(MqttClientState.DISCONNECTED)
        assertEquals(Service.START_STICKY, start(3))
        newRead.awaitStarted()
        oldRead.release.countDown()
        awaitPollCompletion(oldJob)

        assertEquals("Status: DISCONNECTED", notificationText())
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun disablingMqttDuringAStatusReadStopsTheRunningService() {
        assertDisabledDuringPoll { userSettings.mqttEnabled = false }
    }

    @Test
    fun disablingForegroundModeDuringAStatusReadStopsTheRunningService() {
        assertDisabledDuringPoll { userSettings.mqttUseForegroundService = false }
    }

    private fun assertDisabledDuringPoll(disable: () -> Unit) {
        val read = StateReadGate()
        stateReadGates[2] = read
        start()
        read.awaitStarted()
        disable()
        read.release.countDown()
        awaitMainCondition { shadowOf(service).isStoppedBySelf }

        assertNoPowerOrReceiver()
        assertNull(serviceNotification())
        awaitSubscriptions(0)
    }

    private fun start(startId: Int = 1) = service.onStartCommand(null, 0, startId)

    private fun hasScreenReceiver() = screenReceiverCount() > 0

    private fun screenReceiverCount() = shadowOf(RuntimeEnvironment.getApplication())
        .registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }

    private fun currentPollJob() = service.javaClass.getDeclaredField("pollLockTaskModeJob")
        .apply { isAccessible = true }.get(service) as Job?

    private fun pollingJob() = requireNotNull(currentPollJob()).also { observedPollJobs.add(it) }

    private fun awaitPollCompletion(job: Job) = awaitMainCondition { job.isCompleted }

    private fun awaitMainCondition(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(1)
            }
        }
    }

    private fun serviceNotification() = shadowOf(context.getSystemService(NotificationManager::class.java))
        .getNotification(CustomNotificationType.MQTT_SERVICE)

    private fun notificationText() = serviceNotification()?.extras?.getCharSequence(Notification.EXTRA_TEXT)
        ?.toString()

    private class StateReadGate(val result: MqttClientState? = null) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun awaitStarted() {
            assertTrue("The status poll did not start", started.await(5, TimeUnit.SECONDS))
        }
    }

    private fun assertNoPowerOrReceiver() {
        val wakeLock: PowerManager.WakeLock? = ShadowPowerManager.getLatestWakeLock()
        assertFalse("A stopped/unstarted service must not hold a wake lock", wakeLock?.isHeld == true)
        assertFalse("A stopped/unstarted service must not keep its screen receiver", hasScreenReceiver())
    }

    private fun awaitSubscriptions(count: Int) = runBlocking {
        withTimeout(5_000) {
            while (
                (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value != count
                || (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value != count
            ) delay(1)
        }
    }

    private fun dispatchClearHistory() = runBlocking {
        val id = UUID.randomUUID().toString()
        val collector = launch(Dispatchers.Unconfined) {
            RemoteMessageManager.commandsFlow.first { it.message.messageId == id }
        }
        try {
            RemoteMessageManager.emitCommand(
                InboundClearHistoryCommand(messageId = id, interact = false),
                Source.MQTT,
            )
            withTimeout(5_000) {
                while (!collector.isCompleted) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(1)
                }
                collector.join()
            }
        } finally {
            collector.cancel()
        }
    }

    private fun mqttField(name: String) = MqttManager.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }

    private fun publicationBuilder(type: Class<*>): Any {
        var topic = ""
        var payload = byteArrayOf()
        fun builder(builderType: Class<*>, parent: Any? = null): Any {
            val proxyType = builderType.declaredClasses.singleOrNull {
                it.simpleName == "Complete" && builderType.isAssignableFrom(it)
            } ?: builderType
            return Proxy.newProxyInstance(proxyType.classLoader, arrayOf(proxyType)) { proxy, method, args ->
                when (method.name) {
                    "topic" -> { topic = args!![0].toString(); proxy }
                    "payload" -> { payload = args!![0] as ByteArray; proxy }
                    "correlationData", "qos", "retain", "payloadFormatIndicator", "contentType", "add" -> proxy
                    "userProperties" -> builder(method.returnType, proxy)
                    "applyUserProperties" -> requireNotNull(parent)
                    "send" -> {
                        publications.add(topic to payload)
                        CompletableFuture.completedFuture(null)
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "ServiceTestPublishBuilder"
                    else -> throw AssertionError("Unexpected MQTT builder operation: ${method.name}")
                }
            }
        }
        return builder(type)
    }
}
