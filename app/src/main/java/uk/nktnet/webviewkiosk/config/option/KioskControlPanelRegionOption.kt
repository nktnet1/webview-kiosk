package uk.nktnet.webviewkiosk.config.option

enum class KioskControlPanelRegionOption {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    FULL,
    DISABLED;

    companion object {
        fun fromString(value: String?): KioskControlPanelRegionOption {
            return entries.find { it.name == value } ?: TOP_LEFT
        }
    }
}
