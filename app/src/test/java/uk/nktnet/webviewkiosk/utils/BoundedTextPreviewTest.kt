package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedTextPreviewTest {
    @Test
    fun preservesTextWithinTheLimit() {
        for (text in listOf("", "https://example.com/", "a".repeat(512))) {
            assertEquals(text, boundedTextPreview(text))
        }
    }

    @Test
    fun boundsLargeDataUrlsBeforeLayoutWithoutChangingTheSource() {
        val source = "data:image/png;base64," + "a".repeat(200_000)
        val preview = boundedTextPreview(source)

        assertEquals(512, preview.length)
        assertEquals(source.take(511) + "\u2026", preview)
        assertEquals(200_022, source.length)
        assertTrue(source.endsWith("aaaa"))
    }

    @Test
    fun truncatesJustAboveTheLimit() {
        assertEquals("abc\u2026", boundedTextPreview("abcde", 4))
        assertEquals("\u2026", boundedTextPreview("ab", 1))
    }

    @Test
    fun doesNotSplitASurrogatePair() {
        val source = "ab\uD83D\uDE00remaining"
        assertEquals("ab\u2026", boundedTextPreview(source, 4))
        assertEquals("ab\uD83D\uDE00\u2026", boundedTextPreview(source, 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroLimit() {
        boundedTextPreview("", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLimit() {
        boundedTextPreview("text", -1)
    }
}
