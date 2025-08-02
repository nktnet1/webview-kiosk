package com.example.webview_locker.ui.components

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.webview_locker.config.SystemSettingsKeys
import kotlin.math.roundToInt

@Composable
fun FloatingMenuButton(
    onMenuClick: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onHomeClick: () -> Unit,
    onPinClick: () -> Unit,
    onSettingsClick: () -> Unit,
    tintColor: Color
) {
    val context = LocalContext.current
    val systemPrefs = context.getSharedPreferences(SystemSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val savedOffsetX = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_X, 0f)
    val savedOffsetY = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_Y, 0f)
    var offsetX by remember { mutableFloatStateOf(savedOffsetX) }
    var offsetY by remember { mutableFloatStateOf(savedOffsetY) }
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(24.dp)
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = offsetX + dragAmount.x
                        offsetY = offsetY + dragAmount.y
                        systemPrefs.edit {
                            putFloat(SystemSettingsKeys.MENU_OFFSET_X, offsetX)
                            putFloat(SystemSettingsKeys.MENU_OFFSET_Y, offsetY)
                        }
                    }
                )
            }
    ) {
        IconButton(
            onClick = onMenuClick,
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
            expanded = isMenuExpanded,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("Home", color = tintColor) },
                onClick = onHomeClick,
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
                onClick = onPinClick,
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
                onClick = onSettingsClick,
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
