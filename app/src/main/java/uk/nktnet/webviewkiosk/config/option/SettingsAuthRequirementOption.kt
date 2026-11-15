package uk.nktnet.webviewkiosk.config.option

/** Off / Require, with Require as the fail-closed default. */
enum class SettingsAuthRequirementOption(val label: String) {
    OFF("Off"),
    REQUIRE("Require");

    companion object {
        fun fromString(value: String?): SettingsAuthRequirementOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                    it.label.equals(value, ignoreCase = true)
            } ?: REQUIRE
        }
    }
}
