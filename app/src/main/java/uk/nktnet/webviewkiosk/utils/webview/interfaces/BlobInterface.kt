package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class BlobInterface(
    private val context: Context
) {
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
                if (window.__${Constants.APP_SCHEME}_blobHookInstalled) {
                    return;
                }

                window.__${Constants.APP_SCHEME}_blobHookInstalled = true;

                const orig = URL.createObjectURL;

                URL.createObjectURL = function(blob) {
                    window.__${Constants.APP_SCHEME}_lastBlob = blob;
                    return orig.call(URL, blob);
                };
            })();
        """
    }

    private data class ActiveDownload(
        val file: File,
        val output: FileOutputStream,
        val mimeType: String?
    )

    private val activeDownloads =
        ConcurrentHashMap<String, ActiveDownload>()

    @JavascriptInterface
    fun error(message: String?) {
        ToastManager.show(
            context,
            message ?: "Unknown error"
        )
    }

    @Suppress("unused")
    @JavascriptInterface
    fun startDownload(
        transferId: String,
        mimeType: String?,
        filename: String
    ): Boolean {
        if (!isActive) {
            return false
        }

        if (!isValidFilename(filename)) {
            ToastManager.show(
                context,
                "Invalid filename: '$filename'"
            )
            return false
        }

        return try {
            // Clean up an accidentally reused transfer ID.
            activeDownloads.remove(transferId)?.let {
                try {
                    it.output.close()
                } catch (_: Exception) {
                }
            }

            val downloads =
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )

            if (!downloads.exists()) {
                downloads.mkdirs()
            }

            val file = File(downloads, filename)
            val output = FileOutputStream(file)

            activeDownloads[transferId] = ActiveDownload(
                file = file,
                output = output,
                mimeType = mimeType
            )

            true
        } catch (e: Exception) {
            ToastManager.show(
                context,
                "Unable to start download: ${e.message}"
            )
            false
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun appendChunk(
        transferId: String,
        base64Chunk: String?
    ): Boolean {
        if (!isActive || base64Chunk == null) {
            return false
        }

        val download =
            activeDownloads[transferId]
                ?: return false

        return try {
            val bytes = Base64.decode(
                base64Chunk,
                Base64.NO_WRAP
            )

            synchronized(download) {
                download.output.write(bytes)
            }

            true
        } catch (e: Exception) {
            abortInternal(transferId)

            ToastManager.show(
                context,
                "Download failed: ${e.message}"
            )

            false
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun finishDownload(
        transferId: String
    ): Boolean {
        val download =
            activeDownloads.remove(transferId)
                ?: return false

        return try {
            synchronized(download) {
                download.output.flush()
                download.output.close()
            }

            ToastManager.show(
                context,
                "${download.file.name} downloaded"
            )

            val userSettings = UserSettings(context)

            if (userSettings.allowNotifications) {
                CustomNotificationManager
                    .sendBlobDownloadNotification(
                        context,
                        download.file
                    )
            }

            true
        } catch (e: Exception) {
            try {
                download.output.close()
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to close download output", e)
            }

            ToastManager.show(
                context,
                "Failed to finish download: ${e.message}"
            )

            false
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun abortDownload(
        transferId: String
    ) {
        abortInternal(transferId)
    }

    private fun abortInternal(
        transferId: String
    ) {
        val download =
            activeDownloads.remove(transferId)
                ?: return

        try {
            download.output.close()
        } catch (e: Exception) {
            Log.e(
                javaClass.simpleName,
                "Failed to close download output during abort",
                e
            )
        }

        try {
            download.file.delete()
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to delete file during abort", e)
        }
    }

    private fun isValidFilename(
        filename: String
    ): Boolean {
        return (
            filename.isNotBlank()
                && !filename.contains("..")
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains('\u0000')
        )
    }
}
