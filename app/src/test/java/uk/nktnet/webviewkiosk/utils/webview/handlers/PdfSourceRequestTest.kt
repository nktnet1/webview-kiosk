package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.webview.HttpAuthRequest
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Checks actual PDF source routing and HTTP behaviour, not just URL helper functions. */
@RunWith(RobolectricTestRunner::class)
class PdfSourceRequestTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val base = Constants.PDF_JS_ASSETS_DUMMY_URL
    private lateinit var settings: UserSettings
    private var originalAllowLocalFiles = false
    private lateinit var server: HttpServer
    private lateinit var remote: String
    private val createdTokens = mutableListOf<String>()

    @Before
    fun setUp() {
        settings = UserSettings(context)
        originalAllowLocalFiles = settings.allowLocalFiles
        settings.allowLocalFiles = false
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        remote = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
        settings.allowLocalFiles = originalAllowLocalFiles
        // Registry entries are intentionally long-lived in production; don't share them between tests.
        val registry = Class.forName(
            "uk.nktnet.webviewkiosk.utils.webview.handlers.HandlePdfSourceRequestKt",
        ).getDeclaredField("registeredPdfSources").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val sources = registry.get(null) as MutableMap<String, Any>
        createdTokens.forEach { sources.remove(it) }
    }

    @Test
    fun requiresARegisteredTokenAndTheExactInternalOriginAndPath() {
        val valid = register("$remote/report.pdf")
        assertEquals(400, load(null, requestUrl = "$base/pdf_source")?.statusCode)
        assertEquals(404, load("not-registered")?.statusCode)

        val invalid = listOf(
            "http://pdf-dummy.webviewkiosk.nktnet.uk/pdf_source",
            "https://pdf-dummy.webviewkiosk.nktnet.uk:8443/pdf_source",
            "https://pdf-dummy.webviewkiosk.nktnet.uk.evil.example/pdf_source",
            "https://user@pdf-dummy.webviewkiosk.nktnet.uk/pdf_source",
            "$base/other",
        )
        for (origin in invalid) {
            assertNull(origin, load(valid, requestUrl = "$origin?wk_pdf_token=$valid"))
        }
    }

    @Test
    fun navigationRecognizesOnlyTheOwnTokenAndExactSourceUrl() {
        val sourceUrl = "$remote/report.pdf?name=one%20two"
        val token = register(sourceUrl)
        val otherToken = register(sourceUrl)
        val viewerUrl = "$base/?wk_pdf_token=$token&wk_pdf_url=${Uri.encode(sourceUrl)}"

        assertTrue(isPdfSourceNavigation(token, sourceUrl))
        assertTrue(isPdfSourceNavigation(token, viewerUrl))
        assertFalse(isPdfSourceNavigation(otherToken, viewerUrl))
        val otherSource = "$base/?wk_pdf_token=$token&wk_pdf_url=${Uri.encode("$remote/other.pdf")}"
        assertFalse(isPdfSourceNavigation(token, otherSource))
        assertFalse(isPdfSourceNavigation(token, "$base/other?wk_pdf_token=$token&wk_pdf_url=${Uri.encode(sourceUrl)}"))
        assertFalse(isPdfSourceNavigation("missing", sourceUrl))
    }

    @Test
    fun viewerStatusAcceptsOnlyKnownEventsWithoutFetchingTheDocument() {
        val sourceUrl = "$remote/not-fetched.pdf"
        val token = register(sourceUrl)
        val events = mutableListOf<String>()
        for (event in listOf("loaded", "error", "authentication", "retry")) {
            val result = load(
                token,
                requestUrl = "$base/pdf_status?wk_pdf_token=$token&event=$event",
                onViewerEvent = { receivedToken, receivedUrl, receivedEvent ->
                    assertEquals(token, receivedToken)
                    assertEquals(sourceUrl, receivedUrl)
                    events.add(receivedEvent)
                },
            )
            assertEquals(200, result?.statusCode)
        }
        assertEquals(listOf("loaded", "error", "authentication", "retry"), events)
        assertEquals(400, load(token, requestUrl = "$base/pdf_status?wk_pdf_token=$token&event=unknown")?.statusCode)
    }

    @Test
    fun localFilesRespectPolicyAndReturnOnlyTheirContentsWhenEnabled() {
        val bytes = "%PDF-1.7\nlocal test\n".toByteArray()
        val file = temporaryFolder.newFile("local.pdf").apply { writeBytes(bytes) }
        val token = register(file.toURI().toString())

        assertEquals(403, load(token)?.statusCode)
        settings.allowLocalFiles = true
        val response = load(token)
        assertNotNull(response)
        assertEquals("application/pdf", response?.mimeType)
        assertArrayEquals(bytes, response!!.data.use { it.readBytes() })
    }

    @Test
    fun forwardsRangeHeadersAndUserAgentAndPreservesPartialContent() {
        val incoming = CopyOnWriteArrayList<Map<String, String?>>()
        server.createContext("/partial.pdf") { exchange ->
            incoming.add(
                listOf("Range", "If-Range", "Accept", "User-Agent").associateWith {
                    exchange.requestHeaders.getFirst(it)
                },
            )
            respond(exchange, 206, "PDF bytes", "Content-Range" to "bytes 2-10/20")
        }
        val token = register("$remote/partial.pdf")

        val result = load(
            token,
            headers = mapOf("Range" to "bytes=2-10", "If-Range" to "etag-v1", "Accept" to "application/pdf"),
            userAgent = "Kiosk-Pdf-Test/1.0",
        )
        assertEquals(206, result?.statusCode)
        assertEquals("PDF bytes", result!!.data.use { it.bufferedReader().readText() })
        assertEquals(
            mapOf(
                "Range" to "bytes=2-10",
                "If-Range" to "etag-v1",
                "Accept" to "application/pdf",
                "User-Agent" to "Kiosk-Pdf-Test/1.0",
            ),
            incoming.single(),
        )
        assertEquals("no-store", result.responseHeaders["Cache-Control"])
    }

    @Test
    fun rechecksTheBlocklistBeforeFollowingARedirect() {
        val forbiddenRequests = AtomicInteger()
        server.createContext("/entry") { exchange ->
            respond(exchange, 302, "redirect", "Location" to "$remote/forbidden.pdf")
        }
        server.createContext("/forbidden.pdf") { exchange ->
            forbiddenRequests.incrementAndGet()
            respond(exchange, 200, "should never be served")
        }
        val token = register("$remote/entry")

        assertEquals(403, load(token, blacklist = listOf(Regex("/forbidden\\.pdf$")))?.statusCode)
        assertEquals(0, forbiddenRequests.get())
    }

    @Test
    fun redirectLoopsHaveAFiniteLimit() {
        val requests = AtomicInteger()
        server.createContext("/loop") { exchange ->
            requests.incrementAndGet()
            respond(exchange, 302, "again", "Location" to "/loop")
        }
        val token = register("$remote/loop")

        assertEquals(508, load(token)?.statusCode)
        assertEquals(6, requests.get())
    }

    @Test
    fun successfulBasicAuthPersistsWithinTheRegisteredDocument() {
        val authorization = CopyOnWriteArrayList<String?>()
        server.createContext("/private.pdf") { exchange ->
            val header = exchange.requestHeaders.getFirst("Authorization")
            authorization.add(header)
            if (header == "Basic YWxpY2U6c2VjcmV0") {
                respond(exchange, 200, "private PDF")
            } else {
                respond(
                    exchange, 401, "authenticate",
                    "WWW-Authenticate" to "Basic realm=\"private, reports\", charset=\"UTF-8\"",
                )
            }
        }
        val url = "$remote/private.pdf"
        val token = register(url)
        val prompts = mutableListOf<HttpAuthRequest>()
        val authenticated = mutableListOf<String>()
        val onPrompt: (String, String, HttpAuthRequest) -> Unit = { callbackToken, callbackUrl, request ->
            assertEquals(token, callbackToken)
            assertEquals(url, callbackUrl)
            prompts.add(request)
        }

        assertEquals(
            401,
            load(
                token,
                onAuthenticationRequired = onPrompt,
                onAuthenticated = { completedToken, completedUrl ->
                    authenticated.add("$completedToken:$completedUrl")
                },
            )?.statusCode,
        )
        assertEquals(1, prompts.size)
        assertEquals("private, reports", prompts.single().realm)
        prompts.single().proceed("alice", "secret")
        assertEquals(listOf("$token:$url"), authenticated)

        val success = load(token, onAuthenticationRequired = onPrompt)
        assertEquals(200, success?.statusCode)
        assertEquals("private PDF", success!!.data.use { it.bufferedReader().readText() })
        assertEquals(1, prompts.size)
        assertTrue(authorization.contains("Basic YWxpY2U6c2VjcmV0"))
    }

    @Test
    fun credentialsAreNotForwardedWhenAnAuthenticatedSourceRedirectsToAnotherOrigin() {
        val secondServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        secondServer.start()
        try {
            val forwardedAuthorization = CopyOnWriteArrayList<String?>()
            secondServer.createContext("/final.pdf") { exchange ->
                forwardedAuthorization.add(exchange.requestHeaders.getFirst("Authorization"))
                respond(exchange, 200, "redirected PDF")
            }
            server.createContext("/start.pdf") { exchange ->
                if (exchange.requestHeaders.getFirst("Authorization") == "Basic YWxpY2U6c2VjcmV0") {
                    respond(
                        exchange, 302, "redirect",
                        "Location" to "http://127.0.0.1:${secondServer.address.port}/final.pdf",
                    )
                } else {
                    respond(exchange, 401, "authenticate", "WWW-Authenticate" to "Basic realm=\"private\"")
                }
            }
            val token = register("$remote/start.pdf")
            val prompts = mutableListOf<HttpAuthRequest>()
            val prompt: (String, String, HttpAuthRequest) -> Unit = { _, _, request -> prompts.add(request) }

            assertEquals(401, load(token, onAuthenticationRequired = prompt)?.statusCode)
            prompts.single().proceed("alice", "secret")
            val response = load(token, onAuthenticationRequired = prompt)
            assertEquals(200, response?.statusCode)
            assertEquals("redirected PDF", response!!.data.use { it.bufferedReader().readText() })
            assertEquals(listOf(null), forwardedAuthorization.toList())
            assertEquals(1, prompts.size)
        } finally {
            secondServer.stop(0)
        }
    }

    @Test
    fun rejectedSavedCredentialsAreClearedBeforePromptingAgain() {
        server.createContext("/private.pdf") { exchange ->
            if (exchange.requestHeaders.getFirst("Authorization") == "Basic YWxpY2U6c2VjcmV0") {
                respond(exchange, 200, "ok")
            } else {
                respond(exchange, 401, "no", "WWW-Authenticate" to "Basic realm=\"private\"")
            }
        }
        val token = register("$remote/private.pdf")
        val prompts = mutableListOf<HttpAuthRequest>()
        val prompt: (String, String, HttpAuthRequest) -> Unit = { _, _, request -> prompts.add(request) }

        assertEquals(401, load(token, onAuthenticationRequired = prompt)?.statusCode)
        prompts.single().proceed("wrong", "password")
        assertEquals(401, load(token, onAuthenticationRequired = prompt)?.statusCode)
        assertEquals(2, prompts.size)
        prompts.last().proceed("alice", "secret")
        assertEquals(200, load(token, onAuthenticationRequired = prompt)?.statusCode)
        assertEquals(2, prompts.size)
    }

    private fun register(sourceUrl: String): String = registerPdfSource(sourceUrl).also(createdTokens::add)

    private fun load(
        token: String?,
        requestUrl: String = "$base/pdf_source?wk_pdf_token=${token.orEmpty()}",
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        blacklist: List<Regex> = emptyList(),
        onAuthenticationRequired: (String, String, HttpAuthRequest) -> Unit = { _, _, request -> request.cancel() },
        onAuthenticated: (String, String) -> Unit = { _, _ -> },
        onViewerEvent: (String, String, String) -> Unit = { _, _, _ -> },
    ) = handlePdfSourceRequest(
        context = context,
        request = object : WebResourceRequest {
            override fun getUrl() = Uri.parse(requestUrl)
            override fun isForMainFrame() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = headers
            override fun isRedirect() = false
        },
        userAgent = userAgent,
        userSettings = settings,
        blacklistRegexes = blacklist,
        whitelistRegexes = emptyList(),
        onAuthenticationRequired = onAuthenticationRequired,
        onAuthenticated = onAuthenticated,
        onViewerEvent = onViewerEvent,
    )

    private fun respond(exchange: HttpExchange, code: Int, body: String, vararg headers: Pair<String, String>) {
        exchange.responseHeaders.set("Content-Type", "application/pdf")
        for ((name, value) in headers) exchange.responseHeaders.set(name, value)
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
