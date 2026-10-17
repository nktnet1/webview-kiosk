package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.FilterSkipReason
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

internal data class ScriptletRule(
    val instruction: ScriptletInstruction?,
    val domains: DomainScope,
    val exception: Boolean,
    val targets: List<ScriptletTarget> = emptyList(),
)

/** A strict subset: unknown arguments discard the entire instruction. */
internal object ScriptletParser {
    private val aliases =
        mapOf(
            "set" to ScriptletType.SET,
            "set-constant" to ScriptletType.SET,
            "aopr" to ScriptletType.ABORT_READ,
            "abort-on-property-read" to ScriptletType.ABORT_READ,
            "aopw" to ScriptletType.ABORT_WRITE,
            "abort-on-property-write" to ScriptletType.ABORT_WRITE,
            "nowoif" to ScriptletType.PREVENT_WINDOW_OPEN,
            "no-window-open-if" to ScriptletType.PREVENT_WINDOW_OPEN,
            "window.open-defuser" to ScriptletType.PREVENT_WINDOW_OPEN,
            "prevent-window-open" to ScriptletType.PREVENT_WINDOW_OPEN,
            "aeld" to ScriptletType.PREVENT_ADD_EVENT_LISTENER,
            "addEventListener-defuser" to ScriptletType.PREVENT_ADD_EVENT_LISTENER,
            "prevent-addEventListener" to ScriptletType.PREVENT_ADD_EVENT_LISTENER,
            "nostif" to ScriptletType.PREVENT_SET_TIMEOUT,
            "no-setTimeout-if" to ScriptletType.PREVENT_SET_TIMEOUT,
            "setTimeout-defuser" to ScriptletType.PREVENT_SET_TIMEOUT,
            "prevent-setTimeout" to ScriptletType.PREVENT_SET_TIMEOUT,
            "acs" to ScriptletType.ABORT_CURRENT_SCRIPT,
            "acis" to ScriptletType.ABORT_CURRENT_SCRIPT,
            "abort-current-script" to ScriptletType.ABORT_CURRENT_SCRIPT,
            "abort-current-inline-script" to ScriptletType.ABORT_CURRENT_SCRIPT,
            "json-prune" to ScriptletType.JSON_PRUNE,
            "no-fetch-if" to ScriptletType.PREVENT_FETCH,
            "prevent-fetch" to ScriptletType.PREVENT_FETCH,
        )
    private val property = Regex("[a-zA-Z_$][a-zA-Z0-9_$]*")
    private val pathPart = Regex("[a-zA-Z0-9_$-]+")
    private val forbidden = setOf("__proto__", "prototype", "constructor")
    private val constants =
        setOf(
            "",
            "''",
            "undefined",
            "null",
            "true",
            "false",
            "[]",
            "{}",
            "emptyArr",
            "emptyObj",
            "noopFunc",
            "trueFunc",
            "falseFunc",
            "throwFunc",
        )

    fun parse(expression: String, trusted: Boolean = false): ScriptletInstruction? {
        val fields = arguments(expression) ?: return null
        val name = fields.first().removeSuffix(".js")
        ScriptletResources.aliases[name]?.let { resource ->
            return if (fields.size == 1) {
                validated(
                    if (resource in ScriptletResources.trustedFiles) {
                        ScriptletType.TRUSTED_RESOURCE
                    } else {
                        ScriptletType.RESOURCE
                    },
                    listOf(resource),
                    trusted,
                )
            } else {
                null
            }
        }
        if (name in setOf("noeval", "noeval-silent", "silent-noeval")) {
            return if (fields.size == 1) {
                validated(ScriptletType.PREVENT_EVAL, listOf(""))
            } else {
                null
            }
        }
        val type =
            aliases[name]
                ?: AdditionalScriptlets.aliases[name]
                ?: ResponseScriptlets.aliases[name]
                ?: AdvancedScriptlets.aliases[name]
                ?: JsonEditing.aliases[name]
                ?: ObjectEditing.aliases[name]
                ?: ObjectPruning.aliases[name]
                ?: return null
        val args =
            if (name == "prevent-innerHTML") {
                listOf("innerHTML") + fields.drop(1)
            } else {
                fields.drop(1)
            }
        if (
            type == ScriptletType.PREVENT_EVAL && args.firstOrNull().isNullOrEmpty()
        ) {
            return null
        }
        return validated(type, args, trusted)
    }

