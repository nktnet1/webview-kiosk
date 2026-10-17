package uk.nktnet.webviewkiosk.blocking

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.adblock.ScriptletInjection

/**
 * All calls run on Main. The legacy document-start API executes in page world,
 * including child frames, before their scripts. An async message bridge or a
 * page-finished fallback cannot provide the same guarantee and is not used.
 */
internal class WebViewScriptlets
private constructor(
    private val service: ContentBlockingService,
    private val binding: DocumentStartScriptBinding,
    scope: CoroutineScope,
) {
    private val job: Job =
        scope.launch(
            Dispatchers.Main.immediate,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            service.scriptletInjection.collect { refresh() }
        }

    fun refresh() {
        try {
            binding.replace(service.scriptletInjection.value)
        } catch (error: Exception) {
            Log.w("ContentBlocking", "Scriptlet injection is unavailable", error)
        } catch (error: LinkageError) {
            Log.w("ContentBlocking", "Scriptlet provider is unavailable", error)
        }
    }

    fun dispose() {
        job.cancel()
        runCatching { binding.dispose() }
    }

    companion object {
        fun attach(
            webView: WebView,
            service: ContentBlockingService,
            scope: CoroutineScope,
        ): WebViewScriptlets? =
            try {
                val supported by lazy {
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.DOCUMENT_START_SCRIPT
                    )
                }
                val binding =
                    DocumentStartScriptBinding({ supported }) { injection ->
                        // Keep explicit positive guards beside both optional APIs
                        // so Android lint can verify them across these callbacks.
                        if (
                            WebViewFeature.isFeatureSupported(
                                WebViewFeature.DOCUMENT_START_SCRIPT
                            )
                        ) {
                            val handler =
                                WebViewCompat.addDocumentStartJavaScript(
                                    webView,
                                    injection.source,
                                    injection.allowedOrigins,
                                )
                            val remove: () -> Unit = {
                                if (
                                    WebViewFeature.isFeatureSupported(
                                        WebViewFeature.DOCUMENT_START_SCRIPT
                                    )
                                ) {
                                    handler.remove()
                                }
                            }
                            remove
                        } else {
                            throw UnsupportedOperationException(
                                "Document-start scripts are unavailable"
                            )
                        }
                    }
                WebViewScriptlets(service, binding, scope)
            } catch (error: Exception) {
                Log.w("ContentBlocking", "Scriptlet injection is unavailable", error)
                null
            } catch (error: LinkageError) {
                Log.w("ContentBlocking", "Scriptlet provider is unavailable", error)
                null
            }
    }
}

/** Small testable owner: one registration for one immutable compiled snapshot. */
internal class DocumentStartScriptBinding(
    private val supported: () -> Boolean,
    private val add: (ScriptletInjection) -> (() -> Unit),
) {
    constructor(
        supported: Boolean,
        add: (ScriptletInjection) -> (() -> Unit),
    ) : this({ supported }, add)

    private var active: ScriptletInjection? = null
    private var remove: (() -> Unit)? = null
    private var disposed = false

    fun replace(injection: ScriptletInjection?) {
        if (disposed || injection === active || injection != null && !supported()) {
            return
        }
        remove?.invoke()
        remove = null
        active = null
        if (injection != null) {
            remove = add(injection)
            active = injection
        }
    }

    fun dispose() {
        if (!disposed) {
            replace(null)
            disposed = true
        }
    }
}
