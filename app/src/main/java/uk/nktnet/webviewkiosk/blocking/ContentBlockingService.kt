package uk.nktnet.webviewkiosk.blocking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal enum class BlockingStatus { DISABLED, LOADING, ACTIVE, ERROR }

/**
 * Serialises list compilation away from the UI and the WebView request threads.
 * A successful compilation atomically replaces the immutable Kotlin engine snapshot.
 */
internal class ContentBlockingService(
    private val store: FilterListStore,
    private val engine: NetworkFilterEngine,
    private val worker: Executor,
) {
    private val generation = AtomicLong(0)
    private var started = false

    @Volatile
    private var ready = false

    private val _initialLoadComplete = MutableStateFlow(false)
    val initialLoadComplete = _initialLoadComplete.asStateFlow()
    val hasActiveRules: Boolean get() = ready
    val hasActiveCosmeticRules: Boolean get() = ready && engine.hasCosmeticRules()

    private val _status = MutableStateFlow(BlockingStatus.DISABLED)
    val status = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()
    private val _skippedRules = MutableStateFlow(0)
    val skippedRules = _skippedRules.asStateFlow()

    @Synchronized
    fun start() {
        if (started) return
        started = true
        reload()
    }

    /** Suspend the first navigation, never the UI or request-interception thread. */
    suspend fun awaitInitialLoad() {
        start()
        initialLoadComplete.first { it }
    }

    fun reload() {
        val revision = generation.incrementAndGet()
        _status.value = BlockingStatus.LOADING
        try {
            worker.execute {
                try {
                    if (revision != generation.get()) return@execute
                    val texts = store.enabledTexts()
                    if (revision != generation.get()) return@execute
                    if (texts.isEmpty()) {
                        engine.clear()
                    } else {
                        engine.replace(texts)
                    }
                    if (revision == generation.get()) {
                        _lastError.value = null
                        ready = texts.isNotEmpty()
                        _skippedRules.value = engine.unsupportedRuleCount()
                        _status.value = if (ready) BlockingStatus.ACTIVE else BlockingStatus.DISABLED
                        _initialLoadComplete.value = true
                    }
                } catch (e: Exception) {
                    reportError(revision, e)
                } catch (e: LinkageError) {
                    reportError(revision, e)
                }
            }
        } catch (e: Exception) {
            reportError(revision, e)
        }
    }

    private fun reportError(revision: Long, error: Throwable) {
        if (revision == generation.get()) {
            // Compilation is atomic. A failed update leaves the last working rules active.
            _lastError.value = error.message ?: "Content-blocking rules could not be loaded"
            _status.value = BlockingStatus.ERROR
            _initialLoadComplete.value = true
            Log.e(TAG, "Failed to load content-blocking rules", error)
        }
    }

    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean {
        if (!ready) return false
        return try {
            engine.shouldBlock(url, sourceUrl, type, method)
        } catch (e: LinkageError) {
            ready = false
            reportError(generation.get(), e)
            false
        }
    }

    fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> {
        if (!ready) return emptyList()
        return try {
            engine.cosmeticSelectors(url, classes, ids)
        } catch (e: LinkageError) {
            ready = false
            reportError(generation.get(), e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ContentBlocking"
        @Volatile private var instance: ContentBlockingService? = null

        fun get(context: Context): ContentBlockingService = instance ?: synchronized(this) {
            instance ?: ContentBlockingService(
                FilterListStore(context.applicationContext),
                KotlinFilterEngine(),
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "content-blocking-loader").apply { isDaemon = true }
                },
            ).also {
                // Explicit initialization also covers test/headless entry points where
                // AndroidX Startup has not initialized OkHttp's existing suffix asset.
                okhttp3.OkHttp.initialize(context.applicationContext)
                instance = it
            }
        }
    }
}
