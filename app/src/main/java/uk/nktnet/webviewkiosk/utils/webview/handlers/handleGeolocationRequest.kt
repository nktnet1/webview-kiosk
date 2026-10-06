package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AlertDialog
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.hasPermissionForResource
import uk.nktnet.webviewkiosk.utils.webview.WebViewDialogController

@SuppressLint("SetTextI18n")
fun handleGeolocationRequest(
    context: Context,
    origin: String,
    callback: GeolocationPermissions.Callback?,
    systemSettings: SystemSettings,
    userSettings: UserSettings,
    dialogs: WebViewDialogController,
) {
    var resolved = false
    fun respond(allow: Boolean = false): Boolean {
        if (resolved) {
            return false
        }
        resolved = true
        val granted = allow && dialogs.isActive()
        return try {
            callback?.invoke(origin, granted, false)
            granted
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView location request", e)
            false
        }
    }
    if (!dialogs.isActive()) {
        respond()
        return
    }

    val isAllowed = (
        userSettings.allowLocation
        && hasPermissionForResource(context, Constants.GEOLOCATION_RESOURCE)
    )
    if (!isAllowed) {
        val prompt = AlertDialog.Builder(context)
            .setTitle("Permission blocked")
            .setMessage(
                """
                $origin requested access to your location.

                However, location permission is either disabled in settings or
                not yet granted to ${context.getString(R.string.app_name)}.
                """.trimIndent()
            )
            .setPositiveButton("Close") { _, _ -> respond() }
            .create()
        val dialog = dialogs.show(prompt, WebViewDialogController.GEOLOCATION_PROMPT) { resolveRequest ->
            if (resolveRequest) {
                respond()
            } else {
                resolved = true
            }
            UserInteractionStateSingleton.onUserInteraction()
        } ?: return
        dialog.setOnKeyListener { _, _, event ->
            handleKeyEvent(context, event)
        }
        return
    }

    val remembered = systemSettings.getSitePermissions(origin)

    if (remembered.contains(Constants.GEOLOCATION_RESOURCE)) {
        respond(true)
        return
    }

    val checkBox = CheckBox(context).apply { text = "Remember my choice" }
    checkBox.setOnClickListener {
        UserInteractionStateSingleton.onUserInteraction()
    }

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(80, 20, 40, 0)
        addView(checkBox)
    }

    val prompt = AlertDialog.Builder(context)
        .setTitle("Permission request")
        .setMessage("$origin is requesting access to your location")
        .setView(layout)
        .setPositiveButton("Allow") { _, _ ->
            val granted = respond(true)
            if (granted && checkBox.isChecked) {
                systemSettings.saveSitePermissions(origin, Constants.GEOLOCATION_RESOURCE)
            }
        }
        .setNegativeButton("Deny") { _, _ ->
            respond()
        }
        .setOnCancelListener {
            respond()
        }
        .create()

    val dialog = dialogs.show(prompt, WebViewDialogController.GEOLOCATION_PROMPT) { resolveRequest ->
        if (resolveRequest) {
            respond()
        } else {
            resolved = true
        }
        UserInteractionStateSingleton.onUserInteraction()
    } ?: return

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}
