package uk.nktnet.webviewkiosk.ui.components.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarPreviewTest {
    @Test
    fun shortAddressIsNotChanged() {
        val address = "https://example.invalid/path?query=hello"
        assertEquals(address, boundedAddressBarPreview(address))
    }

    @Test
    fun oversizedAddressIsBoundedBeforeItReachesComposeTextLayout() {
        val address = "https://example.invalid/" + "x".repeat(100_000)
        val preview = boundedAddressBarPreview(address)

        assertTrue(preview.startsWith("https://example.invalid/"))
        assertTrue(preview.endsWith("\u2026"))
        assertTrue(preview.length <= 257)
        assertFalse(preview.contains("x".repeat(MAX_ADDRESS_BAR_EDITABLE_LENGTH)))
        assertEquals(100_000 + "https://example.invalid/".length, address.length)
    }

    @Test
    fun previewNeverCutsAnEmojiInHalf() {
        val address = "a".repeat(255) + "\uD83D\uDE00" + "b".repeat(5_000)
        assertEquals("a".repeat(255) + "\u2026", boundedAddressBarPreview(address))
    }
}
