package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Unsupported option forms are skipped, including logging-only invocations. */
internal object AdditionalScriptlets {
    val aliases =
        mapOf(
            "set-attr" to ScriptletType.SET_ATTRIBUTE,
            "remove-attr" to ScriptletType.REMOVE_ATTRIBUTE,
            "ra" to ScriptletType.REMOVE_ATTRIBUTE,
            "href-sanitizer" to ScriptletType.HREF_SANITIZER,
            "urlskip" to ScriptletType.HREF_SANITIZER,
            "abort-on-stack-trace" to ScriptletType.ABORT_STACK_TRACE,
            "aost" to ScriptletType.ABORT_STACK_TRACE,
            "noeval-if" to ScriptletType.PREVENT_EVAL,
            "prevent-eval-if" to ScriptletType.PREVENT_EVAL,
            "prevent-bab" to ScriptletType.PREVENT_BAB,
            "bab-defuser" to ScriptletType.PREVENT_BAB,
            "nobab" to ScriptletType.PREVENT_BAB,
            "prevent-dialog" to ScriptletType.PREVENT_DIALOG,
            "freeze-element-property" to ScriptletType.PREVENT_ELEMENT_PROPERTY,
            "prevent-innerHTML" to ScriptletType.PREVENT_ELEMENT_PROPERTY,
            "prevent-navigation" to ScriptletType.PREVENT_NAVIGATION,
            "prevent-setInterval" to ScriptletType.PREVENT_SET_INTERVAL,
            "no-setInterval-if" to ScriptletType.PREVENT_SET_INTERVAL,
            "nosiif" to ScriptletType.PREVENT_SET_INTERVAL,
            "setInterval-defuser" to ScriptletType.PREVENT_SET_INTERVAL,
            "prevent-requestAnimationFrame" to ScriptletType.PREVENT_ANIMATION_FRAME,
            "no-requestAnimationFrame-if" to ScriptletType.PREVENT_ANIMATION_FRAME,
            "norafif" to ScriptletType.PREVENT_ANIMATION_FRAME,
            "spoof-css" to ScriptletType.SPOOF_CSS,
            "set-cookie" to ScriptletType.SET_COOKIE,
            "set-cookie-reload" to ScriptletType.SET_COOKIE_RELOAD,
            "remove-cookie" to ScriptletType.REMOVE_COOKIE,
            "cookie-remover" to ScriptletType.REMOVE_COOKIE,
            "set-local-storage-item" to ScriptletType.SET_LOCAL_STORAGE,
            "set-session-storage-item" to ScriptletType.SET_SESSION_STORAGE,
            "adjust-setInterval" to ScriptletType.ADJUST_SET_INTERVAL,
            "nano-setInterval-booster" to ScriptletType.ADJUST_SET_INTERVAL,
            "nano-sib" to ScriptletType.ADJUST_SET_INTERVAL,
            "adjust-setTimeout" to ScriptletType.ADJUST_SET_TIMEOUT,
            "nano-setTimeout-booster" to ScriptletType.ADJUST_SET_TIMEOUT,
            "nano-stb" to ScriptletType.ADJUST_SET_TIMEOUT,
            "prevent-refresh" to ScriptletType.PREVENT_REFRESH,
            "refresh-defuser" to ScriptletType.PREVENT_REFRESH,
            "remove-class" to ScriptletType.REMOVE_CLASS,
            "rc" to ScriptletType.REMOVE_CLASS,
            "webrtc-if" to ScriptletType.WEBRTC_IF,
            "window.name-defuser" to ScriptletType.CLEAR_WINDOW_NAME,
            "alert-buster" to ScriptletType.ALERT_BUSTER,
            "nowebrtc" to ScriptletType.PREVENT_WEBRTC,
            "disable-newtab-links" to ScriptletType.DISABLE_NEWTAB_LINKS,
            "call-nothrow" to ScriptletType.CALL_NOTHROW,
            "remove-node-text" to ScriptletType.REMOVE_NODE_TEXT,
            "rmnt" to ScriptletType.REMOVE_NODE_TEXT,
            "prevent-canvas" to ScriptletType.PREVENT_CANVAS,
            "multiup" to ScriptletType.MULTIUP,
        )

