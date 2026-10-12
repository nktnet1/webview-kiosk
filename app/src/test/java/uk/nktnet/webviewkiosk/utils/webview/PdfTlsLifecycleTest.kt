package uk.nktnet.webviewkiosk.utils.webview

import android.net.http.SslError
import android.webkit.ClientCertRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.nktnet.webviewkiosk.utils.webview.handlers.cancelPdfSourceRequests
import uk.nktnet.webviewkiosk.utils.webview.handlers.registerPdfSource
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Covers TLS request ownership across PDF viewer replacement without global SSL state. */
@RunWith(RobolectricTestRunner::class)
class PdfTlsLifecycleTest {
    private val createdTokens = mutableListOf<String>()

    @After
    fun tearDown() {
        val sources = sources()
        createdTokens.forEach { token ->
            cancelPdfSourceRequests(token)
            sources.remove(token)
        }
    }

    @Test
    fun cancellingViewerClosesAllSslAndClientCertificateRequestsExactlyOnce() {
        val state = PdfTlsState()
        val ssl = addPendingSsl(state, "first.example:443")
        val clientCert = addPendingClientCert(state, "second.example:443")
        assertTrue(state.hasPendingRequests)

        state.cancelPendingRequests()
        state.cancelPendingRequests()
        ssl.request.proceed()
        clientCert.proceed(null, null)

        assertEquals(1, ssl.cancelled.get())
        assertEquals(0, ssl.approved.get())
        assertEquals(1, clientCert.ignored.get())
        assertEquals(0, clientCert.proceeded.get())
        assertFalse(state.hasPendingRequests)
    }

    @Test
    fun pendingRequestsAreNotInheritedByANewViewerButApprovalsAreCopied() {
        val original = PdfTlsState()
        val permittedSite = approvalKey("trusted.example:443", "cert-sha256", SslError.SSL_UNTRUSTED)
        approvals(original)[permittedSite] = true
        val pending = addPendingSsl(original, "untrusted.example:443")
        val originalClientCert = addPendingClientCert(original, "client.example:443")

        val replacement = PdfTlsState(original)

        assertFalse(replacement.hasPendingRequests)
        assertEquals(mapOf(permittedSite to true), approvals(replacement).toMap())
        assertNotSame(approvals(original), approvals(replacement))
        assertTrue(original.hasPendingRequests)

        original.cancelPendingRequests()
        assertEquals(1, pending.cancelled.get())
        assertEquals(1, originalClientCert.ignored.get())
        assertFalse(replacement.hasPendingRequests)
        assertTrue(approvals(replacement).containsKey(permittedSite))
    }

    @Test
    fun laterApprovalsInOneViewerDoNotLeakBackToTheEarlierViewer() {
        val previous = PdfTlsState()
        val approved = approvalKey("first.example:443", "fingerprint-a", SslError.SSL_UNTRUSTED)
        val later = approvalKey("second.example:8443", "fingerprint-b", SslError.SSL_IDMISMATCH)
        approvals(previous)[approved] = true

        val current = PdfTlsState(previous)
        approvals(current)[later] = true
        approvals(current).remove(approved)

        assertEquals(setOf(approved), approvals(previous).keys)
        assertEquals(setOf(later), approvals(current).keys)
    }

    @Test
    fun replacingTheSamePdfSourceCancelsPendingRequestsAndKeepsPriorApprovals() {
        val originalToken = register("https://reports.example/private.pdf")
        val originalState = stateFor(originalToken)
        val approved = approvalKey("reports.example:443", "approved-cert", SslError.SSL_UNTRUSTED)
        approvals(originalState)[approved] = true
        val ssl = addPendingSsl(originalState, "reports.example:443")
        val clientCert = addPendingClientCert(originalState, "reports.example:443")

        val nextToken = register("https://reports.example/private.pdf", originalToken)
        val nextState = stateFor(nextToken)

        assertNotSame(originalState, nextState)
        assertEquals(1, ssl.cancelled.get())
        assertEquals(1, clientCert.ignored.get())
        assertFalse(originalState.hasPendingRequests)
        assertFalse(nextState.hasPendingRequests)
        assertEquals(mapOf(approved to true), approvals(nextState).toMap())
        ssl.request.proceed()
        assertEquals(0, ssl.approved.get())
    }

    @Test
    fun changingTheSourceDoesNotInheritCertificateDecisionsOrCancelUnrelatedRequests() {
        val firstToken = register("https://reports.example/first.pdf")
        val firstState = stateFor(firstToken)
        val oldApproval = approvalKey("reports.example:443", "old-fingerprint", SslError.SSL_UNTRUSTED)
        approvals(firstState)[oldApproval] = true
        val pending = addPendingSsl(firstState, "reports.example:443")

        val secondToken = register("https://reports.example/second.pdf", firstToken)
        val secondState = stateFor(secondToken)

        assertTrue(approvals(secondState).isEmpty())
        assertFalse(secondState.hasPendingRequests)
        assertTrue(firstState.hasPendingRequests)
        assertEquals(0, pending.cancelled.get())
    }

