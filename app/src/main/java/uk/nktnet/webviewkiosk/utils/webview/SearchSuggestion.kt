package uk.nktnet.webviewkiosk.utils.webview

import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class SearchEngine(
    val baseUrl: String,
    val parse: (String) -> List<String>
)

object Suggest {
    private val GOOGLE = SearchEngine(
        "https://suggestqueries.google.com/complete/search?client=firefox&q="
    ) { response ->
        val jsonArray = JSONArray(response)
        (1 until jsonArray.length()).map { jsonArray.getJSONArray(it)[0].toString() }
    }

    private val DUCKDUCKGO = SearchEngine(
        "https://duckduckgo.com/ac?q="
    ) { response ->
        val jsonArray = JSONArray(response)
        (0 until jsonArray.length()).map { jsonArray.getJSONObject(it).getString("phrase") }
    }

    private val BRAVE = SearchEngine(
        "https://search.brave.com/api/suggest?q="
    ) { response ->
        val jsonArray = JSONArray(response)
        (1 until jsonArray.length()).map { jsonArray.getString(it) }
    }

    private fun get(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().use(BufferedReader::readText)
    }

    fun suggest(engine: SearchEngine, query: String): List<String> {
        val response = get(engine.baseUrl + query)
        return engine.parse(response)
    }

    fun google(q: String) = suggest(GOOGLE, q)
    fun duckduckgo(q: String) = suggest(DUCKDUCKGO, q)
    fun brave(q: String) = suggest(BRAVE, q)
}
