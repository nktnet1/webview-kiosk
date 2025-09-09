package uk.nktnet.webviewkiosk.config.option

enum class AddressBarOption {
    HIDDEN,
    HIDDEN_WHEN_LOCKED,
    SHOWN;
    companion object {
        fun fromString(value: String?): AddressBarOption {
            return entries.find { it.name == value } ?: HIDDEN_WHEN_LOCKED
        }
    }
}