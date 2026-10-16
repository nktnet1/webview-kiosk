package uk.nktnet.webviewkiosk.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.adblock.NetworkFilterEngine
import uk.nktnet.webviewkiosk.adblock.RuleLoadStage
import uk.nktnet.webviewkiosk.adblock.ScriptletInjection
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

internal enum class BlockingStatus {
    DISABLED,
    LOADING,
    ACTIVE,
    ERROR,
}

private data class RuleLoadCompletion(
    val revision: Long,
    val error: Throwable? = null,
)

/**
 * Serialises list compilation away from the UI and the WebView request threads. A
 * successful compilation atomically replaces the immutable Kotlin engine snapshot.
 */
internal class ContentBlockingService(
    private val store: FilterListStore,
    private val engine: NetworkFilterEngine,
    private val worker: Executor,
) {
    private val generation = AtomicLong(0)
    private var started = false
    private var settingsBinding: AutoCloseable? = null

    private val _enabled = MutableStateFlow(true)
    val enabled = _enabled.asStateFlow()

    @Volatile private var ready = false

    private val _initialLoadComplete = MutableStateFlow(false)
    val initialLoadComplete = _initialLoadComplete.asStateFlow()

    private val _scriptletInjection = MutableStateFlow<ScriptletInjection?>(null)
    val scriptletInjection = _scriptletInjection.asStateFlow()

    // Saved list changes do not need recompiling while Settings is open.
    // The WebView gate consumes this flag before creating the next page.
    private val _rulesPending = MutableStateFlow(false)
    val rulesPending = _rulesPending.asStateFlow()
    val hasActiveRules: Boolean
        get() = ready

    val hasActiveCosmeticRules: Boolean
        get() = ready && engine.hasCosmeticRules()

    private val _status = MutableStateFlow(BlockingStatus.DISABLED)
    val status = _status.asStateFlow()
    private val _loadStage = MutableStateFlow<RuleLoadStage?>(null)
    val loadStage = _loadStage.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()
    private val _skippedRules = MutableStateFlow(0)
    val skippedRules = _skippedRules.asStateFlow()
    private val completedLoad = MutableStateFlow(RuleLoadCompletion(0))

    fun bindSettings(context: Context): AutoCloseable {
        check(settingsBinding == null) { "Settings are already bound" }
        val application = context.applicationContext
        val settings = UserSettings(application)
        val preferences =
            application.getSharedPreferences(
                UserSettingsKeys.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == null ||
                    key == UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING
            ) {
                setEnabled(settings.enableContentBlocking)
            }
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    setEnabled(settings.enableContentBlocking)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        setEnabled(settings.enableContentBlocking)
        val binding = AutoCloseable {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            application.unregisterReceiver(receiver)
            settingsBinding = null
        }
        settingsBinding = binding
        return binding
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) {
            return
        }
        _enabled.value = value
        if (started || !value) {
            reload()
        } else {
            _initialLoadComplete.value = false
        }
    }

    @Synchronized
    fun start() {
        if (started) {
            return
        }
        started = true
        reload()
    }

    /**
     * Save the need to reload; do not compile merely because Settings was edited.
     */
    @Synchronized
    fun markRulesChanged() {
        _rulesPending.value = true
    }

    /** Called when the WebView destination is entered, before page creation. */
    @Synchronized
    fun prepareForWebView() {
        if (!started) {
            // The initial load reads the latest saved lists already.
            _rulesPending.value = false
            start()
        } else if (_rulesPending.value) {
            reload()
            _rulesPending.value = false
        }
    }

    /** Suspend the first navigation, never the UI or request-interception thread. */
    suspend fun awaitInitialLoad() {
        start()
        initialLoadComplete.first { it }
    }

    @Synchronized
    fun reload(): Long {
        val revision = generation.incrementAndGet()
        if (!_enabled.value) {
            ready = false
            _scriptletInjection.value = null
            _status.value = BlockingStatus.DISABLED
            _loadStage.value = null
            _lastError.value = null
            _skippedRules.value = 0
            _initialLoadComplete.value = true
            completedLoad.value = RuleLoadCompletion(revision)
            return revision
        }
        _loadStage.value = RuleLoadStage.READING_LISTS
        _status.value = BlockingStatus.LOADING
        try {
            worker.execute {
                try {
                    if (revision != generation.get()) {
                        return@execute
                    }
                    val texts = store.enabledTexts()
                    if (revision != generation.get()) {
                        return@execute
                    }
                    if (texts.isEmpty()) {
                        updateStage(revision, RuleLoadStage.CLEARING)
                        engine.clear()
                    } else {
                        engine.replace(texts) { stage ->
                            updateStage(revision, stage)
                        }
                    }
                    finishReload(revision, texts.isNotEmpty())
                } catch (e: Exception) {
                    reportError(revision, e)
                } catch (e: LinkageError) {
                    reportError(revision, e)
                }
            }
        } catch (e: Exception) {
            reportError(revision, e)
        }
        return revision
    }

    @Synchronized
    private fun updateStage(revision: Long, stage: RuleLoadStage) {
        if (revision == generation.get()) {
            _loadStage.value = stage
        }
    }

    /**
     * Wait for this load, or the newer load that superseded it, without blocking a
     * thread.
     */
    suspend fun awaitReload(revision: Long) {
        require(revision > 0) { "A rule-load revision is required" }
        val result = completedLoad.first {
            it.revision >= revision && it.revision == generation.get()
        }
        result.error?.let { error ->
            throw IllegalStateException(
                "Filter lists were saved, but rules could not be loaded: " +
                    (error.message ?: "Unknown error"),
                error,
            )
        }
    }

    @Synchronized
    private fun finishReload(revision: Long, hasRules: Boolean) {
        if (revision != generation.get()) {
            return
        }
        _lastError.value = null
        ready = hasRules
        _scriptletInjection.value =
            if (ready) {
                engine.scriptletInjection()
            } else {
                null
            }
        _skippedRules.value = engine.unsupportedRuleCount()
        _status.value =
            if (ready) {
                BlockingStatus.ACTIVE
            } else {
                BlockingStatus.DISABLED
            }
        _loadStage.value = null
        _initialLoadComplete.value = true
        completedLoad.value = RuleLoadCompletion(revision)
    }

    @Synchronized
    private fun reportError(revision: Long, error: Throwable) {
        if (revision == generation.get()) {
            // Compilation is atomic. A failed update leaves the last working rules
            // active.
            _lastError.value =
                error.message ?: "Content-blocking rules could not be loaded"
            _status.value = BlockingStatus.ERROR
            _loadStage.value = null
            _initialLoadComplete.value = true
            completedLoad.value = RuleLoadCompletion(revision, error)
            Log.e(TAG, "Failed to load content-blocking rules", error)
        }
    }

    fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean {
        return ready &&
            try {
                engine.shouldBlock(url, sourceUrl, type, method)
            } catch (e: LinkageError) {
                ready = false
                reportError(generation.get(), e)
                false
            }
    }

    fun responseFor(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): BlockingResponse {
        if (!ready) {
            return BlockingResponse.ALLOW
        }
        return try {
            engine.responseFor(url, sourceUrl, type, method)
        } catch (error: LinkageError) {
            ready = false
            reportError(generation.get(), error)
            BlockingResponse.ALLOW
        }
    }

    fun cosmeticSelectors(
        url: String,
        classes: List<String>,
        ids: List<String>,
    ): List<String> {
        if (!ready) {
            return emptyList()
        }
        return try {
            engine.cosmeticSelectors(url, classes, ids)
        } catch (e: LinkageError) {
            ready = false
            reportError(generation.get(), e)
            emptyList()
        }
    }

    fun cosmeticProcedures(url: String): List<CosmeticProcedure> {
        if (!ready) {
            return emptyList()
        }
        return try {
            engine.cosmeticProcedures(url)
        } catch (error: LinkageError) {
            ready = false
            reportError(generation.get(), error)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ContentBlocking"

        @Volatile private var instance: ContentBlockingService? = null

        fun get(context: Context): ContentBlockingService =
            instance
                ?: synchronized(this) {
                    instance
                        ?: ContentBlockingService(
                                FilterListStore(context.applicationContext),
                                KotlinFilterEngine(
                                    cacheFile =
                                        File(
                                            context.applicationContext.cacheDir,
                                            "content-blocking/compiled-rules.bin",
                                        ),
                                    cacheVersion = BuildConfig.VERSION_CODE,
                                ),
                                Executors.newSingleThreadExecutor { task ->
                                    Thread(task, "content-blocking-loader").apply {
                                        isDaemon = true
                                    }
                                },
                            )
                            .also {
                                // Explicit initialization also covers test/headless
                                // entry points
                                // where
                                // AndroidX Startup has not initialized OkHttp's
                                // existing suffix
                                // asset.
                                okhttp3.OkHttp.initialize(context.applicationContext)
                                it.bindSettings(context)
                                instance = it
                            }
                }
    }
}
