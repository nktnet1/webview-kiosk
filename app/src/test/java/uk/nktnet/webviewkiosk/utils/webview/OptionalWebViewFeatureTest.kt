package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OptionalWebViewFeatureTest {
    @Test
    fun classLoadingFailureDisablesOnlyTheOptionalFeature() {
        var remainingStartupRan = false

        withOptionalWebViewFeature("DOCUMENT_START_SCRIPT") {
            throw NoClassDefFoundError("Missing WebView implementation class")
        }
        remainingStartupRan = true

        assertTrue(remainingStartupRan)
    }

    @Test
    fun otherFailuresRemainVisible() {
        assertThrows(IllegalStateException::class.java) {
            withOptionalWebViewFeature("DOCUMENT_START_SCRIPT") {
                throw IllegalStateException("Unrelated application failure")
            }
        }
    }

    @Test
    fun availableFeatureStillRunsNormally() {
        var calls = 0
        withOptionalWebViewFeature("DOCUMENT_START_SCRIPT") { calls++ }
        assertEquals(1, calls)
    }
}
