package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushEndpoint
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager
import uk.nktnet.webviewkiosk.utils.wakeScreen

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        val userSettings = UserSettings(this)
        val contentString = message.content.toString(Charsets.UTF_8)

        if (!userSettings.unifiedPushEnabled) {
            UnifiedPushManager.addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                message decrypted: ${message.decrypted}
                message content: $contentString

                Reason:
                - UnifiedPush is not enabled.
                """.trimIndent()
            )
            return
        }
        if (instance != userSettings.unifiedPushInstance) {
            UnifiedPushManager.addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                message decrypted: ${message.decrypted}
                message content: $contentString

                Reason:
                - Instance mismatch: '$instance' instead of ${userSettings.unifiedPushInstance}
                """.trimIndent()
            )
            return
        }
        if (!(message.decrypted || userSettings.unifiedPushProcessUnencryptedMessages)) {
            UnifiedPushManager.addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                message decrypted: ${false}
                message content: $contentString

                Reason:
                - message did not decrypt successfully
                """.trimIndent()
            )
            return
        }
        ToastManager.show(this, "UnifiedPush: message received.")
        wakeScreen(this)
        UnifiedPushManager.addDebugLog(
            "message received",
            """
            instance: $instance
            message decrypted: ${message.decrypted}
            message content: $contentString
            """.trimIndent()
        )
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        ToastManager.show(this, "UnifiedPush: new endpoint.")
        val systemSettings = SystemSettings(this)
        val userSettings = UserSettings(this)
        systemSettings.unifiedpushEndpoint = if (
                userSettings.unifiedPushRedactEndpointOnRegister
            ) {
                UnifiedPushEndpoint.createRedactEndpoint(
                    endpoint.temporary,
                )
            } else {
                UnifiedPushEndpoint.fromPushEndpoint(
                    endpoint,
                    redacted = false,
                )
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
        systemSettings.unifiedpushEndpoint = null
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
