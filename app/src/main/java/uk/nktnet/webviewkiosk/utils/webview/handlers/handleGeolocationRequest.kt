package com.nktnet.webview_kiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AlertDialog
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton
import com.nktnet.webview_kiosk.utils.handleKeyEvent
import com.nktnet.webview_kiosk.utils.hasPermissionForResource

@SuppressLint("SetTextI18n")
fun handleGeolocationRequest(
    context: Context,
    origin: String,
    callback: GeolocationPermissions.Callback?,
    systemSettings: SystemSettings,
    userSettings: UserSettings
) {
    val isAllowed = (
        userSettings.allowLocation
        && hasPermissionForResource(context, Constants.GEOLOCATION_RESOURCE)
    )
    if (!isAllowed) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Permission blocked")
            .setMessage(
                """
                $origin requested access to your location.

                However, location permission is either disabled in settings or
                not yet granted to ${Constants.APP_NAME}.
                """.trimIndent()
            )
            .setPositiveButton("Close") { _, _ -> callback?.invoke(origin, false, false) }
            .show()
        dialog.setOnKeyListener { _, _, event ->
            handleKeyEvent(context, event)
        }
        return
    }

    val remembered = systemSettings.getSitePermissions(origin)

    if (remembered.contains(Constants.GEOLOCATION_RESOURCE)) {
        callback?.invoke(origin, true, false)
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

    val dialog = AlertDialog.Builder(context)
        .setTitle("Permission request")
        .setMessage("$origin is requesting access to your location")
        .setView(layout)
        .setPositiveButton("Allow") { _, _ ->
            callback?.invoke(origin, true, false)
            if (checkBox.isChecked) {
                systemSettings.saveSitePermissions(origin, Constants.GEOLOCATION_RESOURCE)
            }
        }
        .setNegativeButton("Deny") { _, _ ->
            callback?.invoke(origin, false, false)
        }
        .setOnCancelListener {
            callback?.invoke(origin, false, false)
        }
        .setOnDismissListener {
            UserInteractionStateSingleton.onUserInteraction()
        }
        .show()

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}
