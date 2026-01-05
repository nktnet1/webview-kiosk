package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast

object ToastManager {
    private var toastRef: Toast? = null

    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        Handler(context.mainLooper).post {
            try {
                toastRef?.cancel()
                toastRef = Toast.makeText(
                    context,
                    text,
                    duration,
                ).apply {
                    show()
                }
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Failed to show toast", e)
            }
        }
    }

    fun cancel() {
        toastRef?.cancel()
    }
}
