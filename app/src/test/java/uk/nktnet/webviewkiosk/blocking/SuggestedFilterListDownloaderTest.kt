package uk.nktnet.webviewkiosk.blocking

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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class SuggestedFilterListDownloaderTest {
    private lateinit var store: FilterListStore
    private val preset = SuggestedFilterLists.all.first()
    private var currentTime = 1_000L

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        store = FilterListStore(context, validator = FilterListValidator {}, now = { currentTime })
    }

    private fun downloader(
        content: String = "[Adblock Plus 2.0]\n||ads.example^\n",
        mime: String = "text/plain",
        status: Int = 200,
        requests: AtomicInteger = AtomicInteger(),
        suggestion: SuggestedFilterList = preset,
    ): SuggestedFilterListDownloader = SuggestedFilterListDownloader(
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
            .build(),
    )

    @Test
    fun suggestionsAreLinksNotBundledLists() {
        assertEquals(
            listOf("EasyList", "EasyPrivacy", "HaGeZi Multi NORMAL"),
            SuggestedFilterLists.all.map { it.name },
        )
        assertEquals(
            "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/multi.txt",
            SuggestedFilterLists.all.last().url,
        )
        assertFalse(SuggestedFilterLists.all.any { it.name == "AdGuard Base" })
        assertTrue(SuggestedFilterLists.all.all { it.url.startsWith("https://") })
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun hageziCanBeDownloadedOnDemand() {
        val hagezi = SuggestedFilterLists.all.last()
        val imported = downloader(suggestion = hagezi).download(hagezi, store)
        assertEquals("HaGeZi Multi NORMAL.txt", imported.name)
        assertEquals(hagezi.url, imported.sourceUrl)
        assertTrue(imported.enabled)
        assertTrue(store.containsSource(hagezi.url))
    }

    @Test
    fun previouslyImportedAdGuardListIsNotRemovedByChangingSuggestions() {
        val old =
            store.importFrom(
                "AdGuard Base.txt",
                "||ads.example^\n".byteInputStream(),
                sourceUrl = "https://filters.adtidy.org/extension/ublock/filters/2.txt",
            )
        assertFalse(SuggestedFilterLists.all.any { it.url == old.sourceUrl })
        assertEquals(old, FilterListStore(RuntimeEnvironment.getApplication()).list().single())
    }

    @Test
    fun explicitDownloadStoresListAndSourceWithoutRequiringNativeCode() {
        val imported = downloader().download(preset, store)
        assertEquals("EasyList.txt", imported.name)
        assertEquals(preset.url, imported.sourceUrl)
        assertTrue(imported.enabled)
        assertEquals(listOf("[Adblock Plus 2.0]\n||ads.example^\n"), store.enabledTexts())
        assertEquals(imported, FilterListStore(RuntimeEnvironment.getApplication()).list().single())
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
            assertThrows(IllegalArgumentException::class.java) { client.download(preset, store) }
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
        val second = store.importFrom("Second", "||second.example^".byteInputStream(), preset.url)
        val calls = AtomicInteger()
        assertThrows(IllegalArgumentException::class.java) {
            downloader(requests = calls).editUrl(first, "Duplicate", second.sourceUrl!!, store)
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
            downloader(requests = calls).editUrl(url, "Matching preset", preset.url, store)
        assertEquals(1, calls.get())
        assertEquals(preset.url, updated.sourceUrl)
        assertEquals(FilterListType.URL, updated.type)
        assertEquals(installed, store.list().first())
    }

    @Test
    fun errorsAndHtmlDoNotImportOrActivateLists() {
        assertTrue(
            runCatching { downloader(status = 503).download(preset, store) }.exceptionOrNull()
                is IOException,
        )
        assertTrue(
            runCatching {
                downloader(content = "<html>error</html>", mime = "text/html")
                    .download(preset, store)
            }
                .exceptionOrNull() is IOException,
        )
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
    }

    @Test
    fun downloadedBodyUsesSameSizeValidationAsLocalImports() {
        val oversized = "||ads.example^\n".repeat(600_000)
        assertTrue(
            runCatching { downloader(content = oversized).download(preset, store) }.isFailure,
        )
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun importedFilesHaveNoSourceAndRemainCompatible() {
        val imported = store.importFrom("custom.txt", "||ads.example^".byteInputStream())
        assertEquals(null, imported.sourceUrl)
        assertEquals(imported, FilterListStore(RuntimeEnvironment.getApplication()).list().single())
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
            downloader(content = "||new.example^", requests = calls).refresh(original, store)

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
        downloader(content = "||updated.example^", suggestion = legacy).refresh(old, store)
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
        assertThrows(IllegalArgumentException::class.java) { client.refresh(local, store) }
        assertThrows(IllegalArgumentException::class.java) {
            client.refresh(local.copy(id = "missing"), store)
        }
        assertEquals(0, calls.get())
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }

    @Test
    fun refreshRejectsInsecureSourceUrlsWithoutNetworkAccess() {
        val old =
            store.importFrom(
                "Unsafe.txt",
                "||old.example^".byteInputStream(),
                sourceUrl = "http://example.org/list.txt",
            )
        val calls = AtomicInteger()
        val client = downloader(requests = calls)
        assertThrows(IllegalArgumentException::class.java) { client.refresh(old, store) }
        assertEquals(0, calls.get())
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }

    @Test
    fun manuallyEnteredHttpsUrlCreatesEditableUrlList() {
        val imported = downloader().downloadUrl(preset.url, "My manual list", store)
        assertEquals(FilterListType.URL, imported.type)
        assertEquals("My manual list", imported.name)
        assertEquals(preset.url, imported.sourceUrl)
        assertEquals(listOf(imported), store.list())
    }

    @Test
    fun manualDownloadRejectsUnsafeUrlsBeforeFetching() {
        val calls = AtomicInteger()
        val client = downloader(requests = calls)
        listOf("http://example.org/list.txt", "not a URL", "https://user:pass@example.org/list")
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
                        assertEquals("https://example.org/new.txt", chain.request().url.toString())
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("ok")
                            .body("||new.example^".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    .build(),
            )
        client.editUrl(original, "Rename only", original.sourceUrl!!, store)
        assertEquals(0, calls.get())
        val changed = client.editUrl(original, "New source", "https://example.org/new.txt", store)
        assertEquals(1, calls.get())
        assertEquals(original.id, changed.id)
        assertEquals("New source", changed.name)
        assertEquals("https://example.org/new.txt", changed.sourceUrl)
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
                    .build(),
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
            store.importFrom("Good", "old good".byteInputStream(), "https://example.org/good.txt")
        val bad =
            store.importFrom("Bad", "old bad".byteInputStream(), "https://example.org/bad.txt")
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
                            .code(if (isGood) 200 else 500)
                            .message("test")
                            .body((if (isGood) "new good" else "error").toResponseBody())
                            .build()
                    }
                    .build(),
            )
        currentTime = 2_000L
        val result = client.refreshAll(store)
        assertEquals(2, result.total)
        assertEquals(1, result.updated)
        assertEquals(listOf("Bad"), result.failures.map { it.name })
        assertEquals(2, calls.get())
        assertEquals(listOf("new good", "local"), store.enabledTexts())
        assertEquals(
            listOf(good.copy(lastRefreshedAt = 2_000L), bad.copy(enabled = false), local),
            store.list(),
        )
    }
}
