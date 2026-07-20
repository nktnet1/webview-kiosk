package uk.nktnet.webviewkiosk.ui.components.apps

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.createBitmap

@Composable
fun AppIcon(
    icon: Drawable,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(icon) {
        val width = icon.intrinsicWidth.coerceAtLeast(1)
        val height = icon.intrinsicHeight.coerceAtLeast(1)

        val maxSize = 128

        val scale = minOf(
            maxSize.toFloat() / width,
            maxSize.toFloat() / height,
            1f
        )

        val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
        val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)

        createBitmap(bitmapWidth, bitmapHeight).also { bitmap ->
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, bitmap.width, bitmap.height)
            icon.draw(canvas)
        }
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
