package uk.nktnet.webviewkiosk.config.option

enum class AddressBarAction(val label: String) {
    BACK("Back"),
    FORWARD("Forward"),
    REFRESH("Refresh"),
    HOME("Home"),
    HISTORY("History"),
    BOOKMARK("Bookmark"),
    FILES("Files");

    companion object {
        fun fromString(value: String?): AddressBarAction? {
            return AddressBarAction.entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            }
        }
        fun getDefault(): List<AddressBarAction> = listOf(
            BACK,
            FORWARD,
            REFRESH,
            HOME,
            HISTORY,
            BOOKMARK,
            FILES
        )
    }
}
