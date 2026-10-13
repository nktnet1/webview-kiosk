package uk.nktnet.webviewkiosk.blocking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal enum class BlockingStatus { DISABLED, LOADING, ACTIVE, ERROR }

/**
 * Serialises list compilation away from the UI and the WebView request threads.
 * A successful compilation atomically replaces the native engine; disabled lists use no JNI.
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

    private val _status = MutableStateFlow(BlockingStatus.DISABLED)
    val status = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    @Synchronized
    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun reload() {
        val revision = generation.incrementAndGet()
        ready = false
        _status.value = BlockingStatus.LOADING
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
                    _status.value = if (ready) BlockingStatus.ACTIVE else BlockingStatus.DISABLED
                }
            } catch (e: Exception) {
                reportError(revision, e)
            } catch (e: LinkageError) {
                reportError(revision, e)
            }
        }
    }

    private fun reportError(revision: Long, error: Throwable) {
        if (revision == generation.get()) {
            ready = false
            _lastError.value = error.message ?: "Native content-blocking engine unavailable"
            _status.value = BlockingStatus.ERROR
            Log.e(TAG, "Failed to load content-blocking rules", error)
        }
    }

    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean {
        if (!ready) return false
        return try {
            engine.shouldBlock(url, sourceUrl, type, method)
        } catch (e: LinkageError) {
            reportError(generation.get(), e)
            false
        }
    }

    companion object {
        private const val TAG = "ContentBlocking"
        @Volatile private var instance: ContentBlockingService? = null

        fun get(context: Context): ContentBlockingService = instance ?: synchronized(this) {
            instance ?: ContentBlockingService(
                FilterListStore(context.applicationContext),
                NativeAdblockEngine(),
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "content-blocking-loader").apply { isDaemon = true }
                },
            ).also { instance = it }
        }
    }
}
