package uk.nktnet.webviewkiosk.blocking

import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine

@RunWith(RobolectricTestRunner::class)
class WebViewRequestsTest {
    private lateinit var service: ContentBlockingService
    private lateinit var rules: WebViewRequests.Rules

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        okhttp3.OkHttp.initialize(context)
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context)
        store.importFrom(
            "requests.txt",
            ("||ads.example^\$websocket,domain=frame.example\n" +
                    "||beacon.example^\$ping,third-party\n" +
                    "@@||beacon.example/safe\$ping")
                .byteInputStream(),
        )
        service =
            ContentBlockingService(
                    store,
                    KotlinFilterEngine(),
                    Executor { it.run() },
                )
                .also { it.start() }
        rules = WebViewRequests.Rules(service)
    }

    @Test
    fun pageHooksUseLivePolicyAndTheCallingFramesContext() {
        assertTrue(
            rules.shouldBlock(
                "wss://ads.example/socket",
                "https://frame.example/",
                "websocket",
            )
        )
        assertFalse(
            rules.shouldBlock(
                "wss://ads.example/socket",
                "https://top.example/",
                "websocket",
            )
        )
        assertTrue(
            rules.shouldBlock(
                "https://beacon.example/report",
                "https://frame.example/",
                "ping",
            )
        )
        assertFalse(
            rules.shouldBlock(
                "https://beacon.example/safe",
                "https://frame.example/",
                "ping",
            )
        )
        service.setEnabled(false)
        assertFalse(
            rules.shouldBlock(
                "https://beacon.example/report",
                "https://frame.example/",
                "ping",
            )
        )
    }

    @Test
    fun bridgeCannotQueryPrivilegedOriginsOrOtherOperationsAndDisposesImmediately() {
        for (source in
            listOf(
                "file:///private",
                "null",
                "https://appassets.androidplatform.net/",
            )) {
            assertFalse(
                rules.shouldBlock("https://beacon.example/report", source, "ping")
            )
        }
        assertFalse(
            rules.shouldBlock("file:///private", "https://frame.example/", "ping")
        )
        assertFalse(
            rules.shouldBlock(
                "https://beacon.example/report",
                "https://frame.example/",
                "document",
            )
        )
        assertFalse(
            rules.shouldBlock("x".repeat(8193), "https://frame.example/", "ping")
        )
        rules.disposed = true
        assertFalse(
            rules.shouldBlock(
                "wss://ads.example/socket",
                "https://frame.example/",
                "websocket",
            )
        )
    }

    @Test
    fun nativeInitiatorsStayUnknownUnlessHeadersSupplyReliableEvidence() {
        val target = "https://ads.example/a.js"
        val page = "https://frame.example/"
        assertEquals("", WebViewBlocker.initiator(target, page, emptyMap()))
        assertEquals(
            "",
            WebViewBlocker.initiator(target, page, mapOf("Origin" to "null")),
        )
        assertEquals(
            "",
            WebViewBlocker.initiator(
                target,
                page,
                mapOf("Sec-Fetch-Site" to "same-site"),
            ),
        )
        assertEquals(
            "https://ads.example/",
            WebViewBlocker.initiator(
                target,
                "",
                mapOf("Sec-Fetch-Site" to "same-origin"),
            ),
        )
        assertEquals(
            page,
            WebViewBlocker.initiator(
                target,
                "https://top.example/",
                mapOf("Referer" to page),
            ),
        )
        assertEquals(
            "file:///private",
            WebViewBlocker.initiator(
                target,
                page,
                mapOf("Origin" to "file:///private"),
            ),
        )
    }
}
