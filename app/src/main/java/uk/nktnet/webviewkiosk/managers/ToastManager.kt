package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.os.Handler
import android.widget.Toast

object ToastManager {
    private var toastRef: Toast? = null

    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            Handler(context.mainLooper).post {
                toastRef?.cancel()
                toastRef = Toast.makeText(
                    context,
                    text,
                    duration,
                ).apply {
                    show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancel() {
        toastRef?.cancel()
    }
}
