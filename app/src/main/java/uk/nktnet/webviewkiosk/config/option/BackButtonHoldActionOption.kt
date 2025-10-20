package uk.nktnet.webviewkiosk.config.option

enum class BackButtonHoldActionOption {
    OPEN_KIOSK_CONTROL_PANEL,
    GO_HOME,
    DISABLED;
    companion object {
        fun fromString(value: String?): BackButtonHoldActionOption {
            return entries.find { it.name == value } ?: OPEN_KIOSK_CONTROL_PANEL
        }
    }
}
