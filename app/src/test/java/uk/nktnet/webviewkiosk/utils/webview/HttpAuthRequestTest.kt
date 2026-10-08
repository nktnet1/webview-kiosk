package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.nktnet.webviewkiosk.testing.runConcurrently
import java.util.concurrent.atomic.AtomicInteger

class HttpAuthRequestTest {
    @Test
    fun submitsCredentialsOnlyOnceAndIgnoresLaterCancellation() {
        val submitted = mutableListOf<Pair<String, String>>()
        var cancelled = 0
        val request = HttpAuthRequest(
            "example.com",
            "private",
            { user, password -> submitted.add(user to password) },
            { cancelled++ },
        )

        request.proceed("alice", "secret")
        request.proceed("bob", "other")
        request.cancel()

        assertEquals(listOf("alice" to "secret"), submitted)
        assertEquals(0, cancelled)
    }

    @Test
    fun cancellationPreventsLaterCredentialSubmission() {
        var submitted = 0
        var cancelled = 0
        val request = HttpAuthRequest(null, null, { _, _ -> submitted++ }, { cancelled++ })

        request.cancel()
        request.cancel()
        request.proceed("alice", "secret")

        assertEquals(0, submitted)
        assertEquals(1, cancelled)
    }

    @Test
    fun concurrentSubmissionAndCancellationCompleteExactlyOnce() {
        val submitted = AtomicInteger()
        val cancelled = AtomicInteger()
        val request = HttpAuthRequest(
            null,
            null,
            { _, _ -> submitted.incrementAndGet() },
            { cancelled.incrementAndGet() },
        )

        runConcurrently({ request.proceed("alice", "secret") }, { request.cancel() })
        request.proceed("bob", "other")
        request.cancel()

        assertEquals(1, submitted.get() + cancelled.get())
    }

    @Test
    fun aThrowingSubmissionCallbackStillConsumesTheRequest() {
        val failure = IllegalStateException("callback failed")
        var cancelled = 0
        val request = HttpAuthRequest(null, null, { _, _ -> throw failure }, { cancelled++ })

        assertSame(failure, assertThrows(IllegalStateException::class.java) { request.proceed("alice", "secret") })
        request.proceed("alice", "secret")
        request.cancel()

        assertEquals(0, cancelled)
    }
}
