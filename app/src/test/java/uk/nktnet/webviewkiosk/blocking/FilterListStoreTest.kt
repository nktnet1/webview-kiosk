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
        java.io.RandomAccessFile(file, "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        assertThrows(java.io.IOException::class.java) { store.info(item.id) }
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
        assertEquals("untrusted.txt", item.name)
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
    fun presetClonesBecomeIndependentEditableUrlListsAndStartDisabled() {
        val preset = store.importFrom(
            "EasyList.txt", "||ads.example^".byteInputStream(),
            sourceUrl = SuggestedFilterLists.all.first().url,
            type = FilterListType.PRESET,
        )
        val copy = store.cloneList(preset.id)
        assertNotEquals(preset.id, copy.id)
        assertEquals("EasyList (copy)", copy.name)
        assertEquals(FilterListType.URL, copy.type)
        assertEquals(preset.sourceUrl, copy.sourceUrl)
        assertFalse(copy.enabled)
        assertThrows(IllegalArgumentException::class.java) { store.rename(preset.id, "Changed") }
        assertThrows(IllegalArgumentException::class.java) {
            store.importFrom("Duplicate", "rules".byteInputStream(), preset.sourceUrl)
        }
        store.rename(copy.id, "Editable copy")
        store.updateUrl(
            copy.id, preset.sourceUrl!!, "https://example.org/edited.txt",
            "Changed source", "||changed.example^".byteInputStream(),
        )
        assertEquals(preset, store.list().first())
        assertEquals("Changed source", store.list().last().name)
        assertEquals(listOf("||ads.example^"), store.enabledTexts())
        store.setEnabled(copy.id, true)
        assertEquals(listOf("||ads.example^", "||changed.example^"), store.enabledTexts())
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
    fun urlEditsRejectOtherSourcesButCanPreserveTheClonedSource() {
        val first = store.importFrom("First", "first".byteInputStream(), "https://example.org/one.txt")
        val second = store.importFrom("Second", "second".byteInputStream(), "https://example.org/two.txt")
        assertThrows(IllegalArgumentException::class.java) {
            store.updateUrl(first.id, first.sourceUrl!!, second.sourceUrl!!, "Invalid", "new".byteInputStream())
        }
        assertEquals(listOf(first, second), store.list())
        val cloned = store.cloneList(first.id)
        store.updateUrl(cloned.id, first.sourceUrl!!, first.sourceUrl, "Identical URL clone", "new".byteInputStream())
        assertEquals("Identical URL clone", store.list().last().name)
        assertFalse(store.list().last().enabled)
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
