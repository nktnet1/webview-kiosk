package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

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

        fun parseAddressBarActions(jsonArray: JSONArray?): List<AddressBarAction> {
            if (jsonArray == null) {
                return AddressBarAction.getDefault()
            }
            return List(jsonArray.length()) { idx ->
                AddressBarAction.fromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
