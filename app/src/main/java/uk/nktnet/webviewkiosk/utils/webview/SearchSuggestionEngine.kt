package com.nktnet.webview_kiosk.utils.webview

import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import com.nktnet.webview_kiosk.config.option.SearchSuggestionEngineOption

data class SearchEngineInterface(
    val baseUrl: String,
    val parse: (String) -> List<String>
)

object SearchSuggestionEngine {
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
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().use(BufferedReader::readText)
    }

    fun suggest(engineOption: SearchSuggestionEngineOption, query: String): List<String> {
        val engine = engines[engineOption] ?: return emptyList()
        val response = get(engine.baseUrl + query)
        return engine.parse(response)
    }
}
