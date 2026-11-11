package uk.nktnet.webviewkiosk.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loads small, public HTTPS thumbnails; never sends WebView cookies or credentials.
 */
internal class MediaArtworkLoader {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var serial = 0

    fun load(url: String, pageHost: String?, onLoaded: (Bitmap?) -> Unit) {
        val generation = ++serial
        if (!isEligibleUrl(url, pageHost)) {
            onLoaded(null)
            return
        }
        worker.execute {
            val bitmap = retrieve(url)
            main.post {
                if (generation == serial) {
                    onLoaded(bitmap)
                }
            }
        }
    }

    fun cancel() {
        serial++
    }

    fun close() {
        cancel()
        worker.shutdownNow()
    }

    companion object {
        private const val MAX_BYTES = 1_048_576

        fun isEligibleUrl(value: String, pageHost: String?): Boolean {
            if (value.length > 1_024) {
                return false
            }
            val uri = Uri.parse(value)
            if (
                uri.scheme != "https" || uri.userInfo != null || uri.port !in -1..443
            ) {
                return false
            }
            if (uri.port != -1 && uri.port != 443) {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            return host == "i.ytimg.com" ||
                host == "img.youtube.com" ||
                host == "yt3.ggpht.com" ||
                (pageHost != null && host == pageHost.lowercase())
        }

        private fun retrieve(value: String): Bitmap? {
            val connection = (URL(value).openConnection() as HttpURLConnection)
            return try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 3_000
                connection.readTimeout = 3_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return null
                }
                if (!connection.contentType.orEmpty().startsWith("image/")) {
                    return null
                }
                if (connection.contentLength > MAX_BYTES) {
                    return null
                }
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val chunk = ByteArray(8_192)
                    while (true) {
                        val length = input.read(chunk)
                        if (length < 0) {
                            break
                        }
                        if (bytes.size() + length > MAX_BYTES) {
                            return null
                        }
                        bytes.write(chunk, 0, length)
                    }
                }
                val image = bytes.toByteArray()
                val bounds =
                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
                if (
                    bounds.outWidth <= 0 ||
                        bounds.outHeight <= 0 ||
                        bounds.outWidth > 8_192 ||
                        bounds.outHeight > 8_192
                ) {
                    return null
                }
                val sample =
                    generateSequence(1) { it * 2 }
                        .first {
                            bounds.outWidth / it <= 512 &&
                                bounds.outHeight / it <= 512
                        }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(image, 0, image.size, opts)
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }
    }
}
