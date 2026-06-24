package uk.nktnet.webviewkiosk.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import java.util.UUID

object IconUtils {

    fun buildLetterIcon(shortLabel: String): IconCompat {
        val safeLabel = shortLabel.trim()

        val iconTextDisplay = if (safeLabel.isEmpty()) {
            "?"
        } else if (safeLabel.length == 2) {
            safeLabel
        } else {
            safeLabel
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }.take(2)
                .joinToString("") {
                    it.first().uppercase()
                }
        }

        val size = 128
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val bgColor = generateColor(safeLabel)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = bgColor
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = if (iconTextDisplay.length > 1) 48f else 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(iconTextDisplay, size / 2f, yPos, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun generateColor(input: String): Int {
        val hash = UUID.nameUUIDFromBytes(input.toByteArray()).hashCode() and 0x7FFFFFFF
        val hue = (hash % 360).toFloat()
        val saturation = 0.75f + ((hash % 15) / 100f)
        val value = 0.30f + ((hash % 30) / 100f)
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
}
