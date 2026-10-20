package uk.nktnet.webviewkiosk.utils

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntentUtilsTest {
    @Test
    fun viewAcceptsOnlyTheRegisteredBrowserAndAttachmentSchemes() {
        val webUrl = "https://example.com/path?q=1"
        assertEquals(
            IntentResult(url = webUrl),
            handleMainIntent(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))),
        )
        assertEquals(
            IntentResult(url = "http://localhost:8080/"),
            handleMainIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8080/"))
            ),
        )
        val attachment = Uri.parse("content://other.app/documents/42")
        assertEquals(
            IntentResult(uploadUri = attachment),
            handleMainIntent(Intent(Intent.ACTION_VIEW, attachment)),
        )
    }

    @Test
    fun explicitViewIntentsCannotPassActiveOrUnsupportedSchemesToWebview() {
        for (url in
            listOf(
                "javascript:alert(document.domain)",
                "data:text/html,<script>alert(1)</script>",
                "intent://scan/#Intent;scheme=zxing;end",
                "file:///sdcard/private.html",
                "http:///no-host",
                "https:///no-host",
            )) {
            assertEquals(
                url,
                IntentResult(),
                handleMainIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url))),
            )
        }
    }

    @Test
    fun sharedTextIsValidatedAndTrimmedAndFilesRequireLocalUriScheme() {
        assertEquals(
            IntentResult(url = "https://example.com/"),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, " https://example.com/ ")
                }
            ),
        )
        val attachment = Uri.parse("content://other.app/image/2")
        assertEquals(
            IntentResult(uploadUri = attachment),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, attachment)
                }
            ),
        )
        assertEquals(
            IntentResult(),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    data = Uri.parse("https://example.com/not-an-attachment")
                }
            ),
        )
    }

    @Test
    fun sharedPlainTextFileFallsBackToItsContentStream() {
        val attachment = Uri.parse("content://other.app/docs/notes.txt")
        assertEquals(
            IntentResult(uploadUri = attachment),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, attachment)
                }
            ),
        )
        assertEquals(
            IntentResult(uploadUri = attachment),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "not a URL")
                    putExtra(Intent.EXTRA_STREAM, attachment)
                }
            ),
        )
    }

    @Test
    fun malformedExtrasFailClosedInsteadOfCrashingOnTypeMismatch() {
        val badText =
            object : Intent(ACTION_SEND) {
                    override fun getStringExtra(name: String): String? {
                        throw ClassCastException("EXTRA_TEXT is not a string")
                    }
                }
                .apply { type = "text/plain" }
        assertEquals(IntentResult(), handleMainIntent(badText))

        val badBoolean =
            object : Intent() {
                override fun getBooleanExtra(
                    name: String,
                    defaultValue: Boolean,
                ): Boolean {
                    throw ClassCastException("flag is not a boolean")
                }
            }
        assertFalse(badBoolean.getBooleanExtraSafely("flag", false))
        assertTrue(badBoolean.getBooleanExtraSafely("flag", true))

        assertEquals(
            IntentResult(),
            handleMainIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, 25)
                }
            ),
        )
    }
}
