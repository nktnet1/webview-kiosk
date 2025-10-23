package uk.nktnet.webviewkiosk.utils.webview

import android.annotation.SuppressLint
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AlertDialog
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.hasPermissionForResource

@SuppressLint("SetTextI18n")
fun handleGeolocationRequest(
    context: Context,
    origin: String,
    callback: GeolocationPermissions.Callback?,
    systemSettings: SystemSettings,
    userSettings: UserSettings
) {
    val isALlowed = userSettings.allowLocation && hasPermissionForResource(context, Constants.GEOLOCATION_RESOURCE)
    if (!isALlowed) {
        AlertDialog.Builder(context)
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
        return
    }

    val remembered = systemSettings.getSitePermissions(origin)

    if (remembered.contains(Constants.GEOLOCATION_RESOURCE)) {
        callback?.invoke(origin, true, false)
        return
    }

    val checkBox = CheckBox(context).apply { text = "Remember my choice" }
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(80, 20, 40, 0)
        addView(checkBox)
    }

    AlertDialog.Builder(context)
        .setTitle("Permission request")
        .setMessage("$origin is requesting access to your location")
        .setView(layout)
        .setPositiveButton("Allow") { _, _ ->
            callback?.invoke(origin, true, false)
            if (checkBox.isChecked) systemSettings.saveSitePermissions(origin, Constants.GEOLOCATION_RESOURCE)
        }
        .setNegativeButton("Deny") { _, _ ->
            callback?.invoke(origin, false, false)
        }
        .setOnCancelListener { callback?.invoke(origin, false, false) }
        .show()
}
