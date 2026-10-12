package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BlobInterface
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class BlobInterfaceTest {
    private lateinit var context: Context
    private lateinit var downloads: File
    private lateinit var blob: BlobInterface
    private lateinit var filename: String
    private var previousAllowNotifications = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val settings = UserSettings(context)
        previousAllowNotifications = settings.allowNotifications
        settings.allowNotifications = false
        downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        blob = BlobInterface(context)
        filename = "regression-${UUID.randomUUID()}.bin"
    }

    @After
    fun tearDown() {
        blob.dispose()
        UserSettings(context).allowNotifications = previousAllowNotifications
        downloads.listFiles().orEmpty()
            .filter { it.name.startsWith(filename.removeSuffix(".bin")) }
            .forEach { it.delete() }
    }

    @Test
    fun concurrentDownloadsReserveDistinctFilenamesAndKeepTheirOwnBytes() {
        val firstBytes = "first download".toByteArray()
        val secondBytes = "second download".toByteArray()
        val duplicate = File(downloads, filename.replace(".bin", " (1).bin"))
        val original = File(downloads, filename)

        assertTrue(blob.startDownload("first", "application/octet-stream", filename))
        assertTrue(blob.startDownload("second", "application/octet-stream", filename))
        assertTrue(blob.appendChunk("first", encode(firstBytes)))
        assertTrue(blob.appendChunk("second", encode(secondBytes)))
        assertTrue(blob.finishDownload("second"))
        assertTrue(blob.finishDownload("first"))

        assertArrayEquals(firstBytes, original.readBytes())
        assertArrayEquals(secondBytes, duplicate.readBytes())
        assertFalse(blob.finishDownload("first"))
        assertFalse(blob.appendChunk("second", encode(secondBytes)))
    }

    @Test
    fun abortOnlyRemovesTheSelectedTransfer() {
        val bytes = "keep me".toByteArray()
        val original = File(downloads, filename)
        val duplicate = File(downloads, filename.replace(".bin", " (1).bin"))

        assertTrue(blob.startDownload("cancel", null, filename))
        assertTrue(blob.startDownload("keep", null, filename))
        blob.abortDownload("cancel")
        blob.abortDownload("cancel")

        assertFalse(original.exists())
        assertTrue(blob.appendChunk("keep", encode(bytes)))
        assertTrue(blob.finishDownload("keep"))
        assertArrayEquals(bytes, duplicate.readBytes())
        assertFalse(blob.finishDownload("cancel"))
    }

    @Test
    fun reusingTransferIdClosesAndDeletesPreviousPartialFile() {
        val original = File(downloads, filename)

        assertTrue(blob.startDownload("same-id", null, filename))
        assertTrue(blob.appendChunk("same-id", encode("discard".toByteArray())))
        assertTrue(blob.startDownload("same-id", null, filename))
        assertTrue(blob.appendChunk("same-id", encode("replacement".toByteArray())))
        assertTrue(blob.finishDownload("same-id"))

        assertArrayEquals("replacement".toByteArray(), original.readBytes())
        assertFalse(File(downloads, filename.replace(".bin", " (1).bin")).exists())
    }

    @Test
    fun disposalAbortsPendingTransfersAndRejectsFurtherOperations() {
        val original = File(downloads, filename)
        assertTrue(blob.startDownload("active", null, filename))
        assertTrue(blob.appendChunk("active", encode("partial".toByteArray())))

        blob.dispose()
        blob.dispose()

        assertFalse(original.exists())
        assertFalse(blob.startDownload("new", null, filename))
        assertFalse(blob.appendChunk("active", encode("late".toByteArray())))
        assertFalse(blob.finishDownload("active"))
    }

    @Test
    fun invalidFilenameDoesNotStartTransfer() {
        for (invalid in listOf("", "../outside.bin", "folder/file.bin", "folder\\file.bin")) {
            assertFalse(blob.startDownload("invalid", null, invalid))
        }
        assertFalse(blob.appendChunk("missing", encode(byteArrayOf(1))))
        assertFalse(blob.finishDownload("missing"))
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
