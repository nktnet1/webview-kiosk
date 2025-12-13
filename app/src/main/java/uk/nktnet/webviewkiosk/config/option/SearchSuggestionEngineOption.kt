package com.nktnet.webview_kiosk.config.option

enum class SearchSuggestionEngineOption(val label: String) {
    NONE("None"),
    GOOGLE("Google"),
    DUCKDUCKGO("DuckDuckGo"),
    YAHOO("Yahoo");

    companion object {
        fun fromString(value: String?): SearchSuggestionEngineOption {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: NONE
        }
    }
}
