package uk.nktnet.webviewkiosk.testing

import android.view.KeyEvent
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowKeyEvent
import java.lang.reflect.Modifier

// Robolectric 4.17 implements nativeKeyCodeFromString but not its inverse.
@Implements(KeyEvent::class)
class ShadowKeyEventWithNames : ShadowKeyEvent() {
    companion object {
        private val keyCodeNames = KeyEvent::class.java.fields
            .filter {
                it.name.startsWith("KEYCODE_")
                    && it.type == Int::class.javaPrimitiveType
                    && Modifier.isStatic(it.modifiers)
            }
            .associate { it.getInt(null) to it.name.removePrefix("KEYCODE_") }

        @JvmStatic
        @Implementation
        protected fun nativeKeyCodeToString(keyCode: Int): String? = keyCodeNames[keyCode]
    }
}
