package uk.nktnet.webviewkiosk.blocking

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class SuggestedFilterListDownloaderTest {
    private lateinit var store: FilterListStore
    private val preset = SuggestedFilterLists.all.first()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        store = FilterListStore(context)
    }

    private fun downloader(
        content: String = "[Adblock Plus 2.0]\n||ads.example^\n",
        mime: String = "text/plain",
        status: Int = 200,
        requests: AtomicInteger = AtomicInteger(),
    ): SuggestedFilterListDownloader = SuggestedFilterListDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            assertEquals(preset.url, chain.request().url.toString())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test response")
                .body(content.toResponseBody(mime.toMediaType()))
                .build()
        }.build(),
    )

    @Test
    fun suggestionsAreLinksNotBundledLists() {
        assertEquals(listOf("EasyList", "EasyPrivacy", "AdGuard Base"), SuggestedFilterLists.all.map { it.name })
        assertTrue(SuggestedFilterLists.all.all { it.url.startsWith("https://") })
        assertTrue(store.list().isEmpty())
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
    fun errorsAndHtmlDoNotImportOrActivateLists() {
        assertTrue(runCatching { downloader(status = 503).download(preset, store) }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            downloader(content = "<html>error</html>", mime = "text/html")
                .download(preset, store)
        }.exceptionOrNull() is IOException)
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
    }

    @Test
    fun downloadedBodyUsesSameSizeValidationAsLocalImports() {
        val oversized = "||ads.example^\n".repeat(600_000)
        assertTrue(runCatching { downloader(content = oversized).download(preset, store) }.isFailure)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun importedFilesHaveNoSourceAndRemainCompatible() {
        val imported = store.importFrom("custom.txt", "||ads.example^".byteInputStream())
        assertEquals(null, imported.sourceUrl)
        assertEquals(imported, FilterListStore(RuntimeEnvironment.getApplication()).list().single())
    }
}
