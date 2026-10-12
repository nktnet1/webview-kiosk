package uk.nktnet.webviewkiosk.utils

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import uk.nktnet.webviewkiosk.testing.ShadowOsWithRename

@RunWith(RobolectricTestRunner::class)
class FileUtilsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsUtf8TextIncludingWhitespaceAndEmptyFiles() {
        val file = temporaryFolder.newFile("page.html")
        val content = "Hello 你好 😀\t\r\nnext line"
        file.writeText(content)

        assertEquals(content, readEditableTextFile(file))
        file.writeText("")
        assertEquals("", readEditableTextFile(file))
    }

    @Test
    fun rejectsBinaryContentsEvenWithATextFileName() {
        val file = temporaryFolder.newFile("page.txt")
        for (bytes in listOf(
            byteArrayOf(0x41, 0, 0x42),
            byteArrayOf(0xC3.toByte(), 0x28),
            byteArrayOf(0x41, 0x01, 0x42),
            byteArrayOf(0x41, 0x7F, 0x42),
        )) {
            file.writeBytes(bytes)
            assertNull(bytes.contentToString(), readEditableTextFile(file))
        }
    }

    @Test
    fun boundsEditableTextByUtf8BytesRatherThanCharacterCount() {
        val file = temporaryFolder.newFile("large.txt")
        val atLimit = "é".repeat(512 * 1024)
        file.writeText(atLimit)
        assertEquals(atLimit, readEditableTextFile(file))

        file.appendText("é")
        assertNull(readEditableTextFile(file))
    }

    @Test
    fun rejectsDirectoriesAndMissingFiles() {
        assertNull(readEditableTextFile(temporaryFolder.root))
        assertNull(readEditableTextFile(File(temporaryFolder.root, "missing.txt")))
    }

    @Test
    fun oversizedWritesPreserveTheOriginalFileAndCreateNoTemporaryFiles() {
        val file = temporaryFolder.newFile("original.txt").apply { writeText("original") }

        assertFalse(writeEditableTextFile(file, "é".repeat(512 * 1024 + 1)))

        assertEquals("original", file.readText())
        assertEquals(listOf(file.name), temporaryFolder.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun listsNewestFilesFirstAndHidesOnlyInternalTemporaryFiles() {
        val old = temporaryFolder.newFile("old.html")
        val recent = temporaryFolder.newFile("recent.html")
        val ordinaryTemp = temporaryFolder.newFile("notes.tmp")
        temporaryFolder.newFile(".wk-edit-pending.tmp")
        temporaryFolder.newFile(".wk-import-pending.tmp")
        temporaryFolder.newFolder("directory")
        assertTrue(old.setLastModified(1_000))
        assertTrue(ordinaryTemp.setLastModified(2_000))
        assertTrue(recent.setLastModified(3_000))

        assertEquals(listOf(recent, ordinaryTemp, old), listLocalFiles(temporaryFolder.root))
        assertEquals(emptyList<File>(), listLocalFiles(File(temporaryFolder.root, "missing")))
    }

    @Test
    @Config(shadows = [ShadowOsWithRename::class])
    fun successfulContentImportPublishesCompleteFileAndCleansTemporaryFiles() = runBlocking {
        val source = temporaryFolder.newFile("source.json").apply { writeText("{\"ready\":true}") }
        val destination = temporaryFolder.newFolder("imported")

        val imported = saveContentIntentToFile(
            RuntimeEnvironment.getApplication(), Uri.fromFile(source), destination,
        )

        assertTrue("Import did not publish ${imported.absolutePath}", imported.exists())
        assertArrayEquals(source.readBytes(), imported.readBytes())
        assertEquals(source.name, imported.getDisplayName())
        assertEquals(listOf(imported), destination.listFiles().orEmpty().toList())
    }

    @Test
    fun cancellationDuringContentImportLeavesNoPartialOrTemporaryFiles() {
        val source = temporaryFolder.newFile("large.bin").apply {
            writeBytes(ByteArray(5 * 1024 * 1024) { 42 })
        }
        val destination = temporaryFolder.newFolder("cancelled")
        val existing = File(destination, "existing.txt").apply { writeText("keep") }
        var progressCallbacks = 0

        assertThrows(CancellationException::class.java) {
            runBlocking {
                saveContentIntentToFile(
                    RuntimeEnvironment.getApplication(), Uri.fromFile(source), destination,
                ) {
                    progressCallbacks++
                    throw CancellationException("user cancelled the upload")
                }
            }
        }

        assertTrue("Import must have started copying", progressCallbacks > 0)
        assertEquals("keep", existing.readText())
        assertEquals(listOf(existing), destination.listFiles().orEmpty().toList())
    }

    @Test
    fun missingImportSourceDoesNotCreateAnEmptyDestinationFile() {
        val destination = temporaryFolder.newFolder("missing-import")
        val missing = File(temporaryFolder.root, "not-found.txt")

        assertThrows(FileNotFoundException::class.java) {
            runBlocking {
                saveContentIntentToFile(
                    RuntimeEnvironment.getApplication(), Uri.fromFile(missing), destination,
                )
            }
        }

        assertTrue(destination.listFiles().isNullOrEmpty())
    }

    @Test
    fun localFileLinksRoundTripNamesWithUrlReservedCharacters() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(getWebContentFilesDir(context), "id|report #+%.html")
        val uri = Uri.parse(file.getLocalFileLink())

        assertTrue(uri.isLocalFileLink())
        assertEquals(file.name, uri.lastPathSegment)
        assertEquals(file.canonicalFile, uri.resolveLocalFileLink(context))
    }

    @Test
    fun rejectsLocalFileLinkTraversalAndForeignOrigins() {
        val context = RuntimeEnvironment.getApplication()
        val base = "https://appassets.androidplatform.net/web-content-files/"
        for (url in listOf(
            "${base}..", "${base}..%2Foutside.txt", "${base}sub%2Ffile.html",
            "${base}%2Ftmp%2Foutside.txt", "https://example.com/web-content-files/page.html",
            "http://appassets.androidplatform.net/web-content-files/page.html",
        )) {
            assertNull(url, Uri.parse(url).resolveLocalFileLink(context))
        }
    }

    @Test
    fun rejectsLocalFileLinksThroughSymlinksOutsideTheContentDirectory() {
        val context = RuntimeEnvironment.getApplication()
        val target = temporaryFolder.newFile("outside.txt")
        val link = File(getWebContentFilesDir(context), "link.txt")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        try {
            assertNull(Uri.parse(link.getLocalFileLink()).resolveLocalFileLink(context))
        } finally {
            Files.deleteIfExists(link.toPath())
        }
    }
}
