package uk.nktnet.webviewkiosk.config.option

enum class AddressBarModeOption {
    HIDDEN,
    HIDDEN_WHEN_LOCKED,
    SHOWN;
    companion object {
        fun fromString(value: String?): AddressBarModeOption {
            return entries.find { it.name == value } ?: HIDDEN_WHEN_LOCKED
        }
    }
}
