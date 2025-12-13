package com.nktnet.webview_kiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.view.KeyEvent
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton

val modifierKeyCodes = setOf(
    KeyEvent.KEYCODE_SHIFT_LEFT,
    KeyEvent.KEYCODE_SHIFT_RIGHT,
    KeyEvent.KEYCODE_CTRL_LEFT,
    KeyEvent.KEYCODE_CTRL_RIGHT,
    KeyEvent.KEYCODE_ALT_LEFT,
    KeyEvent.KEYCODE_ALT_RIGHT,
    KeyEvent.KEYCODE_META_LEFT,
    KeyEvent.KEYCODE_META_RIGHT
)

fun keyEventToShortcutString(event: KeyEvent): String? {
    if (event.action != KeyEvent.ACTION_DOWN) {
        return null
    }
    val modifiers = mutableListOf<String>()
    if (event.isCtrlPressed) {
        modifiers.add("Ctrl")
    }
    if (event.isShiftPressed) {
        modifiers.add("Shift")
    }
    if (event.isAltPressed) {
        modifiers.add("Alt")
    }
    if (event.isMetaPressed) {
        modifiers.add("Meta")
    }
    val keyCode = event.keyCode
    if (keyCode in modifierKeyCodes) {
        return null
    }
    if (modifiers.isEmpty()) {
        return null
    }
    val mainKey = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    return modifiers.joinToString("+") + "+" + mainKey
}

fun isShortcutPressed(event: KeyEvent, storedShortcut: String): Boolean {
    val shortcut = keyEventToShortcutString(event) ?: return false
    return shortcut.equals(storedShortcut, ignoreCase = true)
}

private fun handleCustomUnlockShortcut(
    context: Context,
    event: KeyEvent,
): Boolean {
    val activity = context as? Activity ?: return false
    val activityManager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val userSettings = UserSettings(activity)

    val shouldUnlock = getIsLocked(activityManager)
        && userSettings.customUnlockShortcut.isNotEmpty()
        && isShortcutPressed(event, userSettings.customUnlockShortcut)

    if (shouldUnlock) {
        unlockWithAuthIfRequired(activity)
    }
    return shouldUnlock
}

private fun handleBlockVolumeKeys(event: KeyEvent): Boolean {
    return when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> false
    }
}

fun handleKeyEvent(context: Context, event: KeyEvent): Boolean {
    UserInteractionStateSingleton.onUserInteraction()
    val userSettings = UserSettings(context)
    return (
        (userSettings.blockVolumeKeys && handleBlockVolumeKeys(event))
        || handleCustomUnlockShortcut(context, event)
    )
}
