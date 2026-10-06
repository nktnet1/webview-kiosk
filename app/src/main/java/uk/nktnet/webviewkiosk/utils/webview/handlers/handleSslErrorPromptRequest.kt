package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup.LayoutParams
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.webview.WebViewDialogController

@SuppressLint("SetTextI18n")
fun handleSslErrorPromptRequest(
    context: Context,
    handler: SslErrorHandler?,
    error: SslError?,
    dialogs: WebViewDialogController,
) {
    var resolved = false
    fun respond(proceed: Boolean = false) {
        if (resolved) {
            return
        }
        resolved = true
        try {
            if (proceed && dialogs.isActive()) {
                handler?.proceed()
            } else {
                handler?.cancel()
            }
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView SSL request", e)
        }
    }
    if (!dialogs.isActive()) {
        respond()
        return
    }

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(60, 60, 60, 0)
    }

    val titleView = TextView(context).apply {
        text = "SSL Certificate Error"
        textSize = 22f
        setPadding(0, 0, 0, 30)
    }
    layout.addView(titleView)

    val errorDescription = when (error?.primaryError) {
        SslError.SSL_EXPIRED -> "The certificate has expired."
        SslError.SSL_IDMISMATCH -> "The certificate Hostname mismatch."
        SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted."
        SslError.SSL_NOTYETVALID -> "The certificate is not yet valid."
        else -> "Unknown SSL error."
    }

    val messageView = TextView(context).apply {
        text = """
            $errorDescription

            URL:
                ${error?.url}
        """.trimIndent()
        setPadding(0, 0, 0, 30)
    }
    layout.addView(messageView)

    val buttonsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
    }

    val prompt = AlertDialog.Builder(context)
        .setView(layout)
        .setNegativeButton("Cancel") { _, _ ->
            respond()
        }
        .setOnCancelListener {
            respond()
        }
        .create()
    val dialog = dialogs.show(prompt) {
        respond()
        UserInteractionStateSingleton.onUserInteraction()
    } ?: return

    val proceedButton = Button(context).apply { text = "Proceed" }
    proceedButton.setOnClickListener {
        respond(true)
        dialogs.dismiss(dialog)
    }

    val advancedButton = Button(context).apply { text = "Advanced" }
    buttonsLayout.addView(advancedButton)
    layout.addView(buttonsLayout)

    var advancedVisible = false
    val confirmTextView = TextView(context).apply {
        text = "WARNING: Proceeding may compromise your security!"
        setPadding(0, 20, 0, 20)
    }
    advancedButton.setOnClickListener {
        UserInteractionStateSingleton.onUserInteraction()
        advancedVisible = !advancedVisible
        if (advancedVisible) {
            layout.addView(confirmTextView)
            layout.addView(proceedButton)
        } else {
            layout.removeView(confirmTextView)
            layout.removeView(proceedButton)
        }
    }

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}
