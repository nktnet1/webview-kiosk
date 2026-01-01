package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        ToastManager.show(this, "$instance: ${message.content}")
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {

    }

    override fun onUnregistered(instance: String) {

    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        UnifiedPushManager.addDebugLog(
            "register failed",
            normaliseInfoText(
                """
                    reason: $reason
                    instance: $instance
                """.trimIndent()
            )
        )
    }
}
