package uk.nktnet.webviewkiosk.config.unifiedpush

import kotlinx.serialization.Serializable
import org.unifiedpush.android.connector.data.PushEndpoint

@Serializable
data class UnifiedPushPublicKeySet(
    val auth: String,
    val pubKey: String,
)

@Serializable
data class UnifiedPushEndpoint(
    val pubKeySet: UnifiedPushPublicKeySet?,
    val url: String,
    val temporary: Boolean,
    val redacted: Boolean,
) {
    companion object {
        fun fromPushEndpoint(
            endpoint: PushEndpoint,
            redacted: Boolean,
        ): UnifiedPushEndpoint {
            return UnifiedPushEndpoint(
                pubKeySet = endpoint.pubKeySet?.let {
                    UnifiedPushPublicKeySet(it.auth, it.pubKey)
                },
                url = endpoint.url,
                temporary = endpoint.temporary,
                redacted = redacted,
            )
        }

        fun createRedactEndpoint(temporary: Boolean): UnifiedPushEndpoint {
            val text = "(redacted)"
            return UnifiedPushEndpoint(
                pubKeySet = UnifiedPushPublicKeySet(text, text),
                url = text,
                temporary = temporary,
                redacted = true,
            )
        }
    }
}
