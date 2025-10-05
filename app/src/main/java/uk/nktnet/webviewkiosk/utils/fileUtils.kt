package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
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

fun uploadFile(context: Context, uri: Uri, targetDir: File): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val originalFileName = getFileNameFromUri(context, uri)
    val fileName = generateUuidFileName(originalFileName)
    val file = File(targetDir, fileName)

    inputStream?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return file
}

fun saveContentIntentToFile(context: Context, contentUri: Uri, targetDir: File): File {
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

    inputStream.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return file
}

fun humanReadableSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", size.toDouble() / (1024 * 1024))
        size >= 1024 -> String.format(Locale.ROOT, "%.1f KB", size.toDouble() / 1024)
        else -> "$size B"
    }
}
fun File.getUUID(): String {
    return this.name.split("|", limit = 2).getOrElse(0) { "" }
}

fun File.getDisplayName(): String {
    return this.name.split("|", limit = 2).getOrElse(1) { this.name }
}
