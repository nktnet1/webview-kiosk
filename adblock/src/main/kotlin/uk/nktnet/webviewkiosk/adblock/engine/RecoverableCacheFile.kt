package uk.nktnet.webviewkiosk.adblock.engine

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** AtomicFile-style rollback without introducing Android into the JVM engine. */
internal class RecoverableCacheFile(private val file: File) {
    private val backup = File("${file.path}.bak")
    private val pending = File("${file.path}.new")

    fun recover(): File {
        if (backup.exists()) {
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot discard incomplete cache replacement")
            }
            if (!backup.renameTo(file)) {
                throw IOException("Cannot recover previous cache")
            }
        }
        return file
    }

    fun write(maxBytes: Long, writer: (FileOutputStream) -> Unit) {
        recover()
        val parent = file.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create cache directory")
        }
        try {
            FileOutputStream(pending).use { output ->
                writer(output)
                output.flush()
                output.fd.sync()
            }
            if (pending.length() > maxBytes) {
                throw IOException("Cache exceeds the size limit")
            }
            if (file.exists() && !file.renameTo(backup)) {
                throw IOException("Cannot preserve previous cache")
            }
            if (!pending.renameTo(file)) {
                throw IOException("Cannot promote cache replacement")
            }
            if (backup.exists() && !backup.delete()) {
                throw IOException("Cannot finish cache replacement")
            }
        } catch (error: IOException) {
            recover()
            throw error
        } finally {
            pending.delete()
        }
    }
}
