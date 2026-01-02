package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager
import uk.nktnet.webviewkiosk.utils.wakeScreen

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        ToastManager.show(this, "UnifiedPush: message received.")
        wakeScreen(this)
        UnifiedPushManager.addDebugLog(
            "message received",
            """
            instance: $instance
            message decrypted: ${message.decrypted}
            message content: ${message.content}
            """.trimIndent()
        )
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        ToastManager.show(this, "UnifiedPush: new endpoint.")
        if (!endpoint.temporary) {
            val systemSettings = SystemSettings(this)
            systemSettings.unifiedpushEndpointUrl = endpoint.url
        }
        UnifiedPushManager.addDebugLog(
            "new endpoint",
            """
            instance: $instance
            endpoint.url: ${endpoint.url}
            endpoint.temporary: ${endpoint.temporary}
            """.trimIndent()
        )
    }

    override fun onUnregistered(instance: String) {
        val systemSettings = SystemSettings(this)
        systemSettings.unifiedpushEndpointUrl = ""
        ToastManager.show(this, "UnifiedPush: unregistered called.")
        UnifiedPushManager.addDebugLog(
            "unregistered",
            "instance: $instance"
        )
    }

    override fun onTempUnavailable(instance: String) {
        ToastManager.show(this, "UnifiedPush: temporarily unavailable.")
        UnifiedPushManager.addDebugLog(
            "temp unavailable",
            "instance: $instance"
        )
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        ToastManager.show(this, "UnifiedPush: registration failed.")
        UnifiedPushManager.addDebugLog(
            "register failed",
            """
            instance: $instance
            reason: $reason
            """.trimIndent()
        )
    }
}
