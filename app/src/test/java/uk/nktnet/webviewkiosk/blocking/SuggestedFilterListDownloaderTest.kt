package uk.nktnet.webviewkiosk.blocking

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.FilterListValidator

@RunWith(RobolectricTestRunner::class)
class SuggestedFilterListDownloaderTest {
    private lateinit var store: FilterListStore
    private val preset = SuggestedFilterLists.all.first()
    private var currentTime = 1_000L

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        store =
            FilterListStore(
                context,
                validator = FilterListValidator {},
                now = { currentTime },
            )
    }

    private fun downloader(
        content: String = "[Adblock Plus 2.0]\n||ads.example^\n",
        mime: String = "text/plain",
        status: Int = 200,
        requests: AtomicInteger = AtomicInteger(),
        suggestion: SuggestedFilterList = preset,
    ): SuggestedFilterListDownloader =
        SuggestedFilterListDownloader(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requests.incrementAndGet()
                    assertEquals(suggestion.url, chain.request().url.toString())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("test response")
                        .body(content.toResponseBody(mime.toMediaType()))
                        .build()
                }
                .build()
        )

    @Test
    fun onlyFreshApprovedPresetDownloadsCarryTrustedProvenance() {
        val text =
            "||ads.example^\n" +
                "site.example##+js(trusted-prevent-xhr, ads, literal response)"
        val ublock = SuggestedFilterLists.all.first { it.name == "uBlock Ads" }
        // Metadata alone does not attest that stored bytes passed the loader.
        val legacy =
            store.importFrom(
                "Legacy",
                text.byteInputStream(),
                ublock.url,
                FilterListType.PRESET,
            )
        assertFalse(legacy.trusted)
        assertFalse(store.enabledSources().single().trusted)
        val refreshed =
            downloader(content = text, suggestion = ublock).refresh(legacy, store)
        assertTrue(refreshed.trusted)
        assertTrue(store.list().single().trusted)
        assertTrue(store.enabledSources().single().trusted)
        val easy = SuggestedFilterLists.all.first { it.name == "EasyList" }
        val ordinary =
            downloader(content = text, suggestion = easy).download(easy, store)
        assertFalse(ordinary.trusted)
        val custom = store.importFrom("Custom", text.byteInputStream(), ublock.url)
        assertFalse(custom.trusted)
        assertFalse(store.enabledSources().last().trusted)
        assertThrows(IllegalArgumentException::class.java) {
            store.importFrom(
                "Spoofed",
                text.byteInputStream(),
                ublock.url,
                trusted = true,
            )
        }
    }

    @Test
    fun trustedOnlyPresetPassesValidationAndIsCountedAsExecutable() {
        val validatedStore = FilterListStore(RuntimeEnvironment.getApplication())
        val text = "site.example##+js(trusted-prevent-xhr, ads, literal response)"
        val imported = downloader(content = text).download(preset, validatedStore)
        assertTrue(imported.trusted)
        assertEquals(1, validatedStore.info(imported.id).scriptletRules)
        assertEquals(0, validatedStore.info(imported.id).unsupportedRules)
        assertThrows(IllegalArgumentException::class.java) {
            validatedStore.importFrom("Custom", text.byteInputStream())
        }
    }

    @Test
    fun officialUBlockIncludesResolveSameOriginAndSkipInactiveBranches() {
        val ublock = SuggestedFilterLists.all.first { it.name == "uBlock Ads" }
        val requested = mutableListOf<String>()
        val contents =
            mapOf(
                "/uAssets/filters/filters.txt" to
                    "||root.example^\n!#include filters-2026.txt\n" +
                        "!#if env_mobile\n!#include filters-mobile.txt\n!#endif\n" +
                        "!#if env_safari\n!#include safari.txt\n!#endif\n",
                "/uAssets/filters/filters-2026.txt" to
                    "||year.example^\n!#include nested/ads.txt\n",
                "/uAssets/filters/nested/ads.txt" to "nested.example##.advert\n",
                "/uAssets/filters/filters-mobile.txt" to "||mobile.example^\n",
            )
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val address = chain.request().url
                        requested += address.toString()
                        val body =
                            contents[address.encodedPath]
                                ?: error("Unexpected request")
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    .build()
            )
        client.download(ublock, store)
        assertEquals(4, requested.size)
        assertTrue(
            requested.all { it.startsWith("https://ublockorigin.github.io/") }
        )
        assertFalse(requested.any { it.endsWith("safari.txt") })
        val saved = store.enabledTexts().single()
        assertTrue("||root.example^" in saved)
        assertTrue("||year.example^" in saved)
        assertTrue("nested.example##.advert" in saved)
        assertTrue("||mobile.example^" in saved)
    }

    @Test
    fun unsafeRepeatedAndMissingIncludesNeverReplaceSavedRules() {
        val source = "https://example.org/filters/list.txt"
        val saved =
            store.importFrom("List", "||old.example^".byteInputStream(), source)
        val originals = store.enabledTexts()
        val malformed =
            listOf(
                "!#include ../private.txt",
                "!#include https://other.example/filters.txt",
                "!#include repeated.txt\n!#include repeated.txt",
                "!#include missing.txt",
            )
        malformed.forEach { text ->
            val client =
                SuggestedFilterListDownloader(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val path = chain.request().url.encodedPath
                            val status =
                                if (path.endsWith("missing.txt")) {
                                    404
                                } else {
                                    200
                                }
                            val body =
                                if (path.endsWith("list.txt")) {
                                    text
                                } else {
                                    "||new.example^"
                                }
                            Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(status)
                                .message("test")
                                .body(
                                    body.toResponseBody("text/plain".toMediaType())
                                )
                                .build()
                        }
                        .build()
                )
            assertThrows(Exception::class.java) { client.refresh(saved, store) }
            assertEquals(originals, store.enabledTexts())
        }
    }

    @Test
    fun suggestionsAreLinksNotBundledLists() {
        assertEquals(
            listOf(
                "uBlock Ads",
                "uBlock Privacy",
                "uBlock Badware",
                "uBlock Quick fixes",
                "uBlock Unbreak",
                "EasyList",
                "EasyPrivacy",
                "Online Malicious URL Blocklist",
                "Peter Lowe’s Ad and tracking server list",
                "AdGuard Base",
                "HaGeZi Multi LIGHT",
                "HaGeZi Multi NORMAL",
                "HaGeZi Multi PRO",
                "HaGeZi Multi PRO++",
                "HaGeZi Multi ULTIMATE",
            ),
            SuggestedFilterLists.all.map { it.name },
        )
        assertEquals(
            listOf(
                "light.txt",
                "multi.txt",
                "pro.txt",
                "pro.plus.txt",
                "ultimate.txt",
            ),
            SuggestedFilterLists.all.takeLast(5).map {
                it.url.substringAfterLast('/')
            },
        )
        assertEquals(
            "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            SuggestedFilterLists.all.first { it.name == "AdGuard Base" }.url,
        )
        assertEquals(
            "https://ublockorigin.github.io/uAssets/filters/filters.txt",
            SuggestedFilterLists.all.first { it.name == "uBlock Ads" }.url,
        )
        assertTrue(SuggestedFilterLists.all.all { it.url.startsWith("https://") })
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun defaultSubscriptionsSkipExtendedScopesButStillRejectMalformedRefreshes() {
        val validatedStore = FilterListStore(RuntimeEnvironment.getApplication())
        val inputs =
            listOf(
                "uBlock Quick fixes" to
                    "||ads.example^\nf51rm.com>>##+js(nowoif, /^ [-\\d]/)\n" +
                        "igorslab.de/community/#@#img:remove-attr(/data-.+/)",
                "uBlock Unbreak" to
                    "@@||needed.example^\nnijie.info/ad_log.php#@#._banner_view",
                "Online Malicious URL Blocklist" to
                    "||shared.example/uploads/malware.exe^\$document",
            )
        inputs.forEach { (name, text) ->
            val suggestion = SuggestedFilterLists.defaults.first { it.name == name }
            val imported =
                downloader(content = text, suggestion = suggestion)
                    .download(suggestion, validatedStore)
            assertEquals(suggestion.url, imported.sourceUrl)
        }
        assertEquals(3, validatedStore.list().size)
        assertEquals(inputs.map { it.second }, validatedStore.enabledTexts())

        val original = validatedStore.list().first()
        val suggestion =
            SuggestedFilterLists.defaults.first { it.url == original.sourceUrl }
        val malformed =
            downloader(content = "site example##.advert", suggestion = suggestion)
        assertThrows(IllegalArgumentException::class.java) {
            malformed.refresh(original, validatedStore)
        }
        assertEquals(original, validatedStore.list().first())
        assertEquals(inputs.map { it.second }, validatedStore.enabledTexts())
    }

    @Test
    fun defaultsMatchUpstreamAndPreserveExistingStateAndOrder() {
        assertEquals(SuggestedFilterLists.all.take(9), SuggestedFilterLists.defaults)
        val existing =
            store.importFrom(
                "My EasyList",
                "||kept.example^".byteInputStream(),
                SuggestedFilterLists.defaults[5].url,
            )
        store.setEnabled(existing.id, false)
        val requested = mutableListOf<String>()
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requested += chain.request().url.toString()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                "||ads.example^"
                                    .toResponseBody("text/plain".toMediaType())
                            )
                            .build()
                    }
                    .build()
            )
        val result = client.downloadDefaults(store)
        assertEquals(8, result.updated)
        assertEquals(8, result.total)
        assertTrue(result.failures.isEmpty())
        assertEquals(8, requested.distinct().size)
        assertFalse(existing.sourceUrl in requested)
        assertEquals(existing.copy(enabled = false), store.list().first())
        assertEquals(9, store.list().size)
        val second = client.downloadDefaults(store)
        assertEquals(0, second.total)
        assertEquals(8, requested.size)
        assertEquals(9, store.list().size)
    }

    @Test
    fun defaultDownloadsKeepSuccessfulListsAndRetryOnlyFailures() {
        val failedUrl = SuggestedFilterLists.defaults[2].url
        var fail = true
        val requested = mutableListOf<String>()
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requested += chain.request().url.toString()
                        if (fail && chain.request().url.toString() == failedUrl) {
                            throw IOException("Offline")
                        }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                "||ads.example^"
                                    .toResponseBody("text/plain".toMediaType())
                            )
                            .build()
                    }
                    .build()
            )
        val first = client.downloadDefaults(store)
        assertEquals(8, first.updated)
        assertEquals(listOf("uBlock Badware"), first.failures.map { it.name })
        fail = false
        requested.clear()
        val second = client.downloadDefaults(store)
        assertEquals(1, second.updated)
        assertEquals(listOf(failedUrl), requested)
        assertEquals(9, store.list().size)
    }

    @Test
    fun everyPresetCanBeDownloadedOnDemand() {
        SuggestedFilterLists.all.forEach { suggestion ->
            val imported =
                downloader(suggestion = suggestion).download(suggestion, store)
            assertEquals("${suggestion.name}.txt", imported.name)
            assertEquals(suggestion.url, imported.sourceUrl)
            assertEquals(FilterListType.PRESET, imported.type)
            assertTrue(imported.enabled)
            assertTrue(store.containsPreset(suggestion.url))
        }
    }

    @Test
    fun previouslyImportedAdGuardUrlListIsPreservedWhenItBecomesAPreset() {
        val old =
            store.importFrom(
                "AdGuard Base.txt",
                "||ads.example^\n".byteInputStream(),
                sourceUrl =
                    "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            )
        assertEquals(FilterListType.URL, old.type)
        assertTrue(SuggestedFilterLists.all.any { it.url == old.sourceUrl })
        assertEquals(
            old,
            FilterListStore(RuntimeEnvironment.getApplication()).list().single(),
        )
    }

    @Test
    fun manuallyAddedUBlockUrlRemainsEditableAfterAddingThePreset() {
        val source = SuggestedFilterLists.all.first { it.name == "uBlock Ads" }.url
        val existing =
            store.importFrom(
                "Custom uBlock",
                "||ads.example^\n".byteInputStream(),
                sourceUrl = source,
            )
        assertEquals(FilterListType.URL, existing.type)
        assertEquals(existing, store.list().single())
        assertEquals(
            FilterListType.URL,
            FilterListStore(RuntimeEnvironment.getApplication())
                .list()
                .single()
                .type,
        )

        downloader(suggestion = preset).download(preset, store)
        assertEquals(
            listOf(FilterListType.URL, FilterListType.PRESET),
            store.list().map { it.type },
        )
    }

    @Test
    fun explicitDownloadStoresListAndSourceWithoutRequiringNativeCode() {
        val imported = downloader().download(preset, store)
        assertEquals("${preset.name}.txt", imported.name)
        assertEquals(preset.url, imported.sourceUrl)
        assertTrue(imported.enabled)
        assertEquals(
            listOf("[Adblock Plus 2.0]\n||ads.example^\n"),
            store.enabledTexts(),
        )
        assertEquals(
            imported,
            FilterListStore(RuntimeEnvironment.getApplication()).list().single(),
        )
        assertTrue(store.containsSource(preset.url))

        store.setEnabled(imported.id, false)
        assertTrue(store.containsSource(preset.url))
        assertTrue(store.enabledTexts().isEmpty())
        store.remove(imported.id)
        assertFalse(store.containsSource(preset.url))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun repeatedDownloadIsRejectedWithoutAnotherNetworkRequest() {
        val calls = AtomicInteger()
        val client = downloader(requests = calls)
        client.download(preset, store)
        assertTrue(runCatching { client.download(preset, store) }.isFailure)
        assertEquals(1, calls.get())
        assertEquals(1, store.list().size)
    }

    @Test
    fun presetAndUrlCanShareASourceAndDuplicateTypesAreRejectedBeforeFetching() {
        for (presetFirst in listOf(true, false)) {
            val calls = AtomicInteger()
            val client = downloader(requests = calls)
            if (presetFirst) {
                client.download(preset, store)
                client.downloadUrl(preset.url, "Custom URL", store)
            } else {
                client.downloadUrl(preset.url, "Custom URL", store)
                client.download(preset, store)
            }
            assertEquals(2, calls.get())
            assertEquals(
                setOf(FilterListType.PRESET, FilterListType.URL),
                store.list().map { it.type }.toSet(),
            )
            assertThrows(IllegalArgumentException::class.java) {
                client.download(preset, store)
            }
            assertThrows(IllegalArgumentException::class.java) {
                client.downloadUrl(preset.url, "Duplicate", store)
            }
            assertEquals(2, calls.get())
            store.list().forEach { store.remove(it.id) }
        }
    }

    @Test
    fun duplicateUrlEditsAreRejectedBeforeFetchingWithoutChangingSavedData() {
        val first =
            store.importFrom(
                "First",
                "||first.example^".byteInputStream(),
                "https://example.org/one.txt",
            )
        val second =
            store.importFrom(
                "Second",
                "||second.example^".byteInputStream(),
                preset.url,
            )
        val calls = AtomicInteger()
        assertThrows(IllegalArgumentException::class.java) {
            downloader(requests = calls)
                .editUrl(first, "Duplicate", second.sourceUrl!!, store)
        }
        assertEquals(0, calls.get())
        assertEquals(listOf(first, second), store.list())
    }

    @Test
    fun aUrlEditMayMatchAnExistingPreset() {
        val installed = downloader().download(preset, store)
        val url =
            store.importFrom(
                "Custom",
                "||old.example^".byteInputStream(),
                "https://example.org/old.txt",
            )
        val calls = AtomicInteger()
        val updated =
            downloader(requests = calls)
                .editUrl(url, "Matching preset", preset.url, store)
        assertEquals(1, calls.get())
        assertEquals(preset.url, updated.sourceUrl)
        assertEquals(FilterListType.URL, updated.type)
        assertEquals(installed, store.list().first())
    }

    @Test
    fun errorsAndHtmlDoNotImportOrActivateLists() {
        assertTrue(
            runCatching { downloader(status = 503).download(preset, store) }
                .exceptionOrNull() is IOException
        )
        assertTrue(
            runCatching {
                downloader(content = "<html>error</html>", mime = "text/html")
                    .download(preset, store)
            }
                .exceptionOrNull() is IOException
        )
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
    }

    @Test
    fun downloadedBodyUsesSameSizeValidationAsLocalImports() {
        val oversized = "||ads.example^\n".repeat(600_000)
        assertTrue(
            runCatching { downloader(content = oversized).download(preset, store) }
                .isFailure
        )
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun importedFilesHaveNoSourceAndRemainCompatible() {
        val imported =
            store.importFrom("custom.txt", "||ads.example^".byteInputStream())
        assertEquals(null, imported.sourceUrl)
        assertEquals(
            imported,
            FilterListStore(RuntimeEnvironment.getApplication()).list().single(),
        )
    }

    @Test
    fun refreshDownloadsInPlaceAndPreservesRenamedDisabledLists() {
        val original =
            store.importFrom(
                "EasyList.txt",
                "||old.example^".byteInputStream(),
                sourceUrl = preset.url,
            )
        store.rename(original.id, "My filters")
        store.setEnabled(original.id, false)
        val calls = AtomicInteger()
        currentTime = 2_000L
        val refreshed =
            downloader(content = "||new.example^", requests = calls)
                .refresh(original, store)

        assertEquals(1, calls.get())
        assertEquals(original.id, refreshed.id)
        assertEquals("My filters", refreshed.name)
        assertFalse(refreshed.enabled)
        assertEquals(preset.url, refreshed.sourceUrl)
        assertEquals(2_000L, refreshed.lastRefreshedAt)
        assertEquals(listOf(refreshed), store.list())
        assertTrue(store.enabledTexts().isEmpty())
        store.setEnabled(original.id, true)
        assertEquals(listOf("||new.example^"), store.enabledTexts())
    }

    @Test
    fun refreshSupportsOlderCustomSourceUrls() {
        val legacyUrl = "https://filters.adtidy.org/extension/ublock/filters/2.txt"
        val legacy = SuggestedFilterList("Previously downloaded", "", legacyUrl)
        val old =
            store.importFrom(
                "AdGuard Base.txt",
                "||old.example^".byteInputStream(),
                sourceUrl = legacyUrl,
            )
        currentTime = 2_000L
        downloader(content = "||updated.example^", suggestion = legacy)
            .refresh(old, store)
        assertEquals(listOf("||updated.example^"), store.enabledTexts())
        assertEquals(old.copy(lastRefreshedAt = 2_000L), store.list().single())
    }

    @Test
    fun failedRefreshKeepsOriginalRulesAndMetadata() {
        val original =
            store.importFrom(
                "Original.txt",
                "||old.example^".byteInputStream(),
                sourceUrl = preset.url,
            )
        val invalid =
            listOf(
                downloader(status = 503),
                downloader(content = "<html>oops</html>", mime = "text/html"),
                downloader(content = ""),
                downloader(content = "||ads.example^\n".repeat(600_000)),
            )
        invalid.forEach { client ->
            assertTrue(runCatching { client.refresh(original, store) }.isFailure)
            assertEquals(original, store.list().single())
            assertEquals(listOf("||old.example^"), store.enabledTexts())
        }
    }

    @Test
    fun refreshNeverFetchesLocalOrMissingLists() {
        val local = store.importFrom("Local.txt", "||old.example^".byteInputStream())
        val calls = AtomicInteger()
        val client = downloader(requests = calls)
        assertThrows(IllegalArgumentException::class.java) {
            client.refresh(local, store)
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.refresh(local.copy(id = "missing"), store)
        }
        assertEquals(0, calls.get())
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }

    @Test
    fun refreshAcceptsHttpSourceUrlsAndPreservesTheSavedList() {
        val old =
            store.importFrom(
                "HTTP list",
                "||old.example^".byteInputStream(),
                sourceUrl = "http://example.org/list.txt",
            )
        val calls = AtomicInteger()
        val client =
            downloader(
                content = "||new.example^",
                requests = calls,
                suggestion = preset.copy(url = old.sourceUrl!!),
            )
        currentTime = 2_000L
        val updated = client.refresh(old, store)
        assertEquals(1, calls.get())
        assertEquals(old.copy(lastRefreshedAt = 2_000L), updated)
        assertEquals(listOf(updated), store.list())
        assertEquals(listOf("||new.example^"), store.enabledTexts())
    }

    @Test
    fun manuallyEnteredHttpAndHttpsUrlsCreateEditableUrlLists() {
        listOf("http", "https").forEach { scheme ->
            val source = preset.copy(url = "$scheme://example.org/list.txt")
            val imported =
                downloader(suggestion = source)
                    .downloadUrl(
                        "  ${source.url}  ",
                        "My manual list",
                        store,
                    )
            assertEquals(FilterListType.URL, imported.type)
            assertEquals("My manual list", imported.name)
            assertEquals(source.url, imported.sourceUrl)
            assertTrue(store.list().contains(imported))
        }
    }

    @Test
    fun duplicateHttpUrlsAreNormalizedAndRejectedBeforeFetching() {
        val calls = AtomicInteger()
        val source = preset.copy(url = "http://example.org/list.txt")
        val client = downloader(suggestion = source, requests = calls)
        client.downloadUrl(" HTTP://EXAMPLE.ORG:80/list.txt ", "First", store)
        assertThrows(IllegalArgumentException::class.java) {
            client.downloadUrl(source.url, "Duplicate", store)
        }
        assertEquals(1, calls.get())
        assertEquals(1, store.list().size)
    }

    @Test
    fun manualDownloadRejectsUnsafeUrlsBeforeFetching() {
        val calls = AtomicInteger()
        val client = downloader(requests = calls)
        listOf(
                "ftp://example.org/list.txt",
                "file:///list.txt",
                "javascript:alert(1)",
                "not a URL",
                "http://user:pass@example.org/list",
                "https://user:pass@example.org/list",
            )
            .forEach { unsafe ->
                assertThrows(IllegalArgumentException::class.java) {
                    client.downloadUrl(unsafe, "Invalid", store)
                }
            }
        assertEquals(0, calls.get())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun editingUrlFetchesNewSourceWhileNameOnlyEditStaysOffline() {
        val original =
            store.importFrom(
                "Initial",
                "||old.example^".byteInputStream(),
                "https://example.org/initial.txt",
            )
        val calls = AtomicInteger()
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        calls.incrementAndGet()
                        assertEquals(
                            "http://example.org/new.txt",
                            chain.request().url.toString(),
                        )
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("ok")
                            .body(
                                "||new.example^"
                                    .toResponseBody("text/plain".toMediaType())
                            )
                            .build()
                    }
                    .build()
            )
        client.editUrl(original, "Rename only", original.sourceUrl!!, store)
        assertEquals(0, calls.get())
        val changed =
            client.editUrl(
                original,
                "New source",
                "http://example.org/new.txt",
                store,
            )
        assertEquals(1, calls.get())
        assertEquals(original.id, changed.id)
        assertEquals("New source", changed.name)
        assertEquals("http://example.org/new.txt", changed.sourceUrl)
        assertEquals(listOf("||new.example^"), store.enabledTexts())
    }

    @Test
    fun failedUrlChangeDoesNotChangeNameSourceOrRules() {
        val original =
            store.importFrom(
                "Initial",
                "||old.example^".byteInputStream(),
                "https://example.org/initial.txt",
            )
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("unavailable")
                            .body("error".toResponseBody())
                            .build()
                    }
                    .build()
            )
        assertThrows(IOException::class.java) {
            client.editUrl(original, "Other", "https://example.org/new.txt", store)
        }
        assertEquals(original, store.list().single())
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }

    @Test
    fun updateAllSkipsFilesAndContinuesAfterIndividualFailures() {
        val good =
            store.importFrom(
                "Good",
                "old good".byteInputStream(),
                "https://example.org/good.txt",
            )
        val bad =
            store.importFrom(
                "Bad",
                "old bad".byteInputStream(),
                "https://example.org/bad.txt",
            )
        val local = store.importFrom("Local", "local".byteInputStream())
        store.setEnabled(bad.id, false)
        val calls = AtomicInteger()
        val client =
            SuggestedFilterListDownloader(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        calls.incrementAndGet()
                        val isGood = chain.request().url.toString() == good.sourceUrl
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(
                                if (isGood) {
                                    200
                                } else {
                                    500
                                }
                            )
                            .message("test")
                            .body(
                                (if (isGood) {
                                        "new good"
                                    } else {
                                        "error"
                                    })
                                    .toResponseBody()
                            )
                            .build()
                    }
                    .build()
            )
        currentTime = 2_000L
        val result = client.refreshAll(store)
        assertEquals(2, result.total)
        assertEquals(1, result.updated)
        assertEquals(listOf("Bad"), result.failures.map { it.name })
        assertEquals(2, calls.get())
        assertEquals(listOf("new good", "local"), store.enabledTexts())
        assertEquals(
            listOf(
                good.copy(lastRefreshedAt = 2_000L),
                bad.copy(enabled = false),
                local,
            ),
            store.list(),
        )
    }
}
