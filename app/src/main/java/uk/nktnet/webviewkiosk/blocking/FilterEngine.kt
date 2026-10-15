package uk.nktnet.webviewkiosk.blocking

/** Injectable request/selector boundary shared by production and host tests. */
internal interface NetworkFilterEngine {
    fun replace(lists: List<String>)
    fun clear()
    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean
    fun hasCosmeticRules(): Boolean = false
    fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> = emptyList()
    fun unsupportedRuleCount(): Int = 0
}

internal fun interface FilterListValidator {
    fun validate(text: String)
}
