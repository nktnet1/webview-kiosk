package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.util.concurrent.TimeUnit
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
    private lateinit var cookies: CookieManager
    private var originalAcceptCookies = false
    private lateinit var savedSources: Map<String, Any>
    private lateinit var server: HttpServer
    private lateinit var remote: String
    private val createdTokens = mutableListOf<String>()

    @Before
    fun setUp() {
        settings = UserSettings(context)
        originalAllowLocalFiles = settings.allowLocalFiles
        settings.allowLocalFiles = false
        cookies = CookieManager.getInstance()
        originalAcceptCookies = cookies.acceptCookie()
        cookies.removeAllCookies(null)
        cookies.setAcceptCookie(true)
        // Capacity tests must not evict a source registered by another fixture.
        savedSources = sources().toMap()
        sources().clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        remote = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
        settings.allowLocalFiles = originalAllowLocalFiles
        cookies.removeAllCookies(null)
        cookies.setAcceptCookie(originalAcceptCookies)
        val sources = sources()
        createdTokens.forEach { token ->
            sources[token]?.let { source ->
                pendingAuthentication(source).values.toList().forEach { it.cancel() }
            }
            cancelPdfSourceRequests(token)
            sources.remove(token)
        }
        sources.putAll(savedSources)
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
    fun sendsTheSourceHostsWebViewCookiesInsteadOfInternalRequestCookies() {
        val incoming = CopyOnWriteArrayList<String?>()
        val url = "$remote/session.pdf"
        cookies.setCookie(url, "session=source-secret; Path=/; HttpOnly")
        cookies.setCookie(url, "language=en; Path=/")
        cookies.setCookie(base, "viewer=internal-secret; Path=/")
        server.createContext("/session.pdf") { exchange ->
            incoming.add(exchange.requestHeaders.getFirst("Cookie"))
            respond(exchange, 200, "session PDF")
        }

        val response = load(register(url), headers = mapOf("Cookie" to "forged=request-cookie"))

        assertEquals(200, response?.statusCode)
        assertEquals("session PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(setOf("session=source-secret", "language=en"), cookiePairs(incoming.single()))
        assertEquals("viewer=internal-secret", cookies.getCookie(base))
    }

    @Test
    fun storesEveryResponseCookieWithoutExposingSetCookieOnTheInternalOrigin() {
        val url = "$remote/cookies.pdf"
        server.createContext("/cookies.pdf") { exchange ->
            exchange.responseHeaders.add("sEt-CoOkIe", "session=server-secret; Path=/; HttpOnly")
            exchange.responseHeaders.add("sEt-CoOkIe", "language=fr; Path=/")
            respond(exchange, 200, "cookie PDF", "X-Pdf-Test" to "preserved")
        }

        val response = load(register(url))

        assertEquals(200, response?.statusCode)
        assertEquals("cookie PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(setOf("session=server-secret", "language=fr"), cookiePairs(cookies.getCookie(url)))
        assertNull(cookies.getCookie(base))
        assertTrue(response.responseHeaders.keys.none { it.equals("Set-Cookie", ignoreCase = true) })
        assertEquals("preserved", response.responseHeaders.entries.single {
            it.key.equals("X-Pdf-Test", ignoreCase = true)
        }.value)
        assertEquals("no-store", response.responseHeaders["Cache-Control"])
    }

    @Test
    fun redirectCookiesReplaceTheSessionBeforeTheNextHttpRequest() {
        val incoming = CopyOnWriteArrayList<String?>()
        cookies.setCookie(remote, "session=old; Path=/")
        server.createContext("/cookie-entry") { exchange ->
            incoming.add(exchange.requestHeaders.getFirst("Cookie"))
            respond(
                exchange, 302, "redirect",
                "Location" to "/cookie-final.pdf",
                "Set-Cookie" to "session=new; Path=/; HttpOnly",
            )
        }
        server.createContext("/cookie-final.pdf") { exchange ->
            val cookie = exchange.requestHeaders.getFirst("Cookie")
            incoming.add(cookie)
            if (cookiePairs(cookie) == setOf("session=new")) {
                respond(exchange, 200, "redirected session PDF", "Set-Cookie" to "download=ready; Path=/")
            } else {
                respond(exchange, 403, "missing redirected session")
            }
        }

        val response = load(register("$remote/cookie-entry"))

        assertEquals(200, response?.statusCode)
        assertEquals("redirected session PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(listOf(setOf("session=old"), setOf("session=new")), incoming.map(::cookiePairs))
        assertEquals(setOf("session=new", "download=ready"), cookiePairs(cookies.getCookie(remote)))
    }

    @Test
    fun redirectsUseTheDestinationHostsCookiesWithoutLeakingTheSourcesSession() {
        val destinationServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        destinationServer.start()
        try {
            val destination = "http://localhost:${destinationServer.address.port}"
            val incoming = CopyOnWriteArrayList<String?>()
            cookies.setCookie(remote, "source=private; Path=/")
            cookies.setCookie(destination, "destination=allowed; Path=/")
            server.createContext("/host-entry") { exchange ->
                incoming.add(exchange.requestHeaders.getFirst("Cookie"))
                respond(exchange, 302, "redirect", "Location" to "$destination/host-final.pdf")
            }
            destinationServer.createContext("/host-final.pdf") { exchange ->
                incoming.add(exchange.requestHeaders.getFirst("Cookie"))
                respond(exchange, 200, "other host PDF", "Set-Cookie" to "received=target; Path=/")
            }

            val response = load(register("$remote/host-entry"))

            assertEquals(200, response?.statusCode)
            assertEquals("other host PDF", response!!.data.use { it.bufferedReader().readText() })
            assertEquals(
                listOf(setOf("source=private"), setOf("destination=allowed")),
                incoming.map(::cookiePairs),
            )
            assertEquals(setOf("source=private"), cookiePairs(cookies.getCookie(remote)))
            assertEquals(setOf("destination=allowed", "received=target"), cookiePairs(cookies.getCookie(destination)))
        } finally {
            destinationServer.stop(0)
        }
    }

    @Test
    fun disabledCookiesAreNeitherSentNorStoredAcrossRedirects() {
        val incoming = CopyOnWriteArrayList<String?>()
        cookies.setCookie(remote, "session=kept; Path=/")
        cookies.setAcceptCookie(false)
        server.createContext("/disabled-entry") { exchange ->
            incoming.add(exchange.requestHeaders.getFirst("Cookie"))
            respond(
                exchange, 302, "redirect",
                "Location" to "/disabled-final.pdf",
                "Set-Cookie" to "session=overwritten; Path=/",
            )
        }
        server.createContext("/disabled-final.pdf") { exchange ->
            incoming.add(exchange.requestHeaders.getFirst("Cookie"))
            respond(exchange, 200, "cookies disabled PDF", "Set-Cookie" to "unexpected=stored; Path=/")
        }

        val response = load(register("$remote/disabled-entry"))

        assertEquals(200, response?.statusCode)
        assertEquals("cookies disabled PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(listOf(null, null), incoming.toList())
        assertTrue(response.responseHeaders.keys.none { it.equals("Set-Cookie", ignoreCase = true) })
        cookies.setAcceptCookie(true)
        assertEquals("session=kept", cookies.getCookie(remote))
    }

    @Test
    fun authenticationResponseCookiesAreSharedWithTheAuthenticatedRetry() {
        val incoming = CopyOnWriteArrayList<String?>()
        server.createContext("/cookie-auth.pdf") { exchange ->
            val cookie = exchange.requestHeaders.getFirst("Cookie")
            incoming.add(cookie)
            if (exchange.requestHeaders.getFirst("Authorization") == "Basic YWxpY2U6c2VjcmV0"
                && cookiePairs(cookie) == setOf("challenge=present")
            ) {
                respond(exchange, 200, "authenticated cookie PDF")
            } else {
                respond(
                    exchange, 401, "authenticate",
                    "WWW-Authenticate" to "Basic realm=\"cookie session\"",
                    "Set-Cookie" to "challenge=present; Path=/; HttpOnly",
                )
            }
        }
        val token = register("$remote/cookie-auth.pdf")
        val prompts = mutableListOf<HttpAuthRequest>()
        val prompt: (String, String, HttpAuthRequest) -> Unit = { _, _, request -> prompts.add(request) }

        assertStatus(401, load(token, onAuthenticationRequired = prompt))
        assertEquals("challenge=present", cookies.getCookie(remote))
        prompts.single().proceed("alice", "secret")
        val response = load(token, onAuthenticationRequired = prompt)

        assertEquals(200, response?.statusCode)
        assertEquals("authenticated cookie PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(listOf(null, "challenge=present", "challenge=present"), incoming.toList())
        assertEquals(1, prompts.size)
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

    @Test
    fun expiredSourcesCancelPendingAuthenticationWithoutMakingAnotherHttpRequest() {
        val requests = AtomicInteger()
        server.createContext("/expired.pdf") { exchange ->
            requests.incrementAndGet()
            respond(exchange, 401, "authenticate", "WWW-Authenticate" to "Basic realm=\"expired\"")
        }
        val token = register("$remote/expired.pdf")
        val registered = source(token)
        val prompts = mutableListOf<HttpAuthRequest>()
        val authenticated = AtomicInteger()
        assertStatus(401, load(
            token,
            onAuthenticationRequired = { _, _, request -> prompts.add(request) },
            onAuthenticated = { _, _ -> authenticated.incrementAndGet() },
        ))
        assertEquals(1, pendingAuthentication(registered).size)
        setExpiresAt(registered, 0)

        assertStatus(404, load(token))
        assertFalse(sources().containsKey(token))
        assertTrue(pendingAuthentication(registered).isEmpty())
        prompts.single().proceed("alice", "secret")

        assertEquals(1, requests.get())
        assertTrue(credentials(registered).isEmpty())
        assertEquals(0, authenticated.get())
    }

    @Test
    fun lateAuthenticationCannotSaveCredentialsAfterExpiryEvenBeforeRegistryCleanup() {
        val requests = AtomicInteger()
        server.createContext("/late-auth.pdf") { exchange ->
            requests.incrementAndGet()
            respond(exchange, 401, "authenticate", "WWW-Authenticate" to "Basic realm=\"late\"")
        }
        val token = register("$remote/late-auth.pdf")
        val registered = source(token)
        val prompts = mutableListOf<HttpAuthRequest>()
        val authenticated = AtomicInteger()
        assertStatus(401, load(
            token,
            onAuthenticationRequired = { _, _, request -> prompts.add(request) },
            onAuthenticated = { _, _ -> authenticated.incrementAndGet() },
        ))
        setExpiresAt(registered, 0)

        prompts.single().proceed("alice", "secret")

        assertTrue(sources().containsKey(token))
        assertTrue(pendingAuthentication(registered).isEmpty())
        assertTrue(credentials(registered).isEmpty())
        assertEquals(0, authenticated.get())
        assertStatus(404, load(token))
        assertEquals(1, requests.get())
    }

    @Test
    fun readingAnActiveSourceRenewsItsRegistrationForTwentyFourHours() {
        server.createContext("/active.pdf") { exchange -> respond(exchange, 200, "active PDF") }
        val token = register("$remote/active.pdf")
        val registered = source(token)
        setExpiresAt(registered, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
        val before = System.currentTimeMillis()

        val response = load(token)

        assertEquals(200, response?.statusCode)
        assertEquals("active PDF", response!!.data.use { it.bufferedReader().readText() })
        val after = System.currentTimeMillis()
        val renewed = expiresAt(registered)
        val lifetime = TimeUnit.HOURS.toMillis(24)
        assertTrue(renewed >= before + lifetime)
        assertTrue(renewed <= after + lifetime)
        assertTrue(sources().containsKey(token))
    }

    @Test
    fun retryEventsStillReachTheViewerAfterItsSourceHasExpired() {
        val requests = AtomicInteger()
        server.createContext("/retry.pdf") { exchange ->
            requests.incrementAndGet()
            respond(exchange, 200, "must not be fetched")
        }
        val token = register("$remote/retry.pdf")
        setExpiresAt(source(token), 0)
        val events = mutableListOf<Triple<String, String, String>>()

        assertStatus(200, load(
            token,
            requestUrl = "$base/pdf_status?wk_pdf_token=$token&event=retry",
            onViewerEvent = { receivedToken, url, event -> events.add(Triple(receivedToken, url, event)) },
        ))

        assertEquals(listOf(Triple(token, "", "retry")), events)
        assertFalse(sources().containsKey(token))
        assertStatus(404, load(token))
        assertEquals(0, requests.get())
    }

    @Test
    fun registrationPrunesExpiredAuthenticationButKeepsAnActiveViewersPrompt() {
        server.createContext("/cleanup.pdf") { exchange ->
            respond(exchange, 401, "authenticate", "WWW-Authenticate" to "Basic realm=\"cleanup\"")
        }
        val url = "$remote/cleanup.pdf"
        val expiredToken = register(url)
        val activeToken = register(url)
        val expiredSource = source(expiredToken)
        val activeSource = source(activeToken)
        val prompts = mutableMapOf<String, HttpAuthRequest>()
        val authenticated = mutableListOf<String>()
        for (token in listOf(expiredToken, activeToken)) {
            assertStatus(401, load(
                token,
                onAuthenticationRequired = { receivedToken, _, request -> prompts[receivedToken] = request },
                onAuthenticated = { receivedToken, _ -> authenticated.add(receivedToken) },
            ))
        }
        setExpiresAt(expiredSource, 0)

        val newToken = register("$remote/new.pdf")

        assertEquals(setOf(activeToken, newToken), sources().keys)
        assertTrue(pendingAuthentication(expiredSource).isEmpty())
        assertEquals(1, pendingAuthentication(activeSource).size)
        prompts.getValue(expiredToken).proceed("alice", "secret")
        assertTrue(credentials(expiredSource).isEmpty())
        assertTrue(authenticated.isEmpty())
        prompts.getValue(activeToken).proceed("alice", "secret")
        assertEquals(listOf(activeToken), authenticated)
        assertTrue(pendingAuthentication(activeSource).isEmpty())
        assertEquals(1, credentials(activeSource).size)
    }

    @Test
    fun fullRegistryEvictsTheLeastRecentlyUsedSourceAndOnlyItsPendingAuthentication() {
        val requests = AtomicInteger()
        server.createContext("/capacity.pdf") { exchange ->
            requests.incrementAndGet()
            if (exchange.requestHeaders.getFirst("Authorization") == "Basic YWxpY2U6c2VjcmV0") {
                respond(exchange, 200, "surviving PDF")
            } else {
                respond(exchange, 401, "authenticate", "WWW-Authenticate" to "Basic realm=\"capacity\"")
            }
        }
        val recentToken = register("$remote/capacity.pdf")
        val evictedToken = register("$remote/capacity.pdf")
        val recentSource = source(recentToken)
        val evictedSource = source(evictedToken)
        val prompts = mutableMapOf<String, HttpAuthRequest>()
        val authenticated = mutableListOf<String>()
        for (token in listOf(recentToken, evictedToken)) {
            assertStatus(401, load(
                token,
                onAuthenticationRequired = { receivedToken, _, request -> prompts[receivedToken] = request },
                onAuthenticated = { receivedToken, _ -> authenticated.add(receivedToken) },
            ))
        }
        repeat(62) { register("$remote/unused-$it.pdf") }
        assertEquals(64, sources().size)
        val now = System.currentTimeMillis()
        setExpiresAt(recentSource, now + TimeUnit.HOURS.toMillis(1))
        setExpiresAt(evictedSource, now + TimeUnit.HOURS.toMillis(2))
        // A viewer status request refreshes the older registration before capacity eviction.
        assertStatus(200, load(
            recentToken,
            requestUrl = "$base/pdf_status?wk_pdf_token=$recentToken&event=loaded",
        ))
        val previousTokens = sources().keys.toSet()

        val replacement = register("$remote/replacement.pdf")

        assertEquals(64, sources().size)
        assertEquals(previousTokens - evictedToken + replacement, sources().keys)
        assertTrue(pendingAuthentication(evictedSource).isEmpty())
        assertEquals(1, pendingAuthentication(recentSource).size)
        prompts.getValue(evictedToken).proceed("alice", "secret")
        assertTrue(credentials(evictedSource).isEmpty())
        assertTrue(authenticated.isEmpty())
        assertStatus(404, load(evictedToken))
        assertEquals(2, requests.get())
        prompts.getValue(recentToken).proceed("alice", "secret")
        assertEquals(listOf(recentToken), authenticated)
        val response = load(recentToken)
        assertEquals(200, response?.statusCode)
        assertEquals("surviving PDF", response!!.data.use { it.bufferedReader().readText() })
        assertEquals(4, requests.get())
        assertTrue(pendingAuthentication(recentSource).isEmpty())
    }

    private fun cookiePairs(header: String?): Set<String> =
        header?.split(';')?.map { it.trim() }?.toSet().orEmpty()

    private fun assertStatus(expected: Int, response: WebResourceResponse?) {
        val actual = requireNotNull(response)
        try {
            assertEquals(expected, actual.statusCode)
        } finally {
            actual.data.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sources(): MutableMap<String, Any> = Class.forName(
        "uk.nktnet.webviewkiosk.utils.webview.handlers.HandlePdfSourceRequestKt",
    ).getDeclaredField("registeredPdfSources").apply { isAccessible = true }
        .get(null) as MutableMap<String, Any>

    private fun source(token: String): Any = requireNotNull(sources()[token])

    private fun expiresAt(source: Any): Long = source.javaClass.getDeclaredField("expiresAt")
        .apply { isAccessible = true }.getLong(source)

    private fun setExpiresAt(source: Any, value: Long) {
        source.javaClass.getDeclaredField("expiresAt").apply { isAccessible = true }.setLong(source, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pendingAuthentication(source: Any): MutableMap<Any, HttpAuthRequest> =
        source.javaClass.getDeclaredField("pendingAuthentication").apply { isAccessible = true }
            .get(source) as MutableMap<Any, HttpAuthRequest>

    @Suppress("UNCHECKED_CAST")
    private fun credentials(source: Any): Map<Any, String> =
        source.javaClass.getDeclaredField("credentials").apply { isAccessible = true }
            .get(source) as Map<Any, String>

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
