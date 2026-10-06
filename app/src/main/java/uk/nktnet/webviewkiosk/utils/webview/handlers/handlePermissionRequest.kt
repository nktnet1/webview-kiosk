package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.PermissionRequest
import androidx.appcompat.app.AlertDialog
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.handleKeyEvent
import uk.nktnet.webviewkiosk.utils.hasPermissionForResource
import uk.nktnet.webviewkiosk.utils.webview.WebViewDialogController

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
    userSettings: UserSettings,
    dialogs: WebViewDialogController,
) {
    var resolved = false
    fun respond(resources: Array<String>? = null): Boolean {
        if (resolved) {
            return false
        }
        resolved = true
        val granted = resources != null && dialogs.isActive()
        return try {
            if (granted) {
                request.grant(resources)
            } else {
                request.deny()
            }
            granted
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView permission request", e)
            false
        }
    }
    if (!dialogs.isActive()) {
        respond()
        return
    }
    val host = request.origin.toString().trimEnd('/')

    val permissions = request.resources.mapNotNull { res ->
        when (res) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                WebPermission(
                    res, "Camera",
                    userSettings.allowCamera && hasPermissionForResource(context, res)
                )
            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                WebPermission(res,
                    "Microphone",
                    userSettings.allowMicrophone && hasPermissionForResource(context, res))

            else -> null
        }
    }

    val allowedPermissions = permissions.filter { it.allowed }
    val blockedPermissions = permissions.filter { !it.allowed }
    val unhandledPermissions = request.resources.filter { res ->
        res != PermissionRequest.RESOURCE_VIDEO_CAPTURE
        && res != PermissionRequest.RESOURCE_AUDIO_CAPTURE
    }

    if (allowedPermissions.isEmpty() && blockedPermissions.isEmpty()) {
        respond()
        return
    }

    val isBlockedDialog = blockedPermissions.isNotEmpty()

    val remembered = systemSettings.getSitePermissions(host)
    if (!isBlockedDialog && permissions.all { remembered.contains(it.resource) }) {
        respond(permissions.map { it.resource }.toTypedArray())
        return
    }

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
            append(
                "However, these permissions are either disabled in settings or not yet granted to ${context.getString(R.string.app_name)}."
            )
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
        .setOnCancelListener {
            respond()
        }

    if (isBlockedDialog) {
        builder.setPositiveButton("Close") { _, _ ->
            respond()
        }
    } else {
        val checkBox = CheckBox(context).apply { text = "Remember my choice" }
        checkBox.setOnClickListener {
            UserInteractionStateSingleton.onUserInteraction()
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 20, 40, 0)
            addView(checkBox)
        }
        builder.setView(layout)
            .setPositiveButton("Allow") { _, _ ->
                val granted = respond(allowedPermissions.map { it.resource }.toTypedArray())
                if (granted && checkBox.isChecked) {
                    allowedPermissions.forEach {
                        systemSettings.saveSitePermissions(host, it.resource)
                    }
                }
            }
            .setNegativeButton("Deny") { _, _ ->
                respond()
            }
    }

    val dialog = dialogs.show(builder.create(), key = request) { resolveRequest ->
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
