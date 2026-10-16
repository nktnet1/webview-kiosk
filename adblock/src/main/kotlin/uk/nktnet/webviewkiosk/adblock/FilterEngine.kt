package uk.nktnet.webviewkiosk.adblock

/** Only a bounded set of local surrogate resources is supported. */
enum class BlockingResponse {
    ALLOW,
    BLOCK,
    EMPTY_SCRIPT,
    EMPTY_STYLESHEET,
    EMPTY_HTML,
    EMPTY_TEXT,
    TRANSPARENT_GIF,
    TRANSPARENT_PNG,
    TRANSPARENT_2X2_PNG,
}

enum class RuleLoadStage(val description: String) {
    READING_LISTS("Reading saved filter lists…"),
    CHECKING_CACHE("Checking for saved compiled rules…"),
    RESTORING("Loading compiled rules into memory…"),
    COMPILING("Compiling rules for the enabled lists…"),
    INDEXING("Building rule indexes in memory…"),
    SAVING_CACHE("Saving compiled rules for faster startup…"),
    CLEARING("Disabling filter rules…"),
}

/** Only declarative, bounded DOM operations are sent to the WebView. */
data class CosmeticProcedure(
    val selector: String,
    val text: String? = null,
    val upward: Int = 0,
    val remove: Boolean = false,
    val style: String? = null,
    val removeAttribute: String? = null,
    val removeClass: String? = null,
    val upwardSelector: String? = null,
)

/** Injectable request/selector boundary shared by production and host tests. */
interface NetworkFilterEngine {
    fun replace(lists: List<String>)

    fun replace(lists: List<String>, onProgress: (RuleLoadStage) -> Unit) {
        onProgress(RuleLoadStage.COMPILING)
        replace(lists)
    }

    fun clear()

    fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean

    fun responseFor(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): BlockingResponse =
        if (shouldBlock(url, sourceUrl, type, method)) {
            BlockingResponse.BLOCK
        } else {
            BlockingResponse.ALLOW
        }

    fun hasCosmeticRules(): Boolean = false

    fun cosmeticSelectors(
        url: String,
        classes: List<String>,
        ids: List<String>,
    ): List<String> = emptyList()

    fun cosmeticProcedures(url: String): List<CosmeticProcedure> = emptyList()

    fun scriptletInjection(): ScriptletInjection? = null

    fun scriptlets(url: String): List<ScriptletInstruction> = emptyList()

    fun unsupportedRuleCount(): Int = 0
}

fun interface FilterListValidator {
    fun validate(text: String)
}
