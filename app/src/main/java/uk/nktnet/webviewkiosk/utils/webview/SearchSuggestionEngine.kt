package uk.nktnet.webviewkiosk.utils.webview

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption

data class SearchEngineInterface(
    val baseUrl: String,
    val parse: (String) -> List<String>
)

object SearchSuggestionEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val engines: Map<SearchSuggestionEngineOption, SearchEngineInterface> = mapOf(
        SearchSuggestionEngineOption.GOOGLE to SearchEngineInterface(
            "https://suggestqueries.google.com/complete/search?client=firefox&q="
        ) { response ->
            val jsonArray = JSONArray(response)
            val suggestionsArray = jsonArray.getJSONArray(1)
            (0 until suggestionsArray.length()).map { suggestionsArray.getString(it) }
        },
        SearchSuggestionEngineOption.DUCKDUCKGO to SearchEngineInterface(
            "https://duckduckgo.com/ac?q="
        ) { response ->
            val jsonArray = JSONArray(response)
            (0 until jsonArray.length()).map { jsonArray.getJSONObject(it).getString("phrase") }
        },
        SearchSuggestionEngineOption.YAHOO to SearchEngineInterface(
            "https://api.search.yahoo.com/sugg/gossip/gossip-in-ura?output=sd1&command="
        ) { response ->
            val jsonObject = org.json.JSONObject(response)
            val suggestionsArray = jsonObject.getJSONArray("r")
            (0 until suggestionsArray.length()).map { i ->
                suggestionsArray.getJSONObject(i).getString("k")
            }
        }
    )

    private fun get(urlString: String): String {
        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Search suggestion request failed with HTTP ${response.code}")
            }
            response.body.string()
        }
    }

    fun suggest(engineOption: SearchSuggestionEngineOption, query: String): List<String> {
        val engine = engines[engineOption] ?: return emptyList()
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val response = get(engine.baseUrl + encodedQuery)
        return engine.parse(response)
    }
}
