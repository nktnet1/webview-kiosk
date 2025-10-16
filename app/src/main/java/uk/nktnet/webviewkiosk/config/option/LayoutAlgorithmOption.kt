package uk.nktnet.webviewkiosk.config.option

import android.webkit.WebSettings

enum class LayoutAlgorithmOption(val algorithm: WebSettings.LayoutAlgorithm) {
    NORMAL(WebSettings.LayoutAlgorithm.NORMAL),
    SINGLE_COLUMN(WebSettings.LayoutAlgorithm.SINGLE_COLUMN),
    NARROW_COLUMNS(WebSettings.LayoutAlgorithm.NARROW_COLUMNS),
    TEXT_AUTOSIZING(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);

    companion object {
        fun fromString(value: String?): LayoutAlgorithmOption =
            entries.find { it.name == value } ?: NORMAL
    }
}