    fun skipReason(expression: String, trusted: Boolean): FilterSkipReason {
        val fields =
            arguments(expression) ?: return FilterSkipReason.SCRIPTLET_ARGUMENTS
        val name = fields.first().removeSuffix(".js")
        if (
            ScriptletResources.aliases[name] in ScriptletResources.trustedFiles &&
                !trusted
        ) {
            return FilterSkipReason.SCRIPTLET_TRUST
        }
        val type =
            aliases[name]
                ?: AdditionalScriptlets.aliases[name]
                ?: ResponseScriptlets.aliases[name]
                ?: AdvancedScriptlets.aliases[name]
                ?: JsonEditing.aliases[name]
                ?: ObjectEditing.aliases[name]
                ?: ObjectPruning.aliases[name]
        if (type?.requiresTrust == true && !trusted) {
            return FilterSkipReason.SCRIPTLET_TRUST
        }
        return if (
            type != null ||
                name in ScriptletResources.aliases ||
                name in setOf("noeval", "noeval-silent", "silent-noeval")
        ) {
            FilterSkipReason.SCRIPTLET_ARGUMENTS
        } else {
            FilterSkipReason.SCRIPTLET_NAME
        }
    }

    internal fun validated(
        type: ScriptletType,
        arguments: List<String>,
        trusted: Boolean = false,
    ): ScriptletInstruction? {
        if (type.requiresTrust && !trusted) {
            return null
        }
        val maxLength =
            if (
                type in AdvancedScriptlets.aliases.values ||
                    type in JsonEditing.types ||
                    type in ObjectEditing.types
            ) {
                8192
            } else {
                512
            }
        if (arguments.any { it.length > maxLength || it.any(Char::isISOControl) }) {
            return null
        }
        if (type == ScriptletType.PREVENT_ADD_EVENT_LISTENER) {
            return listener(arguments)
        }
        if (type in JsonEditing.types) {
            return JsonEditing.validated(type, arguments)
        }
        if (type in ObjectEditing.types) {
            return ObjectEditing.validated(type, arguments)
        }
        if (type in ObjectPruning.types) {
            return ObjectPruning.validated(type, arguments)
        }
        if (type in CookieScriptlets.types) {
            return CookieScriptlets.validated(type, arguments)
        }
        if (type in AdvancedScriptlets.aliases.values) {
            return AdvancedScriptlets.validated(type, arguments)
        }
        if (type in ResponseScriptlets.aliases.values) {
            return ResponseScriptlets.validated(type, arguments)
        }
        if (type.ordinal > ScriptletType.PREVENT_FETCH.ordinal) {
            return AdditionalScriptlets.validated(type, arguments)
        }
        val args = arguments.toMutableList()
        fun patterns(vararg indexes: Int): Boolean = indexes.all {
            pattern(args.getOrElse(it) { "" })
        }
        val valid =
            when (type) {
                ScriptletType.SET ->
                    args.size == 2 && property(args[0]) && constant(args[1])

                ScriptletType.ABORT_READ,
                ScriptletType.ABORT_WRITE -> args.size == 1 && property(args[0])

                ScriptletType.PREVENT_WINDOW_OPEN ->
                    args.size <= 1 && patterns(0) && args.firstOrNull() != "debug"

                ScriptletType.PREVENT_ADD_EVENT_LISTENER ->
                    args.size in 1..2 &&
                        args.any { it.isNotEmpty() } &&
                        patterns(0, 1)

                ScriptletType.PREVENT_SET_TIMEOUT ->
                    args.size in 1..2 &&
                        args.any { it.isNotEmpty() } &&
                        patterns(0) &&
                        delay(args.getOrElse(1) { "" })

                ScriptletType.ABORT_CURRENT_SCRIPT ->
                    args.size in 1..3 && property(args[0]) && patterns(1, 2)

                ScriptletType.JSON_PRUNE ->
                    args.size in 1..2 &&
                        paths(args[0]) &&
                        (args.size == 1 || args[1].isEmpty() || paths(args[1]))

                ScriptletType.PREVENT_FETCH ->
                    args.size in 1..2 &&
                        requestMatch(args[0]) &&
                        (args.size == 1 ||
                            args[1] in setOf("", "emptyStr", "emptyObj", "emptyArr"))
                else -> false
            }
        if (!valid) {
            return null
        }
        // Canonical arity makes aliases, omitted defaults, and exceptions identical.
        val arity =
            when (type) {
                ScriptletType.SET,
                ScriptletType.PREVENT_ADD_EVENT_LISTENER,
                ScriptletType.PREVENT_SET_TIMEOUT,
                ScriptletType.JSON_PRUNE,
                ScriptletType.PREVENT_FETCH -> 2
                ScriptletType.ABORT_CURRENT_SCRIPT -> 3
                else -> 1
            }
        while (args.size < arity) {
            args += ""
        }
        if (type == ScriptletType.SET) {
            args[1] =
                when (args[1]) {
                    "''" -> ""
                    "emptyArr" -> "[]"
                    "emptyObj" -> "{}"
                    else -> args[1]
                }
        }
        return ScriptletInstruction(type, args.toList())
    }

