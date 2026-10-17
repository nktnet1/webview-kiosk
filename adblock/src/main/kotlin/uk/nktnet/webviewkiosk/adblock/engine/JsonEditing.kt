package uk.nktnet.webviewkiosk.adblock.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Compile a bounded, non-executable subset of upstream's JSONPath editing DSL. */
internal object JsonEditing {
    val aliases =
        mapOf(
            "json-edit" to ScriptletType.JSON_EDIT,
            "json-edit-fetch-response" to ScriptletType.JSON_EDIT_FETCH,
            "json-edit-xhr-response" to ScriptletType.JSON_EDIT_XHR,
            "jsonl-edit-fetch-response" to ScriptletType.JSONL_EDIT_FETCH,
            "jsonl-edit-xhr-response" to ScriptletType.JSONL_EDIT_XHR,
        )
    val types = aliases.values.toSet()

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        val query = args.firstOrNull() ?: return null
        val program = compile(query) ?: return null
        // Canonical cache representation is recompiled and compared on restore.
        if (args.size == 5 && args[1] == program) {
            return canonical(type, query, program, args[2], args[3], args[4])
        }
        if (args.size !in setOf(1, 3, 5)) {
            return null
        }
        val options = args.drop(1).chunked(2)
        if (options.map { it[0] }.distinct().size != options.size) {
            return null
        }
        val allowed =
            when (type) {
                ScriptletType.JSON_EDIT -> setOf("matches")
                ScriptletType.JSONL_EDIT_FETCH,
                ScriptletType.JSONL_EDIT_XHR -> setOf("propsToMatch", "jsonExtract")
                else -> setOf("propsToMatch")
            }
        if (options.any { it[0] !in allowed }) {
            return null
        }
        val values = options.associate { it[0] to it[1] }
        return canonical(
            type,
            query,
            program,
            values["propsToMatch"].orEmpty(),
            values["matches"].orEmpty(),
            values["jsonExtract"].orEmpty(),
        )
    }

    private fun canonical(
        type: ScriptletType,
        query: String,
        program: String,
        props: String,
        matches: String,
        extract: String,
    ): ScriptletInstruction? {
        if (
            props.isNotEmpty() && !ScriptletParser.requestMatch(props) ||
                matches.isNotEmpty() && !ScriptletParser.pattern(matches) ||
                type == ScriptletType.JSON_EDIT &&
                    (props.isNotEmpty() || extract.isNotEmpty()) ||
                type != ScriptletType.JSON_EDIT && matches.isNotEmpty()
        ) {
            return null
        }
        if (extract.isNotEmpty()) {
            if (
                type !in
                    setOf(
                        ScriptletType.JSONL_EDIT_FETCH,
                        ScriptletType.JSONL_EDIT_XHR,
                    ) ||
                    !extract.startsWith('^') ||
                    !ScriptletParser.pattern("/$extract/") ||
                    runCatching {
                            Regex(extract).toPattern().matcher("").groupCount()
                        }
                        .getOrDefault(0) != 1
            ) {
                return null
            }
        }
        return ScriptletInstruction(
            type,
            listOf(query, program, props, matches, extract),
        )
    }

    fun compile(query: String): String? = runCatching {
        require(query.length in 1..2048)
        val parser = Reader(query.removePrefix("v2:"))
        val steps = parser.path(0)
        require(parser.position == parser.text.length && steps.isNotEmpty())
        JsonArray(listOf(JsonPrimitive(query.startsWith("v2:")), JsonArray(steps)))
            .toString()
    }
        .getOrNull()

    private class Reader(val text: String) {
        var position = 0
        private var steps = 0
        private val names = Regex("[A-Za-z_][A-Za-z_0-9]*|\\*")
        private val integers = Regex("-?[0-9]{1,6}")
        private val forbidden = setOf("__proto__", "prototype", "constructor")

        private fun spaces() {
            while (text.getOrNull(position) == ' ') {
                position++
            }
        }

        private fun name(literal: Boolean = true): JsonElement {
            val quote = text.getOrNull(position)
            if (quote == '\'' || quote == '"') {
                require(literal)
                position++
                val result = StringBuilder()
                while (position < text.length && text[position] != quote) {
                    val char = text[position++]
                    if (char == '\\') {
                        val next = text.getOrNull(position++)
                        require(next == quote || next == '\\')
                        result.append(next)
                    } else {
                        result.append(char)
                    }
                }
                require(text.getOrNull(position++) == quote)
                require(result.toString() !in forbidden)
                return JsonPrimitive(result.toString())
            }
            val number =
                integers.find(text, position)?.takeIf { it.range.first == position }
            if (number != null) {
                require(literal)
                position += number.value.length
                return JsonPrimitive(number.value.toInt())
            }
            val match =
                requireNotNull(
                    names.find(text, position)?.takeIf { it.range.first == position }
                )
            position += match.value.length
            require(match.value !in forbidden)
            return JsonPrimitive(match.value)
        }

        fun path(depth: Int): List<JsonElement> {
            require(depth <= 4)
            if (text.getOrNull(position) in listOf('$', '@')) {
                position++
            }
            val output = mutableListOf<JsonElement>()
            while (position < text.length) {
                spaces()
                var recursive = false
                if (text.startsWith("..", position)) {
                    recursive = true
                    position += 2
                } else if (text.getOrNull(position) == '.') {
                    position++
                    require(text.getOrNull(position) != '[')
                } else if (text.getOrNull(position) != '[') {
                    break
                }
                require(++steps <= 64 && output.size < 24)
                val selectors = mutableListOf<JsonElement>()
                var filter: JsonElement = JsonNull
                if (text.getOrNull(position) != '[') {
                    selectors += name(literal = false)
                } else if (text.startsWith("[?", position)) {
                    position += 2
                    val negated = text.getOrNull(position) == '!'
                    if (negated) {
                        position++
                    }
                    val nested = path(depth + 1)
                    require(nested.isNotEmpty())
                    spaces()
                    val operator =
                        listOf("==", "!=", "<=", ">=", "^=", "$=", "*=", "<", ">")
                            .firstOrNull { text.startsWith(it, position) }
                            .orEmpty()
                    var value: JsonElement = JsonNull
                    if (operator.isNotEmpty()) {
                        position += operator.length
                        spaces()
                        if (text.getOrNull(position) == '\'') {
                            value = name()
                        } else {
                            val end = text.indexOf(']', position)
                            require(end >= 0)
                            require(text.getOrNull(position) !in listOf('[', '{'))
                            value =
                                Json.parseToJsonElement(
                                    text.substring(position, end).trim()
                                )
                            require(value is JsonPrimitive)
                            position = end
                        }
                    }
                    spaces()
                    require(text.getOrNull(position++) == ']')
                    filter =
                        JsonArray(
                            listOf(
                                JsonPrimitive(negated),
                                JsonArray(nested),
                                JsonPrimitive(operator),
                                value,
                            )
                        )
                } else {
                    position++
                    spaces()
                    do {
                        selectors += name()
                        require(selectors.size <= 16)
                        spaces()
                        val more = text.getOrNull(position) == ','
                        if (more) {
                            position++
                            spaces()
                        }
                    } while (more)
                    require(text.getOrNull(position++) == ']')
                }
                output.add(
                    JsonArray(
                        listOf(
                            JsonPrimitive(recursive),
                            JsonArray(selectors),
                            filter,
                        )
                    )
                )
            }
            return output
        }
    }
}
