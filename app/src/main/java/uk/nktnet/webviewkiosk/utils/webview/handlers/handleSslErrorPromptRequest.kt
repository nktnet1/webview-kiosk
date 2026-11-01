package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.webkit.SslErrorHandler
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.webview.SslErrorRequest
import uk.nktnet.webviewkiosk.utils.webview.WebViewDialogController

@SuppressLint("SetTextI18n")
fun handleSslErrorPromptRequest(
    context: Context,
    handler: SslErrorHandler?,
    error: SslError?,
    dialogs: WebViewDialogController,
) =
    handleSslErrorPromptRequest(
        context,
        SslErrorRequest(error, { handler?.proceed() }, { handler?.cancel() }),
        dialogs,
    )

@SuppressLint("SetTextI18n")
fun handleSslErrorPromptRequest(
    context: Context,
    request: SslErrorRequest,
    dialogs: WebViewDialogController,
) {
    val error = request.error
    var resolved = false
    fun respond(proceed: Boolean = false) {
        if (resolved) {
            return
        }
        resolved = true
        try {
            if (proceed && dialogs.isActive()) {
                request.proceed()
            } else {
                request.cancel()
            }
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView SSL request", e)
        }
    }
    if (!dialogs.isActive()) {
        respond()
        return
    }

    val layout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 0)
        }

    val titleView =
        TextView(context).apply {
            text = context.getString(R.string.ui_ssl_certificate_error)
            textSize = 22f
            setPadding(0, 0, 0, 30)
        }
    layout.addView(titleView)

    val errorDescription =
        when (error?.primaryError) {
            SslError.SSL_EXPIRED -> context.getString(R.string.ui_ssl_expired)
            SslError.SSL_IDMISMATCH ->
                context.getString(R.string.ui_ssl_hostname_mismatch)
            SslError.SSL_UNTRUSTED -> context.getString(R.string.ui_ssl_ca_untrusted)
            SslError.SSL_NOTYETVALID ->
                context.getString(R.string.ui_ssl_not_yet_valid)
            else -> context.getString(R.string.ui_ssl_error_unknown)
        }

    val messageView =
        TextView(context).apply {
            text =
                context.getString(
                    R.string.ui_ssl_error_details,
                    errorDescription,
                    error?.url,
                )
            setPadding(0, 0, 0, 30)
        }
    layout.addView(messageView)

    val buttonsLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams =
                LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,
                )
        }

    val prompt =
        AlertDialog.Builder(context)
            .setView(layout)
            .setNegativeButton(context.getString(R.string.ui_cancel)) { _, _ ->
                respond()
            }
            .setOnCancelListener {
                respond()
            }
            .create()
    val dialog =
        dialogs.show(prompt) {
            respond()
            UserInteractionStateSingleton.onUserInteraction()
        } ?: return

    val proceedButton =
        Button(context).apply { text = context.getString(R.string.ui_ssl_proceed) }
    proceedButton.setOnClickListener {
        respond(true)
        dialogs.dismiss(dialog)
    }

    val advancedButton =
        Button(context).apply { text = context.getString(R.string.ui_ssl_advanced) }
    buttonsLayout.addView(advancedButton)
    layout.addView(buttonsLayout)

    var advancedVisible = false
    val confirmTextView =
        TextView(context).apply {
            text = context.getString(R.string.ui_ssl_proceed_warning)
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