    fun validated(type: ScriptletType, input: List<String>): ScriptletInstruction? {
        val args = input.toMutableList()
        fun arg(index: Int) = args.getOrElse(index) { "" }
        fun patterns(vararg indexes: Int) = indexes.all {
            ScriptletParser.pattern(arg(it))
        }
        var arity = input.size
        val valid =
            when (type) {
                ScriptletType.PREVENT_SET_INTERVAL -> {
                    arity = 2
                    args.size in 1..2 &&
                        args.any { it.isNotEmpty() } &&
                        patterns(0) &&
                        ScriptletParser.delay(arg(1))
                }
                ScriptletType.PREVENT_ANIMATION_FRAME ->
                    args.size == 1 && arg(0).isNotEmpty() && patterns(0)
                ScriptletType.ADJUST_SET_TIMEOUT,
                ScriptletType.ADJUST_SET_INTERVAL -> {
                    arity = 3
                    args.size <= 3 &&
                        patterns(0) &&
                        (arg(1) in setOf("", "*") || unsigned(arg(1), 86_400_000)) &&
                        (arg(2).isEmpty() ||
                            Regex("[0-9]+(?:\\.[0-9]+)?").matches(arg(2)) &&
                                arg(2).toDoubleOrNull()?.isFinite() == true)
                }
                ScriptletType.PREVENT_EVAL -> args.size == 1 && patterns(0)
                ScriptletType.ABORT_STACK_TRACE ->
                    args.size == 2 &&
                        ScriptletParser.property(arg(0)) &&
                        arg(1).isNotEmpty() &&
                        patterns(1)
                ScriptletType.SET_LOCAL_STORAGE,
                ScriptletType.SET_SESSION_STORAGE ->
                    args.size == 2 &&
                        arg(0).isNotEmpty() &&
                        if (arg(1) == $$"$remove$") {
                            patterns(0)
                        } else {
                            safeValue(arg(1)) ||
                                arg(1) in
                                    setOf(
                                        "undefined",
                                        "null",
                                        "{}",
                                        "[]",
                                        "emptyObj",
                                        "emptyArr",
                                        "",
                                    )
                        }
                ScriptletType.SET_COOKIE,
                ScriptletType.SET_COOKIE_RELOAD -> {
                    arity = 3
                    args.size in 2..3 &&
                        arg(0).isNotEmpty() &&
                        safeValue(arg(1)) &&
                        arg(2) in setOf("", "/", "none")
                }
                ScriptletType.REMOVE_COOKIE ->
                    args.size == 1 && arg(0).isNotEmpty() && patterns(0)
                ScriptletType.SET_ATTRIBUTE -> {
                    arity = 3
                    args.size in 2..3 &&
                        selector(arg(0)) &&
                        attribute(arg(1)) &&
                        arg(1).lowercase() !in
                            setOf(
                                "src",
                                "srcdoc",
                                "href",
                                "action",
                                "formaction",
                                "style",
                            ) &&
                        !arg(1).startsWith("on", true) &&
                        (arg(2) in setOf("", "true", "false") ||
                            unsigned(arg(2), 32767) ||
                            arg(2).startsWith('[') &&
                                arg(2).endsWith(']') &&
                                attribute(arg(2).drop(1).dropLast(1)))
                }
                ScriptletType.REMOVE_ATTRIBUTE,
                ScriptletType.REMOVE_CLASS -> {
                    arity = 3
                    args.size in 1..3 &&
                        arg(0).split('|').let { tokens ->
                            tokens.size <= 16 && tokens.all(::attribute)
                        } &&
                        (arg(1).isEmpty() || selector(arg(1))) &&
                        (arg(2).isEmpty() ||
                            arg(2).split(' ').let { words ->
                                words.size <= 3 &&
                                    words.distinct().size == words.size &&
                                    words.all {
                                        it in setOf("asap", "stay", "complete")
                                    }
                            })
                }
                ScriptletType.REMOVE_NODE_TEXT -> {
                    arity = 2
                    args.size in 1..2 && arg(0).isNotEmpty() && patterns(0, 1)
                }
                ScriptletType.PREVENT_ELEMENT_PROPERTY -> {
                    arity = 3
                    args.size in 1..3 &&
                        arg(0) in
                            setOf(
                                "innerHTML",
                                "outerHTML",
                                "textContent",
                                "src",
                                "text",
                            ) &&
                        (arg(1).isEmpty() || selector(arg(1))) &&
                        patterns(2)
                }
                ScriptletType.PREVENT_DIALOG -> {
                    arity = 1
                    args.size <= 1 && (arg(0).isEmpty() || selector(arg(0)))
                }
                ScriptletType.PREVENT_CANVAS -> {
                    arity = 1
                    args.size <= 1 && patterns(0)
                }
                ScriptletType.SPOOF_CSS ->
                    args.size in setOf(3, 5, 7) &&
                        selector(arg(0)) &&
                        args.drop(1).chunked(2).all { (name, value) ->
                            name in
                                setOf(
                                    "display",
                                    "visibility",
                                    "opacity",
                                    "position",
                                    "width",
                                    "height",
                                    "top",
                                    "left",
                                    "right",
                                    "bottom",
                                    "_rectx",
                                    "_recty",
                                    "_rectw",
                                    "_recth",
                                ) &&
                                value.length in 1..64 &&
                                value.none { it in "{};<>" } &&
                                (name !in
                                    setOf(
                                        "width",
                                        "height",
                                        "_rectx",
                                        "_recty",
                                        "_rectw",
                                        "_recth",
                                    ) ||
                                    Regex("-?[0-9]+(?:\\.[0-9]+)?(?:px)?")
                                        .matches(value))
                        }
                ScriptletType.CALL_NOTHROW ->
                    args.size == 1 && ScriptletParser.property(arg(0))
                ScriptletType.CLEAR_WINDOW_NAME,
                ScriptletType.DISABLE_NEWTAB_LINKS,
                ScriptletType.PREVENT_WEBRTC,
                ScriptletType.PREVENT_BAB,
                ScriptletType.ALERT_BUSTER,
                ScriptletType.MULTIUP -> args.isEmpty()
                ScriptletType.PREVENT_REFRESH -> {
                    arity = 1
                    args.size <= 1 && (arg(0).isEmpty() || unsigned(arg(0), 86400))
                }
                ScriptletType.WEBRTC_IF,
                ScriptletType.PREVENT_NAVIGATION ->
                    args.size == 1 && arg(0).isNotEmpty() && patterns(0)
                ScriptletType.HREF_SANITIZER -> {
                    arity = 2
                    args.size in 1..2 &&
                        selector(arg(0)) &&
                        (arg(1) in setOf("", "text") ||
                            arg(1).startsWith('[') &&
                                arg(1).endsWith(']') &&
                                attribute(arg(1).drop(1).dropLast(1)) ||
                            arg(1).startsWith('?') && attribute(arg(1).drop(1)))
                }
                ScriptletType.RESOURCE ->
                    args.size == 1 && arg(0) in ScriptletResources.files
                else -> false
            }
        if (!valid) {
            return null
        }
        while (args.size < arity) {
            args += ""
        }
        if (
            type in
                setOf(
                    ScriptletType.SET_LOCAL_STORAGE,
                    ScriptletType.SET_SESSION_STORAGE,
                )
        ) {
            if (args[1] == "emptyObj") {
                args[1] = "{}"
            }
            if (args[1] == "emptyArr") {
                args[1] = "[]"
            }
        }
        return ScriptletInstruction(type, args.toList())
    }

