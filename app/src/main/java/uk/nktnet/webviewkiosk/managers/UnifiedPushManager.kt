package uk.nktnet.webviewkiosk.managers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.isPackageInstalled
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
            e.printStackTrace()
            addDebugLog("saved failed", "distributor: $distributor")
        }
    }

    fun register(
        context: Context,
        instance: String = INSTANCE_DEFAULT,
        messageForDistributor: String? = null,
        vapid: String? = null
    ) {
        val userSettings = UserSettings(context)

        if (!isPackageInstalled(context, userSettings.unifiedPushDistributor)) {
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=$messageForDistributor
                error: distributor is not installed (${userSettings.unifiedPushDistributor})
                """.trimIndent()
            )
            return
        }

        try {
            saveDistributor(context, userSettings.unifiedPushDistributor)
            UnifiedPush.register(context, instance, messageForDistributor, vapid)
            addDebugLog(
                "registered",
                """
                instance=$instance
                messageForDistributor=$messageForDistributor
                """.trimIndent()

            )
        } catch (e: Exception) {
            e.printStackTrace()
            addDebugLog(
                "register error",
                """
                instance=$instance
                messageForDistributor=$messageForDistributor
                error: $e
                """.trimIndent()
            )
        }
    }

    fun unregister(context: Context) {
        try {
            UnifiedPush.unregister(context)
            val systemSettings = SystemSettings(context)
            systemSettings.unifiedpushEndpointUrl = ""
            addDebugLog(
                "unregistered",
            )
        } catch (e: Exception) {
            addDebugLog(
                "unregister error",
                """
                error: $e
                """.trimIndent()
            )
        }
    }
}
