package uk.nktnet.webviewkiosk.blocking

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Opt-in download shortcuts only. No rule lists or subscriptions are bundled or fetched at startup.
 * The source URLs are published by their respective list maintainers.
 */
internal data class SuggestedFilterList(
    val name: String,
    val description: String,
    val url: String,
)

internal object SuggestedFilterLists {
    val all = listOf(
        SuggestedFilterList(
            name = "EasyList",
            description = "Ads",
            url = "https://easylist.to/easylist/easylist.txt",
        ),
        SuggestedFilterList(
            name = "EasyPrivacy",
            description = "Trackers",
            url = "https://easylist.to/easylist/easyprivacy.txt",
        ),
        SuggestedFilterList(
            name = "AdGuard Base",
            description = "Ads · overlaps EasyList",
            url = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
        ),
    )
}

/** Performs one user-initiated HTTPS download; storage validation is shared with local imports. */
internal class SuggestedFilterListDownloader(
    private val client: OkHttpClient = sharedClient,
) {
    companion object {
        // Reuse one small connection pool across settings visits instead of creating one per screen.
        private val sharedClient by lazy {
            OkHttpClient.Builder()
                // Follow ordinary HTTPS redirects, but never downgrade to cleartext HTTP.
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }
    }

    /** Call on Dispatchers.IO. No automatic refreshing or silent list activation. */
    fun download(suggestion: SuggestedFilterList, store: FilterListStore): ImportedFilterList {
        require(suggestion in SuggestedFilterLists.all) { "Unknown suggested filter list" }
        require(suggestion.url.startsWith("https://")) { "HTTPS is required for filter lists" }
        require(!store.containsSource(suggestion.url)) { "This filter list has already been downloaded" }

        val request = Request.Builder()
            .url(suggestion.url)
            .header("Accept", "text/plain")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Filter-list download failed (HTTP ${response.code})")
            }
            if (response.request.url.scheme != "https") {
                throw IOException("Filter lists must be downloaded over HTTPS")
            }
            val body = response.body
            if (body.contentLength() > FilterListStore.MAX_LIST_BYTES) {
                throw IOException("Filter list exceeds 8 MiB")
            }
            if (body.contentType()?.let {
                    it.type == "text" && it.subtype == "html"
                } == true) {
                throw IOException("The server returned an HTML page instead of a filter list")
            }
            return store.importFrom(
                name = "${suggestion.name}.txt",
                input = body.byteStream(),
                sourceUrl = suggestion.url,
            )
        }
    }
}
