package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Log
import uk.nktnet.webviewkiosk.config.Constants
import java.io.File

fun parseFileChooserResult(
    context: Context,
    resultCode: Int,
    data: Intent?,
    captureUri: Uri? = null,
): Array<Uri>? {
    if (resultCode != Activity.RESULT_OK) {
        return null
    }
    return try {
        val selected = if (captureUri != null) {
            listOf(data?.data ?: captureUri)
        } else {
            val clipData = data?.clipData
            if (clipData != null) {
                List(clipData.itemCount) { index ->
                    clipData.getItemAt(index).uri ?: return null
                }
            } else {
                listOfNotNull(data?.data)
            }
        }
        if (selected.isEmpty()) {
            null
        } else {
            selected.map { uri ->
                validateUploadUri(context, uri, captureUri) ?: return null
            }.toTypedArray()
        }
    } catch (error: Exception) {
        Log.w(Constants.APP_SCHEME, "Invalid file chooser result", error)
        null
    }
}

private fun validateUploadUri(context: Context, uri: Uri, captureUri: Uri?): Uri? {
    // Only the exact output created for this capture may use our private provider.
    if (captureUri != null && uri == captureUri) {
        return uri
    }
    return when (uri.scheme) {
        "content" -> {
            val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return null
            val provider = context.packageManager.resolveContentProvider(
                authority.substringAfterLast('@'),
                0,
            ) ?: return null
            if (provider.packageName == context.packageName || provider.applicationInfo.uid == Process.myUid()) {
                return null
            }
            val hasReadGrant = context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
            // A public provider needs no grant. Protected providers must grant this URI;
            // the app's broader permissions alone are not evidence of a valid selection.
            val isPublic = provider.exported && provider.readPermission == null
                && provider.pathPermissions.orEmpty().none {
                    it.readPermission != null && it.match(uri.path.orEmpty())
                }
            uri.takeIf { hasReadGrant || isPublic }
        }
        "file" -> validateLegacyFileUri(context, uri)
        else -> null
    }
}

private fun validateLegacyFileUri(context: Context, uri: Uri): Uri? {
    if (!uri.authority.isNullOrEmpty()) {
        return null
    }
    val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
    val input = File(path)
    if (!input.isAbsolute) {
        return null
    }
    val file = input.canonicalFile
    val externalFiles = context.getExternalFilesDirs(null).filterNotNull()
    val privateDirs = mutableListOf(
        File(context.applicationInfo.dataDir),
        context.filesDir,
        context.cacheDir,
    )
    privateDirs.addAll(externalFiles)
    privateDirs.addAll(context.externalCacheDirs.filterNotNull())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        privateDirs.add(context.createDeviceProtectedStorageContext().dataDir)
    }
    if (privateDirs.any { file.isWithin(it.canonicalFile) }) {
        return null
    }

    // Older pickers can return file:// URIs. Limit them to shared storage, including
    // removable volumes, and pass the canonical path rather than a symlink alias.
    val sharedDirs = mutableListOf(Environment.getExternalStorageDirectory())
    externalFiles.mapNotNullTo(sharedDirs) { it.parentFile?.parentFile?.parentFile?.parentFile }
    return if (
        sharedDirs.any { file.isWithin(it.canonicalFile) }
        && file.isFile && file.canRead()
    ) {
        Uri.fromFile(file)
    } else {
        null
    }
}

private fun File.isWithin(directory: File): Boolean {
    return this == directory || path.startsWith(directory.path + File.separator)
}
