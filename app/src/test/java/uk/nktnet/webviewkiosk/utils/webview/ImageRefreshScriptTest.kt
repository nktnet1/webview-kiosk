package uk.nktnet.webviewkiosk.utils.webview

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageRefreshScriptTest {
    @Test
    fun packagedScriptReceivesTheEscapedUrlAsItsOnlyArgument() {
        val context = RuntimeEnvironment.getApplication()
        val asset =
            context.assets.open("image-refresh.js").bufferedReader().use {
                it.readText().trim()
            }
        val url = "https://example.com/a'\"\\b.png?x=\n</script>\u2028\u2029"
        val script = refreshImageScript(context, url)
        val prefix = "$asset("

        assertTrue(script.startsWith(prefix))
        assertTrue(script.endsWith(")"))
        val argument = script.substring(prefix.length, script.length - 1)
        assertFalse(argument.contains('\u2028'))
        assertFalse(argument.contains('\u2029'))
        val arguments = JSONArray("[$argument]")
        assertEquals(1, arguments.length())
        assertEquals(url, arguments.getString(0))
    }
}
