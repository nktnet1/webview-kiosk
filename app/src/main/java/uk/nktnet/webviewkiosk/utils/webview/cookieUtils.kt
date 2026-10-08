package uk.nktnet.webviewkiosk.utils.webview

import android.webkit.CookieManager

fun getWebViewRequestCookie(url: String): String? {
    val manager = CookieManager.getInstance()
    return if (manager.acceptCookie()) {
        manager.getCookie(url)?.takeIf { it.isNotBlank() }
    } else {
        null
    }
}

fun storeWebViewResponseCookies(url: String, headers: Map<String?, List<String>?>) {
    val manager = CookieManager.getInstance()
    if (!manager.acceptCookie()) return

    headers.forEach { (name, values) ->
        if (name.equals("Set-Cookie", ignoreCase = true)) {
            values.orEmpty().forEach { cookie ->
                // The synchronous overload makes redirect cookies available to the next request.
                manager.setCookie(url, cookie)
            }
        }
    }
}
