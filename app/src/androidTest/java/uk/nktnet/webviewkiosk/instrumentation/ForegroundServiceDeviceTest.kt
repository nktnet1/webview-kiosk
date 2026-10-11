package uk.nktnet.webviewkiosk.instrumentation

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoToUrlCommand
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import uk.nktnet.webviewkiosk.services.LoopbackMqttBroker
import uk.nktnet.webviewkiosk.services.MqttForegroundService

/** A real Android foreground service and TCP connection, while the activity is stopped. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class ForegroundServiceDeviceTest {
    @Test
    fun serviceRetainsMqttOwnershipInBackgroundAndStopsOnSettingsChange() {
        LoopbackHttpServer().use { http ->
            LoopbackMqttBroker().use { broker ->
                InstalledAppHarness.resetSettings(http.url("/first"))
                val context = InstalledAppHarness.context
                val settings = UserSettings(context).apply {
                    mqttEnabled = true
                    mqttUseForegroundService = true
                    mqttServerHost = "127.0.0.1"
                    mqttServerPort = broker.port
                    mqttClientId = "foreground-service-device-test"
                    mqttUseTls = false
                    mqttUseWebSocket = false
                    mqttAutomaticReconnect = false
                    mqttConnectTimeout = 15
                    mqttSocketConnectTimeout = 15
                    mqttPublishEventTopic = "fgs/events"
                    mqttPublishResponseTopic = "fgs/responses"
                    mqttSubscribeCommandTopic = "fgs/commands"
                    mqttSubscribeSettingsTopic = "fgs/settings"
                    mqttSubscribeRequestTopic = "fgs/requests"
                }
                MqttManager.updateConfig(context)
                val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                try {
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        val connection = broker.awaitConnection()
                        connection.acknowledgeConnect()
                        InstalledAppHarness.eventually("foreground-test MQTT subscriptions") {
                            connection.subscriptions.containsAll(
                                setOf("fgs/commands", "fgs/settings", "fgs/requests")
                            )
                        }
                        InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
                        RemoteMessageManager.emitCommand(
                            InboundGoToUrlCommand(
                                messageId = "seed-background-history",
                                data = InboundGoToUrlCommand.UrlData(http.url("/second")),
                            ),
                            RemoteMessageManager.RemoteMessage.Source.MQTT,
                        )
                        InstalledAppHarness.awaitUrl(scenario, http.url("/second"))
                        assertTrue(SystemSettings(context).historyStack.size >= 2)

                        scenario.onActivity { activity ->
                            ContextCompat.startForegroundService(
                                activity, Intent(activity, MqttForegroundService::class.java)
                            )
                        }
                        InstalledAppHarness.eventually("real foreground-service ownership") {
                            MqttForegroundService.isHandlingMessages()
                        }
                        // A denied runtime notification permission may hide the user-facing
                        // notification while the service is legal and still foregrounded.
                        val canSeeNotifications = Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (canSeeNotifications) {
                            InstalledAppHarness.eventually("one foreground notification") {
                                notifications.activeNotifications.count {
                                    it.id == CustomNotificationType.MQTT_SERVICE
                                } == 1
                            }
                        }

                        scenario.moveToState(Lifecycle.State.CREATED)
                        assertTrue(MqttForegroundService.isHandlingMessages())
                        // No WebView is needed for this native command; it must be processed
                        // by the SERVICE host after the activity relinquishes ownership.
                        connection.publish(
                            "fgs/commands",
                            """{"command":"clear_history","messageId":"background-clear","interact":false}"""
                        )
                        InstalledAppHarness.eventually("background native command") {
                            SystemSettings(context).historyStack.size == 1 &&
                                SystemSettings(context).currentUrl == http.url("/second")
                        }
                        connection.publish(
                            "fgs/requests",
                            """{"requestType":"get_settings","messageId":"background-request"}"""
                        )
                        InstalledAppHarness.eventually("background MQTT response") {
                            connection.publications.any {
                                it.topic == "fgs/responses" &&
                                    JSONObject(it.payload).optString("requestMessageId") ==
                                    "background-request"
                            }
                        }
                        assertEquals(1, connection.publications.count {
                            it.topic == "fgs/responses" &&
                                JSONObject(it.payload).optString("requestMessageId") ==
                                "background-request"
                        })
                        assertEquals(1, broker.connectionCount)

                        settings.mqttEnabled = false
                        InstalledAppHarness.eventually("foreground-service shutdown") {
                            !MqttForegroundService.isHandlingMessages()
                        }
                        if (canSeeNotifications) {
                            InstalledAppHarness.eventually("foreground notification removal") {
                                notifications.activeNotifications.none {
                                    it.id == CustomNotificationType.MQTT_SERVICE
                                }
                            }
                        }
                    }
                } finally {
                    settings.mqttEnabled = false
                    context.stopService(Intent(context, MqttForegroundService::class.java))
                    MqttManager.disconnect(
                        OutboundDisconnectingEvent.DisconnectCause.USER_INITIATED_DISCONNECT
                    )
                }
            }
        }
    }
}
