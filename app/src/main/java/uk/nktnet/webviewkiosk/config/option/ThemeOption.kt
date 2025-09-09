package uk.nktnet.webviewkiosk.config.option

enum class ThemeOption {
    SYSTEM,
    DARK,
    LIGHT;
    companion object {
        fun fromString(value: String?): ThemeOption {
            return entries.find { it.name == value } ?: SYSTEM
        }
    }
}