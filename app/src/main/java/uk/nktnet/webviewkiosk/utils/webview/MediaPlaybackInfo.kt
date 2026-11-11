package uk.nktnet.webviewkiosk.utils.webview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A bounded, untrusted snapshot from a playing WebView document. Times are
 * milliseconds.
 */
internal data class MediaPlaybackInfo(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = -1L,
    val positionMs: Long = -1L,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canSeek: Boolean = false,
    val canChangeSpeed: Boolean = false,
    val speed: Float = 1f,
) {
    /** Do not repaint the notification for each progress tick. */
    fun presentationChanged(other: MediaPlaybackInfo): Boolean =
        title != other.title ||
            artist != other.artist ||
            artworkUrl != other.artworkUrl ||
            durationMs != other.durationMs ||
            canPrevious != other.canPrevious ||
            canNext != other.canNext ||
            canSeek != other.canSeek ||
            canChangeSpeed != other.canChangeSpeed ||
            speed != other.speed

    companion object {
        private const val MAX_PAYLOAD_LENGTH = 8_192
        private const val MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000

        fun parse(payload: String): MediaPlaybackInfo? {
            if (payload.length > MAX_PAYLOAD_LENGTH) {
                return null
            }
            return try {
                val data = Json.parseToJsonElement(payload).jsonObject
                fun string(name: String, limit: Int): String =
                    (data[name]?.jsonPrimitive?.contentOrNull ?: "")
                        .filter { it >= ' ' }
                        .take(limit)
                        .trim()

                fun number(name: String): Long =
                    data[name]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.toDoubleOrNull()
                        ?.takeIf {
                            it.isFinite() && it >= 0.0 && it <= MAX_DURATION_MS
                        }
                        ?.toLong() ?: -1L

                fun flag(name: String): Boolean =
                    data[name]?.jsonPrimitive?.booleanOrNull == true

                val duration = number("durationMs")
                val position = number("positionMs")
                val rate =
                    data["speed"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.toFloatOrNull()
                        ?.takeIf { it.isFinite() && it in 0.25f..4f } ?: 1f
                MediaPlaybackInfo(
                    title = string("title", 200),
                    artist = string("artist", 160),
                    artworkUrl = string("artworkUrl", 1_024),
                    durationMs = duration,
                    positionMs =
                        if (duration in 1..<position) {
                            duration
                        } else {
                            position
                        },
                    canPrevious = flag("canPrevious"),
                    canNext = flag("canNext"),
                    canSeek = flag("canSeek") && duration > 0,
                    canChangeSpeed = flag("canChangeSpeed"),
                    speed = rate,
                )
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
