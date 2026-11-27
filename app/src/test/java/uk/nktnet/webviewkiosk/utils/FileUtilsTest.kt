package uk.nktnet.webviewkiosk.utils

import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import uk.nktnet.webviewkiosk.testing.ShadowOsWithRename

@RunWith(RobolectricTestRunner::class)
class FileUtilsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun readsUtf8TextIncludingWhitespaceAndEmptyFilesRegardlessOfExtension() {
        val file = temporaryFolder.newFile("page.png")
        val content = "Hello 你好 😀\t\r\nnext line"
        file.writeText(content)

        assertEquals(LocalFileContents.Text(content), readEditableTextFile(file))
        file.writeText("")
        assertEquals(LocalFileContents.Text(""), readEditableTextFile(file))
    }

    @Test
    fun rejectsBinaryContentsEvenWithATextFileName() {
        val file = temporaryFolder.newFile("page.txt")
        for (bytes in
            listOf(
                byteArrayOf(0x41, 0, 0x42),
                byteArrayOf(0xC3.toByte(), 0x28),
                byteArrayOf(0x41, 0x01, 0x42),
                byteArrayOf(0x41, 0x7F, 0x42),
            )) {
            file.writeBytes(bytes)
            assertEquals(
                bytes.contentToString(),
                LocalFileContents.NotUtf8Text,
                readEditableTextFile(file),
            )
        }
    }

    @Test
    fun boundsEditableTextByUtf8BytesRatherThanCharacterCount() {
        val file = temporaryFolder.newFile("large.txt")
        val atLimit = "é".repeat(512 * 1024)
        file.writeText(atLimit)
        assertEquals(LocalFileContents.Text(atLimit), readEditableTextFile(file))

        file.appendText("é")
        assertEquals(LocalFileContents.TooLarge, readEditableTextFile(file))
    }

    @Test
    fun rejectsDirectoriesAndMissingFiles() {
        assertEquals(
            LocalFileContents.Unreadable,
            readEditableTextFile(temporaryFolder.root),
        )
        assertEquals(
            LocalFileContents.Unreadable,
            readEditableTextFile(File(temporaryFolder.root, "missing.txt")),
        )
    }

    @Test
    @Config(shadows = [ShadowOsWithRename::class])
    fun createsTextAndEmptyFilesWithDistinctIdsForTheSameName() {
        val directory = temporaryFolder.root
        val content = "<h1>Hello 你好</h1>"

        assertTrue(saveLocalFile(directory, null, "page.html", content))
        assertTrue(saveLocalFile(directory, null, "page.html", ""))

        val files = listLocalFiles(directory)
        assertEquals(2, files.size)
        assertEquals(2, files.map { it.getUUID() }.toSet().size)
        assertEquals(setOf("page.html"), files.map { it.getDisplayName() }.toSet())
        assertEquals(
            setOf(LocalFileContents.Text(content), LocalFileContents.Text("")),
            files.map(::readEditableTextFile).toSet(),
        )
        assertEquals(files.toSet(), directory.listFiles().orEmpty().toSet())
    }

    @Test
    @Config(shadows = [ShadowOsWithRename::class])
    fun savesNameAndContentsTogetherAndPreservesTheFileId() {
        val directory = temporaryFolder.root
        val original = temporaryFolder.newFile(generateUuidFileName("original.txt"))
        original.writeText("original")

        assertTrue(
            saveLocalFile(directory, original, "updated.html", "new contents")
        )

        val updated = File(directory, "${original.getUUID()}|updated.html")
        assertEquals(listOf(updated), directory.listFiles().orEmpty().toList())
        assertEquals("new contents", updated.readText())

        assertTrue(saveLocalFile(directory, updated, "updated.html", ""))
        assertEquals(listOf(updated), listLocalFiles(directory))
        assertEquals("", updated.readText())
    }

    @Test
    @Config(shadows = [ShadowOsWithFailedRename::class])
    fun restoresTheOriginalNameIfPublishingTheNewContentsFails() {
        val directory = temporaryFolder.root
        val original = temporaryFolder.newFile(generateUuidFileName("original.txt"))
        original.writeText("original")

        assertFalse(
            saveLocalFile(directory, original, "updated.txt", "new contents")
        )

        assertEquals(listOf(original), directory.listFiles().orEmpty().toList())
        assertEquals("original", original.readText())
    }

    @Test
    fun renamesNonEditableFilesWithoutChangingTheirBytes() {
        val directory = temporaryFolder.root
        val original = temporaryFolder.newFile(generateUuidFileName("original.png"))
        val bytes = byteArrayOf(0x00, 0x41, 0x7F)
        original.writeBytes(bytes)
        assertEquals(LocalFileContents.NotUtf8Text, readEditableTextFile(original))

        assertTrue(saveLocalFile(directory, original, "renamed.png", null))

        val renamed = File(directory, "${original.getUUID()}|renamed.png")
        assertEquals(listOf(renamed), listLocalFiles(directory))
        assertArrayEquals(bytes, renamed.readBytes())
    }

    @Test
    fun rejectsEmptyNamesAndPathsForNewFilesAndRenames() {
        val directory = temporaryFolder.root
        val original = temporaryFolder.newFile(generateUuidFileName("original.txt"))
        original.writeText("original")

        for (name in listOf("", " ", ".", "..", "a/b", "a\\b", "a\u0000b")) {
            assertFalse(saveLocalFile(directory, null, name, "new contents"))
            assertFalse(saveLocalFile(directory, original, name, null))
        }

        assertEquals(listOf(original), directory.listFiles().orEmpty().toList())
        assertEquals("original", original.readText())
    }

    @Test
    fun oversizedWritesPreserveTheOriginalNameAndContents() {
        val file =
            temporaryFolder.newFile("original.txt").apply { writeText("original") }

        assertFalse(
            saveLocalFile(
                temporaryFolder.root,
                file,
                "renamed.txt",
                "é".repeat(512 * 1024 + 1),
            )
        )

        assertEquals("original", file.readText())
        assertEquals(
            listOf(file.name),
            temporaryFolder.root.listFiles().orEmpty().map { it.name },
        )
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

        assertEquals(
            listOf(recent, ordinaryTemp, old),
            listLocalFiles(temporaryFolder.root),
        )
        assertEquals(
            emptyList<File>(),
            listLocalFiles(File(temporaryFolder.root, "missing")),
        )
    }

    @Test
    @Config(shadows = [ShadowOsWithRename::class])
    fun successfulContentImportPublishesCompleteFileAndCleansTemporaryFiles() =
        runBlocking {
            val source =
                temporaryFolder.newFile("source.json").apply {
                    writeText("{\"ready\":true}")
                }
            val destination = temporaryFolder.newFolder("imported")

            val imported =
                saveContentIntentToFile(
                    RuntimeEnvironment.getApplication(),
                    Uri.fromFile(source),
                    destination,
                )

            assertTrue(
                "Import did not publish ${imported.absolutePath}",
                imported.exists(),
            )
            assertArrayEquals(source.readBytes(), imported.readBytes())
            assertEquals(source.name, imported.getDisplayName())
            assertEquals(
                listOf(imported),
                destination.listFiles().orEmpty().toList(),
            )
        }

    @Test
    @Config(shadows = [ShadowOsWithRename::class])
    fun contentImportDoesNotBlockTheCallerThreadWhileCopying() = runBlocking {
        val source =
            temporaryFolder.newFile("thread-check.bin").apply {
                writeBytes(ByteArray(16 * 1024) { 42 })
            }
        val destination = temporaryFolder.newFolder("thread-check")
        val callerThread = Thread.currentThread()
        var copyThread: Thread? = null

        val imported =
            saveContentIntentToFile(
                RuntimeEnvironment.getApplication(),
                Uri.fromFile(source),
                destination,
            ) {
                copyThread = Thread.currentThread()
            }

        assertNotNull("Import should report progress", copyThread)
        assertNotSame(
            "File copying must run on the IO dispatcher",
            callerThread,
            copyThread,
        )
        assertArrayEquals(source.readBytes(), imported.readBytes())
        assertEquals(listOf(imported), destination.listFiles().orEmpty().toList())
    }

    @Test
    fun cancellationDuringContentImportLeavesNoPartialOrTemporaryFiles() {
        val source =
            temporaryFolder.newFile("large.bin").apply {
                writeBytes(ByteArray(5 * 1024 * 1024) { 42 })
            }
        val destination = temporaryFolder.newFolder("cancelled")
        val existing = File(destination, "existing.txt").apply { writeText("keep") }
        var progressCallbacks = 0

        assertThrows(CancellationException::class.java) {
            runBlocking {
                saveContentIntentToFile(
                    RuntimeEnvironment.getApplication(),
                    Uri.fromFile(source),
                    destination,
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
                    RuntimeEnvironment.getApplication(),
                    Uri.fromFile(missing),
                    destination,
                )
            }
        }

        assertTrue(destination.listFiles().isNullOrEmpty())
    }

    @Test
    fun canonicalLinksAlwaysUseUuidAndManualAliasesDecodeReservedCharacters() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(getWebContentFilesDir(context), "id|report #+%.html")
        file.writeText("report")
        try {
            val canonicalLink = Uri.parse(file.getLocalFileLink())
            val shortAlias = manualAlias(file.getDisplayName())

            assertTrue(canonicalLink.isLocalFileLink())
            assertEquals(file.name, canonicalLink.lastPathSegment)
            assertEquals("report #+%.html", shortAlias.lastPathSegment)
            assertEquals(
                file.canonicalFile,
                canonicalLink.resolveLocalFileLink(context),
            )
            assertEquals(
                file.canonicalFile,
                shortAlias.resolveLocalFileLink(context),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun duplicateDisplayNamesOnlyResolveThroughTheirUuidLinks() {
        val context = RuntimeEnvironment.getApplication()
        val filesDir = getWebContentFilesDir(context)
        val first = File(filesDir, "id-first|duplicate-asset-test.html")
        val second = File(filesDir, "id-second|duplicate-asset-test.html")
        first.writeText("first")
        try {
            val alias = manualAlias("duplicate-asset-test.html")
            assertEquals(
                first.name,
                Uri.parse(first.getLocalFileLink()).lastPathSegment,
            )
            assertEquals(first.canonicalFile, alias.resolveLocalFileLink(context))

            second.writeText("second")
            assertNull(alias.resolveLocalFileLink(context))
            assertEquals(
                listOf(first.canonicalFile, second.canonicalFile).toSet(),
                alias.findLocalFileMatches(context).toSet(),
            )
            for (file in listOf(first, second)) {
                assertEquals(
                    file.name,
                    Uri.parse(file.getLocalFileLink()).lastPathSegment,
                )
                assertEquals(
                    file.canonicalFile,
                    Uri.parse(file.getLocalFileLink()).resolveLocalFileLink(context),
                )
            }

            second.delete()
            assertEquals(first.canonicalFile, alias.resolveLocalFileLink(context))
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun existingUuidPathWinsOverAliasAndMissingUuidPathIsNeverAnAlias() {
        val context = RuntimeEnvironment.getApplication()
        val filesDir = getWebContentFilesDir(context)
        val uuid = "12345678-1234-1234-1234-123456789abc"
        val first = File(filesDir, "$uuid|index.html")
        val second =
            File(filesDir, "87654321-4321-4321-4321-cba987654321|$uuid|index.html")
        first.writeText("first")
        second.writeText("second")
        try {
            val requestedLink = manualAlias("$uuid|index.html")
            assertEquals(
                first.canonicalFile,
                requestedLink.resolveLocalFileLink(context),
            )
            assertEquals(
                second.canonicalFile,
                Uri.parse(second.getLocalFileLink()).resolveLocalFileLink(context),
            )

            first.delete()
            assertNull(requestedLink.resolveLocalFileLink(context))
            assertTrue(requestedLink.findLocalFileMatches(context).isEmpty())
        } finally {
            first.delete()
            second.delete()
        }
    }

    private fun manualAlias(name: String): Uri =
        Uri.parse(
            "https://appassets.androidplatform.net/web-content-files/${Uri.encode(name)}"
        )

    @Test
    fun rejectsLocalFileLinkTraversalAndForeignOrigins() {
        val context = RuntimeEnvironment.getApplication()
        val base = "https://appassets.androidplatform.net/web-content-files/"
        for (url in
            listOf(
                "${base}..",
                "${base}..%2Foutside.txt",
                "${base}sub%2Ffile.html",
                "${base}%2Ftmp%2Foutside.txt",
                "https://example.com/web-content-files/page.html",
                "http://appassets.androidplatform.net/web-content-files/page.html",
            )) {
            assertNull(url, Uri.parse(url).resolveLocalFileLink(context))
        }
    }

    @Test
    fun rejectsLocalFileLinksThroughSymlinksOutsideTheContentDirectory() {
        val context = RuntimeEnvironment.getApplication()
        val target = temporaryFolder.newFile("outside.txt")
        val link = File(getWebContentFilesDir(context), "id|link.txt")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        try {
            assertNull(
                Uri.parse(link.getLocalFileLink()).resolveLocalFileLink(context)
            )
            assertNull(
                Uri.parse(
                        "https://appassets.androidplatform.net/web-content-files/link.txt"
                    )
                    .resolveLocalFileLink(context)
            )
        } finally {
            Files.deleteIfExists(link.toPath())
        }
    }

    @Implements(Os::class)
    class ShadowOsWithFailedRename {
        companion object {
            @Suppress("unused", "UNUSED_PARAMETER")
            @JvmStatic
            @Implementation
            fun rename(oldPath: String, newPath: String) {
                throw ErrnoException("rename", OsConstants.EIO)
            }
        }
    }
}
