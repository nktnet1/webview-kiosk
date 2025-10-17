package com.nktnet.webview_kiosk.config.option

enum class KioskControlPanelOption {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    FULL,
    DISABLED;

    companion object {
        fun fromString(value: String?): KioskControlPanelOption {
            return entries.find { it.name == value } ?: TOP_LEFT
        }
    }
}
