package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
