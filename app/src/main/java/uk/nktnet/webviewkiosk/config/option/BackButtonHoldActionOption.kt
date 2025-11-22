package com.nktnet.webview_kiosk.config.option

enum class BackButtonHoldActionOption(val label: String) {
    OPEN_KIOSK_CONTROL_PANEL("Open Kiosk Control Panel"),
    GO_HOME("Go Home"),
    DISABLED("Disabled");

    companion object {
        fun fromString(value: String?): BackButtonHoldActionOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: OPEN_KIOSK_CONTROL_PANEL
        }
    }
}
