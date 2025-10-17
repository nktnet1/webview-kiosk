package uk.nktnet.webviewkiosk.config.option

enum class ImmersiveModeOption {
    ALWAYS_ON,
    ALWAYS_OFF,
    ONLY_WHEN_LOCKED;

    companion object {
        fun fromString(value: String?): ImmersiveModeOption {
            return entries.find { it.name == value } ?: ONLY_WHEN_LOCKED
        }
    }
}
