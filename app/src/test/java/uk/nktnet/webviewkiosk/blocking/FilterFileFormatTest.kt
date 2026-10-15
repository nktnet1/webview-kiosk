package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterFileFormatTest {
    private fun assertJsonRejected(text: String) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateFilterFileFormat(text)
        }
        assertTrue(error.message!!.contains("JSON document"))
        assertTrue(error.message!!.contains("Adblock or hosts"))
    }

    @Test
    fun jsonObjectsAreRejectedIncludingNestedAndPrettyPrintedDocuments() {
        for (text in listOf(
            "{}",
            """{"name":"Example","enabled":true,"count":123,"value":null}""",
            """
                {
                  "rules": ["||ads.example^", "@@||allowed.example^"],
                  "nested": {"name": "Example"}
                }
            """.trimIndent(),
        )) assertJsonRejected(text)
    }

    @Test
    fun jsonArraysAreRejectedEvenWhenTheyContainFilterStrings() {
        for (text in listOf("[]", "[1, true, null]", """["||ads.example^", "0.0.0.0 telemetry.example"]""")) {
            assertJsonRejected(text)
        }
    }

    @Test
    fun bomAndSurroundingWhitespaceDoNotHideJson() {
        assertJsonRejected("\uFEFF \r\n {\"message\": \"例子\"}\r\n\t")
        assertJsonRejected(" \n\t[{}, [1, 2]] \r\n")
    }

    @Test
    fun listHeadersAndBracketPatternsStillReachTheRuleParser() {
        for (text in listOf(
            "[Adblock Plus 2.0]\n! Title: Test\n||ads.example^",
            "[Adblock Plus 2.0]",
            "[advertisement]",
            "[0-9]*tracking",
            "[\"ad\"]\$script",
            "{\"ad\":true}\$script",
        )) validateFilterFileFormat(text)
    }

    @Test
    fun rulesMayContainJsonLiteralsAndJsonUrls() {
        for (text in listOf(
            "||ads.example^\n@@||allowed.example^\n0.0.0.0 telemetry.example",
            "||api.example/ads?payload={\"ad\":true}",
            "/json[0-9]+/\$script",
            "example.com##[data-ad=\"true\"]",
            "! Example payload: {\"ad\":true}\n||ads.example^",
        )) validateFilterFileFormat(text)
    }

    @Test
    fun scalarSubstringPatternsAreNotMistakenForStructuredDocuments() {
        for (text in listOf("true", "false", "null", "123", "\"advertisement\"", "", " \n\t")) {
            validateFilterFileFormat(text)
        }
    }

    @Test
    fun malformedJsonLikePatternsAreLeftToTheRuleParser() {
        // A failed JSON parse is not evidence that a legal Adblock literal is invalid.
        for (text in listOf("{ads}", "[ads, trackers]", "[\"ads\",]", "{\"ad\":true}suffix")) {
            validateFilterFileFormat(text)
        }
    }

    @Test
    fun largeJsonDocumentsAreRejected() {
        assertJsonRejected("[" + "{\"ad\":true},".repeat(100_000) + "{}]")
    }

    @Test
    fun deeplyNestedJsonDoesNotRequireRecursiveTreeParsing() {
        assertJsonRejected("[".repeat(5_000) + "0" + "]".repeat(5_000))
    }

    @Test
    fun controlCharactersAreRejectedInAnyContentIncludingComments() {
        for (character in listOf('\u0000', '\u0001', '\u000C', '\u001B', '\u007F', '\u0085', '\u009F')) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                validateFilterFileFormat("||working.example^\r\n! comment $character")
            }
            assertTrue(error.message!!.contains("Line 2:"))
            assertTrue(error.message!!.contains("control character"))
        }
    }

    @Test
    fun leadingControlCharactersCannotHideInTrimmedWhitespace() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateFilterFileFormat("\u001B||ads.example^")
        }
        assertTrue(error.message!!.contains("Line 1:"))
        assertTrue(error.message!!.contains("U+001B"))
    }

    @Test
    fun normalWhitespaceUnicodeAndLiteralPatternsRemainPermitted() {
        for (text in listOf(
            "\uFEFF! 例子\tfilters\r\n0.0.0.0\tads.example\r\n||例子.测试^\r\n",
            "advertisement\n@@allowed\n/ads/banner*\n||ads.example^",
            "||ads.example^\$csp=script-src 'none'\nexample.com##div > .ad",
        )) validateFilterFileFormat(text)
    }
}
