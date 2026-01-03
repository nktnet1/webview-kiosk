package uk.nktnet.webviewkiosk.handlers

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttClearHistoryCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttClearCacheCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttCommandMessage
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttDisconnectingEvent
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttErrorRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttLaunchPackageCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttLaunchablePackagesRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttLockDeviceCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttLockTaskPackagesRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttNotifyCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttReconnectCommand
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttRequestMessage
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttSettingsMessage
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttSettingsRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttStatusRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttSystemInfoRequest
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttToastCommand
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
    fun handleMqttCommand(
        context: Context,
        command: MqttCommandMessage,
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
            is MqttReconnectCommand -> {
                MqttManager.disconnect(
                    cause = MqttDisconnectingEvent.DisconnectCause.MQTT_RECONNECT_COMMAND_RECEIVED,
                    onDisconnected = {
                        MqttManager.connect(context.applicationContext)
                    }
                )
            }
            is MqttClearHistoryCommand -> {
                WebViewNavigation.clearHistory(systemSettings)
            }
            is MqttClearCacheCommand -> {
                Handler(Looper.getMainLooper()).post {
                    try {
                        WebView(context).clearCache(true)
                    } catch (e: Exception) {
                        ToastManager.show(
                            context,
                            context.getString(R.string.settings_more_action_toast_cache_clear_failed)
                        )
                        e.printStackTrace()
                    }
                }
            }
            is MqttToastCommand -> {
                if (!command.data?.message.isNullOrEmpty()) {
                    ToastManager.show(context, command.data.message)
                }
            }
            is MqttLockDeviceCommand -> {
                if (DeviceOwnerManager.hasOwnerPermission(context)) {
                    try {
                        DeviceOwnerManager.DPM.lockNow()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ToastManager.show(
                            context,
                            "Failed to lock device: ${e.message}"
                        )
                    }
                }
            }
            is MqttNotifyCommand -> {
                if (userSettings.allowNotifications) {
                    CustomNotificationManager.sendMqttNotifyCommandNotification(
                        context,
                        command,
                    )
                }
            }
            is MqttLaunchPackageCommand -> {
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

    fun handleMqttSettings(
        context: Context,
        settings: MqttSettingsMessage,
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
            ToastManager.show(context, "MQTT: settings $action.")
        }
    }

    fun handleMqttRequest(
        context: Context,
        request: MqttRequestMessage,
    ) {
        val userSettings = UserSettings(context)
        when (request) {
            is MqttStatusRequest -> {
                MqttManager.publishStatusResponse(
                    request, getStatus(context)
                )
            }
            is MqttSettingsRequest -> {
                val settings = userSettings.exportJson()
                MqttManager.publishSettingsResponse(request, settings)
            }
            is MqttSystemInfoRequest -> {
                MqttManager.publishSystemInfoResponse(
                    request,
                    getSystemInfo(context),
                )
            }
            is MqttLaunchablePackagesRequest -> {
                MqttManager.publishLaunchablePackagesResponse(
                    request,
                    AppFlowManager
                        .getLaunchablePackageNames(
                            context,
                            request.data.filterLockTaskPermitted
                        ).sorted(),
                )
            }
            is MqttLockTaskPackagesRequest -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MqttManager.publishLockTaskPermittedPackagesResponse(
                        request,
                        AppFlowManager.getLockTaskPackageNames(
                            context,
                        ).sorted(),
                    )
                }
            }
            is MqttErrorRequest -> {
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
