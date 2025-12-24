package com.nktnet.webview_kiosk.config.option

import android.webkit.WebSettings

enum class CacheModeOption(val mode: Int, val label: String) {
    DEFAULT(WebSettings.LOAD_DEFAULT, "Default"),
    CACHE_ELSE_NETWORK(WebSettings.LOAD_CACHE_ELSE_NETWORK, "Cache Else Network"),
    NO_CACHE(WebSettings.LOAD_NO_CACHE, "No Cache"),
    CACHE_ONLY(WebSettings.LOAD_CACHE_ONLY, "Cache Only");

    companion object {
        fun fromString(value: String?): CacheModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.mode.toString() == value
            } ?: DEFAULT
        }
    }
}
