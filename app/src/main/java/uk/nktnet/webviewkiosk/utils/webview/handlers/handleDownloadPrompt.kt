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
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.handleKeyEvent

@SuppressLint("SetTextI18n")
fun handleDownloadPrompt(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
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

    val downloadsDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).absolutePath.let {
        if (it.endsWith("/")) it else "$it/"
    }
    val infoText = TextView(context).apply {
        text = downloadsDir
        textSize = 12f
        setTypeface(typeface, Typeface.ITALIC)
        setPadding(10, 10, 10, 0)
    }
    layout.addView(infoText)

    val suggestedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
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
            dialog.dismiss()
        } catch (e: Exception) {
            ToastManager.show(context, "Error: ${e.message}")
            Log.e(Constants.APP_SCHEME, "Download failed", e)
        }
    }

    buttonsLayout.addView(cancelButton)
    buttonsLayout.addView(downloadButton)
    layout.addView(buttonsLayout)

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}
