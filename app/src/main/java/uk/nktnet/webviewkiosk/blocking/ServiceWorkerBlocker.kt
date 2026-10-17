package uk.nktnet.webviewkiosk.blocking

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature

/** One process-wide client; service-worker callbacks do not identify a WebView. */
internal object ServiceWorkerBlocker {
    private var installed = false

    @Synchronized
    fun install(service: ContentBlockingService) {
        if (installed) {
            return
        }
        try {
            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.SERVICE_WORKER_BASIC_USAGE
                ) &&
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST
                    )
            ) {
                ServiceWorkerControllerCompat.getInstance()
                    .setServiceWorkerClient(
                        object : ServiceWorkerClientCompat() {
                            override fun shouldInterceptRequest(
                                request: WebResourceRequest
                            ): WebResourceResponse? = intercept(request, service)
                        }
                    )
            }
            installed = true
        } catch (error: RuntimeException) {
            // Older/broken providers must not prevent ordinary WebView browsing.
            Log.w(
                "ContentBlocking",
                "Service-worker filtering is unavailable",
                error,
            )
        } catch (error: LinkageError) {
            // A failed provider initializer can surface as an Error, not an
            // Exception.
            installed = true
            Log.w("ContentBlocking", "Service-worker provider is unavailable", error)
        }
    }

    internal fun intercept(
        request: WebResourceRequest,
        service: ContentBlockingService,
    ): WebResourceResponse? {
        // Never borrow an unrelated tab's URL or guess the worker's
        // first/third-party context.
        // An empty source asks the engine to consider context-free rules only.
        // Contextual exceptions conservatively allow when their other facts match.
        return WebViewBlocker.intercept(request, "", service)
    }
}
