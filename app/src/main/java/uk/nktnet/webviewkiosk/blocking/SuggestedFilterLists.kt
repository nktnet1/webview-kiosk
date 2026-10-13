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
            name = "HaGeZi Multi NORMAL",
            description = "Ads, trackers & threats",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/multi.txt",
        ),
    )
}

/** Performs user-initiated HTTPS downloads and refreshes, never background subscriptions. */
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
        require(!store.containsSource(suggestion.url)) { "This filter list has already been downloaded" }
        return fetch(suggestion.url) { stream ->
            store.importFrom(
                name = "${suggestion.name}.txt",
                input = stream,
                sourceUrl = suggestion.url,
            )
        }
    }

    /** Refetches any previously downloaded list, including older non-preset URLs. */
    fun refresh(item: ImportedFilterList, store: FilterListStore): ImportedFilterList {
        val current = store.list().firstOrNull { it.id == item.id }
            ?: throw IllegalArgumentException("Unknown filter list")
        val url = current.sourceUrl ?: throw IllegalArgumentException("Local lists cannot be refreshed")
        return fetch(url) { stream -> store.replaceFromSource(current.id, url, stream) }
    }

    private inline fun <T> fetch(url: String, save: (java.io.InputStream) -> T): T {
        require(url.startsWith("https://", ignoreCase = true)) { "HTTPS is required for filter lists" }

        val request = Request.Builder()
            .url(url)
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
            return save(body.byteStream())
        }
    }
}