    @Test
    fun cancellingOnePdfTokenDoesNotCancelAnotherPdfViewer() {
        val first = register("https://one.example/report.pdf")
        val second = register("https://two.example/report.pdf")
        val firstPending = addPendingSsl(stateFor(first), "one.example:443")
        val secondPending = addPendingSsl(stateFor(second), "two.example:443")

        cancelPdfSourceRequests(first)
        cancelPdfSourceRequests(first)
        cancelPdfSourceRequests("missing-source-token")
        cancelPdfSourceRequests(null)

        assertEquals(1, firstPending.cancelled.get())
        assertFalse(stateFor(first).hasPendingRequests)
        assertEquals(0, secondPending.cancelled.get())
        assertTrue(stateFor(second).hasPendingRequests)
    }

    private fun register(url: String, previousToken: String? = null): String =
        registerPdfSource(url, previousToken).also(createdTokens::add)

    private fun stateFor(token: String): PdfTlsState {
        val registered = requireNotNull(sources()[token])
        return registered.javaClass.getDeclaredField("tls").apply { isAccessible = true }
            .get(registered) as PdfTlsState
    }

    @Suppress("UNCHECKED_CAST")
    private fun sources(): MutableMap<String, Any> = Class.forName(
        "uk.nktnet.webviewkiosk.utils.webview.handlers.HandlePdfSourceRequestKt",
    ).getDeclaredField("registeredPdfSources").apply { isAccessible = true }
        .get(null) as MutableMap<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun approvals(state: PdfTlsState): MutableMap<Any, Boolean> =
        state.javaClass.getDeclaredField("approvals").apply { isAccessible = true }
            .get(state) as MutableMap<Any, Boolean>

    @Suppress("UNCHECKED_CAST")
    private fun sslRequests(state: PdfTlsState): MutableMap<Any, SslErrorRequest> =
        state.javaClass.getDeclaredField("sslRequests").apply { isAccessible = true }
            .get(state) as MutableMap<Any, SslErrorRequest>

    @Suppress("UNCHECKED_CAST")
    private fun clientCertRequests(state: PdfTlsState): MutableMap<String, ClientCertRequest> =
        state.javaClass.getDeclaredField("clientCertRequests").apply { isAccessible = true }
            .get(state) as MutableMap<String, ClientCertRequest>

    private fun approvalKey(site: String, fingerprint: String, error: Int): Any =
        PdfTlsState::class.java.declaredClasses.single { it.simpleName == "SslApproval" }
            .getDeclaredConstructor(String::class.java, String::class.java, Int::class.javaPrimitiveType!!)
            .apply { isAccessible = true }
            .newInstance(site, fingerprint, error)

    private data class SslProbe(
        val request: SslErrorRequest,
        val approved: AtomicInteger,
        val cancelled: AtomicInteger,
    )

    private fun addPendingSsl(state: PdfTlsState, site: String): SslProbe {
        val key = approvalKey(site, "unapproved-cert", SslError.SSL_UNTRUSTED)
        val requests = sslRequests(state)
        val approved = AtomicInteger()
        val cancelled = AtomicInteger()
        lateinit var request: SslErrorRequest
        request = SslErrorRequest(
            null,
            onProceed = {
                requests.remove(key, request)
                approved.incrementAndGet()
            },
            onCancel = {
                requests.remove(key, request)
                cancelled.incrementAndGet()
            },
        )
        requests[key] = request
        return SslProbe(request, approved, cancelled)
    }

    private fun addPendingClientCert(state: PdfTlsState, site: String): RecordingClientCertRequest {
        val requests = clientCertRequests(state)
        lateinit var request: RecordingClientCertRequest
        request = RecordingClientCertRequest { requests.remove(site, request) }
        requests[site] = request
        return request
    }

    private class RecordingClientCertRequest(private val onComplete: () -> Unit) : ClientCertRequest() {
        private val completed = AtomicBoolean(false)
        val ignored = AtomicInteger()
        val proceeded = AtomicInteger()

        override fun getHost(): String = "reports.example"
        override fun getPort(): Int = 443
        override fun getKeyTypes(): Array<String>? = null
        override fun getPrincipals(): Array<Principal>? = null

        override fun proceed(key: PrivateKey?, chain: Array<X509Certificate>?) {
            if (completed.compareAndSet(false, true)) {
                proceeded.incrementAndGet()
                onComplete()
            }
        }

        override fun ignore() {
            if (completed.compareAndSet(false, true)) {
                ignored.incrementAndGet()
                onComplete()
            }
        }

        override fun cancel() = ignore()
    }
}
