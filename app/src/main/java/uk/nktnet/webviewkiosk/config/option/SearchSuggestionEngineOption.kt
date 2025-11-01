package uk.nktnet.webviewkiosk.config.option

enum class SearchSuggestionEngineOption {
    NONE,
    GOOGLE,
    DUCKDUCKGO,
    BRAVE,
    YAHOO;

    companion object {
        fun fromString(value: String?): SearchSuggestionEngineOption {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}
