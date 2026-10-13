package uk.nktnet.webviewkiosk.utils.webview

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebViewStartupGateTest {
    @Test
    fun startupBeginsOnlyOnceAndWaitsForTheCompletionCallback() = runBlocking {
        var completions = 0
        var callback: (() -> Unit)? = null
        val gate = WebViewStartupGate(beginStartup = {
            completions++
            callback = it
        })

        repeat(4) { gate.start() }
        assertEquals(1, completions)
        assertFalse(gate.ready.value)
        val waiting = async { gate.awaitReady() }
        assertFalse(waiting.isCompleted)

        val finish = requireNotNull(callback)
        finish()
        waiting.await()
        assertTrue(gate.ready.value)
        finish() // Duplicate native completion is harmless.
        gate.start()
        assertEquals(1, completions)
    }

    @Test
    fun startupFailureFallsBackRatherThanStrandingTheUi() = runBlocking {
        val failures = mutableListOf<Exception>()
        val gate = WebViewStartupGate(
            beginStartup = { throw IllegalStateException("No WebView provider") },
            onFailure = failures::add,
        )

        gate.start()
        gate.awaitReady()
        assertEquals(1, failures.size)
        assertTrue(gate.ready.value)
    }

    @Test
    fun concurrentCallersStillStartOnlyOneWarmup() {
        val starts = AtomicInteger()
        val gate = WebViewStartupGate(beginStartup = { starts.incrementAndGet() })
        val pool = Executors.newFixedThreadPool(6)
        val begin = CountDownLatch(1)
        val futures = (1..24).map {
            pool.submit {
                begin.await()
                gate.start()
            }
        }
        try {
            begin.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, starts.get())
            assertFalse(gate.ready.value)
            gate.complete()
            assertTrue(gate.ready.value)
        } finally {
            pool.shutdownNow()
        }
    }
}