    private fun listener(args: List<String>): ScriptletInstruction? {
        if (args.size !in setOf(1, 2, 4, 6, 8)) {
            return null
        }
        val event = args[0]
        val callback = args.getOrElse(1) { "" }
        if (
            (event.isEmpty() && callback.isEmpty()) ||
                !pattern(event) ||
                !pattern(callback)
        ) {
            return null
        }
        val options = args.drop(2).chunked(2)
        if (
            options.map { it[0] }.distinct().size != options.size ||
                options.any { (key, value) ->
                    when (key) {
                        "runAt" ->
                            value !in setOf("", "loading", "interactive", "complete")
                        "protect" -> value !in setOf("", "0", "1")
                        "elements" ->
                            value.isNotEmpty() &&
                                value !in setOf("window", "document") &&
                                (!AdditionalScriptlets.selector(value) ||
                                    ":scope" in value)
                        else -> true
                    }
                }
        ) {
            return null
        }
        val values = options.associate { it[0] to it[1] }
        val stage =
            values["runAt"].orEmpty().takeUnless { it == "loading" }.orEmpty()
        val elements = values["elements"].orEmpty()
        val protect =
            if (values["protect"] == "1") {
                "1"
            } else {
                ""
            }
        val canonical = listOf(event, callback)
        return ScriptletInstruction(
            ScriptletType.PREVENT_ADD_EVENT_LISTENER,
            if (stage.isEmpty() && elements.isEmpty() && protect.isEmpty()) {
                canonical
            } else {
                canonical +
                    listOf("runAt", stage, "elements", elements, "protect", protect)
            },
        )
    }

    /**
     * uBO quotes whole arguments; unquoted quotes inside patterns remain literal.
     */
    internal fun arguments(expression: String): List<String>? {
        if (
            !expression.startsWith("+js(") ||
                !expression.endsWith(')') ||
                expression.length > 8192
        ) {
            return null
        }
        val body = expression.substring(4, expression.length - 1)
        val result = mutableListOf<String>()
        var position = 0
        var fieldLimit = 8
        while (position <= body.length) {
            while (body.getOrNull(position)?.isWhitespace() == true) {
                position++
            }
            val quote =
                body.getOrNull(position).takeIf { it in listOf('\'', '"', '`') }
            if (quote != null) {
                position++
            }
            val value = StringBuilder()
            var closed = quote == null
            while (position < body.length) {
                val char = body[position++]
                if (
                    char == '\\' &&
                        position < body.length &&
                        (body[position] == ',' ||
                            body[position] == quote ||
                            body[position] == '\\' && quote != null)
                ) {
                    value.append(body[position++])
                } else if (quote != null && char == quote) {
                    closed = true
                    break
                } else if (quote == null && char == ',') {
                    position--
                    break
                } else {
                    value.append(char)
                }
            }
            if (!closed || value.length > 8192 || value.any(Char::isISOControl)) {
                return null
            }
            if (quote != null) {
                while (body.getOrNull(position)?.isWhitespace() == true) {
                    position++
                }
            }
            result +=
                if (quote == null) {
                    value.toString().trim()
                } else {
                    value.toString()
                }
            if (result.size == 1) {
                val name = result.single().removeSuffix(".js")
                if (
                    name == "trusted-set-cookie" ||
                        name == "trusted-set-cookie-reload"
                ) {
                    fieldLimit = CookieScriptlets.MAX_ARGUMENTS + 1
                } else if (
                    aliases[name] == ScriptletType.PREVENT_ADD_EVENT_LISTENER
                ) {
                    fieldLimit = 9
                }
            }
            if (result.size > fieldLimit) {
                return null
            }
            if (position == body.length) {
                break
            }
            if (body[position++] != ',') {
                return null
            }
        }
        return result
    }

    internal fun property(value: String): Boolean =
        value.length in 1..256 &&
            value.split('.').let { parts ->
                parts.size <= 12 &&
                    parts.all {
                        property.matches(it) &&
                            it != "__proto__" &&
                            it != "constructor"
                    }
            }

    internal fun constant(value: String): Boolean =
        value in constants ||
            (Regex("-?[0-9]{1,5}").matches(value) &&
                (value.toIntOrNull() ?: 32768) in -32767..32767)

