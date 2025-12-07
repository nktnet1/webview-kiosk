package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.widget.Toast

object ToastManager {
    private var toastRef: Toast? = null

    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        toastRef?.cancel()
        toastRef = Toast.makeText(
            context,
            text,
            duration,
        ).apply {
            show()
        }
    }

    fun cancel() {
        toastRef?.cancel()
    }
}
