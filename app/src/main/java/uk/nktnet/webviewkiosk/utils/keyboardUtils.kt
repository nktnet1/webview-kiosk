package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.view.KeyEvent as AndroidKeyEvent
import uk.nktnet.webviewkiosk.config.UserSettings

val modifierKeyCodes = setOf(
    AndroidKeyEvent.KEYCODE_SHIFT_LEFT,
    AndroidKeyEvent.KEYCODE_SHIFT_RIGHT,
    AndroidKeyEvent.KEYCODE_CTRL_LEFT,
    AndroidKeyEvent.KEYCODE_CTRL_RIGHT,
    AndroidKeyEvent.KEYCODE_ALT_LEFT,
    AndroidKeyEvent.KEYCODE_ALT_RIGHT,
    AndroidKeyEvent.KEYCODE_META_LEFT,
    AndroidKeyEvent.KEYCODE_META_RIGHT
)
fun keyEventToShortcutString(event: AndroidKeyEvent): String? {
    if (event.action != AndroidKeyEvent.ACTION_DOWN) {
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
    val mainKey = AndroidKeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    return modifiers.joinToString("+") + "+" + mainKey
}

fun isShortcutPressed(event: AndroidKeyEvent, storedShortcut: String): Boolean {
    val shortcut = keyEventToShortcutString(event) ?: return false
    return shortcut.equals(storedShortcut, ignoreCase = true)
}

fun handlePreviewKeyEvent(
    activity: Activity,
    activityManager: ActivityManager,
    userSettings: UserSettings,
    event: AndroidKeyEvent,
): Boolean {
    val shouldUnlock = getIsLocked(activityManager)
            && userSettings.customUnlockShortcut.isNotEmpty()
            && isShortcutPressed(event, userSettings.customUnlockShortcut)
    if (shouldUnlock) {
        tryUnlockTask(activity)
    }
    return shouldUnlock
}
