package uk.nktnet.webviewkiosk.adblock.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure

/** Declarative selector chains; no filter text is evaluated as JavaScript. */
internal object ProceduralCosmetics {
    private val operators =
        setOf(
            "has-text",
            "contains",
            "-abp-contains",
            "min-text-length",
            "has",
            "-abp-has",
            "if",
            "if-not",
            "not",
            "upward",
            "matches-attr",
            "matches-css",
            "matches-css-before",
            "matches-css-after",
            "matches-path",
            "watch-attr",
            "remove",
            "remove-attr",
            "remove-class",
            "style",
        )
    private val opening = Regex(":([a-z-]+)\\(")

    fun parse(expression: String, style: (String) -> String): CosmeticProcedure? =
        runCatching {
            require(expression.length <= 4096)
            val program = Reader(style).program(expression, 0)
            val selector = (program.getValue("selector") as JsonPrimitive).content
            CosmeticProcedure(selector, operations = program.toString())
        }
        .getOrNull()

    private class Reader(val style: (String) -> String) {
        private var steps = 0

        fun program(raw: String, depth: Int): JsonObject {
            require(depth <= 4)
            var position = 0
            var base: String? = null
            var action: JsonArray? = null
            val output = mutableListOf<JsonElement>()
            while (position < raw.length) {
                val match =
                    opening.findAll(raw, position).firstOrNull {
                        it.groupValues[1] in operators
                    }
                val native =
                    raw.substring(position, match?.range?.first ?: raw.length)
                if (base == null) {
                    base = native.trim()
                    require(base.isEmpty() || selector(base))
                } else if (native.isNotEmpty()) {
                    require(action == null)
                    require(selector(native.trim()))
                    if (
                        base.isEmpty() &&
                            output.all {
                                (it as JsonArray)[0] == JsonPrimitive("path") ||
                                    it[0] == JsonPrimitive("watch")
                            } &&
                            native.first().isWhitespace() &&
                            native.trim().first() !in ">+~"
                    ) {
                        base = native.trim()
                    } else {
                        output.add(step("css", native))
                    }
                }
                if (match == null) {
                    break
                }
                require(action == null && ++steps <= 32)
                val close = close(raw, match.range.last)
                require(close >= 0)
                val argument = raw.substring(match.range.last + 1, close).trim()
                val name = match.groupValues[1]
                val plainNot =
                    name == "not" &&
                        opening.findAll(argument).none {
                            it.groupValues[1] in operators
                        }
                if (plainNot) {
                    require(selector(argument))
                }
                val entry =
                    when (name) {
                        "has-text",
                        "contains",
                        "-abp-contains" -> step("text", pattern(argument))
                        "min-text-length" -> {
                            require(argument.toIntOrNull() in 0..65536)
                            step("length", argument)
                        }
                        "has",
                        "-abp-has",
                        "if",
                        "if-not",
                        "not" ->
                            JsonArray(
                                listOf(
                                    JsonPrimitive(
                                        when (name) {
                                            "not" ->
                                                if (plainNot) {
                                                    "css"
                                                } else {
                                                    "has-not"
                                                }
                                            "if-not" -> "has-not"
                                            else -> "has"
                                        }
                                    ),
                                    if (plainNot) {
                                        JsonPrimitive(":not($argument)")
                                    } else {
                                        program(argument, depth + 1)
                                    },
                                )
                            )
                        "upward" -> {
                            require(
                                argument.toIntOrNull()?.let { it in 1..255 }
                                    ?: selector(argument)
                            )
                            step("upward", argument)
                        }
                        "matches-attr" -> {
                            val separator = argument.indexOf('=')
                            val attribute =
                                if (separator < 0) {
                                    argument
                                } else {
                                    argument.substring(0, separator)
                                }
                            val value =
                                if (separator < 0) {
                                    ""
                                } else {
                                    pattern(argument.substring(separator + 1))
                                }
                            JsonArray(
                                listOf(
                                    JsonPrimitive("attr"),
                                    JsonPrimitive(pattern(attribute)),
                                    JsonPrimitive(value),
                                )
                            )
                        }
                        "matches-css",
                        "matches-css-before",
                        "matches-css-after" -> {
                            val property = argument.substringBefore(':').trim()
                            require(
                                Regex("[a-z-]{1,64}").matches(property) &&
                                    ':' in argument
                            )
                            JsonArray(
                                listOf(
                                    JsonPrimitive("style"),
                                    JsonPrimitive(property),
                                    JsonPrimitive(
                                        pattern(
                                            argument.substringAfter(':').trim(),
                                            false,
                                        )
                                    ),
                                    JsonPrimitive(
                                        when (name) {
                                            "matches-css-before" -> "::before"
                                            "matches-css-after" -> "::after"
                                            else -> ""
                                        }
                                    ),
                                )
                            )
                        }
                        "matches-path" -> step("path", pattern(argument))
                        "watch-attr" -> {
                            require(
                                argument.split(',').all {
                                    it.trim().isEmpty() ||
                                        AdditionalScriptlets.attribute(it.trim())
                                }
                            )
                            step("watch", argument)
                        }
                        "remove" -> {
                            require(argument.isEmpty())
                            step("remove", "")
                        }
                        "remove-attr",
                        "remove-class" -> {
                            require(AdditionalScriptlets.attribute(argument))
                            step(name, argument)
                        }
                        "style" -> step("style-action", style(argument))
                        else -> error("Unsupported procedure")
                    }
                if (
                    name in setOf("remove", "remove-attr", "remove-class", "style")
                ) {
                    action = entry
                } else {
                    output.add(entry)
                }
                position = close + 1
            }
            require(base != null)
            require(depth > 0 || base.isNotEmpty())
            return JsonObject(
                mapOf(
                    "selector" to JsonPrimitive(base),
                    "steps" to JsonArray(output),
                    "action" to (action ?: step("hide", "")),
                )
            )
        }

