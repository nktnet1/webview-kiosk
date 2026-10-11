package uk.nktnet.webviewkiosk.instrumentation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoHomeCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoToUrlCommand
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager

/** Tests callbacks from the installed app's WebView/provider, not Robolectric shadows. */
@RunWith(AndroidJUnit4::class)
class RealWebViewTest {
    private lateinit var http: LoopbackHttpServer

    @Before
    fun setUp() {
        http = LoopbackHttpServer()
        InstalledAppHarness.resetSettings(http.url("/first"))
    }

    @After
    fun tearDown() {
        http.close()
    }

    @Test
    fun redirectAndSpaNavigationUpdateRealHistory() {
        InstalledAppHarness.resetSettings(http.url("/redirect"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
            assertTrue(http.requests("/redirect") >= 1)
            assertEquals(http.url("/first"), SystemSettings(InstalledAppHarness.context).currentUrl)
            assertFalse(SystemSettings(InstalledAppHarness.context).historyStack.any {
                it.url == http.url("/redirect")
            })

            val loadsBeforePush = http.requests("/first")
            assertTrue(loadsBeforePush > 0)
            InstalledAppHarness.javascript(
                scenario,
                "history.pushState({page: 2}, '', '/first?spa=1'); 'pushed'"
            )
            InstalledAppHarness.eventually("SPA history callback") {
                SystemSettings(InstalledAppHarness.context).currentUrl == http.url("/first?spa=1")
            }
            assertEquals(loadsBeforePush, http.requests("/first")) // SPA updates do not send GETs.
        }
    }

    @Test
    fun iframe404DoesNotReplaceTopLevelHistoryOrSetAnErrorPage() {
        InstalledAppHarness.resetSettings(http.url("/iframe"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstalledAppHarness.awaitUrl(scenario, http.url("/iframe"))
            InstalledAppHarness.eventually("iframe's HTTP 404") { http.requests("/missing") > 0 }
            assertEquals("\"iframe\"", InstalledAppHarness.javascript(
                scenario, "document.querySelector('#marker').textContent"
            ))
            assertEquals(http.url("/iframe"), SystemSettings(InstalledAppHarness.context).currentUrl)
            assertEquals(
                listOf(http.url("/iframe")),
                SystemSettings(InstalledAppHarness.context).historyStack.map { it.url },
            )
        }
    }

    @Test
    fun remoteNavigationAndHomeReachTheActualWebViewOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
            RemoteMessageManager.emitCommand(
                InboundGoToUrlCommand(
                    messageId = "device-navigation",
                    data = InboundGoToUrlCommand.UrlData(http.url("/second")),
                ),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            InstalledAppHarness.awaitUrl(scenario, http.url("/second"))
            assertEquals(1, http.requests("/second"))

            RemoteMessageManager.emitCommand(
                InboundGoHomeCommand(messageId = "device-home"),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
            assertEquals(2, http.requests("/first"))
        }
    }

    @Test
    fun recreatedActivityRendersLastPageAndAcceptsNewRemoteCommands() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
            RemoteMessageManager.emitCommand(
                InboundGoToUrlCommand(data = InboundGoToUrlCommand.UrlData(http.url("/second"))),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            InstalledAppHarness.awaitUrl(scenario, http.url("/second"))
            scenario.recreate()
            InstalledAppHarness.awaitUrl(scenario, http.url("/second"))
            RemoteMessageManager.emitCommand(
                InboundGoHomeCommand(),
                RemoteMessageManager.RemoteMessage.Source.MQTT,
            )
            InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
        }
    }
}