    internal fun paths(value: String): Boolean =
        value.isNotBlank() &&
            value.split(' ').let { paths ->
                paths.size <= 16 &&
                    paths.all { path ->
                        val parts = path.split('.')
                        parts.size <= 24 &&
                            parts.last() !in setOf("[]", "{}", "[-]", "{-}") &&
                            parts.all {
                                it !in forbidden &&
                                    (pathPart.matches(it) ||
                                        it in setOf("[]", "{}", "*", "[-]", "{-}"))
                            }
                    }
            }

    internal fun delay(value: String): Boolean {
        val raw = value.removePrefix("!")
        if (raw.isEmpty()) {
            return value.isEmpty()
        }
        if (Regex("[0-9]{1,9}").matches(raw)) {
            return true
        }
        if (!Regex("[0-9]{0,9}-[0-9]{0,9}").matches(raw) || raw == "-") {
            return false
        }
        val low = raw.substringBefore('-').toIntOrNull() ?: 0
        val high = raw.substringAfter('-').toIntOrNull() ?: Int.MAX_VALUE
        return low <= high
    }

    internal fun requestMatch(value: String): Boolean =
        value.isNotBlank() &&
            value.split(' ').let { parts ->
                parts.size <= 8 &&
                    parts.all { part ->
                        val colon = part.indexOf(':')
                        if (
                            colon > 0 &&
                                part.substring(0, colon) in setOf("url", "method")
                        ) {
                            pattern(part.substring(colon + 1))
                        } else {
                            // A colon in an URL/regex is literal; unknown named
                            // fields are not.
                            (colon < 0 ||
                                part.startsWith('/') ||
                                part.contains("://")) && pattern(part)
                        }
                    }
            }

    /**
     * Bounded regex subset shared by all call hooks. No lookarounds, backreferences,
     * repeated groups, or multiple repeats. Repeats require an absolute start
     * anchor; alternations have a fixed branching budget. Java compiles only after
     * this check; JS compiles once per selected rule.
     */
    internal fun pattern(value: String): Boolean {
        val raw = value.removePrefix("!")
        if (raw.length > 256) {
            return false
        }
        if (!raw.startsWith('/') || raw.lastIndexOf('/') == 0) {
            return true
        }
        val end = raw.lastIndexOf('/')
        val flags = raw.substring(end + 1)
        // Paths such as /i/api/1.1/flow/viewer.json are literal predicates.
        if (flags.any { it !in "dgimsuvy" }) {
            return true
        }
        if (flags.any { it !in "ims" } || flags.toSet().size != flags.length) {
            return false
        }
        val regex = raw.substring(1, end)
        var inClass = false
        var escaped = false
        var repeats = 0
        var depth = 0
        var alternatives = 0
        var topAlternative = false
        var previous = ' '
        for ((index, char) in regex.withIndex()) {
            if (escaped) {
                if (
                    char !in "\\/.^$*+?()[]{}|bBdDsSwWfnrtxu" ||
                        (char == 'x' &&
                            regex.substring(index + 1).take(2).let {
                                it.length != 2 ||
                                    it.any { c -> c.digitToIntOrNull(16) == null }
                            }) ||
                        (char == 'u' &&
                            regex.substring(index + 1).take(4).let {
                                it.length != 4 ||
                                    it.any { c -> c.digitToIntOrNull(16) == null }
                            })
                ) {
                    return false
                }
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '[') {
                inClass = true
            } else if (char == ']') {
                inClass = false
            } else if (!inClass) {
                if (
                    char == '(' &&
                        regex.getOrNull(index + 1) == '?' &&
                        regex.getOrNull(index + 2) != ':'
                ) {
                    return false
                }
                if (char in "*+{" || char == '?' && previous != '(') {
                    if (
                        previous in ")^$" ||
                            previous in "bB" && regex.getOrNull(index - 2) == '\\' ||
                            char == '{' ||
                            ++repeats > 1
                    ) {
                        return false
                    }
                }
                if (char == '|') {
                    if (++alternatives > 4) {
                        return false
                    }
                    if (depth == 0) {
                        topAlternative = true
                    }
                }
                if (char == '(') {
                    depth++
                } else if (char == ')') {
                    depth--
                }
            }
            previous = char
        }
        return !(repeats > 0 &&
            (!regex.startsWith('^') || topAlternative || 'm' in flags)) &&
            !escaped &&
            !inClass &&
            runCatching { Regex(regex) }.isSuccess
    }
}
