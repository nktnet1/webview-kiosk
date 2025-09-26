package com.nktnet.webview_kiosk.config.option

import android.webkit.WebSettings

val ValidCacheModes = setOf(
    WebSettings.LOAD_DEFAULT,
    WebSettings.LOAD_CACHE_ELSE_NETWORK,
    WebSettings.LOAD_NO_CACHE,
    WebSettings.LOAD_CACHE_ONLY,
)