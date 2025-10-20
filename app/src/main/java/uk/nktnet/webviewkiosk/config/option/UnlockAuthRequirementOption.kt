package uk.nktnet.webviewkiosk.config.option

enum class UnlockAuthRequirementOption {
    DEFAULT,
    OFF,
    REQUIRE;
    companion object {
        fun fromString(value: String?): UnlockAuthRequirementOption {
            return entries.find { it.name == value } ?: DEFAULT
        }
    }
}
