package uk.nktnet.webviewkiosk.utils.webview

import android.net.http.SslError
import java.util.concurrent.atomic.AtomicBoolean

class SslErrorRequest(
    val error: SslError?,
    private val onProceed: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private val completed = AtomicBoolean(false)

    fun proceed() {
        if (completed.compareAndSet(false, true)) {
            onProceed()
        }
    }

    fun cancel() {
        if (completed.compareAndSet(false, true)) {
            onCancel()
        }
    }
}
