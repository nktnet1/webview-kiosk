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
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.FilterListValidator
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.adblock.NetworkFilterEngine

@RunWith(RobolectricTestRunner::class)
class WebViewBlockerTest {
    private lateinit var service: ContentBlockingService

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        okhttp3.OkHttp.initialize(context)
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
    fun blocksHttpDocumentsAndOnlySubresourcesOnKnownWebPages() {
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
        assertTrue(
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
        store.remove(store.list().map { it.id }.toSet())
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

    @Test
    fun bundledRedirectResourcesRetainMimeTypesBinaryBytesAndJsonBody() {
        val resources = BlockingResponse.entries.filter { it.resourcePath != null }
        val context = RuntimeEnvironment.getApplication()
        val store = FilterListStore(context)
        val text =
            resources
                .mapIndexed { index, response ->
                    "||r$index.example^\$redirect=${response.resourcePath}"
                }
                .joinToString("\n")
        store.importFrom("resources.txt", text.byteInputStream())
        val blocker =
            ContentBlockingService(
                    store,
                    KotlinFilterEngine(),
                    Executor { it.run() },
                )
                .also { it.start() }
        resources.forEachIndexed { index, expected ->
            val mime = requireNotNull(expected.mimeType)
            val response =
                WebViewBlocker.intercept(
                    request(
                        "https://r$index.example/resource",
                        "script",
                    ),
                    "https://publisher.example/",
                    blocker,
                )!!
            assertEquals(expected.mimeType, response.mimeType)
            val bytes = response.data.readBytes()
            assertTrue(bytes.isNotEmpty())
            if (
                mime.startsWith("audio/") ||
                    mime.startsWith("video/") ||
                    mime.startsWith("image/")
            ) {
                assertNull(response.encoding)
            }
            if (expected == BlockingResponse.EMPTY_JSON) {
                assertEquals("{}", bytes.toString(Charsets.UTF_8).trim())
            }
        }
    }

    @Test
    fun redirectsImagesWithCorrectMimeTypesAndRealImageBytes() {
        val context = RuntimeEnvironment.getApplication()
        val store = FilterListStore(context)
        store.importFrom(
            "images.txt",
            ("||pixel.example^\$image,redirect=1x1.gif\n" +
                    "||square.example^\$image,redirect=2x2.png")
                .byteInputStream(),
        )
        val blocker =
            ContentBlockingService(
                    store,
                    KotlinFilterEngine(),
                    Executor { it.run() },
                )
                .also { it.start() }
        val gif =
            WebViewBlocker.intercept(
                request("https://pixel.example/ad.gif", "image"),
                "https://site.example/",
                blocker,
            )!!
        assertEquals("image/gif", gif.mimeType)
        assertEquals('G'.code, gif.data.read())
        val png =
            WebViewBlocker.intercept(
                request("https://square.example/ad.png", "image"),
                "https://site.example/",
                blocker,
            )!!
        assertEquals("image/png", png.mimeType)
        assertEquals(0x89, png.data.read())
    }

    @Test
    fun queryRemovalOnlyRewritesGetDocumentsAndKeepsOriginalBlockDecisions() {
        val blocker = realService("*\$removeparam=track\n||blocked.example^\$doc")
        val url = "https://site.example/?track=1&keep=2"
        assertEquals(
            "https://site.example/?keep=2",
            WebViewBlocker.documentUrl(url, "GET", blocker),
        )
        assertEquals(url, WebViewBlocker.documentUrl(url, "POST", blocker))
        assertEquals(url, WebViewBlocker.documentUrl(url, "HEAD", blocker))
        val blocked = "https://blocked.example/?track=1"
        assertEquals(blocked, WebViewBlocker.documentUrl(blocked, "GET", blocker))
        val internal = "https://appassets.androidplatform.net/?track=1"
        assertEquals(internal, WebViewBlocker.documentUrl(internal, "GET", blocker))
        assertNull(
            WebViewBlocker.intercept(
                request(url, "script"),
                "https://site.example/",
                blocker,
            )
        )
        blocker.setEnabled(false)
        assertEquals(url, WebViewBlocker.documentUrl(url, "GET", blocker))
    }

    @Test
    fun documentsUseTheirOwnContextAndActualMethodRegardlessOfHeaders() {
        val blocker =
            realService(
                "||forms.example^\$doc,method=POST,domain=forms.example,~third-party\n" +
                    "@@||forms.example/safe\$document"
            )
        val blocked =
            WebViewBlocker.shouldBlockDocument(
                "https://forms.example/send",
                "POST",
                blocker,
            )
        assertTrue(blocker.lastError.value, blocked)
        assertNull(
            WebViewBlocker.intercept(
                request("https://forms.example/send", "document", true),
                "",
                blocker,
            )
        )
        val response =
            WebViewBlocker.intercept(
                request("https://forms.example/send", "image", true, "POST"),
                "about:blank",
                blocker,
            )!!
        assertEquals(403, response.statusCode)
        assertEquals("text/html", response.mimeType)
        assertEquals("UTF-8", response.encoding)
        assertEquals("no-store", response.responseHeaders["Cache-Control"])
        assertEquals("nosniff", response.responseHeaders["X-Content-Type-Options"])
        assertTrue(
            response.data
                .bufferedReader()
                .use { it.readText() }
                .contains("Access Blocked")
        )
        assertNull(
            WebViewBlocker.intercept(
                request("https://forms.example/safe", "document", true, "POST"),
                "",
                blocker,
            )
        )
        assertNull(
            WebViewBlocker.intercept(
                request("https://forms.example/send", "document", false, "POST"),
                "https://forms.example/",
                blocker,
            )
        )
    }

    @Test
    fun interceptedDocumentsUseProvidedBlockPageAndRespectDisable() {
        val url = "https://ads.example/"
        val response =
            WebViewBlocker.intercept(request(url, "document", true), "", service) {
                assertEquals(url, it)
                "<html>Custom block page</html>"
            }!!
        assertEquals(
            "<html>Custom block page</html>",
            response.data.bufferedReader().use { it.readText() },
        )
        service.setEnabled(false)
        assertFalse(WebViewBlocker.shouldBlockDocument(url, "GET", service))
        assertNull(
            WebViewBlocker.intercept(request(url, "document", true), "", service)
        )
    }

    @Test
    fun documentsKeepInternalAssetsAndNonWebSchemesExempt() {
        listOf(
                "https://appassets.androidplatform.net",
                "https://pdf-dummy.webviewkiosk.nktnet.uk/pdf.js",
                "file:///sdcard/index.html",
                "about:blank",
                "data:text/html,hello",
                "blob:https://ads.example/123",
            )
            .forEach {
                assertFalse(
                    it,
                    WebViewBlocker.shouldBlockDocument(it, "GET", service),
                )
            }
    }

    private fun realService(text: String): ContentBlockingService {
        val store = FilterListStore(RuntimeEnvironment.getApplication())
        store.remove(store.list().map { it.id }.toSet())
        store.importFrom("documents.txt", text.byteInputStream())
        return ContentBlockingService(
                store,
                KotlinFilterEngine(),
                Executor { it.run() },
            )
            .also {
                it.start()
                assertEquals(
                    it.lastError.value,
                    BlockingStatus.ACTIVE,
                    it.status.value,
                )
                assertEquals(0, it.skippedRules.value)
            }
    }

    private fun request(
        url: String,
        type: String,
        mainFrame: Boolean = false,
        method: String = "GET",
    ) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = method

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
