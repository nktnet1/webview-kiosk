package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.view.KeyEvent
import uk.nktnet.webviewkiosk.config.UserSettings

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

fun handlePreviewKeyUnlockEvent(
    activity: Activity,
    activityManager: ActivityManager,
    userSettings: UserSettings,
    event: KeyEvent,
): Boolean {
    val shouldUnlock = getIsLocked(activityManager)
        && userSettings.customUnlockShortcut.isNotEmpty()
        && isShortcutPressed(event, userSettings.customUnlockShortcut)

    if (shouldUnlock) {
        unlockWithAuthIfRequired(activity, {})
    }
    return shouldUnlock
}
