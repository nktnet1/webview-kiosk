package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushVariableName
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
            systemSettings.unifiedpushEndpointUrl = ""
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

    private fun getInstance(context: Context): String {
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
}
