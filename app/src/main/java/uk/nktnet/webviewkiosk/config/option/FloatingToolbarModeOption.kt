package uk.nktnet.webviewkiosk.config.option

enum class FloatingToolbarModeOption {
    HIDDEN,
    HIDDEN_WHEN_LOCKED,
    SHOWN;
    companion object {
        fun fromString(value: String?): FloatingToolbarModeOption {
            return entries.find { it.name == value } ?: HIDDEN_WHEN_LOCKED
        }
    }
}
