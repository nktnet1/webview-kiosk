package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandJsonParser
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushEndpoint
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushVariableName
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.isPackageInstalled
import uk.nktnet.webviewkiosk.utils.isValidVapidPublicKey
import uk.nktnet.webviewkiosk.utils.replaceVariables
import java.util.ArrayDeque
import java.util.Date

data class UnifiedPushLogEntry(
    val timestamp: Date,
    val tag: String,
    val message: String?,
)

object UnifiedPushManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val logHistory = ArrayDeque<UnifiedPushLogEntry>(100)
    private val _debugLog = MutableSharedFlow<UnifiedPushLogEntry>(extraBufferCapacity = 100)
    val debugLog: SharedFlow<UnifiedPushLogEntry> get() = _debugLog

    fun addDebugLog(tag: String, message: String? = null) {
        val logEntry = UnifiedPushLogEntry(Date(), tag, message)
        synchronized(logHistory) {
            if (logHistory.size >= 100) {
                logHistory.removeFirst()
            }
            logHistory.addLast(logEntry)
        }
        scope.launch {
            _debugLog.emit(logEntry)
        }
    }

    val debugLogHistory: List<UnifiedPushLogEntry>
        get() = synchronized(logHistory) { logHistory.toList() }

    fun clearLogs() {
        synchronized(logHistory) {
            logHistory.clear()
        }
    }

    fun getSavedDistributor(context: Context): String? {
        return UnifiedPush.getSavedDistributor(context)
    }

    fun getAckDistributor(context: Context): String? {
        return UnifiedPush.getAckDistributor(context)
    }

    fun saveDistributor(context: Context, distributor: String) {
        try {
            UnifiedPush.saveDistributor(context, distributor)
            addDebugLog("saved success", "distributor: $distributor")
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to save distributor", e)
            addDebugLog("saved failed", "distributor: $distributor")
        }
    }

    fun register(
        context: Context
    ) {
        val userSettings = UserSettings(context)
        val instance = getInstance(context)

        if (userSettings.unifiedPushDistributor.isBlank()) {
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=${userSettings.unifiedPushMessageForDistributor}
                error: distributor cannot be blank
                """.trimIndent()
            )
            ToastManager.show(context, "Error: distributor cannot be blank")
            return
        }

        if (!isPackageInstalled(context, userSettings.unifiedPushDistributor)) {
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=${userSettings.unifiedPushMessageForDistributor}
                error: distributor is not installed (${userSettings.unifiedPushDistributor})
                """.trimIndent()
            )
            ToastManager.show(context, "Error: distributor is not installed")
            return
        }
        if (
            userSettings.unifiedPushVapidPublicKey.isNotEmpty()
            && !isValidVapidPublicKey(userSettings.unifiedPushVapidPublicKey)
        ) {
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=${userSettings.unifiedPushMessageForDistributor}
                error: distributor is not installed (${userSettings.unifiedPushDistributor})
                """.trimIndent()
            )
            ToastManager.show(context, "Error: invalid VAPID public key")
            return
        }

        try {
            saveDistributor(context, userSettings.unifiedPushDistributor)
            UnifiedPush.register(
                context,
                instance,
                userSettings.unifiedPushMessageForDistributor.takeIf {
                    it.isNotBlank()
                },
                userSettings.unifiedPushVapidPublicKey.takeIf {
                    it.isNotBlank()
                }
            )
            addDebugLog(
                "registered",
                """
                instance=$instance
                messageForDistributor=${userSettings.unifiedPushMessageForDistributor}
                """.trimIndent()

            )
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to register distributor", e)
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=${userSettings.unifiedPushMessageForDistributor}
                error: $e
                """.trimIndent()
            )
        }
    }

    fun unregister(context: Context) {
        val instance = getInstance(context)
        try {
            UnifiedPush.unregister(context, instance)
            val systemSettings = SystemSettings(context)
            systemSettings.unifiedpushEndpoint = null
            addDebugLog(
                "unregistered",
                """
                instance: $instance
                """.trimIndent()
            )
        } catch (e: Exception) {
            addDebugLog(
                "unregister error",
                """
                instance: $instance
                error: $e
                """.trimIndent()
            )
        }
    }

    fun getInstance(context: Context): String {
        val userSettings = UserSettings(context)
        val systemSettings = SystemSettings(context)
        val instance = if (userSettings.unifiedPushInstance.isNotEmpty()) {
            replaceVariables(
                userSettings.unifiedPushInstance,
                mapOf(
                    UnifiedPushVariableName.APP_INSTANCE_ID.name to systemSettings.appInstanceId
                )
            )
        } else {
            INSTANCE_DEFAULT
        }
        return instance
    }

    // ===================================================================== //

    fun handleMessage(context: Context, message: PushMessage, instance: String) {
        val userSettings = UserSettings(context)
        val contentString = message.content.toString(Charsets.UTF_8)

        if (!userSettings.unifiedPushEnabled) {
            addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                decrypted: ${message.decrypted}
                content: $contentString

                Reason:
                - UnifiedPush is not enabled.
                """.trimIndent()
            )
            return
        }

        if (instance != getInstance(context)) {
            addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                decrypted: ${message.decrypted}
                content: $contentString

                Reason:
                - Instance mismatch: '$instance' instead of ${userSettings.unifiedPushInstance}
                """.trimIndent()
            )
            return
        }
        if (!(message.decrypted || userSettings.unifiedPushProcessUnencryptedMessages)) {
            addDebugLog(
                "message received (ignored)",
                """
                instance: $instance
                decrypted: ${false}
                content: $contentString

                Reason:
                - message failed to decrypt or was unencrypted
                """.trimIndent()
            )
            return
        }
        addDebugLog(
            "message received",
            """
            instance: $instance
            decrypted: ${message.decrypted}
            content: $contentString
            """.trimIndent()
        )

        if (contentString.isNotEmpty()) {
            runCatching { JSONObject(contentString) }.onSuccess { json ->
                when (val type = json.optString("type")) {
                    "command" -> {
                        RemoteMessageManager.emitCommand(
                            InboundCommandJsonParser.decodeFromString(contentString)
                        )
                    }
                    "settings" -> {
                        val settingsMessage = runCatching {
                            BaseJson.decodeFromString<InboundSettingsMessage>(contentString)
                        }.getOrElse {
                            InboundSettingsMessage()
                        }
                        RemoteMessageManager.emitSettings(settingsMessage)
                    }
                    else -> {
                        ToastManager.show(
                            context,
                            "UnifiedPush: unsupported message type: $type",
                        )
                    }
                }
            }
        }
    }

    fun handleNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        ToastManager.show(context, "UnifiedPush: new endpoint.")
        val systemSettings = SystemSettings(context)
        val userSettings = UserSettings(context)
        systemSettings.unifiedpushEndpoint = if (
            userSettings.unifiedPushStoreEndpointCredentials
        ) {
            UnifiedPushEndpoint.fromPushEndpoint(
                endpoint,
                redacted = false,
            )
        } else {
            UnifiedPushEndpoint.createRedactEndpoint(
                endpoint.temporary,
            )
        }
        addDebugLog(
            "new endpoint",
            """
            instance: $instance
            temporary: ${endpoint.temporary}
            url: ${endpoint.url}
            """.trimIndent()
        )
    }
    fun handleUnregistered(context: Context, instance: String) {
        val systemSettings = SystemSettings(context)
        systemSettings.unifiedpushEndpoint = null
        ToastManager.show(context, "UnifiedPush: unregistered called.")
        addDebugLog(
            "unregistered",
            "instance: $instance"
        )
    }

    fun handleTempUnavailable(context: Context, instance: String) {
        ToastManager.show(context, "UnifiedPush: temporarily unavailable.")
        addDebugLog(
            "temp unavailable",
            "instance: $instance"
        )
    }

    fun handleRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        ToastManager.show(context, "UnifiedPush: registration failed.")
        addDebugLog(
            "register failed",
            """
            instance: $instance
            reason: $reason
            """.trimIndent()
        )
    }
}
