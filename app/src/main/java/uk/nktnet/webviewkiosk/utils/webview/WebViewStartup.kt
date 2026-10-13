package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewStartUpConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** One-shot gate: never block the main thread while the WebView provider is warming up. */
internal class WebViewStartupGate(
    private val beginStartup: (onComplete: () -> Unit) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private val started = AtomicBoolean(false)
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            beginStartup(::complete)
        } catch (error: Exception) {
            // Preserve the existing WebViewUnavailable fallback if warmup is unsupported.
            onFailure(error)
            complete()
        }
    }

    fun complete() {
        readyState.value = true
    }

    suspend fun awaitReady() = ready.first { it }.let { Unit }
}

/**
 * On AndroidX WebKit 1.14 the experimental API works down to our API 21 minimum.
 * Only background-safe startup work is requested; never run native UI initialization
 * synchronously while the Activity is being created. Actual WebView construction
 * remains on the UI thread after the callback, as required by WebKit.
 */
internal object WebViewStartup {
    private const val TAG = "WebViewStartup"
    // WebKit 1.14's experimental callback has no failure callback. Never strand the
    // kiosk on a progress indicator if a WebView provider fails to reply.
    private const val STARTUP_TIMEOUT_MILLIS = 12_000L
    private val timeoutScheduled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var testGate: WebViewStartupGate? = null

    @androidx.annotation.OptIn(markerClass = [WebViewCompat.ExperimentalAsyncStartUp::class])
    private val gate = WebViewStartupGate(
        beginStartup = { onComplete ->
            val backgroundExecutor = Executor { runnable ->
                Thread({
                    try {
                        runnable.run()
                    } catch (error: Exception) {
                        Log.w(TAG, "Background WebView warmup failed; using normal creation", error)
                        onComplete()
                    }
                }, "webview-startup").apply { isDaemon = true }.start()
            }
            // An actual WebView may have been created by another entry point already.
            // The API handles that case and invokes the callback promptly.
            val config = WebViewStartUpConfig.Builder(backgroundExecutor)
                .setShouldRunUiThreadStartUpTasks(false)
                .build()
            WebViewCompat.startUpWebView(applicationContext, config) { onComplete() }
        },
        onFailure = { error -> Log.w(TAG, "Unable to warm up WebView", error) },
    )

    private lateinit var applicationContext: Context
    val ready get() = (testGate ?: gate).ready

    fun start(context: Context) {
        applicationContext = context.applicationContext
        val active = testGate ?: gate
        active.start()
        if (active === gate && !gate.ready.value && timeoutScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                if (!gate.ready.value) {
                    Log.w(TAG, "WebView warmup timed out; falling back to normal creation")
                    gate.complete()
                }
            }, STARTUP_TIMEOUT_MILLIS)
        }
    }

    suspend fun awaitReady(context: Context) {
        start(context)
        (testGate ?: gate).awaitReady()
    }

    /** Robolectric cannot reliably start Chromium; the activity tests supply a fake gate. */
    internal fun setGateForTests(override: WebViewStartupGate?) {
        testGate = override
    }
}
