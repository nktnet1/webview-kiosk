package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
class FilterListStoreTest {
    private lateinit var store: FilterListStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists")
            .deleteRecursively()
        store = FilterListStore(context, validator = FilterListValidator {})
    }

    @Test
    fun initialStateContainsNoLists() {
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
    }

    @Test
    fun downloadTimesPersistAndArePreservedByMetadataChanges() {
        val context = RuntimeEnvironment.getApplication()
        var time = 1_000L
        val timed = FilterListStore(context, FilterListValidator {}, now = { time })
        val remote = timed.importFrom("Remote", "||ads.example^".byteInputStream(), "https://example.org/list.txt")
        assertEquals(1_000L, remote.lastRefreshedAt)
        assertEquals(remote, FilterListStore(context).list().single())
        time = 2_000L
        timed.rename(remote.id, "Renamed")
        timed.setEnabled(remote.id, false)
        timed.setAllEnabled(true)
        assertThrows(IllegalArgumentException::class.java) { timed.cloneList(remote.id) }
        assertEquals(1_000L, timed.info(remote.id).item.lastRefreshedAt)
        val file = timed.importFrom("Local", "||local.example^".byteInputStream())
        assertNull(file.lastRefreshedAt)
        assertNull(timed.updateFile(file.id, "Edited", "||edited.example^".byteInputStream()).lastRefreshedAt)
        assertNull(timed.cloneList(file.id).lastRefreshedAt)
    }

    @Test
    fun successfulRefreshesAndSourceChangesAdvanceTheirSavedTime() {
        val context = RuntimeEnvironment.getApplication()
        var time = 1_000L
        val timed = FilterListStore(context, FilterListValidator {}, now = { time })
        val preset = timed.importFrom("Preset", "||ads.example^".byteInputStream(),
            SuggestedFilterLists.all.first().url, FilterListType.PRESET)
        assertEquals(1_000L, preset.lastRefreshedAt)
        time = 2_000L
        // Identical downloaded contents still count as a successful refresh.
        val refreshed = timed.replaceFromSource(preset.id, preset.sourceUrl!!, "||ads.example^".byteInputStream())
        assertEquals(preset.copy(lastRefreshedAt = 2_000L), refreshed)
        assertEquals(refreshed, FilterListStore(context).list().single())
        val remote = timed.importFrom("Remote", "||ads.example^".byteInputStream(), "https://example.org/original.txt")
        time = 3_000L
        val changed = timed.updateUrl(remote.id, remote.sourceUrl!!, "https://example.org/new.txt", "Changed",
            "||new.example^".byteInputStream())
        assertEquals(3_000L, changed.lastRefreshedAt)
        assertEquals(3_000L, FilterListStore(context).info(remote.id).item.lastRefreshedAt)
        assertEquals(2_000L, timed.info(preset.id).item.lastRefreshedAt)
    }

    @Test
    fun failedRefreshOrSourceChangeRetainsThePreviousTimeAndRules() {
        val context = RuntimeEnvironment.getApplication()
        var time = 1_000L
        val timed = FilterListStore(context, FilterListValidator {}, now = { time })
        val item = timed.importFrom("Remote", "||old.example^".byteInputStream(), "https://example.org/list.txt")
        time = 2_000L
        assertThrows(IllegalArgumentException::class.java) {
            timed.replaceFromSource(item.id, item.sourceUrl!!, byteArrayOf().inputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            timed.updateUrl(item.id, item.sourceUrl!!, "https://example.org/new.txt", "Changed", byteArrayOf(0).inputStream())
        }
        assertEquals(item, FilterListStore(context).list().single())
        assertEquals(listOf("||old.example^"), timed.enabledTexts())
    }

    @Test
    fun legacyListsKeepAnUnknownTimeUntilRefreshed() {
        val context = RuntimeEnvironment.getApplication()
        var time = 1_000L
        val timed = FilterListStore(context, FilterListValidator {}, now = { time })
        val item = timed.importFrom("Preset", "||old.example^".byteInputStream(),
            SuggestedFilterLists.all.first().url, FilterListType.PRESET)
        val preferences = context.getSharedPreferences("content_blocking_lists", 0)
        val index = JSONArray(preferences.getString("imports", "[]"))
        index.getJSONObject(0).remove("lastRefreshedAt")
        preferences.edit().putString("imports", index.toString()).commit()
        assertNull(FilterListStore(context).list().single().lastRefreshedAt)
        time = 2_000L
        timed.setEnabled(item.id, false)
        assertNull(timed.info(item.id).item.lastRefreshedAt)
        val refreshed = timed.replaceFromSource(item.id, item.sourceUrl!!, "||new.example^".byteInputStream())
        assertEquals(2_000L, refreshed.lastRefreshedAt)
    }

    @Test
    fun infoCountsOnlyTheRequestedSavedListWithoutChangingItsState() {
        val text = "\uFEFF! header\r\n0.0.0.0 ads.example alias.example\n||tracker.example^\n" +
            "@@||allowed.example^\nsite.example##.広告\n##.ad\n||popup.example^\$popup\n" +
            "site.example#?#div:has-text(Sponsored)\n||"
        val item = store.importFrom("Mixed", text.byteInputStream())
        store.setEnabled(item.id, false)
        store.importFrom("Other", "||unrelated.example^".byteInputStream())
        val saved = store.list()
        val enabled = store.enabledTexts()
        val info = store.info(item.id)
        assertEquals(item.copy(enabled = false), info.item)
        assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), info.sizeBytes)
        assertEquals(info.sizeBytes, store.fileSizeBytes(item.id))
        assertEquals(4, info.networkRules)
        assertEquals(2, info.cosmeticRules)
        assertEquals(2, info.unsupportedRules)
        assertEquals(1, info.invalidRules)
        assertEquals(3, info.skippedRules)
        assertEquals(9, info.totalRules)
        assertEquals(saved, store.list())
        assertEquals(enabled, store.enabledTexts())
    }

    @Test
    fun infoReflectsFileReplacementAndDisabledClones() {
        val item = store.importFrom("Original", "||ads.example^".byteInputStream())
        assertEquals(1, store.info(item.id).networkRules)
        val text = "##.ad\n||popup.example^\$popup"
        store.updateFile(item.id, "Changed", text.byteInputStream())
        val updated = store.info(item.id)
        assertEquals(0, updated.networkRules)
        assertEquals(1, updated.cosmeticRules)
        assertEquals(1, updated.skippedRules)
        assertEquals(text.toByteArray().size.toLong(), updated.sizeBytes)
        val clone = store.cloneList(item.id)
        val copy = store.info(clone.id)
        assertEquals(clone, copy.item)
        assertFalse(copy.item.enabled)
        assertEquals(updated.totalRules, copy.totalRules)
        assertEquals(updated.skippedRules, copy.skippedRules)
    }

    @Test
    fun infoRejectsUnknownMissingAndOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java) { store.info("../outside") }
        val item = store.importFrom("Original", "||ads.example^".byteInputStream())
        val directory = java.io.File(RuntimeEnvironment.getApplication().filesDir, "content-blocking-lists")
        val file = java.io.File(directory, "${item.id}.txt")
        assertTrue(file.delete())
        assertThrows(java.io.IOException::class.java) { store.info(item.id) }
        assertThrows(java.io.IOException::class.java) { store.fileSizeBytes(item.id) }
        java.io.RandomAccessFile(file, "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        assertThrows(java.io.IOException::class.java) { store.info(item.id) }
        assertThrows(java.io.IOException::class.java) { store.fileSizeBytes(item.id) }
    }

    @Test
    fun bulkTogglesPreserveListMetadataAndAllSavedRuleFiles() {
        val text = "||ads.example^"
        val first = store.importFrom("File", text.byteInputStream())
        store.importFrom("Remote", "##.ad".byteInputStream(), "https://lists.example/ads.txt")
        store.cloneList(first.id)
        val saved = store.list()
        store.setAllEnabled(true)
        assertEquals(saved.map { it.copy(enabled = true) }, store.list())
        assertEquals(listOf(text, "##.ad", text), store.enabledTexts())
        store.setAllEnabled(false)
        assertEquals(saved.map { it.copy(enabled = false) }, store.list())
        assertTrue(store.enabledTexts().isEmpty())
        store.setAllEnabled(true)
        assertEquals(listOf(text, "##.ad", text), store.enabledTexts())
    }

    @Test
    fun bulkTogglesOnAnEmptyStoreDoNotCreateSavedEntries() {
        store.setAllEnabled(true)
        store.setAllEnabled(false)
        assertTrue(store.list().isEmpty())
        assertFalse(RuntimeEnvironment.getApplication().getSharedPreferences("content_blocking_lists", 0).contains("imports"))
    }

    @Test
    fun importedListsPersistAndCanBeDisabledAndRemoved() {
        val entry = store.importFrom("ads.txt", "||ads.example^\n".byteInputStream())
        assertEquals("ads.txt", entry.name)
        assertEquals(listOf("||ads.example^\n"), store.enabledTexts())
        assertEquals(entry, FilterListStore(RuntimeEnvironment.getApplication()).list().single())

        store.setEnabled(entry.id, false)
        assertTrue(store.enabledTexts().isEmpty())
        assertFalse(store.list().single().enabled)

        store.setEnabled(entry.id, true)
        assertEquals(1, store.enabledTexts().size)
        store.remove(entry.id)
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
    }

    @Test
    fun invalidUtf8AndOversizeListAreRejectedWithoutPersisting() {
        val malformed = runCatching {
            store.importFrom("bad", ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
        assertTrue(malformed.isFailure)
        val oversized = runCatching {
            store.importFrom("oversized", ByteArrayInputStream(ByteArray(8 * 1024 * 1024 + 1)))
        }
        assertTrue(oversized.isFailure)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun listNamesCannotEscapePrivateDirectory() {
        val item = store.importFrom("../../untrusted.txt", "||ads.example^".byteInputStream())
        // User-entered names are display text; storage paths only use generated IDs.
        assertEquals("../../untrusted.txt", item.name)
        val directory = java.io.File(RuntimeEnvironment.getApplication().filesDir, "content-blocking-lists")
        assertEquals(listOf("${item.id}.txt"), directory.listFiles().orEmpty().map { it.name })
        assertEquals(1, store.enabledTexts().size)
    }

    @Test
    fun renameIsPersistentAndDoesNotChangeRulesStateOrSource() {
        val item = store.importFrom(
            "Original.txt", "||old.example^".byteInputStream(),
            sourceUrl = "https://example.org/list.txt",
        )
        store.setEnabled(item.id, false)
        store.rename(item.id, "  Custom display name  ")

        val renamed = FilterListStore(RuntimeEnvironment.getApplication()).list().single()
        assertEquals(item.id, renamed.id)
        assertEquals("Custom display name", renamed.name)
        assertEquals(item.sourceUrl, renamed.sourceUrl)
        assertFalse(renamed.enabled)
        store.setEnabled(item.id, true)
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }

    @Test
    fun invalidRenamesLeaveExistingNameUntouched() {
        val item = store.importFrom("Original.txt", "||old.example^".byteInputStream())
        listOf(" ", "x".repeat(101), "Bad\nName", "Bad\u0000Name").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { store.rename(item.id, name) }
        }
        assertThrows(IllegalArgumentException::class.java) { store.rename("unknown", "Valid") }
        assertEquals("Original.txt", store.list().single().name)
    }

    @Test
    fun refreshReplacesRulesButKeepsIdentityNameAndEnablement() {
        val item = store.importFrom(
            "Original.txt", "||old.example^".byteInputStream(),
            sourceUrl = "https://example.org/list.txt",
        )
        store.rename(item.id, "Renamed")
        val refreshed = store.replaceFromSource(
            item.id, item.sourceUrl!!, "||new.example^".byteInputStream(),
        )
        assertEquals("Renamed", store.list().single().name)
        assertEquals("Renamed", refreshed.name)
        assertEquals(item.id, refreshed.id)
        assertEquals(item.sourceUrl, refreshed.sourceUrl)
        assertEquals(listOf("||new.example^"), store.enabledTexts())
        assertEquals(1, store.list().size)

        store.setEnabled(item.id, false)
        store.replaceFromSource(item.id, item.sourceUrl, "||second.example^".byteInputStream())
        assertFalse(store.list().single().enabled)
        store.setEnabled(item.id, true)
        assertEquals(listOf("||second.example^"), store.enabledTexts())
    }

    @Test
    fun badRefreshCannotDeleteOrDamageOriginalRules() {
        val item = store.importFrom(
            "Original.txt", "||old.example^".byteInputStream(),
            sourceUrl = "https://example.org/list.txt",
        )
        val invalidBodies = listOf(
            byteArrayOf(), byteArrayOf(0), byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(8 * 1024 * 1024 + 1),
        )
        invalidBodies.forEach { body ->
            assertTrue(runCatching {
                store.replaceFromSource(item.id, item.sourceUrl!!, ByteArrayInputStream(body))
            }.isFailure)
            assertEquals(listOf("||old.example^"), store.enabledTexts())
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceFromSource(item.id, "https://wrong.example/list", "new".byteInputStream())
        }
        assertEquals(item, store.list().single())
        val directory = java.io.File(RuntimeEnvironment.getApplication().filesDir, "content-blocking-lists")
        assertEquals(1, directory.listFiles()?.size)
    }

    @Test
    fun localImportsCannotBeRefreshedFromUnrelatedUrls() {
        val item = store.importFrom("local.txt", "||old.example^".byteInputStream())
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceFromSource(item.id, "https://example.org/list.txt", "new".byteInputStream())
        }
        assertEquals(listOf("||old.example^"), store.enabledTexts())
    }
    @Test
    fun legacyIndexesInferPresetAndUrlTypesWithoutModifyingTheirRules() {
        val preset = store.importFrom(
            "EasyList.txt", "||preset.example^".byteInputStream(),
            sourceUrl = SuggestedFilterLists.all.first().url,
        )
        val custom = store.importFrom(
            "My source", "||custom.example^".byteInputStream(),
            sourceUrl = "https://example.org/custom.txt",
        )
        val local = store.importFrom("my-file.txt", "||local.example^".byteInputStream())
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("content_blocking_lists", 0)
        val oldIndex = JSONArray(preferences.getString("imports", "[]"))
        for (index in 0 until oldIndex.length()) oldIndex.getJSONObject(index).remove("type")
        preferences.edit().putString("imports", oldIndex.toString()).commit()

        val restored = FilterListStore(RuntimeEnvironment.getApplication()).list()
        assertEquals(listOf(preset.id, custom.id, local.id), restored.map { it.id })
        assertEquals(listOf(FilterListType.PRESET, FilterListType.URL, FilterListType.FILE), restored.map { it.type })
        assertEquals(3, store.enabledTexts().size)
        assertThrows(IllegalArgumentException::class.java) { store.rename(preset.id, "Nope") }
    }

    @Test
    fun presetCannotBeClonedEvenWithoutAMatchingUrlList() {
        val preset = store.importFrom(
            "EasyList.txt", "||ads.example^".byteInputStream(),
            sourceUrl = SuggestedFilterLists.all.first().url,
            type = FilterListType.PRESET,
        )
        val failure = assertThrows(IllegalArgumentException::class.java) { store.cloneList(preset.id) }
        assertEquals("Preset lists cannot be cloned", failure.message)
        assertEquals(listOf(preset), store.list())
        assertEquals(listOf("||ads.example^"), store.enabledTexts())
    }

    @Test
    fun localFileClonesAreIndependentAndInlineEditsKeepOtherRules() {
        val original = store.importFrom("local.txt", "||original.example^".byteInputStream())
        assertEquals("||original.example^", store.editableFileText(original.id))
        val copy = store.cloneList(original.id, "Local copy")
        assertEquals(FilterListType.FILE, copy.type)
        assertNull(copy.sourceUrl)
        assertFalse(copy.enabled)
        val updated = store.updateFile(copy.id, "Edited", "||edited.example^".byteInputStream())
        assertEquals("Edited", updated.name)
        assertEquals("||edited.example^", store.editableFileText(copy.id))
        assertEquals("||original.example^", store.editableFileText(original.id))
        store.updateFile(original.id, "Renamed")
        assertEquals(listOf("||original.example^"), store.enabledTexts())
        assertEquals("Renamed", store.list().first().name)
    }

    @Test
    fun failedFileOrUrlEditsLeaveContentAndMetadataUntouched() {
        val file = store.importFrom("local.txt", "||original.example^".byteInputStream())
        val url = store.importFrom(
            "URL", "||remote.example^".byteInputStream(),
            sourceUrl = "https://example.org/remote.txt",
        )
        assertThrows(IllegalArgumentException::class.java) {
            store.updateFile(file.id, "Renamed", byteArrayOf(0).inputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.updateUrl(
                url.id, url.sourceUrl!!, "https://example.org/new.txt", "Changed",
                byteArrayOf(0xc3.toByte(), 0x28).inputStream(),
            )
        }
        assertEquals(listOf(file, url), store.list())
        assertEquals(listOf("||original.example^", "||remote.example^"), store.enabledTexts())
        val directory = java.io.File(RuntimeEnvironment.getApplication().filesDir, "content-blocking-lists")
        assertEquals(2, directory.listFiles()?.size)
    }

    @Test
    fun urlEditsRejectOtherUrlSourcesAndCanKeepTheirOwnSource() {
        val first = store.importFrom("First", "first".byteInputStream(), "https://example.org/one.txt")
        val second = store.importFrom("Second", "second".byteInputStream(), "https://example.org/two.txt")
        assertThrows(IllegalArgumentException::class.java) {
            store.updateUrl(first.id, first.sourceUrl!!, second.sourceUrl!!, "Invalid", "new".byteInputStream())
        }
        assertEquals(listOf(first, second), store.list())
        assertThrows(IllegalArgumentException::class.java) { store.cloneList(first.id) }
        store.updateUrl(first.id, first.sourceUrl!!, first.sourceUrl, "Same source", "new".byteInputStream())
        assertEquals("Same source", store.list().first().name)
        assertEquals(2, store.list().size)
    }

    @Test
    fun presetAndUrlSourcesMayOverlapButEachTypeRemainsUniqueInEitherOrder() {
        val source = SuggestedFilterLists.all.first().url
        for (types in listOf(listOf(FilterListType.URL, FilterListType.PRESET), listOf(FilterListType.PRESET, FilterListType.URL))) {
            val added = types.map { type -> store.importFrom(type.name, "||ads.example^".byteInputStream(), source, type) }
            assertTrue(store.containsUrl(source))
            assertTrue(store.containsPreset(source))
            for (type in types) {
                assertThrows(IllegalArgumentException::class.java) {
                    store.importFrom("Duplicate", "||other.example^".byteInputStream(), source, type)
                }
            }
            val preset = added.first { it.type == FilterListType.PRESET }
            assertThrows(IllegalArgumentException::class.java) { store.cloneList(preset.id) }
            assertEquals(added, store.list())
            added.forEach { store.remove(it.id) }
        }
    }

    @Test
    fun urlEditsMayAdoptAPresetAddressWithoutChangingThePreset() {
        val source = SuggestedFilterLists.all.first().url
        val preset = store.importFrom("Preset", "||preset.example^".byteInputStream(), source, FilterListType.PRESET)
        val url = store.importFrom("URL", "||old.example^".byteInputStream(), "https://example.org/old.txt")
        val edited = store.updateUrl(url.id, url.sourceUrl!!, source, "Matching preset", "||new.example^".byteInputStream())
        assertEquals(source, edited.sourceUrl)
        assertEquals(FilterListType.URL, edited.type)
        assertEquals(preset, store.list().first())
        assertEquals(listOf("||preset.example^", "||new.example^"), store.enabledTexts())
    }

    @Test
    fun identicalLocalFilesCanBeAddedWithIndependentIds() {
        val first = store.importFrom("Same name", "||ads.example^".byteInputStream())
        val second = store.importFrom("Same name", "||ads.example^".byteInputStream())
        assertNotEquals(first.id, second.id)
        assertEquals(2, store.list().size)
        store.setEnabled(first.id, false)
        assertTrue(store.list().last().enabled)
    }

    @Test
    fun largeFileRequiresReplacementAndCanStillBeUpdated() {
        val originalText = "||ads.example^\n".repeat(12_000)
        val file = store.importFrom("large.txt", originalText.byteInputStream())
        assertNull(store.editableFileText(file.id))
        store.updateFile(file.id, "Still large", "||replacement.example^".byteInputStream())
        assertEquals("||replacement.example^", store.editableFileText(file.id))
        assertEquals(listOf("||replacement.example^"), store.enabledTexts())
        assertEquals("Still large", store.list().single().name)
    }

}
