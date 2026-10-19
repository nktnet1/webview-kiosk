package uk.nktnet.webviewkiosk.adblock

/** Only a bounded set of local surrogate resources is supported. */
enum class BlockingResponse(
    val resourcePath: String? = null,
    val mimeType: String? = null,
) {
    ALLOW,
    BLOCK,
    EMPTY_SCRIPT,
    EMPTY_STYLESHEET,
    EMPTY_HTML,
    EMPTY_TEXT,
    TRANSPARENT_GIF,
    TRANSPARENT_PNG,
    TRANSPARENT_2X2_PNG,
    EMPTY_JSON("noop.json", "application/json"),
    SILENT_MP3("noop-0.1s.mp3", "audio/mpeg"),
    SILENT_MP4("noop-1s.mp4", "video/mp4"),
    TRANSPARENT_32X32_PNG("32x32.png", "image/png"),
    AMAZON_ADS("amazon_ads.js", "application/javascript"),
    AMAZON_APSTAG("amazon_apstag.js", "application/javascript"),
    ADTHRIVE("adthrive_abd.js", "application/javascript"),
    CHARTBEAT("chartbeat.js", "application/javascript"),
    INSTREAM_AD_STATUS(
        "doubleclick_instream_ad_status.js",
        "application/javascript",
    ),
    FINGERPRINT2("fingerprint2.js", "application/javascript"),
    FINGERPRINT3("fingerprint3.js", "application/javascript"),
    GOOGLE_ANALYTICS("google-analytics_analytics.js", "application/javascript"),
    GOOGLE_ANALYTICS_GA("google-analytics_ga.js", "application/javascript"),
    GOOGLE_TAG_SERVICES("googletagservices_gpt.js", "application/javascript"),
    NITROPAY("nitropay_ads.js", "application/javascript"),
    NOFAB("nofab.js", "application/javascript"),
    POPADS("popads.js", "application/javascript"),
    POPADS_DUMMY("popads-dummy.js", "application/javascript"),
    PREBID("prebid-ads.js", "application/javascript"),
    SENSORS_ANALYTICS("sensors-analytics.js", "application/javascript"),
    GOOGLE_ANALYTICS_CX("google-analytics_cx_api.js", "application/javascript"),
    GOOGLE_ANALYTICS_LINK(
        "google-analytics_inpage_linkid.js",
        "application/javascript",
    ),
    SCORECARD_BEACON("scorecardresearch_beacon.js", "application/javascript"),
    EMPTY_VAST2("noop-vast2.xml", "text/xml"),
    EMPTY_VAST3("noop-vast3.xml", "text/xml"),
    EMPTY_VAST4("noop-vast4.xml", "text/xml"),
    EMPTY_VMAP("noop-vmap1.xml", "text/xml"),
    TRANSPARENT_3X2_PNG("3x2.png", "image/png"),
    GOOGLE_IMA("google-ima.js", "application/javascript"),
    PIANO_ANALYTICS("piano-analytics.js", "application/javascript"),
    AMP("ampproject_v0.js", "application/javascript"),
    OUTBRAIN("outbrain-widget.js", "application/javascript"),
    HD_MAIN("hd-main.js", "application/javascript"),
    NOBAB2("nobab2.js", "application/javascript"),
    ADSBYGOOGLE("googlesyndication_adsbygoogle.js", "application/javascript"),
    GOOGLE_IMA_DAI("google-ima-dai.js", "application/javascript"),
    SILENT_HALF_SECOND_MP3("noop-0.5s.mp3", "audio/mpeg"),
    NOEVAL("noeval.js", "application/javascript"),
    NOEVAL_SILENT("noeval-silent.js", "application/javascript"),
}

enum class RuleLoadStage(val description: String) {
    READING_LISTS("Reading saved filter lists…"),
    CHECKING_CACHE("Checking for saved compiled rules…"),
    RESTORING("Loading compiled rules into memory…"),
    COMPILING("Compiling rules for the enabled lists…"),
    INDEXING("Preparing rule indexes in memory…"),
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
    val textSelector: String? = null,
    val textPattern: Boolean = false,
    val media: String? = null,
    val operations: String? = null,
)

/** Trust is assigned by the subscription loader, never by text inside a list. */
data class FilterSource(val text: String, val trusted: Boolean = false)

/** Injectable request/selector boundary shared by production and host tests. */
interface NetworkFilterEngine {
    fun replace(lists: List<String>)

    fun replace(lists: List<String>, onProgress: (RuleLoadStage) -> Unit) {
        onProgress(RuleLoadStage.COMPILING)
        replace(lists)
    }

    fun replaceSources(
        sources: List<FilterSource>,
        onProgress: (RuleLoadStage) -> Unit,
    ) {
        replace(sources.map { it.text }, onProgress)
    }

    fun clear()

    fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean

    /** An empty source URL denotes an unknown initiator, never an inferred party. */
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

    fun removeQueryParameters(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): String = url

    fun cosmeticSelectors(
        url: String,
        classes: List<String>,
        ids: List<String>,
    ): List<String> = emptyList()

    fun cosmeticProcedures(url: String): List<CosmeticProcedure> = emptyList()

    fun scriptletInjection(): ScriptletInjection? = null

    fun scriptlets(url: String): List<ScriptletInstruction> = emptyList()

    fun scriptlets(
        url: String,
        ancestorUrls: List<String>,
    ): List<ScriptletInstruction> = scriptlets(url)

    fun unsupportedRuleCount(): Int = 0
}

fun interface FilterListValidator {
    fun validate(text: String)

    fun validateSource(source: FilterSource) {
        validate(source.text)
    }
}
