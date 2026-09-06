package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Typeface
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.extractFileNameFromContentDisposition
import uk.nktnet.webviewkiosk.utils.getDownloadLocation
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.webview.interfaces.BlobInterface

@SuppressLint("SetTextI18n")
fun handleDownloadPrompt(
    context: Context,
    webView: WebView,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    val userSettings = UserSettings(context)
    if (!userSettings.allowFileDownload) {
        ToastManager.show(
            context,
            "Download is disabled in settings (${UserSettingsKeys.WebEngine.ALLOW_FILE_DOWNLOAD})"
        )
        return
    }

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(60, 60, 60, 30)
    }

    val titleView = TextView(context).apply {
        text = "Download File"
        textSize = 25f
        setPadding(0, 0, 0, 20)
    }
    layout.addView(titleView)

    val infoText = TextView(context).apply {
        text = getDownloadLocation()
        textSize = 12f
        setTypeface(typeface, Typeface.ITALIC)
        setPadding(10, 10, 10, 0)
    }
    layout.addView(infoText)

    val uri = url.toUri()

    val suggestedName = when {
        !contentDisposition.isNullOrBlank() -> {
            extractFileNameFromContentDisposition(contentDisposition)
        }
        uri.scheme == "blob" -> {
            generateBlobFilename(mimeType)
        }
        else -> {
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        }
    }

    val editText = EditText(context).apply {
        setText(suggestedName)
        setPadding(10, 10, 10, 35)
    }
    layout.addView(editText)

    val buttonsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        )
        setPadding(0, 50, 0, 0)
    }

    val dialog = AlertDialog.Builder(context)
        .setView(layout)
        .setOnCancelListener {
            UserInteractionStateSingleton.onUserInteraction()
        }
        .setOnDismissListener {
            UserInteractionStateSingleton.onUserInteraction()
        }
        .show()

    val cancelButton = Button(context).apply { text = "Cancel" }
    cancelButton.setOnClickListener {
        UserInteractionStateSingleton.onUserInteraction()
        dialog.dismiss()
    }

    val downloadButton = Button(context).apply { text = "Download" }
    downloadButton.setOnClickListener {
        try {
            UserInteractionStateSingleton.onUserInteraction()
            val filename = editText.text.toString()

            if (uri.scheme == "blob") {
                fetchBlob(webView, url, mimeType, filename)
            } else {
                downloadNormal(
                    context = context,
                    url = url,
                    userAgent = userAgent,
                    mimeType = mimeType,
                    filename = filename
                )
            }

            dialog.dismiss()
            ToastManager.show(context, "Starting download for $filename")
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "Download failed", e)
            ToastManager.show(context, "Error: ${e.message}")
        }
    }

    buttonsLayout.addView(cancelButton)
    buttonsLayout.addView(downloadButton)
    layout.addView(buttonsLayout)

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}

fun downloadNormal(
    context: Context,
    url: String,
    userAgent: String?,
    mimeType: String?,
    filename: String
) {
    val request = DownloadManager.Request(url.toUri()).apply {
        setMimeType(mimeType)
        userAgent?.let { addRequestHeader("User-Agent", it) }
        setDescription("Downloading file...")
        setTitle(filename)
        setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            filename,
        )
    }

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}

private fun fetchBlob(
    webView: WebView,
    blobUrl: String,
    mimeType: String?,
    filename: String
) {
    val transferId = java.util.UUID.randomUUID().toString()

    val quotedBlobUrl = JSONObject.quote(blobUrl)
    val quotedMimeType = JSONObject.quote(mimeType ?: "application/octet-stream")
    val quotedFilename = JSONObject.quote(filename)
    val quotedTransferId = JSONObject.quote(transferId)

    val js = """
        (async function() {
            const bridge = ${BlobInterface.NAME};
            const blobUrl = $quotedBlobUrl;
            const mimeType = $quotedMimeType;
            const filename = $quotedFilename;
            const transferId = $quotedTransferId;

            // Keep each bridge call comfortably small.
            const CHUNK_SIZE = 256 * 1024;

            function readChunkAsBase64(blob) {
                return new Promise(function(resolve, reject) {
                    const reader = new FileReader();

                    reader.onload = function() {
                        try {
                            const result = reader.result;

                            if (typeof result !== 'string') {
                                reject(new Error('Unexpected FileReader result'));
                                return;
                            }

                            const comma = result.indexOf(',');
                            resolve(comma >= 0 ? result.substring(comma + 1) : result);
                        } catch (e) {
                            reject(e);
                        }
                    };

                    reader.onerror = function() {
                        reject(reader.error || new Error('FileReader failed'));
                    };

                    reader.readAsDataURL(blob);
                });
            }

            try {
                let blob = null;

                try {
                    const response = await fetch(blobUrl);

                    if (!response.ok) {
                        throw new Error(
                            'Blob fetch returned HTTP ' + response.status
                        );
                    }

                    blob = await response.blob();
                } catch (e) {
                    blob = window.__${Constants.APP_SCHEME}_lastBlob || null;
                }

                if (!blob) {
                    throw new Error('Blob fetch failed');
                }

                if (!bridge.startDownload(
                    transferId,
                    mimeType,
                    filename
                )) {
                    throw new Error('Unable to create download file');
                }

                for (
                    let offset = 0;
                    offset < blob.size;
                    offset += CHUNK_SIZE
                ) {
                    const end = Math.min(
                        offset + CHUNK_SIZE,
                        blob.size
                    );

                    const chunk = blob.slice(offset, end);

                    const base64Chunk =
                        await readChunkAsBase64(chunk);

                    if (!bridge.appendChunk(
                        transferId,
                        base64Chunk
                    )) {
                        throw new Error(
                            'Failed writing download chunk'
                        );
                    }
                }

                if (!bridge.finishDownload(transferId)) {
                    throw new Error(
                        'Failed finalising download'
                    );
                }

                // Your hook keeps a strong reference to the most recently
                // created blob. Release it after the file has been written.
                window.__${Constants.APP_SCHEME}_lastBlob = null;

            } catch (e) {
                try {
                    bridge.abortDownload(transferId);
                } catch (_) {}

                bridge.error(
                    'Blob download failed: ' +
                    (e && e.message ? e.message : String(e))
                );
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(js, null)
}

private fun generateBlobFilename(mimeType: String?): String {
    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType)
        ?: "bin"
    return "download_${System.currentTimeMillis()}.$extension"
}
