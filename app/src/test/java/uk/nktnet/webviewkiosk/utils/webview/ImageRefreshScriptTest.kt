package uk.nktnet.webviewkiosk.utils.webview

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageRefreshScriptTest {
    @Test
    fun imageUrlIsQuotedAndEscapedForJavaScript() {
        val url = "https://example.com/a'\"\\b.png?x=\n</script>"
        val script = refreshImageScript(url)

        assertTrue(script.contains("const targetUrl = ${JSONObject.quote(url)};"))
        assertFalse(script.contains("const targetUrl = \"$url\""))
        assertTrue(script.contains("\\n"))
        assertTrue(script.contains("\\\""))
        assertTrue(script.contains("\\\\"))
    }

    @Test
    fun refreshIsIndependentOfImageLoadingStatusAndDuplicateUrls() {
        val script = refreshImageScript("https://example.com/photo.jpg")

        assertTrue(script.contains("for (const image of document.images)"))
        assertTrue(script.contains("image.currentSrc || image.src"))
        assertFalse(script.contains("image.complete"))
        assertFalse(script.contains("naturalWidth"))
        assertFalse(script.contains("matches.length !== 1"))
    }

    @Test
    fun refreshUpdatesImageSourcesWithoutReloadingThePage() {
        val script = refreshImageScript("https://example.com/photo.jpg")

        assertTrue(script.contains("searchParams.set('_webviewkiosk_refresh'"))
        assertTrue(script.contains("refreshSrcset(source)"))
        assertTrue(script.contains("refreshSrcset(image)"))
        assertTrue(script.contains("if (!updated) image.src = refreshedUrl"))
        assertFalse(script.contains("location.reload"))
    }
}
