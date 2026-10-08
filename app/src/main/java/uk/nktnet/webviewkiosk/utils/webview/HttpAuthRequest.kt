package uk.nktnet.webviewkiosk.utils.webview

import java.util.concurrent.atomic.AtomicBoolean

class HttpAuthRequest(
    val host: String?,
    val realm: String?,
    private val onSubmit: (String, String) -> Unit,
    private val onCancel: () -> Unit
) {
    private val completed = AtomicBoolean(false)

    fun proceed(username: String, password: String) {
        if (completed.compareAndSet(false, true)) {
            onSubmit(username, password)
        }
    }

    fun cancel() {
        if (completed.compareAndSet(false, true)) {
            onCancel()
        }
    }
}
