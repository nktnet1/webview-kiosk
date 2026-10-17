package uk.nktnet.webviewkiosk.adblock.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
            "trusted-json-edit" to ScriptletType.TRUSTED_JSON_EDIT,
            "trusted-json-edit-fetch-response" to
                ScriptletType.TRUSTED_JSON_EDIT_FETCH,
            "trusted-json-edit-xhr-response" to ScriptletType.TRUSTED_JSON_EDIT_XHR,
            "trusted-jsonl-edit-fetch-response" to
                ScriptletType.TRUSTED_JSONL_EDIT_FETCH,
            "trusted-jsonl-edit-xhr-response" to
                ScriptletType.TRUSTED_JSONL_EDIT_XHR,
            "json-edit-fetch-request" to ScriptletType.JSON_EDIT_FETCH_REQUEST,
            "json-edit-xhr-request" to ScriptletType.JSON_EDIT_XHR_REQUEST,
            "trusted-json-edit-fetch-request" to
                ScriptletType.TRUSTED_JSON_EDIT_FETCH_REQUEST,
            "trusted-json-edit-xhr-request" to
                ScriptletType.TRUSTED_JSON_EDIT_XHR_REQUEST,
        )
    val types = aliases.values.toSet()
    val parseTypes = setOf(ScriptletType.JSON_EDIT, ScriptletType.TRUSTED_JSON_EDIT)
    val lineTypes =
        setOf(
            ScriptletType.JSONL_EDIT_FETCH,
            ScriptletType.JSONL_EDIT_XHR,
            ScriptletType.TRUSTED_JSONL_EDIT_FETCH,
            ScriptletType.TRUSTED_JSONL_EDIT_XHR,
        )

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        val query = args.firstOrNull() ?: return null
        val program = compile(query, type.requiresTrust) ?: return null
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
                in parseTypes -> setOf("matches")
                in lineTypes -> setOf("propsToMatch", "jsonExtract")
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
                type in parseTypes && (props.isNotEmpty() || extract.isNotEmpty()) ||
                type !in parseTypes && matches.isNotEmpty()
        ) {
            return null
        }
        if (extract.isNotEmpty()) {
            if (
                type !in lineTypes ||
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

    fun compile(query: String, trusted: Boolean = false): String? = runCatching {
        require(query.length in 1..2048)
        val parser = Reader(query.removePrefix("v2:"))
        val guards = mutableListOf<JsonElement>()
        var path = parser.path(0)
        require(path.isNotEmpty() || parser.position > 0)
        while (
            parser.position < parser.text.length &&
                parser.text[parser.position] in "{;"
        ) {
            require(guards.size < 8)
            val bounds = parser.quantifier()
            require(parser.text.startsWith(";$", parser.position))
            parser.position++
            guards.add(
                JsonArray(listOf(JsonArray(path), bounds.first, bounds.second))
            )
            path = parser.path(0)
            require(path.isNotEmpty() || parser.position > 0)
        }
        var mutation: JsonElement = JsonNull
        if (parser.position < parser.text.length) {
            require(trusted)
            val tail = parser.text.substring(parser.position)
            val operator: String
            val value: String
            when {
                tail.startsWith("+=") -> {
                    operator = "+"
                    value = tail.drop(2)
                }
                tail.startsWith("=repl(") && tail.endsWith(')') -> {
                    operator = "repl"
                    value = tail.substring(6, tail.lastIndex)
                }
                tail.startsWith("=call(") && tail.endsWith(')') -> {
                    operator = "call"
                    value = tail.substring(6, tail.lastIndex)
                }
                tail.startsWith('=') -> {
                    operator = "="
                    value = tail.drop(1)
                }
                else -> error("Unsupported JSONPath mutation")
            }
            val json = Json.parseToJsonElement(value)
            require(boundedValue(json))
            if (operator == "+") {
                require(json is JsonObject)
            }
            if (operator == "call") {
                require(json is JsonArray && json.size in 2..18)
                val method = json[1] as? JsonPrimitive
                require(
                    method?.isString == true &&
                        Regex("[A-Za-z_$][A-Za-z_0-9$]*").matches(method.content) &&
                        method.content !in
                            setOf("__proto__", "prototype", "constructor")
                )
            }
            if (operator == "repl") {
                require(json is JsonObject)
                require(
                    json.keys.all {
                        it in setOf("regex", "pattern", "flags", "replacement")
                    }
                )
                require(
                    json["replacement"] is JsonPrimitive &&
                        (json["replacement"] as JsonPrimitive).isString
                )
                val pattern = json["regex"] as? JsonPrimitive
                val literal = json["pattern"] as? JsonPrimitive
                val flags = (json["flags"] as? JsonPrimitive)?.content.orEmpty()
                require(pattern?.isString == true || literal?.isString == true)
                require(
                    flags.all { it in "gims" } && flags.toSet().size == flags.length
                )
                require(
                    pattern == null ||
                        AdvancedScriptlets.replacementPattern(
                            "/${pattern.content}/$flags"
                        )
                )
            }
            mutation = JsonArray(listOf(JsonPrimitive(operator), json))
        }
        JsonArray(
                listOf(
                    JsonPrimitive(query.startsWith("v2:")),
                    JsonArray(path),
                    JsonArray(guards),
                    mutation,
                )
            )
            .toString()
    }
        .getOrNull()

    private fun boundedValue(value: JsonElement, depth: Int = 0): Boolean =
        depth <= 16 &&
            when (value) {
                is JsonObject ->
                    value.all { (key, child) ->
                        key !in setOf("__proto__", "prototype", "constructor") &&
                            boundedValue(child, depth + 1)
                    }
                is JsonArray -> value.all { boundedValue(it, depth + 1) }
                is JsonPrimitive ->
                    value.isString ||
                        value == JsonNull ||
                        value.content in setOf("true", "false") ||
                        Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
                            .matches(value.content) &&
                            value.content.toDoubleOrNull()?.isFinite() == true
            }

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

        fun quantifier(): Pair<JsonPrimitive, JsonPrimitive> {
            if (text.getOrNull(position) != '{') {
                return JsonPrimitive(1) to JsonPrimitive(1_000_000)
            }
            val end = text.indexOf('}', position)
            require(end > position)
            val raw = text.substring(position + 1, end)
            require(
                Regex("[0-9]{1,7}|[0-9]{1,7},[0-9]{0,7}|,[0-9]{1,7}").matches(raw)
            )
            val minimum = raw.substringBefore(',').toIntOrNull() ?: 0
            val maximum =
                if (',' in raw) {
                    raw.substringAfter(',').toIntOrNull() ?: 1_000_000
                } else {
                    minimum
                }
            require(minimum <= maximum && maximum <= 1_000_000)
            // Upstream treats a zero range endpoint as its omitted default.
            // Exact {0} is the unambiguous absence guard.
            require(',' !in raw || maximum != 0)
            position = end + 1
            return JsonPrimitive(minimum) to JsonPrimitive(maximum)
        }

        private fun regex(flagsAllowed: Boolean): JsonObject {
            require(text.getOrNull(position++) == '/')
            val start = position
            var escaped = false
            while (position < text.length) {
                val char = text[position++]
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '/') {
                    val source = text.substring(start, position - 1)
                    val flags =
                        if (flagsAllowed && text.getOrNull(position) == 'i') {
                            position++
                            "i"
                        } else {
                            ""
                        }
                    require(ScriptletParser.pattern("/$source/$flags"))
                    return JsonObject(
                        mapOf(
                            "regex" to JsonPrimitive(source),
                            "flags" to JsonPrimitive(flags),
                        )
                    )
                }
            }
            error("Unterminated JSONPath regex")
        }

        private fun name(literal: Boolean = true): JsonElement {
            val quote = text.getOrNull(position)
            if (quote == '/') {
                require(!literal)
                return regex(flagsAllowed = false)
            }
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
            require(!literal || match.value == "*")
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
                    val start = position
                    val root = text.getOrNull(position) == '$'
                    val nested = path(depth + 1)
                    require(
                        nested.isNotEmpty() ||
                            text.getOrNull(start) in listOf('@', '$')
                    )
                    spaces()
                    val operator =
                        if (text.startsWith("=/", position)) {
                            "re"
                        } else {
                            listOf(
                                    "==",
                                    "!=",
                                    "<=",
                                    ">=",
                                    "^=",
                                    "$=",
                                    "*=",
                                    "<",
                                    ">",
                                )
                                .firstOrNull { text.startsWith(it, position) }
                                .orEmpty()
                        }
                    var value: JsonElement = JsonNull
                    if (operator == "re") {
                        // Negated regex filters have inconsistent upstream
                        // semantics.
                        require(!negated)
                        position++
                        value = regex(flagsAllowed = true)
                    } else if (operator.isNotEmpty()) {
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
                            require(boundedValue(value))
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
                                JsonPrimitive(root),
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
