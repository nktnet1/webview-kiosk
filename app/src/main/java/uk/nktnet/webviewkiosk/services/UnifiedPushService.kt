package uk.nktnet.webviewkiosk.services

import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushMessage
import org.unifiedpush.android.connector.data.PushEndpoint

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {

    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {

    }

    override fun onUnregistered(instance: String) {

    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {

    }
}
