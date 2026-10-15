package uk.nktnet.webviewkiosk.adblock

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterFileFormatTest {
    @Test
    fun documentPunctuationAndFilterSyntaxAreLeftToTheRuleParser() {
        for (
        text in
        listOf(
            "{}",
            "[]",
            "\uFEFF \r\n {\"message\": \"例子\"}\r\n\t",
            "[\n  {\"rules\": [\"||ads.example^\"]}\n]",
            "<rules><rule>||ads.example^</rule></rules>",
            "rules:\n  - ads.example",
            "name,enabled\nads,true",
            "[Adblock Plus 2.0]\n! Title: Test\n||ads.example^",
            "[advertisement]",
            "[0-9]*tracking",
            "[\"ad\"]\$script",
            "{\"ad\":true}\$script",
            "||api.example/ads?payload={\"ad\":true}",
            "example.com##[data-ad=\"true\"]",
        )
        ) {
            validateFilterFileFormat(text)
        }
    }

    @Test
    fun controlCharactersAreRejectedInAnyContentIncludingComments() {
        for (
        character in
        listOf('\u0000', '\u0001', '\u000C', '\u001B', '\u007F', '\u0085', '\u009F')
        ) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    validateFilterFileFormat("||working.example^\r\n! comment $character")
                }
            assertTrue(error.message!!.contains("Line 2:"))
            assertTrue(error.message!!.contains("control character"))
        }
    }

    @Test
    fun leadingControlCharactersCannotHideInTrimmedWhitespace() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                validateFilterFileFormat("\u001B||ads.example^")
            }
        assertTrue(error.message!!.contains("Line 1:"))
        assertTrue(error.message!!.contains("U+001B"))
    }

    @Test
    fun normalWhitespaceUnicodeAndLiteralPatternsRemainPermitted() {
        for (
        text in
        listOf(
            "\uFEFF! 例子\tfilters\r\n0.0.0.0\tads.example\r\n||例子.测试^\r\n",
            "advertisement\n@@allowed\n/ads/banner*\n||ads.example^",
            "||ads.example^\$csp=script-src 'none'\nexample.com##div > .ad",
        )
        ) {
            validateFilterFileFormat(text)
        }
    }
}
