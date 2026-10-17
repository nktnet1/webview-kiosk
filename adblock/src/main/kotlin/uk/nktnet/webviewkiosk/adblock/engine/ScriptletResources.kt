package uk.nktnet.webviewkiosk.adblock.engine

/** Static bundled API shims; no filter-controlled source or resource path. */
internal object ScriptletResources {
    val aliases =
        mapOf(
            "amazon_ads" to "amazon_ads.js",
            "amazon-adsystem.com/aax2/amzn_ads" to "amazon_ads.js",
            "adthrive_abd" to "adthrive_abd.js",
            "doubleclick_instream_ad_status" to "doubleclick_instream_ad_status.js",
            "doubleclick.net/instream/ad_status" to
                "doubleclick_instream_ad_status.js",
            "fingerprint2" to "fingerprint2.js",
            "fingerprint3" to "fingerprint3.js",
            "google-analytics_analytics" to "google-analytics_analytics.js",
            "google-analytics.com/analytics" to "google-analytics_analytics.js",
            "googletagmanager_gtm" to "google-analytics_analytics.js",
            "googletagmanager.com/gtm" to "google-analytics_analytics.js",
            "google-analytics_ga" to "google-analytics_ga.js",
            "google-analytics.com/ga" to "google-analytics_ga.js",
            "google-ima" to "google-ima.js",
            "google-ima3" to "google-ima.js",
            "google-ima-dai" to "google-ima-dai.js",
            "google-ima3-dai" to "google-ima-dai.js",
            "googlesyndication_adsbygoogle" to "googlesyndication_adsbygoogle.js",
            "googlesyndication.com/adsbygoogle" to
                "googlesyndication_adsbygoogle.js",
            "googlesyndication-adsbygoogle" to "googlesyndication_adsbygoogle.js",
            "googletagservices_gpt" to "googletagservices_gpt.js",
            "googletagservices.com/gpt" to "googletagservices_gpt.js",
            "googletagservices-gpt" to "googletagservices_gpt.js",
            "nitropay_ads" to "nitropay_ads.js",
            "nofab" to "nofab.js",
            "fuckadblock.js-3.2.0" to "nofab.js",
            "popads" to "popads.js",
            "popads.net" to "popads.js",
            "prevent-popads-net" to "popads.js",
            "popads-dummy" to "popads-dummy.js",
            "prebid-ads" to "prebid-ads.js",
            "sensors-analytics" to "sensors-analytics.js",
        )
    // Upstream marks these resources redirect-only; they are not +js entry points.
    val redirectAliases =
        mapOf(
            "piano-analytics" to "piano-analytics.js",
            "ampproject_v0" to "ampproject_v0.js",
            "ampproject.org/v0" to "ampproject_v0.js",
            "outbrain-widget" to "outbrain-widget.js",
            "widgets.outbrain.com/outbrain" to "outbrain-widget.js",
            "silent-noeval" to "noeval-silent.js",
            "google-analytics_cx_api" to "google-analytics_cx_api.js",
            "google-analytics.com/cx/api" to "google-analytics_cx_api.js",
            "google-analytics_inpage_linkid" to "google-analytics_inpage_linkid.js",
            "google-analytics.com/inpage_linkid" to
                "google-analytics_inpage_linkid.js",
            "scorecardresearch_beacon" to "scorecardresearch_beacon.js",
            "scorecardresearch.com/beacon" to "scorecardresearch_beacon.js",
        )
    val files = aliases.values.toSet()
    val trustedFiles = setOf("google-ima-dai.js")

    fun factories(names: Set<String>): String =
        names.joinToString(",", "{", "}") { name ->
            require(name in files)
            val source =
                ScriptletResources::class
                    .java
                    .getResourceAsStream("/adblock/ubo/$name")!!
                    .bufferedReader()
                    .use { it.readText() }
            ScriptletIndex.json(name) +
                ":function(setTimeout,clearTimeout,requestAnimationFrame){\n$source\n}"
        }
}
