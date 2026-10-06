package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import uk.nktnet.webviewkiosk.config.Constants

class WebViewDialogController(context: Context) {
    private val activity = findActivity(context)
    private val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    private val dialogs = mutableMapOf<Any, PendingDialog>()
    private var disposed = false

    private class PendingDialog(val dialog: Dialog, val onDismiss: (Boolean) -> Unit)

    private val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            dispose()
        }
    }

    init {
        lifecycle?.addObserver(observer)
    }

    fun isActive(): Boolean = (
        !disposed
        && activity != null
        && !activity.isFinishing
        && !activity.isDestroyed
        && lifecycle?.currentState != Lifecycle.State.DESTROYED
    )

    fun <T : Dialog> show(
        dialog: T,
        key: Any = dialog,
        onDismiss: (resolveRequest: Boolean) -> Unit,
    ): T? {
        dismiss(key)
        if (!isActive()) {
            complete { onDismiss(true) }
            return null
        }

        val pending = PendingDialog(dialog, onDismiss)
        dialogs[key] = pending
        dialog.setOnDismissListener {
            if (dialogs[key] === pending) {
                dialogs.remove(key)
                complete { onDismiss(true) }
            }
        }
        try {
            dialog.show()
            return dialog
        } catch (e: WindowManager.BadTokenException) {
            Log.w(Constants.APP_SCHEME, "Unable to show WebView dialog", e)
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.w(Constants.APP_SCHEME, "Unable to show WebView dialog", e)
        } catch (e: IllegalStateException) {
            Log.w(Constants.APP_SCHEME, "Unable to show WebView dialog", e)
        }
        dismiss(key)
        return null
    }

    fun dismiss(key: Any, resolveRequest: Boolean = true) {
        // WebView cancellation hides the UI without calling the native request again.
        val pending = dialogs.remove(key) ?: return
        try {
            pending.dialog.dismiss()
        } catch (e: IllegalArgumentException) {
            Log.w(Constants.APP_SCHEME, "Unable to dismiss WebView dialog", e)
        } finally {
            complete { pending.onDismiss(resolveRequest) }
        }
    }

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        lifecycle?.removeObserver(observer)
        dialogs.keys.toList().forEach { dismiss(it) }
    }

    private fun complete(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Exception) {
            Log.w(Constants.APP_SCHEME, "Unable to complete WebView dialog request", e)
        }
    }

    companion object {
        val GEOLOCATION_PROMPT: Any = Any()

        private fun findActivity(context: Context): Activity? {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) {
                    return current
                }
                val base = current.baseContext
                if (base === current) {
                    return null
                }
                current = base
            }
            return current as? Activity
        }
    }
}
