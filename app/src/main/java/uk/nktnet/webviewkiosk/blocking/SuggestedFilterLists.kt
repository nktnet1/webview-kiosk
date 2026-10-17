package uk.nktnet.webviewkiosk.blocking

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.nktnet.webviewkiosk.adblock.expandFilterListIncludes

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

    private fun ublock(name: String, description: String, file: String) =
        SuggestedFilterList(
            name = "uBlock $name",
            description = description,
            url = "https://ublockorigin.github.io/uAssets/filters/$file.txt",
        )

    private val ublock =
        listOf(
            ublock("Ads", "Ads", "filters"),
            ublock("Privacy", "Trackers", "privacy"),
            ublock("Badware", "Malicious sites", "badware"),
            ublock("Quick fixes", "Urgent filter fixes", "quick-fixes"),
            ublock("Unbreak", "Site compatibility fixes", "unbreak"),
        )

    private val others =
        listOf(
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
                name = "Online Malicious URL Blocklist",
                // Maintainer's network-filter format avoids unsupported $all/CSP.
                description = "Malicious site navigation",
                url =
                    "https://malware-filter.gitlab.io/urlhaus-filter/" +
                        "urlhaus-filter-vivaldi-online.txt",
            ),
            SuggestedFilterList(
                name = "Peter Lowe’s Ad and tracking server list",
                description = "Ads & trackers",
                url =
                    "https://pgl.yoyo.org/adservers/serverlist.php?" +
                        "hostformat=hosts&showintro=1&mimetype=plaintext",
            ),
            SuggestedFilterList(
                name = "AdGuard Base",
                description = "Ads",
                url = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
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

    private val optionalUblock =
        listOf(
            ublock("Annoyances - Cookies", "Cookie notices", "annoyances-cookies"),
            ublock("Annoyances - Others", "Other annoyances", "annoyances-others"),
        )

    val trustedUrls = (ublock + optionalUblock).map { it.url }.toSet()

    val all = ublock + optionalUblock + others

    // The nine globally enabled subscriptions in upstream assets/assets.json.
    // Regional and optional annoyance lists depend on user preference.
    val defaults = ublock + others.take(4)
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
        private const val MAX_INCLUDE_REQUESTS = 32
        private const val MAX_INCLUDE_DEPTH = 3
        private val INCLUDE_PATH = Regex("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.txt")

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
                trusted = suggestion.url in SuggestedFilterLists.trustedUrls,
            )
        }
    }

    /**
     * Add missing defaults, preserving existing order, edits, and enabled states.
     */
    fun downloadDefaults(store: FilterListStore): FilterRefreshResult {
        val missing =
            SuggestedFilterLists.defaults.filterNot {
                store.containsSource(it.url)
            }
        val failures = mutableListOf<FilterRefreshFailure>()
        var added = 0
        missing.forEach { suggestion ->
            try {
                download(suggestion, store)
                added++
            } catch (error: Exception) {
                failures +=
                    FilterRefreshFailure(
                        suggestion.name,
                        error.message ?: "Download failed",
                    )
            }
        }
        return FilterRefreshResult(added, missing.size, failures)
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
            store.replaceFromSource(
                current.id,
                url,
                stream,
                trusted =
                    current.type == FilterListType.PRESET &&
                        url in SuggestedFilterLists.trustedUrls,
            )
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
    fun refreshAll(store: FilterListStore): FilterRefreshResult =
        refreshSelected(store, store.list().map { it.id }.toSet())

    /** Refresh only the selected subscriptions; local lists have no download URL. */
    fun refreshSelected(
        store: FilterListStore,
        ids: Set<String>,
    ): FilterRefreshResult {
        val candidates = store.list().filter { it.id in ids && it.sourceUrl != null }
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

    /** Resolve only same-origin relative text files, with a shared 8 MiB budget. */
    private fun <T> fetch(url: String, save: (java.io.InputStream) -> T): T {
        val source = requireNotNull(requireListUrl(url).toHttpUrlOrNull())
        val budget = IncludeBudget()
        val seen = mutableSetOf(source.toString())

        fun expand(address: HttpUrl, depth: Int): String {
            val text = fetchText(address, budget)
            return expandFilterListIncludes(text) { path ->
                require(depth < MAX_INCLUDE_DEPTH) {
                    "Filter-list includes exceed the nesting limit"
                }
                val child = includedUrl(address, path)
                require(seen.add(child.toString())) {
                    "A filter-list include is repeated or circular: $path"
                }
                expand(child, depth + 1)
            }
        }

        val expanded = expand(source, 0).toByteArray(StandardCharsets.UTF_8)
        require(expanded.size <= FilterListStore.MAX_LIST_BYTES) {
            "Expanded filter list exceeds 8 MiB"
        }
        return save(ByteArrayInputStream(expanded))
    }

    private class IncludeBudget {
        var requests = 0
        var bytes = 0L
    }

    private fun includedUrl(source: HttpUrl, path: String): HttpUrl {
        // Do not let an untrusted subscription fetch absolute URLs, parent paths,
        // internal services, or even a different directory on the same server.
        require(path.length in 1..160 && INCLUDE_PATH.matches(path)) {
            "Unsafe filter-list include path"
        }
        val resolved = requireNotNull(source.resolve(path))
        require(
            resolved.scheme == source.scheme &&
                resolved.host == source.host &&
                resolved.port == source.port &&
                resolved.encodedPath.startsWith(
                    source.encodedPath.substringBeforeLast('/') + "/"
                )
        ) {
            "Filter-list includes must remain in the source directory"
        }
        return resolved
    }

    private fun fetchText(url: HttpUrl, budget: IncludeBudget): String {
        require(++budget.requests <= MAX_INCLUDE_REQUESTS) {
            "Too many filter-list includes"
        }
        val request =
            Request.Builder().url(url).header("Accept", "text/plain").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Filter-list download failed (HTTP ${response.code})"
                )
            }
            val actual = response.request.url
            require(
                actual.scheme == url.scheme &&
                    actual.host == url.host &&
                    actual.port == url.port
            ) {
                "Filter-list redirect crosses the source origin"
            }
            if (
                response.body.contentLength() >
                    FilterListStore.MAX_LIST_BYTES - budget.bytes
            ) {
                throw IOException("Expanded filter list exceeds 8 MiB")
            }
            if (
                response.body.contentType()?.let {
                    it.type == "text" && it.subtype == "html"
                } == true
            ) {
                throw IOException(
                    "The server returned an HTML page instead of a filter list"
                )
            }
            val output = ByteArrayOutputStream()
            response.body.byteStream().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        break
                    }
                    budget.bytes += read
                    if (budget.bytes > FilterListStore.MAX_LIST_BYTES) {
                        throw IOException("Expanded filter list exceeds 8 MiB")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        }
    }
}
