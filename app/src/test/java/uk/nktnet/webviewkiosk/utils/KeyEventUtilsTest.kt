package uk.nktnet.webviewkiosk.utils

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.testing.ShadowKeyEventWithNames

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyEventWithNames::class])
class KeyEventUtilsTest {
    @Test
    fun usesAStableModifierOrder() {
        val modifiers = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON or
            KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON

        assertEquals("Ctrl+Shift+Alt+Meta+K", keyEventToShortcutString(key(modifiers = modifiers)))
    }

    @Test
    fun ignoresKeyUpUnmodifiedKeysAndModifierKeysThemselves() {
        assertNull(keyEventToShortcutString(key(action = KeyEvent.ACTION_UP)))
        assertNull(keyEventToShortcutString(key(modifiers = 0)))
        for (keyCode in modifierKeyCodes) {
            assertNull(keyEventToShortcutString(key(keyCode = keyCode)))
        }
    }

    @Test
    fun matchesShortcutsIgnoringCaseButRequiresTheSameModifiers() {
        val event = key(modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

        assertTrue(isShortcutPressed(event, "ctrl+shift+k"))
        assertFalse(isShortcutPressed(event, "Ctrl+K"))
        assertFalse(isShortcutPressed(event, "Ctrl+Shift+L"))
        assertFalse(isShortcutPressed(event, ""))
    }

    @Test
    fun preservesMultiwordKeyNamesAndNumericUnknownKeyCodes() {
        assertEquals("Ctrl+DPAD_UP", keyEventToShortcutString(key(keyCode = KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals("Ctrl+1001", keyEventToShortcutString(key(keyCode = 1001)))
    }

    private fun key(
        action: Int = KeyEvent.ACTION_DOWN,
        keyCode: Int = KeyEvent.KEYCODE_K,
        modifiers: Int = KeyEvent.META_CTRL_ON,
    ) = KeyEvent(0, 0, action, keyCode, 0, modifiers)
}
