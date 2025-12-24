package com.nktnet.webview_kiosk.config.option

enum class ImmersiveModeOption(val label: String) {
    ALWAYS_ON("Always On"),
    ALWAYS_OFF("Always Off"),
    ONLY_WHEN_LOCKED("Only When Locked");

    companion object {
        fun fromString(value: String?): ImmersiveModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: ONLY_WHEN_LOCKED
        }
    }
}
