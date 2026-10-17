package uk.nktnet.webviewkiosk.adblock.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Response operations accept data only; no replacement functions or remote code. */
internal object ResponseScriptlets {
    val networkTypes =
        setOf(
            ScriptletType.JSON_EDIT_FETCH,
            ScriptletType.JSON_EDIT_XHR,
            ScriptletType.JSONL_EDIT_FETCH,
            ScriptletType.JSONL_EDIT_XHR,
            ScriptletType.TRUSTED_JSON_EDIT_FETCH,
            ScriptletType.TRUSTED_JSON_EDIT_XHR,
            ScriptletType.TRUSTED_JSONL_EDIT_FETCH,
            ScriptletType.TRUSTED_JSONL_EDIT_XHR,
            ScriptletType.JSON_EDIT_FETCH_REQUEST,
            ScriptletType.JSON_EDIT_XHR_REQUEST,
            ScriptletType.TRUSTED_JSON_EDIT_FETCH_REQUEST,
            ScriptletType.TRUSTED_JSON_EDIT_XHR_REQUEST,
            ScriptletType.PREVENT_XHR,
            ScriptletType.JSON_PRUNE_FETCH,
            ScriptletType.JSON_PRUNE_XHR,
            ScriptletType.XML_PRUNE,
            ScriptletType.M3U_PRUNE,
            ScriptletType.TRUSTED_REPLACE_FETCH,
            ScriptletType.TRUSTED_REPLACE_XHR,
            ScriptletType.TRUSTED_PREVENT_XHR,
            ScriptletType.MPEGDASH_PRUNE,
        )
    val aliases =
        mapOf(
            "prevent-xhr" to ScriptletType.PREVENT_XHR,
            "no-xhr-if" to ScriptletType.PREVENT_XHR,
            "json-prune-fetch-response" to ScriptletType.JSON_PRUNE_FETCH,
            "json-prune-xhr-response" to ScriptletType.JSON_PRUNE_XHR,
            "xml-prune" to ScriptletType.XML_PRUNE,
            "m3u-prune" to ScriptletType.M3U_PRUNE,
            "mpegdash-prune" to ScriptletType.MPEGDASH_PRUNE,
            "trusted-replace-fetch-response" to ScriptletType.TRUSTED_REPLACE_FETCH,
            "trusted-rpfr" to ScriptletType.TRUSTED_REPLACE_FETCH,
            "trusted-replace-xhr-response" to ScriptletType.TRUSTED_REPLACE_XHR,
            "trusted-prevent-fetch" to ScriptletType.TRUSTED_PREVENT_FETCH,
            "trusted-prevent-xhr" to ScriptletType.TRUSTED_PREVENT_XHR,
            "trusted-set-local-storage-item" to ScriptletType.TRUSTED_LOCAL_STORAGE,
            "trusted-set-session-storage-item" to
                ScriptletType.TRUSTED_SESSION_STORAGE,
            "trusted-set-cookie" to ScriptletType.TRUSTED_SET_COOKIE,
            "trusted-set-cookie-reload" to ScriptletType.TRUSTED_SET_COOKIE_RELOAD,
            "trusted-set" to ScriptletType.TRUSTED_SET,
            "trusted-set-constant" to ScriptletType.TRUSTED_SET,
            "trusted-set-attr" to ScriptletType.TRUSTED_SET_ATTRIBUTE,
        )

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        fun arg(index: Int) = args.getOrElse(index) { "" }
        var arity = args.size
        val valid =
            when (type) {
                ScriptletType.PREVENT_XHR,
                ScriptletType.TRUSTED_PREVENT_XHR,
                ScriptletType.TRUSTED_PREVENT_FETCH -> {
                    arity = 2
                    args.size in 1..2 &&
                        ScriptletParser.requestMatch(arg(0)) &&
                        arg(1) != "true" &&
                        !arg(1).startsWith("war:") &&
                        !arg(1).startsWith("length:") &&
                        (type != ScriptletType.PREVENT_XHR ||
                            arg(1) in setOf("", "emptyStr", "emptyObj", "emptyArr"))
                }
                ScriptletType.JSON_PRUNE_FETCH,
                ScriptletType.JSON_PRUNE_XHR -> {
                    if (
                        args.size !in setOf(1, 2, 4, 6) ||
                            !ScriptletParser.paths(arg(0)) ||
                            arg(1).isNotEmpty() && !ScriptletParser.paths(arg(1))
                    ) {
                        return null
                    }
                    val options = args.drop(2).chunked(2)
                    if (
                        options.map { it[0] }.distinct().size != options.size ||
                            options.any { (key, value) ->
                                when (key) {
                                    "propsToMatch" ->
                                        value.isNotEmpty() &&
                                            !ScriptletParser.requestMatch(value)
                                    "stackToMatch" -> !ScriptletParser.pattern(value)
                                    else -> true
                                }
                            }
                    ) {
                        return null
                    }
                    val values = options.associate { it[0] to it[1] }
                    return ScriptletInstruction(
                        type,
                        listOf(
                            arg(0),
                            arg(1),
                            "propsToMatch",
                            values["propsToMatch"].orEmpty(),
                            "stackToMatch",
                            values["stackToMatch"].orEmpty(),
                        ),
                    )
                }
                ScriptletType.XML_PRUNE -> {
                    arity = 3
                    args.size in 1..3 &&
                        (if (arg(0).startsWith("xpath(")) {
                            arg(0).endsWith(')') &&
                                arg(0).length in 8..256 &&
                                arg(0).count { it == '(' } <= 8
                        } else {
                            AdditionalScriptlets.selector(arg(0))
                        }) &&
                        (arg(1).isEmpty() ||
                            AdditionalScriptlets.selector(arg(1))) &&
                        ScriptletParser.pattern(arg(2))
                }
                ScriptletType.MPEGDASH_PRUNE -> {
                    arity = 2
                    args.size in 1..2 &&
                        AdditionalScriptlets.selector(arg(0)) &&
                        (arg(1).isEmpty() || ScriptletParser.requestMatch(arg(1)))
                }
                ScriptletType.M3U_PRUNE -> {
                    arity = 2
                    args.size in 1..2 &&
                        arg(0).isNotEmpty() &&
                        args.all { ScriptletParser.pattern(it) } &&
                        args.none { value ->
                            value.startsWith('!') ||
                                (!value.startsWith('/') && '*' in value) ||
                                value.startsWith('/') &&
                                    'm' in value.substringAfterLast('/')
                        }
                }
                ScriptletType.TRUSTED_REPLACE_FETCH,
                ScriptletType.TRUSTED_REPLACE_XHR -> {
                    arity = 3
                    args.size in 2..3 &&
                        arg(0).isNotEmpty() &&
                        ScriptletParser.pattern(arg(0)) &&
                        !arg(0).startsWith('!') &&
                        (arg(2).isEmpty() || ScriptletParser.requestMatch(arg(2)))
                }
                ScriptletType.TRUSTED_LOCAL_STORAGE,
                ScriptletType.TRUSTED_SESSION_STORAGE ->
                    args.size == 2 && arg(0).isNotEmpty() && !arg(1).contains('$')
                ScriptletType.TRUSTED_SET ->
                    args.size == 2 &&
                        ScriptletParser.property(arg(0)) &&
                        trustedConstant(arg(1))
                ScriptletType.TRUSTED_SET_ATTRIBUTE -> {
                    arity = 3
                    args.size in 2..3 &&
                        AdditionalScriptlets.selector(arg(0)) &&
                        literalAttribute(arg(1))
                }
                else -> false
            }
        return if (valid) {
            val normalized = args.toMutableList()
            if (type == ScriptletType.TRUSTED_SET) {
                normalized[1] =
                    when (arg(1)) {
                        "''" -> ""
                        "emptyArr" -> "[]"
                        "emptyObj" -> "{}"
                        else -> arg(1)
                    }
            }
            ScriptletInstruction(type, normalized + List(arity - args.size) { "" })
        } else {
            null
        }
    }

    private fun literalAttribute(raw: String): Boolean {
        if (!AdditionalScriptlets.attribute(raw)) {
            return false
        }
        val name = raw.lowercase()
        return name.startsWith("data-") ||
            name.startsWith("aria-") ||
            name in
                setOf(
                    "class",
                    "id",
                    "role",
                    "title",
                    "alt",
                    "hidden",
                    "disabled",
                    "checked",
                    "selected",
                    "readonly",
                    "tabindex",
                    "dir",
                    "lang",
                    "open",
                    "value",
                    "name",
                )
    }

    internal fun trustedConstant(raw: String): Boolean {
        if (ScriptletParser.constant(raw)) {
            return true
        }
        if (
            !raw.startsWith("json:") && !(raw.startsWith('{') && raw.endsWith('}'))
        ) {
            return false
        }
        val value =
            runCatching {
                Json.parseToJsonElement(raw.removePrefix("json:"))
            }
                .getOrNull() ?: return false
        fun bounded(element: JsonElement, depth: Int): Boolean =
            depth <= 16 &&
                when (element) {
                    is JsonObject -> element.values.all { bounded(it, depth + 1) }
                    is JsonArray -> element.all { bounded(it, depth + 1) }
                    else -> true
                }
        return bounded(value, 0) &&
            (raw.startsWith("json:") || value is JsonObject && "value" in value)
    }
}
