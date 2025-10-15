package uk.nktnet.webviewkiosk.config.option

import android.webkit.WebSettings

enum class CacheModeOption(val mode: Int) {
    DEFAULT(WebSettings.LOAD_DEFAULT),
    CACHE_ELSE_NETWORK(WebSettings.LOAD_CACHE_ELSE_NETWORK),
    NO_CACHE(WebSettings.LOAD_NO_CACHE),
    CACHE_ONLY(WebSettings.LOAD_CACHE_ONLY);

    companion object {
        fun fromInt(value: Int?): CacheModeOption =
            entries.find { it.mode == value } ?: DEFAULT
    }
}
