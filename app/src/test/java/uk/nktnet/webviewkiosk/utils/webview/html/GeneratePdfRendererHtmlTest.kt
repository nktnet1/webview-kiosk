package uk.nktnet.webviewkiosk.utils.webview.html

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneratePdfRendererHtmlTest {
    @Test
    fun rendersTransparentPdfPageMarginsOnWhiteCanvas() {
        val html = generatePdfRendererHtml("test-token")

        assertTrue(
            "The PDF canvas needs a white background when PDF pages leave margins unpainted",
            Regex("""canvas\s*\{[^}]*background-color:\s*#ffffff\s*;""").containsMatchIn(html),
        )
    }

    @Test
    fun keepsPdfPageOpaqueBeforeAndDuringRendering() {
        val html = generatePdfRendererHtml("test-token")

        assertTrue(
            "The PDF page slot should stay white while its canvas is being rendered",
            Regex("""\.page-slot\s*\{[^}]*background-color:\s*#ffffff\s*;""").containsMatchIn(html),
        )
        assertTrue(
            "PDF.js should explicitly render transparent PDF margins against white",
            Regex("""page\.render\(\{[^}]*background:\s*'#ffffff'""").containsMatchIn(html),
        )
    }
}
