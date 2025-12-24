package com.nktnet.webview_kiosk.config.option

enum class ThemeOption(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromString(value: String?): ThemeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: SYSTEM
        }
    }
}
