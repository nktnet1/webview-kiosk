package com.nktnet.webview_kiosk.config.option

import android.view.View

enum class OverScrollModeOption(val mode: Int, val label: String) {
    ALWAYS(View.OVER_SCROLL_ALWAYS, "Always"),
    IF_CONTENT_SCROLLS(View.OVER_SCROLL_IF_CONTENT_SCROLLS, "If Content Scrolls"),
    NEVER(View.OVER_SCROLL_NEVER, "Never");

    companion object {
        fun fromString(value: String?): OverScrollModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
                || it.mode.toString() == value
            } ?: IF_CONTENT_SCROLLS
        }
    }
}
