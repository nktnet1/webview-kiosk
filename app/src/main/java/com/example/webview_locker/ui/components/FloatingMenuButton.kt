package com.example.webview_locker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.webview_locker.config.SystemSettings

@Composable
fun FloatingMenuButton(
    onHomeClick: () -> Unit,
    onPinClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    var offsetX by remember { mutableFloatStateOf(systemSettings.menuOffsetX) }
    var offsetY by remember { mutableFloatStateOf(systemSettings.menuOffsetY) }
    var menuExpanded by remember { mutableStateOf(false) }

    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(24.dp)
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
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
                imageVector = Icons.AutoMirrored.Filled.List,
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
                text = { Text("Pin app", color = tintColor) },
                onClick = {
                    menuExpanded = false
                    onPinClick()
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
                    menuExpanded = false
                    onSettingsClick()
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
