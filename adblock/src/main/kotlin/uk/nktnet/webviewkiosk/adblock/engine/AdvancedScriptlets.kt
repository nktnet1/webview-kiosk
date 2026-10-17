package uk.nktnet.webviewkiosk.adblock.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/**
 * Trusted source provenance is checked before these operation-specific contracts.
 */
internal object AdvancedScriptlets {
    val aliases =
        mapOf(
            "trusted-replace-node-text" to ScriptletType.TRUSTED_REPLACE_NODE_TEXT,
            "trusted-rpnt" to ScriptletType.TRUSTED_REPLACE_NODE_TEXT,
            "replace-node-text" to ScriptletType.TRUSTED_REPLACE_NODE_TEXT,
            "rpnt" to ScriptletType.TRUSTED_REPLACE_NODE_TEXT,
            "trusted-click-element" to ScriptletType.TRUSTED_CLICK_ELEMENT,
            "trusted-replace-argument" to ScriptletType.TRUSTED_REPLACE_ARGUMENT,
            "trusted-replace-outbound-text" to
                ScriptletType.TRUSTED_REPLACE_OUTBOUND_TEXT,
            "trusted-suppress-native-method" to
                ScriptletType.TRUSTED_SUPPRESS_NATIVE_METHOD,
            "trusted-prevent-dom-bypass" to ScriptletType.TRUSTED_PREVENT_DOM_BYPASS,
            "trusted-override-element-method" to
                ScriptletType.TRUSTED_OVERRIDE_ELEMENT_METHOD,
            "trusted-create-html" to ScriptletType.TRUSTED_CREATE_HTML,
            "prevent-clipboard-write" to ScriptletType.PREVENT_CLIPBOARD_WRITE,
        )

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        fun arg(index: Int) = args.getOrElse(index) { "" }
        var arity = args.size
        val valid =
            when (type) {
                ScriptletType.TRUSTED_REPLACE_NODE_TEXT -> {
                    arity = maxOf(3, args.size)
                    args.size in setOf(3, 5, 7) &&
                        arg(0).isNotEmpty() &&
                        ScriptletParser.pattern(arg(0)) &&
                        replacementPattern(arg(1)) &&
                        options(args.drop(3)) { key, value ->
                            when (key) {
                                "condition",
                                "includes",
                                "excludes" -> ScriptletParser.pattern(value)
                                "sedCount" -> integer(value, 1, 1024)
                                "quitAfter" -> integer(value, 0, 30000)
                                else -> false
                            }
                        }
                }
                ScriptletType.TRUSTED_CLICK_ELEMENT -> {
                    arity = 3
                    args.size in 1..3 &&
                        clickSteps(arg(0)) &&
                        (arg(1).isEmpty() || assertions(arg(1))) &&
                        (arg(2).isEmpty() || integer(arg(2), 0, 30000))
                }
                ScriptletType.TRUSTED_REPLACE_ARGUMENT -> {
                    arity = maxOf(3, args.size)
                    args.size in setOf(3, 5) &&
                        method(arg(0)) &&
                        (arg(1) == "this" || integer(arg(1), -31, 31)) &&
                        replacement(arg(2)) &&
                        options(args.drop(3)) { key, value ->
                            key == "condition" && ScriptletParser.pattern(value)
                        }
                }
                ScriptletType.TRUSTED_REPLACE_OUTBOUND_TEXT -> {
                    arity = maxOf(3, args.size)
                    args.size in setOf(3, 5, 7) &&
                        method(arg(0)) &&
                        arg(1).isNotEmpty() &&
                        replacementPattern(arg(1)) &&
                        (!arg(2).startsWith("json:") ||
                            jsonString(arg(2).drop(5))) &&
                        options(args.drop(3)) { key, value ->
                            when (key) {
                                "condition" -> ScriptletParser.pattern(value)
                                "encoding" -> value == "base64"
                                else -> false
                            }
                        }
                }
                ScriptletType.TRUSTED_SUPPRESS_NATIVE_METHOD -> {
                    arity = 4
                    args.size in 2..4 &&
                        method(arg(0)) &&
                        arg(1).isNotEmpty() &&
                        arg(1).split('|').let { fields ->
                            fields.size <= 16 &&
                                fields.all { raw ->
                                    val value = raw.trim()
                                    value in
                                        setOf(
                                            "*",
                                            "true",
                                            "false",
                                            "null",
                                            "undefined",
                                        ) ||
                                        value.startsWith('"') &&
                                            value.endsWith('"') &&
                                            ScriptletParser.pattern(
                                                value.drop(1).dropLast(1)
                                            ) ||
                                        value.startsWith('/') &&
                                            ScriptletParser.pattern(value)
                                }
                        } &&
                        arg(2) in setOf("", "prevent", "abort") &&
                        ScriptletParser.pattern(arg(3))
                }
                ScriptletType.TRUSTED_PREVENT_DOM_BYPASS -> {
                    arity = 2
                    args.size == 2 && method(arg(0)) && method(arg(1))
                }
                ScriptletType.TRUSTED_OVERRIDE_ELEMENT_METHOD -> {
                    arity = 3
                    args.size in 1..3 &&
                        method(arg(0)) &&
                        (arg(1).isEmpty() ||
                            AdditionalScriptlets.selector(arg(1))) &&
                        (arg(2) == "throw" ||
                            ResponseScriptlets.trustedConstant(arg(2)))
                }
                ScriptletType.TRUSTED_CREATE_HTML -> {
                    arity = maxOf(3, args.size)
                    args.size in setOf(2, 3, 5, 7) &&
                        AdditionalScriptlets.selector(arg(0)) &&
                        arg(1).isNotEmpty() &&
                        (arg(2).isEmpty() || integer(arg(2), 0, 30000)) &&
                        options(args.drop(3)) { key, value ->
                            when (key) {
                                "limit" -> integer(value, 1, 4)
                                "runAt" ->
                                    value in
                                        setOf("loading", "interactive", "complete")
                                else -> false
                            }
                        }
                }
                ScriptletType.PREVENT_CLIPBOARD_WRITE -> {
                    arity = maxOf(1, args.size)
                    args.size in setOf(1, 3) &&
                        arg(0).isNotEmpty() &&
                        ScriptletParser.pattern(arg(0)) &&
                        options(args.drop(1)) { key, value ->
                            key == "excludeMatches" && ScriptletParser.pattern(value)
                        }
                }
                else -> false
            }
        return if (valid) {
            ScriptletInstruction(type, args + List(arity - args.size) { "" })
        } else {
            null
        }
    }

    internal fun method(value: String): Boolean =
        value.length in 1..256 &&
            value.split('.').let { parts ->
                parts.size <= 12 &&
                    parts.all {
                        it != "__proto__" &&
                            Regex("[A-Za-z_$][A-Za-z0-9_$]*").matches(it)
                    }
            }

    internal fun replacementPattern(value: String): Boolean {
        if (!value.startsWith('/') || value.lastIndexOf('/') == 0) {
            return ScriptletParser.pattern(value)
        }
        val end = value.lastIndexOf('/')
        val flags = value.substring(end + 1)
        return flags.count { it == 'g' } <= 1 &&
            ScriptletParser.pattern(
                value.substring(0, end + 1) + flags.replace("g", "")
            )
    }

    private fun replacement(value: String): Boolean =
        when {
            value.startsWith("repl:/") -> parseReplacement(value) != null
            value.startsWith("add:") ->
                value.drop(4).toDoubleOrNull()?.isFinite() == true
            else -> ResponseScriptlets.trustedConstant(value)
        }

    internal fun parseReplacement(raw: String): List<String>? {
        val value = raw.removePrefix("repl:")
        val ends =
            value.indices.filter { index ->
                value[index] == '/' &&
                    value.substring(0, index).takeLastWhile { it == '\\' }.length %
                        2 == 0
            }
        if (ends.size != 3 || ends.first() != 0) {
            return null
        }
        val (start, middle, end) = ends
        if (value.substring(end + 1).any { it !in "gims" }) {
            return null
        }
        val pattern = value.substring(start, middle + 1) + value.substring(end + 1)
        return if (replacementPattern(pattern)) {
            listOf(pattern, value.substring(middle + 1, end).replace("\\/", "/"))
        } else {
            null
        }
    }

    private fun jsonString(value: String): Boolean = runCatching {
        Json.parseToJsonElement(value)
    }
        .getOrNull()
        .let {
            it is JsonPrimitive && it.isString
        }

    private fun clickSteps(raw: String): Boolean {
        val separator = raw.firstOrNull()?.takeIf { it in ";|" } ?: ','
        val steps = raw.removePrefix(separator.toString()).split(separator)
        return steps.size in 1..16 &&
            steps.any { it.trim().toIntOrNull() == null } &&
            steps.all { step ->
                val value = step.trim()
                integer(value, 0, 30000) ||
                    !value.startsWith("xpath:") &&
                        !value.startsWith("when-visible:") &&
                        value.split(">>>").let { selectors ->
                            selectors.size <= 4 &&
                                selectors.all {
                                    AdditionalScriptlets.selector(it.trim())
                                }
                        }
            }
    }

    private fun assertions(raw: String): Boolean =
        raw.split(',').let { values ->
            values.size <= 8 &&
                values.all { value ->
                    value.substringBefore(':').trim().removePrefix("!") in
                        setOf("cookie", "localStorage") &&
                        value.substringAfter(':', "").trim().let {
                            it.isNotEmpty() && ScriptletParser.pattern(it)
                        }
                }
        }

    private fun integer(raw: String, min: Int, max: Int): Boolean =
        Regex("-?[0-9]{1,5}").matches(raw) && raw.toIntOrNull() in min..max

    private fun options(
        args: List<String>,
        valid: (String, String) -> Boolean,
    ): Boolean =
        args.size % 2 == 0 &&
            args.chunked(2).let { pairs ->
                pairs.map { it[0] }.distinct().size == pairs.size &&
                    pairs.all { valid(it[0], it[1]) } &&
                    !(pairs.any { it[0] == "condition" } &&
                        pairs.any { it[0] == "includes" })
            }
}
