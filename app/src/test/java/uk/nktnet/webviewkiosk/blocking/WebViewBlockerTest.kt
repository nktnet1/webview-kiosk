package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class WebViewBlockerTest {
    private lateinit var service: ContentBlockingService

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context, validator = FilterListValidator {})
        store.importFrom("ads.txt", "||ads.example^".byteInputStream())
        val engine = object : NetworkFilterEngine {
            override fun replace(lists: List<String>) = Unit
            override fun clear() = Unit
            override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String) = true
        }
        service = ContentBlockingService(store, engine, Executor { it.run() }).also { it.start() }
    }

    @Test
    fun blocksOnlyHttpSubresourcesOnKnownWebPages() {
        val target = "https://ads.example/pixel"
        val page = "https://publisher.example/index"
        assertTrue(WebViewBlocker.shouldBlock(target, false, page, emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock(target, true, page, emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock(target, false, "", emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock("file:///sdcard/x.js", false, page, emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock("https://appassets.androidplatform.net/web-content-files/a.js", false, page, emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock("https://pdf-dummy.webviewkiosk.nktnet.uk/pdf.js", false, page, emptyMap(), "GET", service))
        assertFalse(WebViewBlocker.shouldBlock("blob:https://ads.example/123", false, page, emptyMap(), "GET", service))
    }

    @Test
    fun onlyUsesKnownResourceTypes() {
        assertEquals("script", WebViewBlocker.resourceType(mapOf("Sec-Fetch-Dest" to "script")))
        assertEquals("image", WebViewBlocker.resourceType(mapOf("accept" to "image/avif,image/webp")))
        assertEquals("stylesheet", WebViewBlocker.resourceType(mapOf("ACCEPT" to "text/css,*/*")))
        assertEquals("other", WebViewBlocker.resourceType(mapOf("accept" to "*/*")))
    }
}
