package uk.nktnet.webviewkiosk.utils.webview

import com.google.re2j.Pattern
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-defined URL rules run in synchronous WebView callbacks, including on the UI
 * thread. Use linear-time RE2/J for supported patterns. Preserve Java regex syntax
 * by executing unsupported patterns on a bounded worker with a strict deadline
 * instead of the UI thread.
 */
internal object UrlRegexMatcher {
    private const val MATCH_BUDGET_MS = 100L
    private const val MAX_FAST_PATTERN_LENGTH = 512
    private const val MAX_FAST_URL_LENGTH = 8192

    private data class Compiled(val fastPattern: Pattern?)

    // A screen owns the Regex instances; weak keys avoid retaining old
    // configurations.
    private val compiled =
        Collections.synchronizedMap(WeakHashMap<Regex, Compiled>())
    private val timedOut =
        Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<Regex, Boolean>())
        )

    // Native Android/ICU matchers cannot reliably be stopped by interrupting their
    // threads.
    // Never grow an unbounded pool/queue of pathological Java-only matches.
    private val fallback =
        ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            { runnable ->
                Thread(runnable, "url-regex-fallback").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )

    fun deadlineNanos(): Long =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MATCH_BUDGET_MS)

    fun matches(
        regex: Regex,
        url: String,
        deadlineNanos: Long,
        failClosed: Boolean,
    ): Boolean {
        if (deadlineNanos - System.nanoTime() <= 0) {
            return failClosed
        }

        val fast =
            synchronized(compiled) {
                compiled
                    .getOrPut(regex) {
                        Compiled(
                            if (
                                regex.options.isEmpty() &&
                                    regex.pattern.length <=
                                        MAX_FAST_PATTERN_LENGTH &&
                                    // Avoid expanding potentially huge counted
                                    // repetitions on UI.
                                    '{' !in regex.pattern
                            ) {
                                try {
                                    Pattern.compile(regex.pattern)
                                } catch (_: RuntimeException) {
                                    null
                                }
                            } else {
                                null
                            }
                        )
                    }
                    .fastPattern
            }
        if (fast != null && url.length <= MAX_FAST_URL_LENGTH) {
            return fast.matcher(url).find()
        }

        if (timedOut.contains(regex)) {
            return failClosed
        }
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0) {
            return failClosed
        }

        val started = AtomicBoolean(false)
        val task = FutureTask {
            started.set(true)
            if (fast != null) {
                fast.matcher(url).find()
            } else {
                regex.containsMatchIn(url)
            }
        }
        try {
            fallback.execute(task)
            return task.get(remainingNanos, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            // A slow native regex may ignore interrupts. Skip this rule next time,
            // rather than repeatedly occupying more worker threads with it.
            if (started.get()) {
                timedOut.add(regex)
            }
        } catch (_: RejectedExecutionException) {
            // All bounded fallback workers are busy; do not block WebView
            // navigation.
        } catch (_: ExecutionException) {
            // A malformed or excessive regex cannot grant access by failing.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            task.cancel(true)
        }
        return failClosed
    }
}
