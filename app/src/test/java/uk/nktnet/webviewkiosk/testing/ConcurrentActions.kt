package uk.nktnet.webviewkiosk.testing

import org.junit.Assert.assertTrue
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Start competing callbacks together without sleeps, and always release their worker threads. */
internal fun <T> runConcurrently(vararg actions: () -> T): List<T> {
    require(actions.isNotEmpty())
    val executor = Executors.newFixedThreadPool(actions.size)
    val ready = CountDownLatch(actions.size)
    val start = CountDownLatch(1)
    try {
        val futures = actions.map { action ->
            executor.submit(Callable {
                ready.countDown()
                assertTrue("Workers did not receive the start signal", start.await(5, TimeUnit.SECONDS))
                action()
            })
        }
        assertTrue("Workers did not reach the start barrier", ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        return futures.map { it.get(5, TimeUnit.SECONDS) }
    } finally {
        start.countDown()
        executor.shutdownNow()
        assertTrue("Workers did not stop", executor.awaitTermination(5, TimeUnit.SECONDS))
    }
}
