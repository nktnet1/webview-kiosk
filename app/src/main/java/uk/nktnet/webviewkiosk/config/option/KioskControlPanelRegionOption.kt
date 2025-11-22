package com.nktnet.webview_kiosk.config.option

enum class KioskControlPanelRegionOption(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    TOP("Top"),
    BOTTOM("Bottom"),
    FULL("Full"),
    DISABLED("Disabled");

    companion object {
        fun fromString(value: String?): KioskControlPanelRegionOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: TOP_LEFT
        }
    }
}
