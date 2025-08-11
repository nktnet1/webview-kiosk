package com.nktnet.webview_kiosk.ui.components

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.SystemSettings
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.zIndex

@Composable
fun FloatingMenuButton(
    onHomeClick: () -> Unit,
    onLockClick: () -> Unit,
    navController: NavController,
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val buttonSizeDp = 64.dp
    val buttonSizePx = with(density) { buttonSizeDp.toPx() }

    val paddingDp = 0.dp
    val paddingPx = with(density) { paddingDp.toPx() }

    val innerPaddingDp = 4.dp
    val innerPaddingPx = with(density) { innerPaddingDp.toPx() }

    val insets = WindowInsets.navigationBars
    val bottomInsetPx = with(density) { insets.getBottom(density).toFloat() }

    val maxX = (containerWidth - buttonSizePx - paddingPx * 2 - innerPaddingPx * 2).coerceAtLeast(0f)
    val maxY = (containerHeight - buttonSizePx - paddingPx * 2 - innerPaddingPx * 2 - bottomInsetPx).coerceAtLeast(0f)

    var offsetX by remember { mutableFloatStateOf(systemSettings.menuOffsetX) }
    var offsetY by remember { mutableFloatStateOf(systemSettings.menuOffsetY) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height

                val marginDp = 40.dp
                val marginPx = with(density) { marginDp.toPx() }

                if (systemSettings.menuOffsetX < 0f || systemSettings.menuOffsetY < 0f) {
                    val initialX = containerWidth - buttonSizePx - paddingPx * 2 - marginPx
                    val initialY = containerHeight - buttonSizePx - paddingPx * 2 - marginPx - bottomInsetPx
                    offsetX = initialX.coerceAtLeast(0f)
                    offsetY = initialY.coerceAtLeast(0f)
                    systemSettings.menuOffsetX = offsetX
                    systemSettings.menuOffsetY = offsetY
                }
            }
            .background(Color.Transparent)
            .padding(paddingDp)
            .padding(bottom = with(density) { insets.getBottom(density).toDp() })
    ) {
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.50f))
                    .zIndex(0f)
            )
        }

        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    IntOffset(
                        x = (offsetX + innerPaddingPx).roundToInt()
                            .coerceIn(innerPaddingPx.toInt(), (maxX + innerPaddingPx).toInt()),
                        y = (offsetY + innerPaddingPx).roundToInt()
                            .coerceIn(innerPaddingPx.toInt(), (maxY + innerPaddingPx).toInt())
                    )
                }
                .size(buttonSizeDp)
                .drawBehind {
                    val radiusPx = size.minDimension / 2
                    drawContext.canvas.nativeCanvas.apply {
                        drawCircle(
                            radiusPx,
                            radiusPx,
                            radiusPx,
                            Paint().apply {
                                color = primaryColor.toArgb()
                                isAntiAlias = true
                                setShadowLayer(
                                    20.dp.toPx(),
                                    0f,
                                    0f,
                                    primaryColor.toArgb()
                                )
                            }
                        )
                    }
                }
                .clip(CircleShape)
                .background(primaryColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                        systemSettings.menuOffsetX = offsetX
                        systemSettings.menuOffsetY = offsetY
                    }
                }
        ) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Home", color = tintColor) },
                    onClick = {
                        menuExpanded = false
                        onHomeClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tintColor
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Lock", color = tintColor) },
                    onClick = {
                        menuExpanded = false
                        onLockClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tintColor
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Settings", color = tintColor) },
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        menuExpanded = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = tintColor
                        )
                    }
                )
            }
        }
    }
}
