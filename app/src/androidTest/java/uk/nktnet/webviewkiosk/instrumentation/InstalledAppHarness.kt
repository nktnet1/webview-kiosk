package uk.nktnet.webviewkiosk.instrumentation

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.fail
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.managers.MqttManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Only run on a disposable emulator: tests reset the installed app's preferences. */
internal object InstalledAppHarness {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun resetSettings(homeUrl: String) {
        // The instrumentation runner shares one app process between methods. Complete
        // any asynchronous disconnect BEFORE rebuilding the MQTT singleton from the
        // next method's preferences, otherwise updateConfig can retain the old peer.
        if (MqttManager.isInitialized()) {
            val disconnected = CountDownLatch(1)
            val disconnectError = AtomicReference<String?>()
            MqttManager.disconnect(
                OutboundDisconnectingEvent.DisconnectCause.USER_INITIATED_DISCONNECT,
                onDisconnected = { disconnected.countDown() },
                onError = { error ->
                    disconnectError.set(error ?: "Unknown MQTT disconnect failure")
                    disconnected.countDown()
                },
            )
            check(disconnected.await(12, TimeUnit.SECONDS)) { "Old MQTT host did not disconnect" }
            check(disconnectError.get() == null) { "Old MQTT host: ${disconnectError.get()}" }
        }
        context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        UserSettings(context).apply {
            this.homeUrl = homeUrl
            resetOnLaunch = false
            lockOnLaunch = false
            mqttEnabled = false
            mqttUseForegroundService = false
            supportPdfRendering = false
            resetOnInactivitySeconds = 0
        }
        SystemSettings(context).clearHistory()
    }

    fun eventually(description: String, timeoutMs: Long = 25_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (condition()) return
            } catch (error: IllegalStateException) {
                // Compose may be replacing its view hierarchy during this sample.
                lastFailure = error
            }
            SystemClock.sleep(75)
        }
        fail("Timed out waiting for $description${lastFailure?.let { ": $it" } ?: ""}")
    }

    private fun currentWebViewUrl(scenario: ActivityScenario<MainActivity>): String? {
        var observedUrl: String? = null
        // onActivity runs on the main thread. Never return a WebView to the test thread:
        // even reading WebView.url there violates Android's WebView thread confinement.
        scenario.onActivity { activity ->
            observedUrl = currentWebViewUnsafe(activity.window.decorView)?.url
        }
        return observedUrl
    }

    fun awaitUrl(scenario: ActivityScenario<MainActivity>, url: String) {
        eventually("a real WebView at $url") {
            SystemSettings(context).currentUrl == url && currentWebViewUrl(scenario) == url
        }
    }

    /** Evaluates script in the actual production WebView, never in a synthetic WebViewClient. */
    fun javascript(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = currentWebViewUnsafe(activity.window.decorView)
                ?: error("Production WebView is missing")
            webView.evaluateJavascript(expression) {
                value.set(it)
                done.countDown()
            }
        }
        check(done.await(10, TimeUnit.SECONDS)) { "No real WebView JS callback" }
        return value.get()
    }

    private fun currentWebViewUnsafe(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                currentWebViewUnsafe(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