        private fun selector(value: String): Boolean =
            value.length in 1..256 &&
                AdditionalScriptlets.selector(
                    if (value.first() in ">+~") {
                        ":scope $value"
                    } else {
                        value
                    }
                ) &&
                opening.findAll(value).none {
                    it.groupValues[1] in operators ||
                        it.groupValues[1] in
                            setOf(
                                "xpath",
                                "others",
                                "matches-media",
                                "-abp-properties",
                                "matches-prop",
                                "shadow",
                                "content",
                            )
                }

        private fun pattern(raw: String, unquote: Boolean = true): String {
            var value = raw.trim()
            val quoted = unquote && value.firstOrNull() in listOf('\'', '"')
            if (quoted) {
                require(value.length >= 2 && value.last() == value.first())
                val quote = value.first()
                val decoded = StringBuilder()
                var index = 1
                while (index < value.lastIndex) {
                    var char = value[index++]
                    if (char == '\\') {
                        require(index < value.lastIndex)
                        val next = value[index++]
                        if (next != quote && next != '\\') {
                            decoded.append('\\')
                        }
                        char = next
                    }
                    decoded.append(char)
                }
                value = decoded.toString()
            }
            require(
                (value.isNotEmpty() || quoted) &&
                    value.length <= 256 &&
                    ScriptletParser.pattern(value)
            )
            return value
        }

        private fun step(name: String, value: String) =
            JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(value)))

        private fun close(raw: String, start: Int): Int {
            var nesting = 0
            var quote: Char? = null
            var regex = false
            var escaped = false
            for (index in start until raw.length) {
                val char = raw[index]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (char == '\\') {
                    escaped = true
                    continue
                }
                if (quote != null) {
                    if (char == quote) {
                        quote = null
                    }
                    continue
                }
                if (
                    char == '/' &&
                        (regex ||
                            raw.substring(start + 1, index).trim().lastOrNull() in
                                listOf(null, '=', ':'))
                ) {
                    regex = !regex
                    continue
                }
                if (regex) {
                    continue
                }
                if (char in "\"'") {
                    quote = char
                    continue
                }
                if (char == '(') {
                    nesting++
                }
                if (char == ')') {
                    nesting--
                    if (nesting == 0) {
                        return index
                    }
                }
            }
            return -1
        }
    }
}
