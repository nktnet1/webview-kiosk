package com.nktnet.webview_kiosk.config.option

enum class OverrideUrlLoadingBlockActionOption(val label: String) {
    SHOW_BLOCK_PAGE("Show Block Page"),
    PREVENT_NAVIGATION("Prevent Navigation"),
    SHOW_TOAST("Show Toast");

    companion object {
        fun fromString(value: String?): OverrideUrlLoadingBlockActionOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: SHOW_BLOCK_PAGE
        }
    }
}
