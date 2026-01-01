package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        ToastManager.show(this, "$instance: ${message.content}")
        UnifiedPushManager.addDebugLog(
            "message",
            """
            instance: $instance
            """.trimIndent()
        )
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
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
        UnifiedPushManager.addDebugLog(
            "unregistered",
            "instance: $instance"
        )
    }

    override fun onTempUnavailable(instance: String) {
        UnifiedPushManager.addDebugLog(
            "temp unavailable",
            "instance: $instance"
        )
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        UnifiedPushManager.addDebugLog(
            "register failed",
            """
            instance: $instance
            reason: $reason
            """.trimIndent()
        )
    }
}
