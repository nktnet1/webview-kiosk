package com.nktnet.webview_kiosk.config.option

import android.webkit.WebSettings

enum class MixedContentModeOption(val mode: Int, val label: String) {
    NEVER_ALLOW(WebSettings.MIXED_CONTENT_NEVER_ALLOW, "Never Allow"),
    COMPATIBILITY_MODE(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE, "Compatibility Mode"),
    ALWAYS_ALLOW(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW, "Always Allow");

    companion object {
        fun fromString(value: String?): MixedContentModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.mode.toString() == value
            } ?: NEVER_ALLOW
        }
    }
}