    private fun unsigned(value: String, limit: Int): Boolean =
        Regex("[0-9]{1,9}").matches(value) &&
            (value.toIntOrNull() ?: Int.MAX_VALUE) <= limit

    private fun attribute(value: String): Boolean =
        value.length in 1..100 && Regex("[A-Za-z_][A-Za-z0-9_:-]*").matches(value)

    private fun selector(value: String): Boolean {
        if (
            value.isBlank() ||
                value.length > 256 ||
                value.any { it in "{};<`" } ||
                value.trim().last() in ",>+~" ||
                "::" in value
        ) {
            return false
        }
        val functions = Regex(":([A-Za-z-]+)\\(").findAll(value)
        if (
            functions.any {
                it.groupValues[1] !in
                    setOf(
                        "not",
                        "is",
                        "where",
                        "nth-child",
                        "nth-of-type",
                    )
            }
        ) {
            return false
        }
        val closing = mutableListOf<Char>()
        var quote: Char? = null
        var escaped = false
        for (char in value) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (quote != null) {
                if (char == quote) {
                    quote = null
                }
            } else if (char == '\'' || char == '"') {
                quote = char
            } else if (char == '[' || char == '(') {
                closing +=
                    if (char == '[') {
                        ']'
                    } else {
                        ')'
                    }
                if (closing.size > 8) {
                    return false
                }
            } else if (char == ']' || char == ')') {
                if (closing.lastOrNull() != char) {
                    return false
                }
                closing.removeAt(closing.lastIndex)
            }
        }
        return !escaped && quote == null && closing.isEmpty()
    }

    private fun safeValue(value: String): Boolean =
        value.lowercase() in safeValues ||
            Regex("-?[0-9]{1,5}").matches(value) &&
                (value.toIntOrNull() ?: 32768) in -32767..32767

    private val safeValues =
        ("accept reject accepted rejected notaccepted allow disallow deny allowed denied " +
                "approved disapproved checked unchecked dismiss dismissed enable disable enabled " +
                "disabled essential nonessential forbidden forever hide hidden necessary " +
                "required " +
                "ok on off true t false f yes y no n all none functional granted done decline " +
                "declined closed next mandatory disagree agree set unset given")
            .split(' ')
            .toSet()
}
