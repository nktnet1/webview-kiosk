package uk.nktnet.webviewkiosk.handlers

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundClearCacheCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundClearHistoryCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundErrorRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLaunchPackageCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLaunchablePackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLockDeviceCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundLockTaskPackagesRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundNotifyCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundReconnectCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSystemInfoRequest
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundToastCommand
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.managers.AppFlowManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.getStatus
import uk.nktnet.webviewkiosk.utils.getSystemInfo
import uk.nktnet.webviewkiosk.utils.openPackage
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings
import uk.nktnet.webviewkiosk.utils.wakeScreen
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

object MqttHandler {
    fun handleInboundCommand(
        context: Context,
        command: InboundCommandMessage,
    ) {
        val userSettings = UserSettings(context)
        val systemSettings = SystemSettings(context)

        if (command.interact) {
            UserInteractionStateSingleton.onUserInteraction()
        }
        if (command.wakeScreen) {
            wakeScreen(context)
        }
        when (command) {
            is InboundReconnectCommand -> {
                MqttManager.disconnect(
                    cause = OutboundDisconnectingEvent.DisconnectCause.MQTT_RECONNECT_COMMAND_RECEIVED,
                    onDisconnected = {
                        MqttManager.connect(context.applicationContext)
                    }
                )
            }
            is InboundClearHistoryCommand -> {
                WebViewNavigation.clearHistory(systemSettings)
            }
            is InboundClearCacheCommand -> {
                Handler(Looper.getMainLooper()).post {
                    try {
                        WebView(context).clearCache(true)
                    } catch (e: Exception) {
                        ToastManager.show(
                            context,
                            context.getString(R.string.settings_more_action_toast_cache_clear_failed)
                        )
                        Log.e(
                            javaClass.simpleName,
                            "Failed to clear webView cache",
                            e
                        )
                    }
                }
            }
            is InboundToastCommand -> {
                if (!command.data?.message.isNullOrEmpty()) {
                    ToastManager.show(context, command.data.message)
                }
            }
            is InboundLockDeviceCommand -> {
                if (DeviceOwnerManager.hasOwnerPermission(context)) {
                    try {
                        DeviceOwnerManager.DPM.lockNow()
                    } catch (e: Exception) {
                        Log.e(javaClass.simpleName, "Failed to lock device", e)
                        ToastManager.show(
                            context,
                            "Failed to lock device: ${e.message}"
                        )
                    }
                }
            }
            is InboundNotifyCommand -> {
                if (userSettings.allowNotifications) {
                    CustomNotificationManager.sendInboundNotifyCommandNotification(
                        context,
                        command,
                    )
                }
            }
            is InboundLaunchPackageCommand -> {
                openPackage(
                    context,
                    command.data.packageName,
                    normaliseActivityName(
                        command.data.packageName,
                        command.data.activityName,
                    ),
                )
            }
            else -> Unit
        }
    }

    fun handleInboundSettings(
        context: Context,
        settings: InboundSettingsMessage,
    ) {
        val userSettings = UserSettings(context)
        userSettings.importJson(settings.data.settings)

        if (settings.reloadActivity) {
            updateDeviceSettings(context)
        }

        if (settings.showToast) {
            /**
             * NOTE: reload action will be handled in main activity
             */
            val action = if (settings.reloadActivity) {
                "applied"
            } else {
                "received"
            }
            ToastManager.show(context, "Remote: settings $action.")
        }
    }

    fun handleInboundMqttRequest(
        context: Context,
        request: InboundRequestMessage,
    ) {
        val userSettings = UserSettings(context)
        when (request) {
            is InboundStatusRequest -> {
                MqttManager.publishStatusResponse(
                    request, getStatus(context)
                )
            }
            is InboundSettingsRequest -> {
                val settings = userSettings.exportJson()
                MqttManager.publishSettingsResponse(request, settings)
            }
            is InboundSystemInfoRequest -> {
                MqttManager.publishSystemInfoResponse(
                    request,
                    getSystemInfo(context),
                )
            }
            is InboundLaunchablePackagesRequest -> {
                MqttManager.publishLaunchablePackagesResponse(
                    request,
                    AppFlowManager
                        .getLaunchablePackageNames(
                            context,
                            request.data.filterLockTaskPermitted
                        ).sorted(),
                )
            }
            is InboundLockTaskPackagesRequest -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MqttManager.publishLockTaskPermittedPackagesResponse(
                        request,
                        AppFlowManager.getLockTaskPackageNames(
                            context,
                        ).sorted(),
                    )
                }
            }
            is InboundErrorRequest -> {
                ToastManager.show(
                    context,
                    "MQTT: invalid request. See debug logs."
                )
                MqttManager.publishErrorResponse(request)
            }
        }
    }
}

fun normaliseActivityName(packageName: String, activityName: String?): String? {
    if (activityName.isNullOrBlank()) {
        return null
    }
    return when {
        activityName.startsWith(packageName) -> activityName
        activityName.startsWith(".") -> packageName + activityName
        else -> "$packageName.$activityName"
    }
}
