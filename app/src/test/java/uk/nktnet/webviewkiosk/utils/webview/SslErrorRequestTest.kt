package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.nktnet.webviewkiosk.testing.runConcurrently
import java.util.concurrent.atomic.AtomicInteger

class SslErrorRequestTest {
    @Test
    fun proceedingIgnoresLaterCompletionAttempts() {
        var proceeded = 0
        var cancelled = 0
        val request = SslErrorRequest(null, { proceeded++ }, { cancelled++ })

        request.proceed()
        request.proceed()
        request.cancel()

        assertEquals(1, proceeded)
        assertEquals(0, cancelled)
    }

    @Test
    fun cancellationPreventsLaterApproval() {
        var proceeded = 0
        var cancelled = 0
        val request = SslErrorRequest(null, { proceeded++ }, { cancelled++ })

        request.cancel()
        request.cancel()
        request.proceed()

        assertEquals(0, proceeded)
        assertEquals(1, cancelled)
    }

    @Test
    fun concurrentApprovalAndCancellationCompleteExactlyOnce() {
        val proceeded = AtomicInteger()
        val cancelled = AtomicInteger()
        val request = SslErrorRequest(null, { proceeded.incrementAndGet() }, { cancelled.incrementAndGet() })

        runConcurrently({ request.proceed() }, { request.cancel() })
        request.proceed()
        request.cancel()

        assertEquals(1, proceeded.get() + cancelled.get())
    }

    @Test
    fun aThrowingCancellationCallbackStillConsumesTheRequest() {
        val failure = IllegalStateException("callback failed")
        var proceeded = 0
        val request = SslErrorRequest(null, { proceeded++ }, { throw failure })

        assertSame(failure, assertThrows(IllegalStateException::class.java) { request.cancel() })
        request.cancel()
        request.proceed()

        assertEquals(0, proceeded)
    }
}
