package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

enum class AddressBarActionOption(val label: String) {
    BACK("Back"),
    FORWARD("Forward"),
    REFRESH("Refresh"),
    HOME("Home"),
    HISTORY("History"),
    BOOKMARK("Bookmark"),
    FILES("Files");

    companion object {
        fun fromString(value: String?): AddressBarActionOption? {
            return AddressBarActionOption.entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            }
        }
        fun getDefault(): List<AddressBarActionOption> = listOf(
            BACK,
            FORWARD,
            REFRESH,
            HOME,
            HISTORY,
            BOOKMARK,
            FILES
        )

        fun parseAddressBarActions(jsonArray: JSONArray?): List<AddressBarActionOption> {
            if (jsonArray == null) {
                return AddressBarActionOption.getDefault()
            }
            return List(jsonArray.length()) { idx ->
                AddressBarActionOption.fromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
