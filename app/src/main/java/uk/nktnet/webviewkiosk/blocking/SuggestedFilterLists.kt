package uk.nktnet.webviewkiosk.blocking

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Opt-in download shortcuts only. No rule lists or subscriptions are bundled or
 * fetched at startup. The source URLs are published by their respective list
 * maintainers.
 */
internal data class SuggestedFilterList(
    val name: String,
    val description: String,
    val url: String,
)

internal object SuggestedFilterLists {
    private const val HAGEZI_BASE_URL =
        "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock"

    val all =
        listOf(
            SuggestedFilterList(
                name = "EasyList",
                description = "Ads",
                url = "https://easylist.to/easylist/easylist.txt",
            ),
            SuggestedFilterList(
                name = "AdGuard Base",
                description = "Ads",
                url = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            ),
            SuggestedFilterList(
                name = "EasyPrivacy",
                description = "Trackers",
                url = "https://easylist.to/easylist/easyprivacy.txt",
            ),
            SuggestedFilterList(
                name = "HaGeZi Multi LIGHT",
                description = "Ads & trackers · basic protection",
                url = "$HAGEZI_BASE_URL/light.txt",
            ),
            SuggestedFilterList(
                name = "HaGeZi Multi NORMAL",
                description = "Ads, trackers & threats",
                url = "$HAGEZI_BASE_URL/multi.txt",
            ),
            SuggestedFilterList(
                name = "HaGeZi Multi PRO",
                description = "Ads, trackers & threats · extended protection",
                url = "$HAGEZI_BASE_URL/pro.txt",
            ),
            SuggestedFilterList(
                name = "HaGeZi Multi PRO++",
                description = "Ads, trackers & threats · aggressive protection",
                url = "$HAGEZI_BASE_URL/pro.plus.txt",
            ),
            SuggestedFilterList(
                name = "HaGeZi Multi ULTIMATE",
                description = "Ads, trackers & threats · maximum protection",
                url = "$HAGEZI_BASE_URL/ultimate.txt",
            ),
        )
}

internal fun String.toFilterListUrlOrNull(): HttpUrl? =
    trim().toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() && it.password.isEmpty()
    }

internal data class FilterRefreshFailure(val name: String, val message: String)

internal data class FilterRefreshResult(
    val updated: Int,
    val total: Int,
    val failures: List<FilterRefreshFailure>,
)

/** Performs user-initiated downloads and refreshes. */
internal class SuggestedFilterListDownloader(
    private val client: OkHttpClient = sharedClient
) {
    companion object {
        private val sharedClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }
    }

    /** Call on Dispatchers.IO. No automatic refreshing or silent list activation. */
    fun download(
        suggestion: SuggestedFilterList,
        store: FilterListStore,
    ): ImportedFilterList {
        require(suggestion in SuggestedFilterLists.all) {
            "Unknown suggested filter list"
        }
        require(!store.containsPreset(suggestion.url)) {
            "This preset has already been added"
        }
        return fetch(suggestion.url) { stream ->
            store.importFrom(
                name = "${suggestion.name}.txt",
                input = stream,
                sourceUrl = suggestion.url,
                type = FilterListType.PRESET,
            )
        }
    }

    /** Download a manually entered URL using the same limits as presets. */
    fun downloadUrl(
        url: String,
        name: String,
        store: FilterListStore,
    ): ImportedFilterList {
        val validated = requireListUrl(url)
        require(!store.containsUrl(validated)) {
            "A URL list with this address already exists"
        }
        return fetch(validated) { stream ->
            store.importFrom(name, stream, validated, FilterListType.URL)
        }
    }

    /**
     * Refetches any downloaded list, including presets and older non-preset URLs.
     */
    fun refresh(
        item: ImportedFilterList,
        store: FilterListStore,
    ): ImportedFilterList {
        val current =
            store.list().firstOrNull { it.id == item.id }
                ?: throw IllegalArgumentException("Unknown filter list")
        val url =
            current.sourceUrl
                ?: throw IllegalArgumentException("Local lists cannot be refreshed")
        return fetch(url) { stream ->
            store.replaceFromSource(current.id, url, stream)
        }
    }

    /**
     * Change the URL and rules together; name-only edits never trigger a download.
     */
    fun editUrl(
        item: ImportedFilterList,
        name: String,
        url: String,
        store: FilterListStore,
    ): ImportedFilterList {
        val current =
            store.list().firstOrNull { it.id == item.id }
                ?: throw IllegalArgumentException("Unknown filter list")
        require(current.type == FilterListType.URL) {
            "Only URL lists have editable download addresses"
        }
        val validated = requireListUrl(url)
        val original =
            current.sourceUrl ?: throw IllegalArgumentException("Missing list URL")
        if (validated == original) {
            store.rename(current.id, name)
            return store.list().first { it.id == current.id }
        }
        require(!store.containsUrl(validated)) {
            "A URL list with this address already exists"
        }
        return fetch(validated) { stream ->
            store.updateUrl(current.id, original, validated, name, stream)
        }
    }

    /**
     * Continue past individual failures. Callers queue one engine reload after the
     * batch.
     */
    fun refreshAll(store: FilterListStore): FilterRefreshResult {
        val candidates = store.list().filter { it.sourceUrl != null }
        val failures = mutableListOf<FilterRefreshFailure>()
        var updated = 0
        candidates.forEach { item ->
            try {
                refresh(item, store)
                updated++
            } catch (error: Exception) {
                failures +=
                    FilterRefreshFailure(item.name, error.message ?: "Update failed")
            }
        }
        return FilterRefreshResult(updated, candidates.size, failures)
    }

    private fun requireListUrl(url: String): String =
        requireNotNull(url.toFilterListUrlOrNull()) {
                "Enter a valid HTTP or HTTPS filter-list URL"
            }
            .toString()

    private inline fun <T> fetch(url: String, save: (java.io.InputStream) -> T): T {
        val request =
            Request.Builder()
                .url(requireListUrl(url))
                .header("Accept", "text/plain")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Filter-list download failed (HTTP ${response.code})"
                )
            }
            val body = response.body
            if (body.contentLength() > FilterListStore.MAX_LIST_BYTES) {
                throw IOException("Filter list exceeds 8 MiB")
            }
            if (
                body.contentType()?.let {
                    it.type == "text" && it.subtype == "html"
                } == true
            ) {
                throw IOException(
                    "The server returned an HTML page instead of a filter list"
                )
            }
            return save(body.byteStream())
        }
    }
}
