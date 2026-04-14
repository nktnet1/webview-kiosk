package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
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
            saveFile(filename, bytes)
        } catch (e: Exception) {
            ToastManager.show(context, "Failed: ${e.message}")
        }
    }

    private fun saveFile(name: String, bytes: ByteArray) {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloads, name)
        FileOutputStream(file).use {
            it.write(bytes)
        }
        ToastManager.show(context, "$name downloaded")

        val userSettings = UserSettings(context)
        if (userSettings.allowNotifications) {
            CustomNotificationManager.sendBlobDownloadNotification(context, file)
        }
    }
}
