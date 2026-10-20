package uk.nktnet.webviewkiosk.services

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.RestrictionsManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import com.hivemq.client.mqtt.MqttClientState
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import uk.nktnet.webviewkiosk.config.mqtt.HomeAssistantDiscovery
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent.DisconnectCause
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.managers.ToastManager

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
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        savedFields =
            listOf(
                    "client",
                    "config",
                    "configurationError",
                    "pendingDisconnect",
                    "disconnectRequestId",
                    "reconnectCancellation",
                    "homeAssistantSession",
                )
                .associateWith { field(it).get(MqttManager) }
        savedCancellation =
            (field("pendingCancelConnect").get(MqttManager) as AtomicBoolean)
                .getAndSet(false)
        listOf(
                "client",
                "config",
                "configurationError",
                "pendingDisconnect",
                "homeAssistantSession",
            )
            .forEach { field(it).set(MqttManager, null) }
        field("disconnectRequestId").set(MqttManager, 0L)
        field("reconnectCancellation").let {
            it.set(MqttManager, it.type.getConstructor().newInstance())
        }
        MqttManager.clearLogs()
        context
            .getSharedPreferences("mqtt_home_assistant", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        broker = LoopbackMqttBroker()
        settings =
            UserSettings(context).apply {
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
        systemSettings =
            SystemSettings(context).apply {
                historyStack =
                    listOf(
                        HistoryEntry("first", "https://example.com/first", 100),
                        HistoryEntry("second", "https://example.com/second", 200),
                    )
                historyIndex = 1
            }
        controller =
            Robolectric.buildService(MqttForegroundService::class.java).create()
    }

    @After
    fun tearDown() {
        try {
            if (::controller.isInitialized) {
                controller.destroy()
            }
            MqttManager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT)
            if (::broker.isInitialized) {
                broker.close()
                awaitCondition {
                    MqttManager.getState() == MqttClientState.DISCONNECTED
                }
                awaitCondition {
                    (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>)
                        .subscriptionCount
                        .value == 0 &&
                        (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>)
                            .subscriptionCount
                            .value == 0
                }
            }
        } finally {
            savedFields.forEach { (name, value) ->
                field(name).set(MqttManager, value)
            }
            (field("pendingCancelConnect").get(MqttManager) as AtomicBoolean).set(
                savedCancellation
            )
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
        assertNotNull(
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CustomNotificationChannel.MqttService.ID)
        )
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(
            1,
            (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
        assertEquals(
            1,
            (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
        connection.acknowledgeConnect()

        awaitCondition { connection.subscriptions.size == 3 }
        assertTrue(MqttManager.isConnected())
        assertEquals(
            setOf("test/commands", "test/settings", "test/requests"),
            connection.subscriptions,
        )
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
        connection.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"repeat-barrier"}""",
        )
        awaitResponse(connection, "repeat-barrier")
        assertSame(client, field("client").get(MqttManager))
        assertEquals(1, broker.connectionCount)
        assertEquals(3, connection.subscriptionCount.get())
        assertEquals(
            1,
            (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
        assertEquals(
            1,
            (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
    }

    @Test
    fun automaticReconnectRestoresSubscriptionsOnTheSameClient() {
        settings.mqttAutomaticReconnect = true
        val first = connect()
        val client = field("client").get(MqttManager)
        assertEquals(3, first.subscriptionCount.get())

        first.close() // Unplanned socket failure: the broker starts with an empty
        // session.
        awaitCondition { first.disconnected.count == 0L }
        val second = broker.awaitConnection()
        assertSame(
            "Automatic reconnect must retain the configured client",
            client,
            field("client").get(MqttManager),
        )
        second.acknowledgeConnect()

        awaitCondition { second.subscriptions.size == 3 }
        assertEquals(
            setOf("test/commands", "test/settings", "test/requests"),
            second.subscriptions,
        )
        assertEquals(3, second.subscriptionCount.get())
        second.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"reconnected-request"}""",
        )
        assertEquals(
            "test/responses",
            awaitResponse(second, "reconnected-request").topic,
        )
        assertEquals(2, broker.connectionCount)
    }

    @Test
    fun aLaterStartRetriesAfterTheBrokerRejectsTheConnection() {
        start()
        val rejected = broker.awaitConnection()
        rejected.acknowledgeConnect(0x87) // MQTT 5: not authorised.
        awaitCondition {
            MqttManager.getState() == MqttClientState.DISCONNECTED &&
                MqttManager.debugLogHistory.any { it.tag == "connect failed" }
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
        assertTrue(
            MqttManager.debugLogHistory.any { it.tag == "configuration invalid" }
        )
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
    fun theColdServiceAppliesTcpSettingsAndEmitsTheSourceAndMessageId() {
        val connection = connect()
        runBlocking {
            val applied =
                async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    RemoteMessageManager.settingsAppliedFlow.first {
                        it.message.messageId == "tcp-settings"
                    }
                }
            try {
                connection.publish(
                    "test/settings",
                    JSONObject()
                        .put("messageId", "tcp-settings")
                        .put("reloadActivity", false)
                        .put("showToast", false)
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "settings",
                                    JSONObject()
                                        .put(
                                            UserSettingsKeys.WebContent.HOME_URL,
                                            "https://example.com/tcp",
                                        ),
                                ),
                        )
                        .toString(),
                )
                val delivery = withTimeout(5.seconds) { applied.await() }

                assertEquals("https://example.com/tcp", settings.homeUrl)
                assertEquals(
                    RemoteMessageManager.RemoteMessage.Source.MQTT,
                    delivery.source,
                )
            } finally {
                applied.cancel()
            }
        }
    }

    @Test
    fun aTcpRequestReturnsAResponseOnItsRequestedTopic() {
        val connection = connect()
        connection.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"tcp-request","responseTopic":"test/replies","data":{"settings":["${UserSettingsKeys.WebContent.HOME_URL}"]}}""",
        )

        val response = awaitResponse(connection, "tcp-request")

        assertEquals("test/replies", response.topic)
        assertEquals(
            settings.homeUrl,
            JSONObject(response.payload)
                .getJSONObject("data")
                .getJSONObject("settings")
                .getString(UserSettingsKeys.WebContent.HOME_URL),
        )
    }

    @Test
    fun mqttResponsePropertiesOverrideJsonAndPreserveUtf8CorrelationData() {
        val connection = connect()
        val correlation = "correlation-雪"
        val properties =
            ByteArrayOutputStream()
                .also { bytes ->
                    DataOutputStream(bytes).use { output ->
                        output.writeByte(0x08) // Response Topic.
                        output.writeUTF("test/wire-replies")
                        output.writeByte(0x09) // Correlation Data.
                        val data = correlation.toByteArray(Charsets.UTF_8)
                        output.writeShort(data.size)
                        output.write(data)
                    }
                }
                .toByteArray()
        connection.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"wire-request","responseTopic":"test/json-replies","correlationData":"json-correlation"}""",
            properties,
        )

        val response = awaitResponse(connection, "wire-request")

        assertEquals("test/wire-replies", response.topic)
        assertEquals(
            correlation,
            JSONObject(response.payload).getString("correlationData"),
        )
        assertArrayEquals(
            correlation.toByteArray(Charsets.UTF_8),
            broker.correlationData(response),
        )
    }

    @Test
    fun malformedSettingsDoNotKillTheTransportOrTheServiceCollector() {
        val connection = connect()
        connection.publish(
            "test/settings",
            """{"messageId":"malformed","data":false}""",
        )
        connection.publish(
            "test/settings",
            JSONObject()
                .put("messageId", "recovery")
                .put("showToast", false)
                .put("reloadActivity", false)
                .put(
                    "data",
                    JSONObject()
                        .put(
                            "settings",
                            JSONObject()
                                .put(
                                    UserSettingsKeys.WebContent.HOME_URL,
                                    "https://example.com/recovered",
                                ),
                        ),
                )
                .toString(),
        )

        awaitCondition { settings.homeUrl == "https://example.com/recovered" }
        connection.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"after-recovery"}""",
        )

        assertEquals(
            "test/responses",
            awaitResponse(connection, "after-recovery").topic,
        )
        assertTrue(MqttManager.isConnected())
        assertTrue(
            MqttManager.debugLogHistory.any {
                it.tag == "settings error" && it.messageId == "malformed"
            }
        )
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
        assertTrue(
            "A disabled connection must not subscribe",
            connection.subscriptions.isEmpty(),
        )
        assertTrue(
            "A disabled connection must not announce a successful start",
            connection.publications.isEmpty(),
        )
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

        assertEquals(
            RemoteMessageManager.RemoteMessage.Source.MQTT,
            publishCommandAndAwait(connection, "unowned-command").source,
        )

        assertEquals(2, systemSettings.historyStack.size)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(
            0,
            (RemoteMessageManager.settingsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
        assertEquals(
            0,
            (RemoteMessageManager.requestsFlow as MutableSharedFlow<*>)
                .subscriptionCount
                .value,
        )
        assertTrue(
            context
                .getSystemService(NotificationManager::class.java)
                .activeNotifications
                .none { it.id == CustomNotificationType.MQTT_SERVICE }
        )
    }

    @Test
    fun discoveryPublishesRetainedDeviceStateAndADedicatedAvailabilityWill() {
        settings.mqttHomeAssistantEnabled = true
        settings.mqttHomeAssistantDeviceName = "Kitchen kiosk"
        val connection = connect()
        val ha = homeAssistant()
        val announcement = awaitPublication(connection, ha.configTopic)
        val device = JSONObject(announcement.payload)

        assertTrue(announcement.retain)
        assertEquals(
            "Kitchen kiosk",
            device.getJSONObject("device").getString("name"),
        )
        assertEquals(193, device.getJSONObject("components").length())
        assertEquals(
            ha.availabilityTopic,
            device.getJSONArray("availability").getJSONObject(0).getString("topic"),
        )
        val state = awaitPublication(connection, ha.stateTopic)
        assertTrue(state.retain)
        assertEquals(
            systemSettings.currentUrl,
            JSONObject(state.payload).getString("currentUrl"),
        )
        assertTrue(
            awaitPublication(connection, ha.availabilityTopic, "online").retain
        )

        val will = requireNotNull(connection.will)
        assertEquals(ha.availabilityTopic, will.topic)
        assertEquals("offline", will.payload)
        assertEquals(1, will.qos)
        assertTrue(will.retain)
        assertNull(will.messageExpiryInterval)
        assertTrue(
            connection.subscriptions.containsAll(ha.controlTopics + ha.birthTopic)
        )
    }

    @Test
    fun homeAssistantBirthResendsDiscoveryStateAndAvailability() {
        settings.mqttHomeAssistantEnabled = true
        settings.mqttHomeAssistantDiscoveryPrefix = "custom/discovery"
        settings.mqttHomeAssistantStatusTopic = "custom/ha-status"
        val connection = connect()
        val ha = homeAssistant()
        awaitPublication(connection, ha.availabilityTopic, "online")
        connection.publications.clear()

        connection.publish(ha.birthTopic, "online")

        assertTrue(awaitPublication(connection, ha.configTopic).retain)
        assertTrue(awaitPublication(connection, ha.stateTopic).retain)
        assertEquals(
            "online",
            awaitPublication(connection, ha.availabilityTopic).payload,
        )
        assertEquals(7, connection.subscriptionCount.get())
    }

    @Test
    fun homeAssistantStateUpdatesImmediatelyFromKioskEvents() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        val ha = homeAssistant()
        awaitPublication(connection, ha.availabilityTopic, "online")
        connection.publications.clear()

        MqttManager.publishUrlChangedEvent("https://example.com/changed")

        val state = awaitPublication(connection, ha.stateTopic)
        assertEquals(
            "https://example.com/changed",
            JSONObject(state.payload).getString("currentUrl"),
        )
        connection.publications.clear()
        MqttManager.publishScreenOffEvent()
        assertFalse(
            JSONObject(awaitPublication(connection, ha.stateTopic).payload)
                .getBoolean("isDeviceInteractive")
        )
    }

    @Test
    fun dedicatedCommandsIgnoreRetainedMessagesAndOtherInstances() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        val ha = homeAssistant()
        val command =
            JSONObject()
                .put("command", "clear_history")
                .put("interact", false)
                .put(
                    "targetInstances",
                    org.json.JSONArray().put(systemSettings.appInstanceId),
                )
        connection.publish(ha.commandTopic, command.toString(), retain = true)
        connection.publish(
            ha.commandTopic,
            command
                .put("targetInstances", org.json.JSONArray().put("another-instance"))
                .toString(),
        )
        connection.publish(
            "test/requests",
            """{"requestType":"get_settings","messageId":"ha-ignored-barrier"}""",
        )
        awaitResponse(connection, "ha-ignored-barrier")
        assertEquals(2, systemSettings.historyStack.size)

        // Await the command completion event as well as its native side effect.
        // Otherwise an unfinished main-thread handoff can hold the shared command
        // mutex after Robolectric tears down this test's looper.
        publishCommandAndAwait(connection, "ha-command", ha.commandTopic)
        assertEquals(1, systemSettings.historyStack.size)
        assertEquals("second", systemSettings.historyStack.single().id)
    }

    @Test
    fun disablingDiscoveryRemovesTheRetainedConfigAndRestoresTheCustomWill() {
        settings.mqttHomeAssistantEnabled = true
        val first = connect()
        val ha = homeAssistant()
        awaitPublication(first, ha.availabilityTopic, "online")
        settings.mqttHomeAssistantEnabled = false

        MqttManager.restart(context, DisconnectCause.USER_INITIATED_RESTART)
        val second = broker.awaitConnection()
        second.acknowledgeConnect()
        awaitCondition { second.subscriptions.size == 3 }

        assertTrue(awaitPublication(first, ha.availabilityTopic, "offline").retain)
        assertTrue(awaitPublication(second, ha.configTopic, "").retain)
        assertEquals("wk/will", second.will?.topic)
        assertFalse(second.subscriptions.contains(ha.birthTopic))
    }

    @Test
    fun changingDiscoveryPrefixRemovesTheOldConfigWithoutChangingEntityIds() {
        settings.mqttHomeAssistantEnabled = true
        val first = connect()
        val previous = homeAssistant()
        val oldConfig =
            JSONObject(awaitPublication(first, previous.configTopic).payload)
        awaitPublication(first, previous.availabilityTopic, "online")
        settings.mqttHomeAssistantDiscoveryPrefix = "another/prefix"

        MqttManager.restart(context, DisconnectCause.USER_INITIATED_RESTART)
        val second = broker.awaitConnection()
        second.acknowledgeConnect()
        awaitCondition { second.subscriptions.size == 7 }
        val current = homeAssistant()

        assertTrue(awaitPublication(second, previous.configTopic, "").retain)
        val newConfig =
            JSONObject(awaitPublication(second, current.configTopic).payload)
        assertEquals(
            oldConfig.getJSONObject("components").toString(),
            newConfig.getJSONObject("components").toString(),
        )
        assertTrue(second.subscriptions.contains(current.birthTopic))
    }

    @Test
    fun automaticReconnectReannouncesDiscoveryWithoutDuplicatingSubscriptions() {
        settings.mqttHomeAssistantEnabled = true
        settings.mqttAutomaticReconnect = true
        val first = connect()
        val ha = homeAssistant()
        awaitPublication(first, ha.availabilityTopic, "online")
        val originalClient = field("client").get(MqttManager)

        first.close()
        val second = broker.awaitConnection()
        second.acknowledgeConnect()
        awaitCondition { second.subscriptions.size == 7 }

        assertSame(originalClient, field("client").get(MqttManager))
        assertTrue(awaitPublication(second, ha.configTopic).retain)
        assertEquals(
            "online",
            awaitPublication(second, ha.availabilityTopic).payload,
        )
        assertEquals(7, second.subscriptionCount.get())
        second.publications.clear()
        second.publish(ha.birthTopic, "online")
        awaitPublication(second, ha.configTopic)
    }

    @Test
    fun batteryStateIsRefreshedPeriodicallyAndPollingStopsOnDisconnect() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        val ha = homeAssistant()
        awaitPublication(connection, ha.availabilityTopic, "online")
        connection.publications.clear()
        shadowOf(context.getSystemService(BatteryManager::class.java))
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 76)

        shadowOf(Looper.getMainLooper()).idleFor(31, TimeUnit.SECONDS)

        assertEquals(
            76,
            JSONObject(awaitPublication(connection, ha.stateTopic).payload)
                .getInt("batteryPercentage"),
        )
        MqttManager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT)
        awaitCondition { MqttManager.getState() == MqttClientState.DISCONNECTED }
        connection.publications.clear()
        shadowOf(Looper.getMainLooper()).idleFor(31, TimeUnit.SECONDS)
        assertTrue(connection.publications.isEmpty())
    }

    @Test
    fun shutdownTimeoutStillRequestsTheOfflineAvailabilityWill() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        awaitPublication(connection, homeAssistant().availabilityTopic, "online")
        connection.acknowledgePublishes = false

        MqttManager.disconnect(DisconnectCause.USER_INITIATED_DISCONNECT)

        assertTrue(connection.disconnected.await(8, TimeUnit.SECONDS))
        assertEquals(0x04, connection.disconnectReason)
        assertTrue(
            MqttManager.debugLogHistory.any {
                it.tag == "disconnect event timed out"
            }
        )
    }

    @Test
    fun homeAssistantSettingsApplyAndPublishAuthoritativeStateWithoutPasswords() {
        settings.mqttHomeAssistantEnabled = true
        settings.mqttPassword = "broker-secret"
        settings.customAuthPassword = "unlock-secret"
        val connection = connect()
        val ha = homeAssistant()
        val initial = awaitPublication(connection, ha.settingsStateTopic)
        assertTrue(initial.retain)
        assertFalse(initial.payload.contains("broker-secret"))
        assertFalse(initial.payload.contains("unlock-secret"))
        connection.publications.clear()
        connection.publish(
            ha.settingsCommandTopic,
            """{"showToast":false,"reloadActivity":false,"data":{"settings":{"device.brightness":24,"web_engine.enable_javascript":false,"appearance.theme":"DARK","web_lifecycle.reset_actions":["CACHE"],"device.custom_auth_password":"new-secret"}}}""",
        )
        awaitCondition {
            settings.brightness == 24 && settings.customAuthPassword == "new-secret"
        }
        assertFalse(settings.enableJavaScript)
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        val state =
            JSONObject(awaitPublication(connection, ha.settingsStateTopic).payload)
                .getJSONObject("settings")
        assertEquals(24, state.getInt("device.brightness"))
        assertFalse(state.getBoolean("web_engine.enable_javascript"))
        assertEquals("DARK", state.getString("appearance.theme"))
        assertEquals(
            "CACHE",
            state.getJSONArray("web_lifecycle.reset_actions").getString(0),
        )
        assertFalse(state.has("device.custom_auth_password"))
        assertFalse(state.has("mqtt.connection.password"))
    }

    @Test
    fun homeAssistantRetainedControlsAndForeignTargetsDoNotApplySettingsOrRequests() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        val ha = homeAssistant()
        awaitPublication(connection, ha.availabilityTopic, "online")
        val original = settings.brightness
        connection.publish(
            ha.settingsCommandTopic,
            """{"data":{"settings":{"device.brightness":33}}}""",
            retain = true,
        )
        connection.publish(
            ha.settingsCommandTopic,
            """{"targetInstances":["elsewhere"],"data":{"settings":{"device.brightness":34}}}""",
        )
        connection.publish(
            ha.requestTopic,
            """{"requestType":"get_status","messageId":"retained-ha"}""",
            retain = true,
        )
        connection.publish(
            ha.requestTopic,
            """{"requestType":"get_status","messageId":"foreign-ha","targetInstances":["elsewhere"]}""",
        )
        awaitCondition {
            MqttManager.debugLogHistory.any {
                it.tag == "request received (ignored)" &&
                    it.messageId == "foreign-ha"
            }
        }
        assertEquals(original, settings.brightness)
        assertFalse(
            connection.publications.any {
                it.topic == ha.responseTopic("get_status")
            }
        )
    }

    @Test
    fun homeAssistantManagedSettingsStayEnforcedAndAreMarkedUnavailable() {
        settings.mqttHomeAssistantEnabled = true
        val restrictions =
            context.getSystemService(Context.RESTRICTIONS_SERVICE)
                as RestrictionsManager
        @Suppress("UsePropertyAccessSyntax")
        shadowOf(restrictions)
            .setApplicationRestrictions(
                Bundle().apply { putInt("device.brightness", 19) }
            )
        try {
            val connection = connect()
            val ha = homeAssistant()
            val state =
                JSONObject(
                    awaitPublication(connection, ha.settingsStateTopic).payload
                )
            assertTrue(
                state.getJSONObject("managed").getBoolean("device.brightness")
            )
            connection.publications.clear()
            connection.publish(
                ha.settingsCommandTopic,
                """{"showToast":false,"reloadActivity":false,"data":{"settings":{"device.brightness":68}}}""",
            )
            awaitCondition {
                MqttManager.debugLogHistory.any { it.tag == "settings received" }
            }
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertEquals(19, settings.brightness)
        } finally {
            @Suppress("UsePropertyAccessSyntax")
            shadowOf(restrictions).setApplicationRestrictions(Bundle())
        }
    }

    @Test
    fun homeAssistantRequestButtonsReturnEachResultOnItsDedicatedTopic() {
        settings.mqttHomeAssistantEnabled = true
        settings.customAuthPassword = "private-setting"
        val connection = connect()
        val ha = homeAssistant()
        val components =
            JSONObject(awaitPublication(connection, ha.configTopic).payload)
                .getJSONObject("components")
        HomeAssistantDiscovery.requestNames.keys.forEach { request ->
            connection.publications.clear()
            connection.publish(
                ha.requestTopic,
                components.getJSONObject(request).getString("payload_press"),
            )
            val response = awaitPublication(connection, ha.responseTopic(request))
            assertTrue(response.retain)
            val result = JSONObject(response.payload)
            assertTrue(result.has("result"))
            assertTrue(result.has("updatedAt"))
            assertFalse(response.payload.contains("private-setting"))
        }
    }

    @Test
    fun homeAssistantRequestOptionsAndErrorResultsUseProtectedResponseTopics() {
        settings.mqttHomeAssistantEnabled = true
        val connection = connect()
        val ha = homeAssistant()
        connection.publish(
            ha.requestTopic,
            """{"requestType":"get_settings","responseTopic":"unrelated/topic","data":{"settings":["device.brightness","device.custom_auth_password"]}}""",
        )
        val result =
            JSONObject(
                    awaitPublication(connection, ha.responseTopic("get_settings"))
                        .payload
                )
                .getJSONObject("result")
                .getJSONObject("settings")
        assertEquals(setOf("device.brightness"), result.keys().asSequence().toSet())
        assertFalse(connection.publications.any { it.topic == "unrelated/topic" })
        connection.publish(ha.requestTopic, """{"requestType":"invalid"}""")
        assertTrue(
            JSONObject(
                    awaitPublication(connection, ha.responseTopic("error")).payload
                )
                .has("error")
        )
    }

    private fun homeAssistant() =
        HomeAssistantDiscovery(
            systemSettings.appInstanceId,
            settings.mqttHomeAssistantDiscoveryPrefix,
            settings.mqttHomeAssistantStatusTopic,
        )

    private fun awaitPublication(
        connection: LoopbackMqttBroker.Connection,
        topic: String,
        payload: String? = null,
    ): LoopbackMqttBroker.Publication {
        var result: LoopbackMqttBroker.Publication? = null
        awaitCondition {
            result =
                connection.publications.firstOrNull {
                    it.topic == topic && (payload == null || it.payload == payload)
                }
            result != null
        }
        return requireNotNull(result)
    }

    private fun connect(): LoopbackMqttBroker.Connection {
        assertEquals(Service.START_STICKY, start())
        val connection = broker.awaitConnection()
        connection.acknowledgeConnect()
        val count =
            if (settings.mqttHomeAssistantEnabled) {
                7
            } else {
                3
            }
        awaitCondition { connection.subscriptions.size == count }
        return connection
    }

    private fun publishCommandAndAwait(
        connection: LoopbackMqttBroker.Connection,
        id: String,
        topic: String = "test/commands",
    ) = runBlocking {
        val dispatched =
            async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                RemoteMessageManager.commandsFlow.first {
                    it.message.messageId == id
                }
            }
        try {
            connection.publish(
                topic,
                """{"command":"clear_history","messageId":"$id","interact":false}""",
            )
            awaitConditionSuspending { dispatched.isCompleted }
            dispatched.await()
        } finally {
            dispatched.cancel()
        }
    }

    private fun awaitResponse(
        connection: LoopbackMqttBroker.Connection,
        id: String,
    ): LoopbackMqttBroker.Publication {
        var response: LoopbackMqttBroker.Publication? = null
        awaitCondition {
            val publication = connection.publications.poll()
            if (
                publication != null &&
                    runCatching {
                        JSONObject(publication.payload).optString("requestMessageId")
                    }
                        .getOrNull() == id
            ) {
                response = publication
            }
            response != null
        }
        return requireNotNull(response)
    }

    private fun start(startId: Int = 1) =
        controller.get().onStartCommand(null, 0, startId)

    private fun awaitCondition(condition: () -> Boolean) = runBlocking {
        awaitConditionSuspending(condition)
    }

    private suspend fun awaitConditionSuspending(condition: () -> Boolean) {
        try {
            withTimeout(5.seconds) {
                while (!condition()) {
                    broker.assertHealthy()
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(1.milliseconds)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "MQTT condition timed out; state=${MqttManager.getState()}; logs=${MqttManager.debugLogHistory}",
                e,
            )
        }
    }

    private fun field(name: String) =
        MqttManager.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
