package uk.nktnet.webviewkiosk.blocking

import android.net.Uri
import android.webkit.WebResourceRequest
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.FilterListValidator
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.adblock.NetworkFilterEngine

@RunWith(RobolectricTestRunner::class)
class WebViewBlockerTest {
    private lateinit var service: ContentBlockingService

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context, validator = FilterListValidator {})
        store.importFrom("ads.txt", "||ads.example^".byteInputStream())
        val engine =
            object : NetworkFilterEngine {
                override fun replace(lists: List<String>) = Unit

                override fun clear() = Unit

                override fun shouldBlock(
                    url: String,
                    sourceUrl: String,
                    type: String,
                    method: String,
                ) = true
            }
        service =
            ContentBlockingService(store, engine, Executor { it.run() }).also {
                it.start()
            }
    }

    @Test
    fun blocksOnlyHttpSubresourcesOnKnownWebPages() {
        val target = "https://ads.example/pixel"
        val page = "https://publisher.example/index"
        assertTrue(
            WebViewBlocker.shouldBlock(
                target,
                false,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
        assertFalse(
            WebViewBlocker.shouldBlock(
                target,
                true,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
        assertFalse(
            WebViewBlocker.shouldBlock(target, false, "", emptyMap(), "GET", service)
        )
        assertFalse(
            WebViewBlocker.shouldBlock(
                "file:///sdcard/x.js",
                false,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
        assertFalse(
            WebViewBlocker.shouldBlock(
                "https://appassets.androidplatform.net/web-content-files/a.js",
                false,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
        assertFalse(
            WebViewBlocker.shouldBlock(
                "https://pdf-dummy.webviewkiosk.nktnet.uk/pdf.js",
                false,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
        assertFalse(
            WebViewBlocker.shouldBlock(
                "blob:https://ads.example/123",
                false,
                page,
                emptyMap(),
                "GET",
                service,
            )
        )
    }

    @Test
    fun webViewReturnsCorrectMimeTypesForNoopResources() {
        val context = RuntimeEnvironment.getApplication()
        val store = FilterListStore(context)
        store.importFrom(
            "surrogates.txt",
            ("||ads.example^\$script,redirect=noopjs\n" +
                    "||styles.example^\$stylesheet,redirect=noopcss\n" +
                    "@@||ads.example/allowed.js\$script")
                .byteInputStream(),
        )
        val blocker =
            ContentBlockingService(
                    store,
                    KotlinFilterEngine(),
                    Executor { it.run() },
                )
                .also { it.start() }
        val source = "https://publisher.example/"
        val script =
            WebViewBlocker.intercept(
                request(
                    "https://ads.example/blocked.js",
                    "script",
                ),
                source,
                blocker,
            )
        assertEquals("application/javascript", script!!.mimeType)
        assertEquals(-1, script.data.read())
        assertEquals(
            "text/css",
            WebViewBlocker.intercept(
                    request(
                        "https://styles.example/ad.css",
                        "style",
                    ),
                    source,
                    blocker,
                )!!
                .mimeType,
        )
        assertNull(
            WebViewBlocker.intercept(
                request(
                    "https://ads.example/allowed.js",
                    "script",
                ),
                source,
                blocker,
            )
        )
        assertNull(
            WebViewBlocker.intercept(
                request(
                    "https://ads.example/main",
                    "script",
                    mainFrame = true,
                ),
                source,
                blocker,
            )
        )
    }

    private fun request(url: String, type: String, mainFrame: Boolean = false) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders(): Map<String, String> =
                mapOf("Sec-Fetch-Dest" to type)
        }

    @Test
    fun onlyUsesKnownResourceTypes() {
        assertEquals(
            "script",
            WebViewBlocker.resourceType(mapOf("Sec-Fetch-Dest" to "script")),
        )
        assertEquals(
            "image",
            WebViewBlocker.resourceType(mapOf("accept" to "image/avif,image/webp")),
        )
        assertEquals(
            "stylesheet",
            WebViewBlocker.resourceType(mapOf("ACCEPT" to "text/css,*/*")),
        )
        assertEquals("other", WebViewBlocker.resourceType(mapOf("accept" to "*/*")))
    }
}
