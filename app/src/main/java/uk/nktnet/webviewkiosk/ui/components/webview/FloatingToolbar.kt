package com.nktnet.webview_kiosk.ui.components.webview

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.states.LockStateSingleton
import com.nktnet.webview_kiosk.utils.handleUserKeyEvent
import com.nktnet.webview_kiosk.utils.handleUserTouchEvent
import kotlin.math.roundToInt

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

fun clampOffset(x: Float, y: Float, bounds: Bounds): Pair<Float, Float> =
    x.coerceIn(bounds.minX, bounds.maxX) to y.coerceIn(bounds.minY, bounds.maxY)

fun calculateBounds(boxBounds: Rect?, buttonSizePx: Float): Bounds {
    val minX = boxBounds?.left ?: 0f
    val minY = boxBounds?.top ?: 0f
    val maxX = (boxBounds?.right ?: buttonSizePx) - buttonSizePx
    val maxY = (boxBounds?.bottom ?: buttonSizePx) - buttonSizePx
    return Bounds(
        minX,
        minY,
        maxX.coerceAtLeast(minX),
        maxY.coerceAtLeast(minY)
    )
}

@Composable
fun MenuItem(text: String, iconRes: Int, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
    )
}

@Composable
fun FloatingToolbar(
    onHomeClick: () -> Unit,
    onLockClick: () -> Unit,
    onUnlockClick: () -> Unit,
    navController: NavController,
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }
    val density = LocalDensity.current
    val buttonSizeDp = 64.dp
    val buttonSizePx = with(density) { buttonSizeDp.toPx() }

    var bounds by remember {
        mutableStateOf(Bounds(0f, 0f, 0f, 0f))
    }
    var offsetX by remember { mutableFloatStateOf(-1f) }
    var offsetY by remember { mutableFloatStateOf(-1f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)

    val isLocked by LockStateSingleton.isLocked

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.safeContent)
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size.toSize()
                val rect = Rect(0f, 0f, size.width, size.height)
                bounds = calculateBounds(rect, buttonSizePx)
                if (offsetX < 0f || offsetY < 0f) {
                    val marginPx = with(density) { 40.dp.toPx() }
                    val savedX = systemSettings.menuOffsetX
                    val savedY = systemSettings.menuOffsetY
                    val (newX, newY) = clampOffset(
                        savedX.takeIf {
                            it in bounds.minX..bounds.maxX
                        } ?: (bounds.maxX - marginPx).coerceAtLeast(bounds.minX),
                        savedY.takeIf {
                            it in bounds.minY..bounds.maxY
                        } ?: (bounds.maxY - marginPx).coerceAtLeast(bounds.minY),
                        bounds
                    )
                    offsetX = newX
                    offsetY = newY
                    visible = true
                }
            }
    ) {
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp)
                    .background(
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.1f)
                    )
                    .zIndex(0f)
                    .border(2.dp, primaryColor)
            )
        }

        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    val (clampedX, clampedY) = clampOffset(offsetX, offsetY, bounds)
                    IntOffset(clampedX.roundToInt(), clampedY.roundToInt())
                }
                .alpha(if (visible) 1f else 0f)
                .size(buttonSizeDp)
                .drawBehind {
                    val radiusPx = size.minDimension / 2
                    drawContext.canvas.nativeCanvas.apply {
                        drawCircle(
                            radiusPx, radiusPx, radiusPx,
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
                        val (clampedX, clampedY) = clampOffset(
                            offsetX + dragAmount.x, offsetY + dragAmount.y, bounds
                        )
                        offsetX = clampedX
                        offsetY = clampedY
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
                    painter = painterResource(R.drawable.baseline_build_24),
                    contentDescription = stringResource(
                        id = R.string.floating_toolbar_icon_content_description
                    ),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .handleUserTouchEvent()
                    .handleUserKeyEvent(context, menuExpanded)
            ) {
                MenuItem(
                    stringResource(id = R.string.floating_toolbar_menu_home),
                    R.drawable.baseline_home_24,
                    tintColor
                ) {
                    menuExpanded = false
                    onHomeClick()
                }
                if (isLocked) {
                    MenuItem(
                        stringResource(id = R.string.floating_toolbar_menu_unlock),
                        R.drawable.baseline_lock_open_24,
                        tintColor
                    ) {
                        menuExpanded = false
                        onUnlockClick()
                    }
                } else {
                    MenuItem(
                        stringResource(id = R.string.floating_toolbar_menu_lock),
                        R.drawable.baseline_lock_24,
                        tintColor
                    ) {
                        menuExpanded = false
                        onLockClick()
                    }
                    MenuItem(
                        stringResource(id = R.string.floating_toolbar_menu_settings),
                        R.drawable.baseline_settings_24,
                        tintColor
                    ) {
                        menuExpanded = false
                        navController.navigate(Screen.Settings.route)
                    }
                }
            }
        }
    }
}
