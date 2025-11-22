package com.nktnet.webview_kiosk.config.option

enum class UnlockAuthRequirementOption(val label: String) {
    DEFAULT("Default"),
    OFF("Off"),
    REQUIRE("Require");

    companion object {
        fun fromString(value: String?): UnlockAuthRequirementOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
