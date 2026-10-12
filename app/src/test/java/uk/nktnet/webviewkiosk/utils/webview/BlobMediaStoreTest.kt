package uk.nktnet.webviewkiosk.utils.webview

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BlobInterface
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** Exercises the scoped-storage branch through the real resolver and BlobInterface. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@LooperMode(LooperMode.Mode.PAUSED)
class BlobMediaStoreTest {
    private lateinit var context: Context
    private lateinit var provider: RecordingDownloadsProvider
    private lateinit var blob: BlobInterface
    private var previousAllowNotifications = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val settings = UserSettings(context)
        previousAllowNotifications = settings.allowNotifications
        settings.allowNotifications = false
        provider = Robolectric.buildContentProvider(RecordingDownloadsProvider::class.java)
            .create(ProviderInfo().apply {
                authority = requireNotNull(MediaStore.Downloads.EXTERNAL_CONTENT_URI.authority)
            })
            .get()
        blob = BlobInterface(context)
    }

    @After
    fun tearDown() {
        blob.dispose()
        shadowOf(Looper.getMainLooper()).idle()
        ToastManager.cancel()
        UserSettings(context).allowNotifications = previousAllowNotifications
        provider.shutdown()
    }

    @Test
    fun completionPublishesOnlyAfterTheOutputIsFlushedAndClosed() {
        val bytes = "complete PDF bytes".toByteArray()
        assertTrue(blob.startDownload("report", null, "report.pdf"))
        val row = provider.created.single()

        assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, provider.insertedAt.single())
        assertEquals("report.pdf", row.values.getAsString(MediaStore.Downloads.DISPLAY_NAME))
        assertEquals("application/octet-stream", row.values.getAsString(MediaStore.Downloads.MIME_TYPE))
        assertEquals(Environment.DIRECTORY_DOWNLOADS, row.values.getAsString(MediaStore.Downloads.RELATIVE_PATH))
        assertEquals(1, pending(row))
        assertTrue(provider.publicationAttempts.isEmpty())

        assertTrue(blob.appendChunk("report", encode(bytes)))
        assertEquals(1, pending(row))
        assertFalse(row.output.closed)
        assertTrue(provider.publicationAttempts.isEmpty())

        assertTrue(blob.finishDownload("report"))

        assertArrayEquals(bytes, row.output.bytes())
        assertEquals(listOf("write", "flush", "close", "publish"), row.output.events)
        assertEquals(0, pending(row))
        assertEquals(listOf(row.uri), provider.publicationAttempts)
        assertTrue(provider.deleted.isEmpty())
        assertTransferRejected("report")
        assertEquals(listOf(row.uri), provider.publicationAttempts)
    }

    @Test
    fun completionUsesTheProviderAssignedFilenameAndExplicitMimeType() {
        provider.nextSavedName = "report (1).pdf"
        assertTrue(blob.startDownload("renamed", "application/pdf", "report.pdf"))
        val row = provider.created.single()
        assertEquals("application/pdf", row.values.getAsString(MediaStore.Downloads.MIME_TYPE))

        assertTrue(blob.appendChunk("renamed", encode(byteArrayOf(1, 2, 3))))
        assertTrue(blob.finishDownload("renamed"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("report (1).pdf downloaded", ShadowToast.getTextOfLatestToast())
        assertEquals(0, pending(row))
    }

    @Test
    fun interleavedDownloadsKeepTheirOwnBytesWhenFinishedInReverseOrder() {
        assertTrue(blob.startDownload("first", null, "same.bin"))
        assertTrue(blob.startDownload("second", null, "same.bin"))
        val first = provider.created[0]
        val second = provider.created[1]
        assertFalse(first.uri == second.uri)

        assertTrue(blob.appendChunk("first", encode("first ".toByteArray())))
        assertTrue(blob.appendChunk("second", encode("second".toByteArray())))
        assertTrue(blob.appendChunk("first", encode("download".toByteArray())))
        assertTrue(blob.finishDownload("second"))
        assertEquals(1, pending(first))
        assertEquals(0, pending(second))
        assertTrue(blob.finishDownload("first"))

        assertArrayEquals("first download".toByteArray(), first.output.bytes())
        assertArrayEquals("second".toByteArray(), second.output.bytes())
        assertEquals(listOf(second.uri, first.uri), provider.publicationAttempts)
        assertEquals(setOf(first.uri, second.uri), provider.rows.keys)
    }

    @Test
    fun insertAndOutputOpenFailuresLeaveNoPendingTransfer() {
        provider.rejectInsert = true
        assertFalse(blob.startDownload("insert-failed", null, "report.bin"))
        assertTrue(provider.created.isEmpty())
        assertTrue(provider.deleted.isEmpty())
        assertTransferRejected("insert-failed")
        provider.rejectInsert = false

        for (failure in listOf(OpenFailure.RETURN_NULL, OpenFailure.THROW)) {
            provider.nextOpenFailure = failure
            val id = failure.name
            assertFalse(blob.startDownload(id, null, "report.bin"))
            val row = provider.created.last()

            assertTrue(provider.rows.isEmpty())
            assertEquals(1, provider.deleted.count { it == row.uri })
            assertEquals(1, row.openAttempts)
            assertTrue(provider.publicationAttempts.isEmpty())
            assertTransferRejected(id)
        }

        // A failed start must not poison a later transfer using the same ID.
        provider.nextOpenFailure = OpenFailure.NONE
        assertTrue(blob.startDownload("insert-failed", null, "report.bin"))
        assertTrue(blob.finishDownload("insert-failed"))
    }

    @Test
    fun displayNameQueryFailureDeletesTheInsertedRowBeforeOpeningItsOutput() {
        provider.failQuery = true

        assertFalse(blob.startDownload("query-failed", null, "report.bin"))
        val row = provider.created.single()

        assertEquals(0, row.openAttempts)
        assertEquals(listOf(row.uri), provider.deleted)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.publicationAttempts.isEmpty())
        assertTransferRejected("query-failed")
    }

    @Test
    fun invalidChunkAndWriteFailureAbortOnlyTheAffectedDownload() {
        assertTrue(blob.startDownload("keep", null, "keep.bin"))
        val kept = provider.created.single()

        for (writeFailure in listOf(false, true)) {
            val id = if (writeFailure) "write-failed" else "invalid-base64"
            assertTrue(blob.startDownload(id, null, "discard.bin"))
            val failed = provider.created.last()
            assertTrue(blob.appendChunk(id, encode("partial".toByteArray())))
            failed.output.failWrite = writeFailure

            // A single Base64 digit is an incomplete group, rather than ignored whitespace.
            val chunk = if (writeFailure) encode("late bytes".toByteArray()) else "a"
            assertFalse(blob.appendChunk(id, chunk))

            assertTrue(failed.output.closed)
            assertEquals(1, provider.deleted.count { it == failed.uri })
            assertEquals(setOf(kept.uri), provider.rows.keys)
            assertEquals(1, pending(kept))
            assertFalse(kept.output.closed)
            assertTransferRejected(id)
        }

        assertTrue(provider.publicationAttempts.isEmpty())
        assertTrue(blob.appendChunk("keep", encode("still intact".toByteArray())))
        assertTrue(blob.finishDownload("keep"))
        assertArrayEquals("still intact".toByteArray(), kept.output.bytes())
    }

    @Test
    fun flushAndCloseFailuresAbortWithoutPublishing() {
        for (flushFailure in listOf(true, false)) {
            val id = if (flushFailure) "flush-failed" else "close-failed"
            assertTrue(blob.startDownload(id, null, "report.bin"))
            val row = provider.created.last()
            assertTrue(blob.appendChunk(id, encode("complete bytes".toByteArray())))
            row.output.failFlush = flushFailure
            row.output.failNextClose = !flushFailure

            assertFalse(blob.finishDownload(id))

            assertTrue(row.output.closed)
            assertEquals(1, pending(row))
            assertEquals(1, provider.deleted.count { it == row.uri })
            assertTrue(provider.rows.isEmpty())
            assertTrue(provider.publicationAttempts.isEmpty())
            assertTransferRejected(id)
        }
    }

    @Test
    fun failedPublicationDeletesTheRowAndRejectsFurtherUse() {
        for (failure in listOf("no-row", "exception")) {
            val id = "publish-$failure"
            assertTrue(blob.startDownload(id, null, "report.bin"))
            val row = provider.created.last()
            assertTrue(blob.appendChunk(id, encode("complete bytes".toByteArray())))
            provider.publicationResult = if (failure == "no-row") 0 else 1
            provider.failPublication = failure == "exception"

            assertFalse(blob.finishDownload(id))

            assertTrue(row.output.closed)
            assertEquals(1, pending(row))
            assertEquals(1, provider.publicationAttempts.count { it == row.uri })
            assertEquals(1, provider.deleted.count { it == row.uri })
            assertTrue(provider.rows.isEmpty())
            assertTransferRejected(id)
            blob.abortDownload(id)
            assertEquals(1, provider.deleted.count { it == row.uri })
        }
    }

    @Test
    fun abortAndTransferIdReuseLeaveOtherDownloadsUntouched() {
        assertTrue(blob.startDownload("replace", null, "report.bin"))
        val original = provider.created.last()
        assertTrue(blob.appendChunk("replace", encode("discard".toByteArray())))
        assertTrue(blob.startDownload("keep", null, "report.bin"))
        val kept = provider.created.last()

        assertTrue(blob.startDownload("replace", null, "report.bin"))
        val replacement = provider.created.last()
        assertTrue(original.output.closed)
        assertEquals(listOf(original.uri), provider.deleted)
        assertEquals(setOf(kept.uri, replacement.uri), provider.rows.keys)

        blob.abortDownload("replace")
        blob.abortDownload("replace")
        blob.abortDownload("missing")

        assertTrue(replacement.output.closed)
        assertEquals(listOf(original.uri, replacement.uri), provider.deleted)
        assertEquals(setOf(kept.uri), provider.rows.keys)
        assertTransferRejected("replace")
        assertTrue(blob.appendChunk("keep", encode("keep me".toByteArray())))
        assertTrue(blob.finishDownload("keep"))
        assertArrayEquals("keep me".toByteArray(), kept.output.bytes())
    }

    @Test
    fun disposalDeletesPendingRowsAndPreservesCompletedDownloads() {
        assertTrue(blob.startDownload("complete", null, "complete.bin"))
        val completed = provider.created.last()
        assertTrue(blob.appendChunk("complete", encode("saved".toByteArray())))
        assertTrue(blob.finishDownload("complete"))
        assertTrue(blob.startDownload("first", null, "first.bin"))
        val first = provider.created.last()
        assertTrue(blob.appendChunk("first", encode("partial".toByteArray())))
        assertTrue(blob.startDownload("second", null, "second.bin"))
        val second = provider.created.last()

        blob.dispose()
        blob.dispose()
        blob.abortAllDownloads()

        assertEquals(setOf(completed.uri), provider.rows.keys)
        assertEquals(setOf(first.uri, second.uri), provider.deleted.toSet())
        assertEquals(2, provider.deleted.size)
        assertTrue(first.output.closed)
        assertTrue(second.output.closed)
        assertEquals(0, pending(completed))
        assertArrayEquals("saved".toByteArray(), completed.output.bytes())
        assertEquals(listOf(completed.uri), provider.publicationAttempts)
        assertTransferRejected("first")
        assertTransferRejected("second")
        assertFalse(blob.startDownload("new", null, "new.bin"))
        assertEquals(3, provider.created.size)
    }

    private fun assertTransferRejected(id: String) {
        assertFalse(blob.appendChunk(id, encode(byteArrayOf(42))))
        assertFalse(blob.finishDownload(id))
    }

    private fun pending(row: StoredDownload): Int =
        requireNotNull(row.values.getAsInteger(MediaStore.Downloads.IS_PENDING))

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    enum class OpenFailure { NONE, RETURN_NULL, THROW }

    class StoredDownload(val uri: Uri, val values: ContentValues) {
        val output = RecordingOutputStream()
        var openAttempts = 0
    }

    /** Records storage effects; collision naming is supplied by the provider, as on Android. */
    class RecordingDownloadsProvider : ContentProvider() {
        val rows = linkedMapOf<Uri, StoredDownload>()
        val created = mutableListOf<StoredDownload>()
        val insertedAt = mutableListOf<Uri>()
        val deleted = mutableListOf<Uri>()
        val publicationAttempts = mutableListOf<Uri>()
        var nextSavedName: String? = null
        var rejectInsert = false
        var nextOpenFailure = OpenFailure.NONE
        var failQuery = false
        var publicationResult = 1
        var failPublication = false
        private var nextId = 1L

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertedAt.add(uri)
            if (rejectInsert) return null
            val row = StoredDownload(
                ContentUris.withAppendedId(uri, nextId++),
                ContentValues(requireNotNull(values)).apply {
                    nextSavedName?.let { put(MediaStore.Downloads.DISPLAY_NAME, it) }
                },
            )
            rows[row.uri] = row
            created.add(row)
            val openFailure = nextOpenFailure
            shadowOf(requireNotNull(context).contentResolver).registerOutputStreamSupplier(row.uri) {
                row.openAttempts++
                when (openFailure) {
                    OpenFailure.NONE -> row.output
                    OpenFailure.RETURN_NULL -> null
                    OpenFailure.THROW -> throw FileNotFoundException("Output unavailable")
                }
            }
            return row.uri
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (failQuery) throw IllegalStateException("Display name unavailable")
            val row = requireNotNull(rows[uri])
            val columns = projection ?: arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            return MatrixCursor(columns).apply {
                addRow(columns.map { row.values.get(it) }.toTypedArray())
            }
        }

        // A registered null stream falls through to the real resolver, which accepts this null.
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            val row = requireNotNull(rows[uri])
            publicationAttempts.add(uri)
            row.output.events.add("publish")
            if (failPublication) throw SecurityException("Publication refused")
            if (publicationResult == 1) row.values.putAll(requireNotNull(values))
            return publicationResult
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted.add(uri)
            return if (rows.remove(uri) == null) 0 else 1
        }

        override fun getType(uri: Uri): String? =
            rows[uri]?.values?.getAsString(MediaStore.Downloads.MIME_TYPE)
    }

    class RecordingOutputStream : OutputStream() {
        private val contents = ByteArrayOutputStream()
        val events = mutableListOf<String>()
        var closed = false
            private set
        var failWrite = false
        var failFlush = false
        var failNextClose = false

        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (closed || failWrite) throw IOException("Write failed")
            events.add("write")
            contents.write(bytes, offset, length)
        }

        override fun flush() {
            events.add("flush")
            if (failFlush) throw IOException("Flush failed")
        }

        override fun close() {
            events.add("close")
            if (failNextClose) {
                failNextClose = false
                throw IOException("Close failed")
            }
            closed = true
        }

        fun bytes(): ByteArray = contents.toByteArray()
    }
}
