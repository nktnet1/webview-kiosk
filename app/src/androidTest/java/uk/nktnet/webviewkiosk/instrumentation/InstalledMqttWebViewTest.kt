package uk.nktnet.webviewkiosk.instrumentation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.services.LoopbackMqttBroker

/** Real TCP -> HiveMQ in the installed APK -> activity -> production WebView. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class InstalledMqttWebViewTest {
    @Test
    fun mqttTcpDeliversNavigationAndSettingsToAnInstalledApp() {
        LoopbackHttpServer().use { http ->
            LoopbackMqttBroker().use { broker ->
                InstalledAppHarness.resetSettings(http.url("/first"))
                UserSettings(InstalledAppHarness.context).apply {
                    mqttEnabled = true
                    mqttUseForegroundService = false
                    mqttServerHost = "127.0.0.1"
                    mqttServerPort = broker.port
                    mqttClientId = "installed-app-integration"
                    mqttUseTls = false
                    mqttUseWebSocket = false
                    mqttAutomaticReconnect = false
                    mqttConnectTimeout = 15
                    mqttSocketConnectTimeout = 15
                    mqttPublishEventTopic = "e2e/events"
                    mqttPublishResponseTopic = "e2e/responses"
                    mqttSubscribeCommandTopic = "e2e/commands"
                    mqttSubscribeSettingsTopic = "e2e/settings"
                    mqttSubscribeRequestTopic = "e2e/requests"
                }
                // The instrumentation process can run several suites without a process restart.
                // Rebuild from these preferences instead of relying on onCreate's one-time init.
                MqttManager.updateConfig(InstalledAppHarness.context)
                try {
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        val connection = broker.awaitConnection()
                        connection.acknowledgeConnect()
                        InstalledAppHarness.eventually("all three MQTT topic subscriptions") {
                            connection.subscriptions.containsAll(
                                setOf("e2e/commands", "e2e/settings", "e2e/requests")
                            )
                        }
                        InstalledAppHarness.awaitUrl(scenario, http.url("/first"))

                        // This publication enters over a REAL TCP socket, not an injected flow.
                        connection.publish("e2e/commands", JSONObject()
                            .put("command", "go_to_url")
                            .put("messageId", "navigate-over-tcp")
                            .put("interact", false)
                            .put("data", JSONObject().put("url", http.url("/second")))
                            .toString())
                        InstalledAppHarness.awaitUrl(scenario, http.url("/second"))
                        assertEquals(1, http.requests("/second"))

                        val changedHomeUrl = http.url("/iframe")
                        connection.publish("e2e/settings", JSONObject()
                            .put("messageId", "settings-over-tcp")
                            .put("reloadActivity", false)
                            .put("showToast", false)
                            .put("data", JSONObject().put("settings", JSONObject()
                                .put(UserSettingsKeys.WebContent.HOME_URL, changedHomeUrl)))
                            .toString())
                        InstalledAppHarness.eventually("settings application from TCP") {
                            UserSettings(InstalledAppHarness.context).homeUrl == changedHomeUrl
                        }
                        assertEquals(http.url("/second"), SystemSettings(InstalledAppHarness.context).currentUrl)

                        connection.publish(
                            "e2e/requests",
                            """{"requestType":"get_settings","messageId":"request-over-tcp"}"""
                        )
                        InstalledAppHarness.eventually("one correlated MQTT response") {
                            connection.publications.any { publication ->
                                publication.topic == "e2e/responses" &&
                                    JSONObject(publication.payload)
                                        .optString("requestMessageId") == "request-over-tcp"
                            }
                        }
                        val responses = connection.publications.filter { publication ->
                            publication.topic == "e2e/responses" &&
                                JSONObject(publication.payload)
                                    .optString("requestMessageId") == "request-over-tcp"
                        }
                        assertEquals(1, responses.size)
                        assertTrue(broker.connectionCount == 1)
                        broker.assertHealthy()
                    }
                } finally {
                    MqttManager.disconnect(
                        OutboundDisconnectingEvent.DisconnectCause.USER_INITIATED_DISCONNECT
                    )
                    UserSettings(InstalledAppHarness.context).mqttEnabled = false
                }
            }
        }
    }
}
