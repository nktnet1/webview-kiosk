package uk.nktnet.webviewkiosk.utils.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewClientCompat

/**
 * The HTTP-error callback in pre-73 Chromium WebView can crash while constructing a
 * WebResourceResponse with an empty reason phrase (Chromium issue 925887).
 *
 * Chromium only takes that buggy support-library path when the installed client is a
 * WebViewClientCompat. Register a framework WebViewClient instead, but forward every
 * callback overridden by our compat client so its navigation and error handling
 * remain unchanged. Keep these forwarders in sync with createCustomWebview's compat
 * client.
 *
 * References:
 * - https://stackoverflow.com/questions/57414530/illegalstateexception-reasonphrase-cant-be-empty-with-android-webview
 * - https://bugs.chromium.org/p/chromium/issues/detail?id=925887
 * - https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/webkit/WebResourceResponse.java#155
 */
internal class LegacyWebViewClient(private val delegate: WebViewClientCompat) :
    WebViewClient() {
    override fun onReceivedClientCertRequest(
        view: WebView?,
        request: ClientCertRequest?,
    ) = delegate.onReceivedClientCertRequest(view, request)

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) =
        delegate.onPageStarted(view, url, favicon)

    override fun onPageFinished(view: WebView?, url: String?) =
        delegate.onPageFinished(view, url)

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? = delegate.shouldInterceptRequest(view, request)

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = delegate.shouldOverrideUrlLoading(view, request)

    @Suppress("DEPRECATION")
    @Deprecated("For API < 24")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        delegate.shouldOverrideUrlLoading(view, url)

    override fun doUpdateVisitedHistory(
        view: WebView?,
        url: String?,
        isReload: Boolean,
    ) = delegate.doUpdateVisitedHistory(view, url, isReload)

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?,
    ) = delegate.onReceivedHttpAuthRequest(view, handler, host, realm)

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) = delegate.onReceivedHttpError(view, request, errorResponse)

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) = delegate.onReceivedError(view, request, error)

    @Suppress("DEPRECATION")
    @Deprecated("For API < 23")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) = delegate.onReceivedError(view, errorCode, description, failingUrl)

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) = delegate.onReceivedSslError(view, handler, error)

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPageCommitVisible(view: WebView, url: String) =
        delegate.onPageCommitVisible(view, url)

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) = delegate.onSafeBrowsingHit(view, request, threatType, callback)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean = delegate.onRenderProcessGone(view, detail)
}
