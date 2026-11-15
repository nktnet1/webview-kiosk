package uk.nktnet.webviewkiosk.config.option

/** Off / Require, with Require as the fail-closed default. */
enum class SettingsAuthRequirementOption(val label: String) {
    OFF("Off"),
    REQUIRE("Require");

    companion object {
        // Managed restrictions use exact option codes, not translated labels.
        fun fromString(value: String?): SettingsAuthRequirementOption =
            entries.find { it.name == value } ?: REQUIRE
    }
}
