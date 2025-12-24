package com.nktnet.webview_kiosk.utils.webview.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.webkit.PermissionRequest
import androidx.appcompat.app.AlertDialog
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton
import com.nktnet.webview_kiosk.utils.handleKeyEvent
import com.nktnet.webview_kiosk.utils.hasPermissionForResource

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
        request.deny()
        return
    }

    val isBlockedDialog = blockedPermissions.isNotEmpty()

    val remembered = systemSettings.getSitePermissions(host)
    if (!isBlockedDialog && permissions.all { remembered.contains(it.resource) }) {
        request.grant(permissions.map { it.resource }.toTypedArray())
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
            append("However, these permissions are either disabled in settings or not yet granted to ${Constants.APP_NAME}.")
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
            request.deny()
        }
        .setOnDismissListener {
            UserInteractionStateSingleton.onUserInteraction()
        }

    if (isBlockedDialog) {
        builder.setPositiveButton("Close") { _, _ ->
            request.deny()
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
    }

    val dialog = builder.show()

    dialog.setOnKeyListener { _, _, event ->
        handleKeyEvent(context, event)
    }
}
