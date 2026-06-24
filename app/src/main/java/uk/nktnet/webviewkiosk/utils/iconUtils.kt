package uk.nktnet.webviewkiosk.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat

object IconUtils {
    fun buildLetterIcon(label: String): IconCompat {
        val safeLabel = label.trim()
        val letter = safeLabel.firstOrNull()?.uppercase() ?: "?"

        val size = 128
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = generateColor(safeLabel)
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, size / 2f, yPos, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun generateColor(input: String): Int {
        val hash = input.hashCode()

        val r = (hash shr 16) and 0xFF
        val g = (hash shr 8) and 0xFF
        val b = hash and 0xFF

        return Color.rgb(
            (r + 128) % 256,
            (g + 128) % 256,
            (b + 128) % 256
        )
    }
}
