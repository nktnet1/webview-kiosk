package uk.nktnet.webviewkiosk.utils.webview

import android.annotation.SuppressLint
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.PermissionRequest
import androidx.appcompat.app.AlertDialog
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings

data class WebPermission(
    val resource: String,
    val name: String,
    val allowed: Boolean
)

@SuppressLint("SetTextI18n")
fun handlePermissionRequest(
    context: Context,
    request: PermissionRequest,
    systemSettings: SystemSettings,
    userSettings: UserSettings
) {
    val host = request.origin.host.toString()

    val permissions = request.resources.mapNotNull { res ->
        when (res) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                WebPermission(res, "Camera", userSettings.allowCamera)
            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                WebPermission(res, "Microphone", userSettings.allowMicrophone)
            else -> null
        }
    }

    val remembered = systemSettings.getSitePermissions(host)

    if (permissions.all { remembered.contains(it.resource) }) {
        request.grant(permissions.map { it.resource }.toTypedArray())
        return
    }

    val allowedPermissions = permissions.filter { it.allowed }
    val blockedPermissions = permissions.filter { !it.allowed }
    val unhandledPermissions = request.resources.filter { res ->
        res != PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                res != PermissionRequest.RESOURCE_AUDIO_CAPTURE
    }

    if (allowedPermissions.isEmpty() && blockedPermissions.isEmpty()) {
        request.deny()
        return
    }

    val isBlockedDialog = blockedPermissions.isNotEmpty()
    val title: String
    val message: String

    if (isBlockedDialog) {
        title = "Permission blocked"
        message = buildString {
            appendLine("|$host requested access to:")
            appendLine()
            append("• ${blockedPermissions.joinToString("\n|• ") { it.name }}")
            if (unhandledPermissions.isNotEmpty()) {
                appendLine()
                appendLine()
                append("Unhandled permissions:")
                appendLine()
                append("• ${unhandledPermissions.joinToString("\n• ")}")
            }
            appendLine()
            appendLine()
            append("However, these permissions are disabled in ${Constants.APP_NAME}.")
        }
    } else {
        title = "Permission request"
        message = buildString {
            appendLine("|$host is requesting access to:")
            appendLine()
            append("• ${allowedPermissions.joinToString("\n|• ") { it.name }}")
            if (unhandledPermissions.isNotEmpty()) {
                appendLine()
                appendLine()
                append("Unhandled permissions:")
                appendLine()
                append("• ${unhandledPermissions.joinToString("\n• ")}")
            }
        }
    }

    val builder = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message.trimMargin())
        .setOnCancelListener { request.deny() }

    if (isBlockedDialog) {
        builder.setPositiveButton("Close") { _, _ -> request.deny() }
        builder.show()
    } else {
        val checkBox = CheckBox(context).apply { text = "Remember this choice." }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 20, 40, 0)
            addView(checkBox)
        }
        builder.setView(layout)
            .setPositiveButton("Allow") { _, _ ->
                request.grant(allowedPermissions.map { it.resource }.toTypedArray())
                if (checkBox.isChecked) {
                    allowedPermissions.forEach {
                        systemSettings.saveSitePermissions(host, it.resource)
                    }
                }
            }
            .setNegativeButton("Deny") { _, _ ->
                request.deny()
            }
            .show()
    }
}
