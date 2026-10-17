package uk.nktnet.webviewkiosk.blocking

import android.net.Uri
import android.webkit.WebResourceRequest
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.FilterListValidator
import uk.nktnet.webviewkiosk.adblock.NetworkFilterEngine

@RunWith(RobolectricTestRunner::class)
class ServiceWorkerBlockerTest {
    private lateinit var service: ContentBlockingService
    private var lastSource: String? = null

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context, FilterListValidator {})
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
                ): Boolean {
                    lastSource = sourceUrl
                    return Uri.parse(url).host == "ads.example"
                }
            }
        service =
            ContentBlockingService(store, engine, Executor { it.run() }).also {
                it.start()
            }
    }

    @Test
    fun missingProviderControllerDoesNotPreventWebViewCreation() {
        // Robolectric advertises the API but has no real Chromium worker controller.
        ServiceWorkerBlocker.install(service)
        ServiceWorkerBlocker.install(service)
    }

    @Test
    fun usesRequestInitiatorRatherThanAnUnrelatedWebView() {
        assertNotNull(
            ServiceWorkerBlocker.intercept(
                request(mapOf("rEfErEr" to "https://publisher.example/article")),
                service,
            )
        )
        assertEquals("https://publisher.example/article", lastSource)
        assertNotNull(
            ServiceWorkerBlocker.intercept(
                request(mapOf("Origin" to "https://worker.example")),
                service,
            )
        )
        assertEquals("https://worker.example", lastSource)
    }

    @Test
    fun internalOriginsRemainOutsideFiltering() {
        for (headers in
            listOf(
                mapOf("Referer" to "file:///private.html"),
                mapOf("Origin" to "https://appassets.androidplatform.net/"),
                mapOf("Origin" to "https://appassets.androidplatform.net"),
            )) {
            assertNull(ServiceWorkerBlocker.intercept(request(headers), service))
        }
        assertNull(lastSource)
    }

    @Test
    fun missingAndOpaqueInitiatorsArePassedAsUnknownRatherThanInvented() {
        for (headers in listOf(emptyMap(), mapOf("Origin" to "null"))) {
            assertNotNull(ServiceWorkerBlocker.intercept(request(headers), service))
            assertEquals("", lastSource)
        }
    }

    private fun request(headers: Map<String, String>) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("https://ads.example/a.js")

            override fun isForMainFrame() = false

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders() = headers
        }
}
