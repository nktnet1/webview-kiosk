package com.nktnet.webview_kiosk.ui.components.apps

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.createBitmap

@Composable
fun AppIcon(
    icon: Drawable,
    modifier: Modifier = Modifier,
) {
    val bitmap = createBitmap(
        icon.intrinsicWidth.coerceAtLeast(1),
        icon.intrinsicHeight.coerceAtLeast(1)
    )
    val canvas = Canvas(bitmap)
    icon.setBounds(0, 0, canvas.width, canvas.height)
    icon.draw(canvas)

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
