package com.nktnet.webview_kiosk.config.option

enum class AddressBarPositionOption(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom");

    companion object {
        fun fromString(value: String?): AddressBarPositionOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: TOP
        }
    }
}
