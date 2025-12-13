package com.nktnet.webview_kiosk.config.option

enum class SslErrorModeOption(val label: String) {
    BLOCK("Block"),
    PROMPT("Prompt"),
    PROCEED("Proceed");

    companion object {
        fun fromString(value: String?): SslErrorModeOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: BLOCK
        }
    }
}
