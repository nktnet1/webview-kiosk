package uk.nktnet.webviewkiosk.testing

import android.view.KeyEvent
import java.lang.reflect.Modifier
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowKeyEvent

// Robolectric 4.17 implements nativeKeyCodeFromString but not its inverse.
@Implements(KeyEvent::class)
class ShadowKeyEventWithNames : ShadowKeyEvent() {
    companion object {
        private val keyCodeNames =
            KeyEvent::class
                .java
                .fields
                .filter {
                    it.name.startsWith("KEYCODE_") &&
                        it.type == Int::class.javaPrimitiveType &&
                        Modifier.isStatic(it.modifiers)
                }
                .associate { it.getInt(null) to it.name.removePrefix("KEYCODE_") }

        // Robolectric invokes this implementation reflectively.
        @Suppress("unused")
        @JvmStatic
        @Implementation
        fun nativeKeyCodeToString(keyCode: Int): String? = keyCodeNames[keyCode]
    }
}
