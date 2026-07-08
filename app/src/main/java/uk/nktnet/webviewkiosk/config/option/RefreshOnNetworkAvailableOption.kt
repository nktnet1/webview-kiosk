package uk.nktnet.webviewkiosk.config.option

enum class RefreshOnNetworkAvailableOption(val label: String) {
    ALWAYS("Always"),
    ON_PAGE_ERROR("On Page Error"),
    NEVER("Never");

    companion object {
        fun fromString(value: String?): RefreshOnNetworkAvailableOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: ON_PAGE_ERROR
        }
    }
}
