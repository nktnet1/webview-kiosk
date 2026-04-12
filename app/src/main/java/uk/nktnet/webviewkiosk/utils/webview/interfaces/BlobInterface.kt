package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.managers.ToastManager
import java.io.File
import java.io.FileOutputStream

// https://proandroiddev.com/blob-downloads-not-working-in-android-web-view-heres-the-real-fix-243144a2a426
class BlobInterface(private val context: Context) {
    companion object {
        const val NAME = "WebviewKioskBlobInterface"

        @JvmStatic
        private var isActive = true

        @Suppress("unused")
        @JvmStatic
        fun setIsActive(value: Boolean) {
            isActive = value
        }

        const val JS_BLOB_HOOK = """
            (function() {
                if (window.__blobHookInstalled) return;
                window.__blobHookInstalled = true;
        
                const orig = URL.createObjectURL;
                URL.createObjectURL = function(blob) {
                    window._lastBlob = blob;
                    try { ${NAME}.onDownloadPreparing(); } catch(e) {}
                    return orig.call(URL, blob);
                };
            })();
        """
    }

    @Suppress("unused")
    @JavascriptInterface
    fun onDownloadPreparing() {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, "Preparing file…", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun error(message: String?) {
        ToastManager.show(context, message ?: "Unknown error")
    }

    @Suppress("unused")
    @JavascriptInterface
    fun download(base64: String?, mimeType: String?, filename: String) {
        if (!isActive || base64 == null) {
            return
        }

        try {
            val cleanBase64 = base64.substringAfter(',')
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            saveFile(filename, bytes, mimeType)

        } catch (e: Exception) {
            ToastManager.show(context, "Failed: ${e.message}")
        }
    }

    private fun saveFile(name: String, bytes: ByteArray, mime: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveQ(name, bytes, mime)
        } else {
            saveLegacy(name, bytes, mime)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveQ(name: String, bytes: ByteArray, mime: String?) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return ToastManager.show(context, "Failed to create file")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }

        ToastManager.show(context, "$name downloaded")
        openFile(uri, mime)
    }

    private fun saveLegacy(name: String, bytes: ByteArray, mime: String?) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, name)
        FileOutputStream(file).use { it.write(bytes) }

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )

        ToastManager.show(context, "$name downloaded")
        openFile(uri, mime)
    }

    private fun openFile(uri: Uri, mime: String?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "No app found to open this file", e)
            ToastManager.show(context, "No app found to open this file")
        }
    }
}
