package com.nktnet.webview_kiosk.config.option

enum class FloatingToolbarModeOption(val label: String) {
    HIDDEN("Hidden"),
    HIDDEN_WHEN_LOCKED("Hidden When Locked"),
    SHOWN("Shown");

    companion object {
        fun fromString(value: String?): FloatingToolbarModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: HIDDEN_WHEN_LOCKED
        }
    }
}
