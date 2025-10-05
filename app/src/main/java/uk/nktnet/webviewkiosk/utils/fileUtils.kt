package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.UUID

fun listLocalFiles(dir: File): List<File> {
    return dir.listFiles { it.isFile && it.extension.lowercase(Locale.ROOT) == "html" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name: String? = null
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index)
        }
    }
    return name ?: uri.lastPathSegment ?: "uploaded_file"
}

fun uploadFile(context: Context, uri: Uri, targetDir: File): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val originalFileName = getFileNameFromUri(context, uri)
    val fileName = "${UUID.randomUUID()}|$originalFileName"
    val file = File(targetDir, fileName)
    inputStream?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    return file
}

fun humanReadableSize(size: Long): String {
    return when {
        size >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", size.toDouble() / (1024 * 1024))
        size >= 1024 -> String.format(Locale.ROOT, "%.1f KB", size.toDouble() / 1024)
        else -> "$size B"
    }
}

fun File.displayName(): String {
    val parts = this.name.split("|", limit = 2)
    return parts.getOrElse(1) { this.name }
}
