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

        const val JS_BLOB_HOOK = """
            (function() {
                if (window.__${Constants.APP_SCHEME}_blobHookInstalled) {
                    return;
                }

                window.__${Constants.APP_SCHEME}_blobHookInstalled = true;
                window.__${Constants.APP_SCHEME}_blobsByUrl = new Map();
                window.__${Constants.APP_SCHEME}_blobTimersByUrl = new Map();

                const MAX_CAPTURED_BLOBS = 32;
                const BLOB_RETENTION_MS = 5 * 60 * 1000;

                function releaseBlob(url) {
                    const timer = window.__${Constants.APP_SCHEME}_blobTimersByUrl.get(url);
                    if (timer !== undefined) {
                        clearTimeout(timer);
                        window.__${Constants.APP_SCHEME}_blobTimersByUrl.delete(url);
                    }
                    window.__${Constants.APP_SCHEME}_blobsByUrl.delete(url);
                }

                window.__${Constants.APP_SCHEME}_releaseBlob = releaseBlob;

                const origCreateObjectURL = URL.createObjectURL;

                URL.createObjectURL = function(blob) {
                    const url = origCreateObjectURL.call(URL, blob);
                    // Keep the Blob available even if the page immediately
                    // revokes its object URL. Some sites (including GitHub)
                    // revoke download URLs before the native download prompt
                    // has been confirmed. The entry is removed after the
                    // download succeeds, fails, or is cancelled.
                    window.__${Constants.APP_SCHEME}_blobsByUrl.set(url, blob);

                    while (window.__${Constants.APP_SCHEME}_blobsByUrl.size > MAX_CAPTURED_BLOBS) {
                        const oldestUrl = window.__${Constants.APP_SCHEME}_blobsByUrl.keys().next().value;
                        if (!oldestUrl) break;
                        releaseBlob(oldestUrl);
                    }

                    const timer = setTimeout(function() {
                        releaseBlob(url);
                    }, BLOB_RETENTION_MS);
                    window.__${Constants.APP_SCHEME}_blobTimersByUrl.set(url, timer);
                    return url;
                };

                window.addEventListener('pagehide', function() {
                    try {
                        ${NAME}.abortAllDownloads();
                    } catch (_) {}
                    Array.from(window.__${Constants.APP_SCHEME}_blobsByUrl.keys()).forEach(releaseBlob);
                });
            })();
        """
    }

    private data class ActiveDownload(
        val file: File,
        val output: FileOutputStream,
        val mimeType: String?
    )

    @Volatile
    private var isActive = true

    private val activeDownloads =
        ConcurrentHashMap<String, ActiveDownload>()

    fun dispose() {
        isActive = false
        abortAllDownloads()
    }

    @Suppress("unused")
    @JavascriptInterface
    fun abortAllDownloads() {
        activeDownloads.keys.toList().forEach(::abortInternal)
    }

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
            abortInternal(transferId)

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
                if (!isActive || activeDownloads[transferId] !== download) {
                    return false
                }
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
            activeDownloads[transferId]
                ?: return false

        return try {
            val completed = synchronized(download) {
                if (!isActive || activeDownloads[transferId] !== download) {
                    false
                } else {
                    download.output.flush()
                    download.output.close()
                    activeDownloads.remove(transferId, download)
                }
            }

            if (!completed) {
                return false
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
                        download.file,
                        download.mimeType
                    )
            }

            true
        } catch (e: Exception) {
            abortInternal(transferId)

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
            activeDownloads[transferId]
                ?: return

        synchronized(download) {
            if (!activeDownloads.remove(transferId, download)) {
                return
            }

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
