package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

enum class AddressBarActionOption(val label: String) {
    NAVIGATION("Navigation"),
    BACK("Back"),
    FORWARD("Forward"),
    REFRESH("Refresh"),
    HOME("Home"),
    HISTORY("History"),
    BOOKMARK("Bookmark"),
    FILES("Files"),
    SETTINGS("Settings"),
    LOCK("Lock"),
    UNLOCK("Unlock");

    companion object {
        fun itemFromString(value: String?): AddressBarActionOption? {
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

        fun parseFromJsonArray(jsonArray: JSONArray?): List<AddressBarActionOption> {
            if (jsonArray == null) {
                return AddressBarActionOption.getDefault()
            }
            return List(jsonArray.length()) { idx ->
                AddressBarActionOption.itemFromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
