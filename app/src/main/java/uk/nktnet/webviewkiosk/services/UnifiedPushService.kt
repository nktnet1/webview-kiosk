package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        UnifiedPushManager.handleMessage(this, message, instance)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        UnifiedPushManager.handleNewEndpoint(this, endpoint, instance)
    }

    override fun onUnregistered(instance: String) {
        UnifiedPushManager.handleUnregistered(this, instance)
    }

    override fun onTempUnavailable(instance: String) {
        UnifiedPushManager.handleTempUnavailable(this, instance)

    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        UnifiedPushManager.handleRegistrationFailed(this, reason, instance)
    }
}
