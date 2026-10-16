package uk.nktnet.webviewkiosk.blocking

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure

@RunWith(RobolectricTestRunner::class)
class WebViewCosmeticsTest {
    @Test
    fun sendsNullableProcedureTextAsExplicitJsonNull() {
        val serialized =
            WebViewCosmetics.proceduresJson(
                listOf(
                    CosmeticProcedure(".advert", remove = true),
                    CosmeticProcedure(".banner", text = "Sponsored", upward = 2),
                )
            )
        val removal = serialized.getJSONObject(0)
        assertEquals(".advert", removal.getString("selector"))
        assertEquals(true, removal.isNull("text"))
        assertEquals(true, removal.getBoolean("remove"))
        val textMatch = serialized.getJSONObject(1)
        assertEquals("Sponsored", textMatch.getString("text"))
        assertEquals(2, textMatch.getInt("upward"))
    }

    @Test
    fun acceptsBoundedTokensFromTheActualFrameOrigin() {
        val parsed =
            WebViewCosmetics.parseQuery(
                query(),
                Uri.parse("https://publisher.example:443"),
            )!!
        assertEquals("https://publisher.example/article", parsed.url)
        assertEquals(listOf("ad"), parsed.classes)
        assertEquals(listOf("sponsor"), parsed.ids)
    }

    @Test
    fun rejectsCrossOriginAndPrivateAssetQueries() {
        assertNull(
            WebViewCosmetics.parseQuery(query(), Uri.parse("https://other.example"))
        )
        assertNull(
            WebViewCosmetics.parseQuery(
                query(),
                Uri.parse("http://publisher.example"),
            )
        )
        assertNull(
            WebViewCosmetics.parseQuery(
                query(),
                Uri.parse("https://publisher.example:8443"),
            )
        )
        assertNull(WebViewCosmetics.parseQuery(query(), Uri.parse("null")))
        for (url in
            listOf(
                "file:///private.html",
                "blob:https://publisher.example/id",
                "https://appassets.androidplatform.net/private.html",
                "https://pdf-dummy.webviewkiosk.nktnet.uk/private.html",
            )) {
            assertNull(WebViewCosmetics.parseQuery(query(url), Uri.parse(url)))
        }
    }

    @Test
    fun rejectsMalformedAndOversizedMessages() {
        val origin = Uri.parse("https://publisher.example")
        assertNull(WebViewCosmetics.parseQuery("not json", origin))
        assertNull(WebViewCosmetics.parseQuery(" ".repeat(262_145), origin))
        assertNull(
            WebViewCosmetics.parseQuery(
                JSONObject(query())
                    .put("classes", JSONArray(List(257) { "ad" }))
                    .toString(),
                origin,
            )
        )
        assertNull(
            WebViewCosmetics.parseQuery(
                JSONObject(query())
                    .put("classes", JSONArray().put("a".repeat(129)))
                    .toString(),
                origin,
            )
        )
        assertNull(
            WebViewCosmetics.parseQuery(
                JSONObject(query())
                    .put("classes", JSONArray().put(JSONObject()))
                    .toString(),
                origin,
            )
        )
    }

    private fun query(url: String = "https://publisher.example/article") =
        JSONObject()
            .put("id", 1)
            .put("url", url)
            .put("classes", JSONArray(listOf("ad", "ad")))
            .put("ids", JSONArray(listOf("sponsor")))
            .toString()
}
