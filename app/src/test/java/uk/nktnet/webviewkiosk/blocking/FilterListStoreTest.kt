package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class FilterListStoreTest {
    private lateinit var store: FilterListStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("content_blocking_lists", 0).edit().clear().commit()
        java.io.File(context.filesDir, "content-blocking-lists")
            .deleteRecursively()
        store = FilterListStore(context)
    }

    @Test
    fun initialStateContainsNoLists() {
        assertTrue(store.list().isEmpty())
        assertTrue(store.enabledTexts().isEmpty())
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
}
