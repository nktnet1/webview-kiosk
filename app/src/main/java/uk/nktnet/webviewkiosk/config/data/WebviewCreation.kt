package uk.nktnet.webviewkiosk.config.data

import android.webkit.WebView

sealed class WebViewCreation {
    data class Success(val webView: WebView) : WebViewCreation()
    data class Failure(val error: Exception) : WebViewCreation()
}
