package uk.nktnet.webviewkiosk.ui.components.webview

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import kotlin.math.roundToInt

@Composable
fun FloatingMenuButton(
    onHomeClick: () -> Unit,
    onLockClick: () -> Unit,
    navController: NavController,
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    val density = LocalDensity.current
    val buttonSizeDp = 64.dp
    val buttonSizePx = with(density) { buttonSizeDp.toPx() }

    var boxBounds by remember { mutableStateOf<Rect?>(null) }

    var offsetX by remember { mutableFloatStateOf(-1f) }
    var offsetY by remember { mutableFloatStateOf(-1f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.safeContent)
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size.toSize()
                val bounds = Rect(0f, 0f, size.width, size.height)
                boxBounds = bounds

                if (offsetX < 0f || offsetY < 0f) {
                    val marginPx = with(density) { 40.dp.toPx() }
                    val maxX = bounds.right - buttonSizePx
                    val maxY = bounds.bottom - buttonSizePx
                    val savedX = systemSettings.menuOffsetX
                    val savedY = systemSettings.menuOffsetY

                    offsetX = if (savedX in bounds.left..maxX) savedX else (maxX - marginPx).coerceAtLeast(bounds.left)
                    offsetY = if (savedY in bounds.top..maxY) savedY else (maxY - marginPx).coerceAtLeast(bounds.top)

                    visible = true
                }
            }
    ) {
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.1f))
                    .zIndex(0f)
                    .border(2.dp, primaryColor)
            )
        }

        val minX = boxBounds?.left ?: 0f
        val minY = boxBounds?.top ?: 0f
        val maxX = (boxBounds?.right ?: buttonSizePx) - buttonSizePx
        val maxY = (boxBounds?.bottom ?: buttonSizePx) - buttonSizePx
        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    IntOffset(
                        x = offsetX.coerceIn(minX, maxX).roundToInt(),
                        y = offsetY.coerceIn(minY, maxY).roundToInt(),
                    )
                }
                .alpha(if (visible) 1f else 0f)
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
                        offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
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
                    painter = if (systemSettings.isDeviceOwner) {
                        painterResource(R.drawable.baseline_file_open_24)
                    } else {
                        painterResource(R.drawable.baseline_build_24)
                    },
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
                            painter = painterResource(R.drawable.baseline_home_24),
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
                            painter = painterResource(R.drawable.baseline_lock_24),
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
                            painter = painterResource(R.drawable.baseline_settings_24),
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
