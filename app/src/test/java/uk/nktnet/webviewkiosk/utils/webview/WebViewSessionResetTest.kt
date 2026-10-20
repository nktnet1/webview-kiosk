package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.option.ResetActionOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewSessionResetTest {
    private lateinit var context: Context
    private lateinit var systemSettings: SystemSettings
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        systemSettings = SystemSettings(context)
        systemSettings.historyStack =
            listOf(HistoryEntry(url = "https://example.com/private"))
        systemSettings.historyIndex = 0
        webView = WebView(context)
    }

    @Test
    fun emptySelectionPreservesHistory() = runBlocking {
        clearSelectedResetData(webView, systemSettings, emptyList())
        assertEquals("https://example.com/private", systemSettings.currentUrl)
    }

    @Test
    fun defaultSelectionClearsHistory() = runBlocking {
        clearSelectedResetData(
            webView,
            systemSettings,
            ResetActionOption.defaults(),
        )
        assertTrue(systemSettings.historyStack.isEmpty())
        assertEquals(-1, systemSettings.historyIndex)
    }

    @Test
    fun cleanupRunsInConfiguredOrder() = runBlocking {
        val calls = mutableListOf<ResetActionOption>()
        val recordingWebView =
            object : WebView(context) {
                override fun clearCache(includeDiskFiles: Boolean) {
                    calls += ResetActionOption.CACHE
                    super.clearCache(includeDiskFiles)
                }

                override fun clearHistory() {
                    calls += ResetActionOption.HISTORY
                    super.clearHistory()
                }
            }

        val selected = listOf(ResetActionOption.CACHE, ResetActionOption.HISTORY)
        clearSelectedResetData(recordingWebView, systemSettings, selected)

        assertEquals(selected, calls)
    }

    @Test
    fun recreatingWebViewAloneDoesNotClearHistory() = runBlocking {
        clearSelectedResetData(
            webView,
            systemSettings,
            listOf(ResetActionOption.RECREATE_WEBVIEW),
        )
        assertEquals("https://example.com/private", systemSettings.currentUrl)
    }
}
