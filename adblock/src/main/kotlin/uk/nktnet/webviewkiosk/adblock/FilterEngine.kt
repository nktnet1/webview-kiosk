package uk.nktnet.webviewkiosk.adblock

enum class RuleLoadStage(val description: String) {
    READING_LISTS("Reading saved filter lists…"),
    CHECKING_CACHE("Checking for saved compiled rules…"),
    RESTORING("Loading compiled rules into memory…"),
    COMPILING("Compiling rules for the enabled lists…"),
    INDEXING("Building rule indexes in memory…"),
    SAVING_CACHE("Saving compiled rules for faster startup…"),
    CLEARING("Disabling filter rules…"),
}

/** Injectable request/selector boundary shared by production and host tests. */
interface NetworkFilterEngine {
    fun replace(lists: List<String>)

    fun replace(lists: List<String>, onProgress: (RuleLoadStage) -> Unit) {
        onProgress(RuleLoadStage.COMPILING)
        replace(lists)
    }

    fun clear()

    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean

    fun hasCosmeticRules(): Boolean = false

    fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> =
        emptyList()

    fun unsupportedRuleCount(): Int = 0
}

fun interface FilterListValidator {
    fun validate(text: String)
}
