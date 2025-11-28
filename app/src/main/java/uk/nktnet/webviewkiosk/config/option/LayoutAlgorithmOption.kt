package com.nktnet.webview_kiosk.config.option

import android.webkit.WebSettings

enum class LayoutAlgorithmOption(val algorithm: WebSettings.LayoutAlgorithm, val label: String) {
    NORMAL(WebSettings.LayoutAlgorithm.NORMAL, "Normal"),
    SINGLE_COLUMN(WebSettings.LayoutAlgorithm.SINGLE_COLUMN, "Single Column"),
    NARROW_COLUMNS(WebSettings.LayoutAlgorithm.NARROW_COLUMNS, "Narrow Columns"),
    TEXT_AUTOSIZING(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING, "Text Autosizing");

    companion object {
        fun fromString(value: String?): LayoutAlgorithmOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: NORMAL
        }
    }
}
