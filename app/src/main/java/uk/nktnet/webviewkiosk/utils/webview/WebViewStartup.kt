package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewStartUpConfig
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService

/**
 * One-shot gate: never block the main thread while the WebView provider is warming
 * up.
 */
internal class WebViewStartupGate(
    private val beginStartup: (onComplete: () -> Unit) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private val started = AtomicBoolean(false)
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        try {
            beginStartup(::complete)
        } catch (error: Exception) {
            // Preserve the existing WebViewUnavailable fallback if warmup is
            // unsupported.
            onFailure(error)
            complete()
        }
    }

    fun complete() {
        readyState.value = true
    }

    suspend fun awaitReady() = ready.first { it }.let {}
}

/**
 * Include the UI-thread phase instead of deferring it until the first WebView API
 * call.
 */
@OptIn(WebViewCompat.ExperimentalAsyncStartUp::class)
internal fun webViewStartupConfig(
    backgroundExecutor: Executor
): WebViewStartUpConfig =
    WebViewStartUpConfig.Builder(backgroundExecutor)
        .setShouldRunUiThreadStartUpTasks(true)
        .build()

/**
 * Fully initialize the provider before mounting a WebView. A background-only warmup
 * leaves the UI-thread phase for the first WebView API call, which in this app is
 * ServiceWorkerControllerCompat.getInstance() during composition. That cold start
 * can block the main thread on Android's broadcast-receiver registration. WebKit
 * schedules the required UI-thread startup tasks asynchronously; WebView
 * construction still happens on the main thread after the callback.
 */
@OptIn(markerClass = [WebViewCompat.ExperimentalAsyncStartUp::class])
internal object WebViewStartup {
    private const val TAG = "WebViewStartup"
    // WebKit 1.14's experimental callback has no failure callback. Never strand the
    // kiosk on a progress indicator if a WebView provider fails to reply.
    private const val STARTUP_TIMEOUT_MILLIS = 12_000L
    private val timeoutScheduled = AtomicBoolean(false)
    private val readinessScheduled = AtomicBoolean(false)
    private val readinessScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val readyState = MutableStateFlow(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var testGate: WebViewStartupGate? = null

    private val gate =
        WebViewStartupGate(
            beginStartup = { onComplete ->
                val backgroundExecutor = Executor { runnable ->
                    Thread(
                            {
                                try {
                                    runnable.run()
                                } catch (error: Exception) {
                                    Log.w(
                                        TAG,
                                        "Background WebView warmup failed; using normal creation",
                                        error,
                                    )
                                    onComplete()
                                }
                            },
                            "webview-startup",
                        )
                        .apply { isDaemon = true }
                        .start()
                }
                // An actual WebView may have been created by another entry point
                // already.
                // The API handles that case and invokes the callback promptly.
                val config = webViewStartupConfig(backgroundExecutor)
                WebViewCompat.startUpWebView(applicationContext, config) {
                    onComplete()
                }
            },
            onFailure = { error -> Log.w(TAG, "Unable to warm up WebView", error) },
        )

    private lateinit var applicationContext: Context
    val ready
        get() = testGate?.ready ?: readyState.asStateFlow()

    fun start(context: Context) {
        applicationContext = context.applicationContext
        val active = testGate ?: gate
        if (active === gate && readinessScheduled.compareAndSet(false, true)) {
            val blocker = ContentBlockingService.get(applicationContext)
            blocker.start()
            readinessScope.launch {
                gate.awaitReady()
                blocker.awaitInitialLoad()
                readyState.value = true
            }
        }
        active.start()
        if (
            active === gate &&
                !gate.ready.value &&
                timeoutScheduled.compareAndSet(false, true)
        ) {
            mainHandler.postDelayed(
                {
                    if (!gate.ready.value) {
                        Log.w(
                            TAG,
                            "WebView warmup timed out; falling back to normal creation",
                        )
                        gate.complete()
                    }
                },
                STARTUP_TIMEOUT_MILLIS,
            )
        }
    }

    suspend fun awaitReady(context: Context) {
        start(context)
        ready.first { it }
    }

    /**
     * Robolectric cannot reliably start Chromium; the activity tests supply a fake
     * gate.
     */
    internal fun setGateForTests(override: WebViewStartupGate?) {
        testGate = override
    }
}
