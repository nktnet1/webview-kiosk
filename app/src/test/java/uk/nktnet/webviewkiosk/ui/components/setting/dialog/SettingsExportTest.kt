package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class SettingsExportTest {
    @Test
    fun exportsUtf8OnIoDispatcherAndClosesTheStreamBeforeReturning() = runBlocking {
        val callingThread = Thread.currentThread()
        var writeThread: Thread? = null
        var closed = false
        val output = ByteArrayOutputStream()
        writeSettingsExport("settings: café") {
            writeThread = Thread.currentThread()
            object : OutputStream() {
                override fun write(b: Int) = output.write(b)
                override fun write(bytes: ByteArray, off: Int, len: Int) = output.write(bytes, off, len)
                override fun close() { closed = true }
            }
        }
        assertArrayEquals("settings: café".toByteArray(Charsets.UTF_8), output.toByteArray())
        assertTrue("provider stream must be closed before success", closed)
        assertTrue("provider IO must not run on the Compose caller thread", writeThread !== callingThread)
    }

    @Test
    fun nullProviderStreamIsAFailureNotASilentSuccess() = runBlocking {
        val result = runCatching { writeSettingsExport("settings") { null } }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Unable to open selected export file.", result.exceptionOrNull()?.message)
    }

    @Test
    fun failureWhileClosingProviderIsPropagated() = runBlocking {
        val result = runCatching {
            writeSettingsExport("settings") {
                object : OutputStream() {
                    override fun write(b: Int) = Unit
                    override fun close(): Unit = throw IOException("provider rejected close")
                }
            }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("provider rejected close", result.exceptionOrNull()?.message)
    }
}
