package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.Constants
import java.io.File
import java.util.UUID

fun listLocalFiles(dir: File): List<File> {
    return dir.listFiles { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name: String? = null
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                name = it.getString(index)
            }
        }
    }
    return name ?: uri.lastPathSegment ?: "uploaded_file"
}

fun generateUuidFileName(originalName: String): String {
    return "${UUID.randomUUID()}|$originalName"
}

private fun copyInputStreamToFile(
    input: java.io.InputStream,
    targetFile: File,
    onProgress: ((Float) -> Unit)? = null
): File {
    val totalBytes = input.available().toLong()
    var copiedBytes = 0L
    input.use { i ->
        targetFile.outputStream().use { o ->
            val buffer = ByteArray(4 * 1024 * 1024)
            var bytesRead: Int
            while (i.read(buffer).also { bytesRead = it } >= 0) {
                o.write(buffer, 0, bytesRead)
                copiedBytes += bytesRead
                if (totalBytes > 0) {
                    onProgress?.invoke(copiedBytes.toFloat() / totalBytes)
                }
            }
        }
    }
    return targetFile
}

fun uploadFile(
    context: Context,
    uri: Uri,
    targetDir: File,
    onProgress: (Float) -> Unit
): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val originalFileName = getFileNameFromUri(context, uri)
    val fileName = generateUuidFileName(originalFileName)
    val file = File(targetDir, fileName)
    return copyInputStreamToFile(inputStream!!, file, onProgress)
}

suspend fun saveContentIntentToFile(
    context: Context,
    contentUri: Uri,
    targetDir: File,
    onProgress: ((Float) -> Unit)? = null
): File = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(contentUri)
        ?: throw IllegalArgumentException("Unable to open InputStream for URI: $contentUri")

    var originalName = contentUri.lastPathSegment ?: "uploaded_file"
    val mimeType = context.contentResolver.getType(contentUri)
    if (!originalName.contains('.') && mimeType != null) {
        android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let {
            originalName += ".$it"
        }
    }

    val fileName = generateUuidFileName(originalName)
    val file = File(targetDir, fileName)

    copyInputStreamToFile(inputStream, file, onProgress)
}

fun getWebContentFilesDir(context: Context): File {
    return File(context.filesDir, Constants.WEB_CONTENT_FILES_DIR).apply {
        if (!exists()) {
            mkdirs()
        }
    }
}

fun humanReadableSize(context: Context, size: Long): String {
    return Formatter.formatFileSize(context, size)
}

fun File.getUUID(): String {
    return this.name.split("|", limit = 2).getOrElse(0) { "" }
}

fun File.getDisplayName(): String {
    return this.name.split("|", limit = 2).getOrElse(1) { this.name }
}
