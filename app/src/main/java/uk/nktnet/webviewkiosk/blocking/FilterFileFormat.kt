package uk.nktnet.webviewkiosk.blocking

import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.StringReader

/**
 * Rule parsers accept arbitrary URL substring patterns, including lines of JSON.
 * Check text integrity and reject complete JSON containers before parsing rules.
 * Stream the candidate document instead of allocating a potentially large JSON tree.
 */
internal fun validateFilterFileFormat(text: String) {
    var lineNumber = 1
    for (character in text) {
        require(!character.isISOControl() || character == '\t' || character == '\r' || character == '\n') {
            val code = character.code.toString(16).uppercase().padStart(4, '0')
            "Line $lineNumber: Filter list must be plain text (unexpected control character U+$code)."
        }
        if (character == '\n') lineNumber++
    }
    var start = 0
    while (start < text.length && (text[start].isWhitespace() || text[start] == '\uFEFF')) start++
    if (start == text.length || (text[start] != '{' && text[start] != '[')) return

    val isJsonDocument = try {
        val input = StringReader(text).apply { skip(start.toLong()) }
        JsonReader(input).use { reader ->
            reader.isLenient = false
            reader.skipValue()
            // A bracket-shaped filter, list header, or pattern with $options must
            // still reach the rule parser when it isn't a complete JSON document.
            reader.peek() == JsonToken.END_DOCUMENT
        }
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
    require(!isJsonDocument) {
        "This file contains a JSON document. Choose a plain-text Adblock or hosts filter list."
    }
}
