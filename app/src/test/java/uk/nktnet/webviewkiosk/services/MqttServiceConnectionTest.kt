package uk.nktnet.webviewkiosk.services

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Looper
import com.hivemq.client.mqtt.MqttClientState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent.DisconnectCause
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MqttServiceConnectionTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var systemSettings: SystemSettings
    private lateinit var broker: LoopbackMqttBroker
    private lateinit var controller: ServiceController<MqttForegroundService>
    private lateinit var savedFields: Map<String, Any?>
    private var savedCancellation = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        savedFields = listOf("client", "config", "configurationError", "pendingDisconnect",
            "disconnectRequestId", "reconnectCancellation")
            .associateWith { field(it).get(MqttManager) }
        savedCancellation = (field("pendingCancelConnect").get(MqttManager) as AtomicBoolean)
            .getAndSet(false)
        listOf("client", "config", "configurationError", "pendingDisconnect")
            .forEach { field(it).set(MqttManager, null) }
        field("disconnectRequestId").set(MqttManager, 0L)
        field("reconnectCancellation").let { it.set(MqttManager, it.type.getConstructor().newInstance()) }
        MqttManager.clearLogs()
        broker = LoopbackMqttBroker()
        settings = UserSettings(context).apply {
            mqttEnabled = true
            mqttUseForegroundService = true
            mqttServerHost = "127.0.0.1"
            mqttServerPort = broker.port
            mqttClientId = "loopback-test-client"
            mqttUsername = "test-user"
            mqttUseTls = false
            mqttUseWebSocket = false
            mqttAutomaticReconnect = false
            mqttConnectTimeout = 5
            mqttSocketConnectTimeout = 5
            mqttPublishEventTopic = "test/events"
            mqttPublishEventQos = MqttQosOption.AT_LEAST_ONCE
            mqttPublishResponseTopic = "test/responses"
            mqttPublishResponseQos = MqttQosOption.AT_LEAST_ONCE
            mqttSubscribeCommandTopic = "test/commands"
            mqttSubscribeSettingsTopic = "test/settings"
            mqttSubscribeRequestTopic = "test/requests"
        }
        systemSettings = SystemSettings(context).apply {
            historyStack = listOf(
                HistoryEntry("first", "https://example.com/first", 100),
                HistoryEntry("second", "https://example.com/second", 200),
            )
            historyIndex = 1
        }
        controller = Robolectric.buildService(MqttForegroundService::class.java).create()
    }

    @After
    fun tearDown() {
        try {
            if (::controller.isInitialized) controller.destroy()
            MqttManager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT)
            if (::broker.isInitialized) {
                broker.close()
                awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
                awaitCondition {
                    (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value == 0
                        && (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value == 0
                }
            }
        } finally {
            savedFields.forEach { (name, value) -> field(name).set(MqttManager, value) }
            (field("pendingCancelConnect").get(MqttManager) as AtomicBoolean).set(savedCancellation)
            ToastManager.cancel()
        }
    }

    @Test
    @Config(sdk = [28, 29])
    fun stickyRestartBuildsARealClientAndSubscribesWithoutAnActivity() {
        assertFalse(MqttManager.isInitialized())

        assertEquals(Service.START_STICKY, start())
        val connection = broker.awaitConnection()
        assertTrue(MqttManager.isInitialized())
        assertEquals(context.packageName, DeviceOwnerManager.DAR.packageName)
        assertNotNull(context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CustomNotificationChannel.MqttService.ID))
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(1, (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertEquals(1, (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        connection.acknowledgeConnect()

        awaitCondition { connection.subscriptions.size == 3 }
        assertTrue(MqttManager.isConnected())
        assertEquals(setOf("test/commands", "test/settings", "test/requests"), connection.subscriptions)
    }

    @Test
    fun stickyRestartConnectsAnInitialisedButDisconnectedManager() {
        MqttManager.updateConfig(context)
        assertTrue(MqttManager.isInitialized())
        assertEquals(MqttClientState.DISCONNECTED, MqttManager.getState())

        assertEquals(Service.START_STICKY, start())
        val connection = broker.awaitConnection()
        connection.acknowledgeConnect()

        awaitCondition { connection.subscriptions.size == 3 }
        assertTrue(MqttManager.isConnected())
    }

    @Test
    fun repeatedStartsReuseAPendingConnectionAndDoNotResubscribeAfterSuccess() {
        start()
        val connection = broker.awaitConnection()
        val client = field("client").get(MqttManager)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()

        assertEquals(Service.START_STICKY, start(2))
        assertSame(client, field("client").get(MqttManager))
        assertSame(wakeLock, ShadowPowerManager.getLatestWakeLock())
        connection.acknowledgeConnect()
        awaitCondition { connection.subscriptions.size == 3 }

        assertEquals(Service.START_STICKY, start(3))
        connection.publish("test/requests", """{"requestType":"get_settings","messageId":"repeat-barrier"}""")
        awaitResponse(connection, "repeat-barrier")
        assertSame(client, field("client").get(MqttManager))
        assertEquals(1, broker.connectionCount)
        assertEquals(3, connection.subscriptionCount.get())
        assertEquals(1, (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertEquals(1, (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value)
    }

    @Test
    fun aLaterStartRetriesAfterTheBrokerRejectsTheConnection() {
        start()
        val rejected = broker.awaitConnection()
        rejected.acknowledgeConnect(0x87) // MQTT 5: not authorised.
        awaitCondition {
            MqttManager.getState() == MqttClientState.DISCONNECTED
                && MqttManager.debugLogHistory.any { it.tag == "connect failed" }
        }
        assertTrue(rejected.subscriptions.isEmpty())

        assertEquals(Service.START_STICKY, start(2))
        val retry = broker.awaitConnection()
        retry.acknowledgeConnect()

        awaitCondition { retry.subscriptions.size == 3 }
        assertTrue(MqttManager.isConnected())
        assertEquals(2, broker.connectionCount)
    }

    @Test
    fun aLaterStartUsesCorrectedSettingsAfterAnInvalidClientConfiguration() {
        settings.mqttWillTopic = "invalid/#"
        start()
        assertEquals(MqttClientState.DISCONNECTED, MqttManager.getState())
        assertTrue(MqttManager.debugLogHistory.any { it.tag == "configuration invalid" })
        settings.mqttWillTopic = "test/will"

        assertEquals(Service.START_STICKY, start(2))
        val connection = broker.awaitConnection()
        connection.acknowledgeConnect()

        awaitCondition { connection.subscriptions.size == 3 }
        assertEquals(1, broker.connectionCount)
    }

    @Test
    fun aCommandReceivedOverTcpExecutesOnTheColdServiceHost() {
        val connection = connect()
        publishCommandAndAwait(connection, "tcp-command")

        assertEquals(listOf("second"), systemSettings.historyStack.map { it.id })
        assertEquals(0, systemSettings.historyIndex)
    }

    @Test
    fun theColdServiceAppliesTcpSettingsAndEmitsTheSourceAndMessageId() = runBlocking {
        val connection = connect()
        val applied = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsAppliedFlow.first { it.message.messageId == "tcp-settings" }
        }
        try {
            connection.publish("test/settings", JSONObject()
                .put("messageId", "tcp-settings")
                .put("reloadActivity", false)
                .put("showToast", false)
                .put("data", JSONObject().put("settings", JSONObject()
                    .put(UserSettingsKeys.WebContent.HOME_URL, "https://example.com/tcp")))
                .toString())
            val delivery = withTimeout(5_000) { applied.await() }

            assertEquals("https://example.com/tcp", settings.homeUrl)
            assertEquals(RemoteMessageManager.RemoteMessage.Source.MQTT, delivery.source)
        } finally {
            applied.cancel()
        }
    }

    @Test
    fun aTcpRequestReturnsAResponseOnItsRequestedTopic() {
        val connection = connect()
        connection.publish("test/requests", """{"requestType":"get_settings","messageId":"tcp-request","responseTopic":"test/replies","data":{"settings":["${UserSettingsKeys.WebContent.HOME_URL}"]}}""")

        val response = awaitResponse(connection, "tcp-request")

        assertEquals("test/replies", response.topic)
        assertEquals(settings.homeUrl, JSONObject(response.payload).getJSONObject("data")
            .getJSONObject("settings")
            .getString(UserSettingsKeys.WebContent.HOME_URL))
    }

    @Test
    fun mqttResponsePropertiesOverrideJsonAndPreserveUtf8CorrelationData() {
        val connection = connect()
        val correlation = "correlation-雪"
        val properties = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeByte(0x08) // Response Topic.
                output.writeUTF("test/wire-replies")
                output.writeByte(0x09) // Correlation Data.
                val data = correlation.toByteArray(Charsets.UTF_8)
                output.writeShort(data.size)
                output.write(data)
            }
        }.toByteArray()
        connection.publish("test/requests", """{"requestType":"get_settings","messageId":"wire-request","responseTopic":"test/json-replies","correlationData":"json-correlation"}""", properties)

        val response = awaitResponse(connection, "wire-request")

        assertEquals("test/wire-replies", response.topic)
        assertEquals(correlation, JSONObject(response.payload).getString("correlationData"))
        assertArrayEquals(correlation.toByteArray(Charsets.UTF_8), broker.correlationData(response))
    }

    @Test
    fun malformedSettingsDoNotKillTheTransportOrTheServiceCollector() {
        val connection = connect()
        connection.publish("test/settings", """{"messageId":"malformed","data":false}""")
        connection.publish("test/settings", JSONObject()
            .put("messageId", "recovery")
            .put("showToast", false)
            .put("reloadActivity", false)
            .put("data", JSONObject().put("settings", JSONObject()
                .put(UserSettingsKeys.WebContent.HOME_URL, "https://example.com/recovered")))
            .toString())

        awaitCondition { settings.homeUrl == "https://example.com/recovered" }
        connection.publish("test/requests", """{"requestType":"get_settings","messageId":"after-recovery"}""")

        assertEquals("test/responses", awaitResponse(connection, "after-recovery").topic)
        assertTrue(MqttManager.isConnected())
        assertTrue(MqttManager.debugLogHistory.any { it.tag == "settings error" && it.messageId == "malformed" })
    }

    @Test
    fun disablingMqttWhileConnectingRejectsTheLateBrokerSuccess() {
        start()
        val connection = broker.awaitConnection()
        settings.mqttEnabled = false

        connection.acknowledgeConnect()

        awaitCondition { connection.disconnected.count == 0L }
        awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
        awaitCondition { !ShadowPowerManager.getLatestWakeLock().isHeld }
        assertTrue("A disabled connection must not subscribe", connection.subscriptions.isEmpty())
        assertTrue("A disabled connection must not announce a successful start", connection.publications.isEmpty())
        assertTrue(shadowOf(controller.get()).isForegroundStopped)
    }

    @Test
    fun changingForegroundModeDuringConnectReleasesTheServiceHost() {
        start()
        val connection = broker.awaitConnection()
        settings.mqttUseForegroundService = false
        assertEquals(Service.START_NOT_STICKY, start(2))
        connection.acknowledgeConnect()
        awaitCondition { connection.subscriptions.size == 3 }

        assertEquals(RemoteMessageManager.RemoteMessage.Source.MQTT,
            publishCommandAndAwait(connection, "unowned-command").source)

        assertEquals(2, systemSettings.historyStack.size)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(0, (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertEquals(0, (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>).subscriptionCount.value)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications
            .none { it.id == CustomNotificationType.MQTT_SERVICE })
    }

    private fun connect(): LoopbackMqttBroker.Connection {
        assertEquals(Service.START_STICKY, start())
        val connection = broker.awaitConnection()
        connection.acknowledgeConnect()
        awaitCondition { connection.subscriptions.size == 3 }
        return connection
    }

    private fun publishCommandAndAwait(connection: LoopbackMqttBroker.Connection, id: String) = runBlocking {
        val dispatched = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.commandsFlow.first { it.message.messageId == id }
        }
        try {
            connection.publish("test/commands", """{"command":"clear_history","messageId":"$id","interact":false}""")
            awaitCondition { dispatched.isCompleted }
            dispatched.await()
        } finally {
            dispatched.cancel()
        }
    }

    private fun awaitResponse(connection: LoopbackMqttBroker.Connection, id: String): LoopbackMqttBroker.Publication {
        var response: LoopbackMqttBroker.Publication? = null
        awaitCondition {
            val publication = connection.publications.poll()
            if (publication != null && JSONObject(publication.payload).optString("requestMessageId") == id) {
                response = publication
            }
            response != null
        }
        return requireNotNull(response)
    }

    private fun start(startId: Int = 1) = controller.get().onStartCommand(null, 0, startId)

    private fun awaitCondition(condition: () -> Boolean) {
        try {
            runBlocking {
                withTimeout(5_000) {
                    while (!condition()) {
                        broker.assertHealthy()
                        shadowOf(Looper.getMainLooper()).idle()
                        delay(1)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("MQTT condition timed out; state=${MqttManager.getState()}; logs=${MqttManager.debugLogHistory}", e)
        }
    }

    private fun field(name: String) = MqttManager.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }
}
