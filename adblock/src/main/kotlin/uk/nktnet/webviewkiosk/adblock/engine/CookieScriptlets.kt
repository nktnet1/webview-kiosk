package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Cookie options run once on selected documents, never on request matching. */
internal object CookieScriptlets {
    val trustedTypes =
        setOf(
            ScriptletType.TRUSTED_SET_COOKIE,
            ScriptletType.TRUSTED_SET_COOKIE_RELOAD,
        )
    val types =
        trustedTypes +
            setOf(
                ScriptletType.SET_COOKIE,
                ScriptletType.SET_COOKIE_RELOAD,
            )
    const val MAX_ARGUMENTS = 10
    private val seconds = Regex("[0-9]{1,10}")
    private val name = Regex("[!#$%&'*+.^_`|~0-9A-Za-z\\[\\]-]+")
    private val placeholders =
        listOf($$"$now$", $$"$currentDate$", $$"$currentISODate$")

    fun validated(type: ScriptletType, input: List<String>): ScriptletInstruction? {
        val trusted = type in trustedTypes
        val prefix =
            if (trusted) {
                4
            } else {
                3
            }
        val limit =
            if (trusted) {
                MAX_ARGUMENTS
            } else {
                7
            }
        if (
            input.size !in 2..limit ||
                input.size > prefix && (input.size - prefix) % 2 != 0 ||
                input[0].isEmpty()
        ) {
            return null
        }
        fun arg(index: Int) = input.getOrElse(index) { "" }
        if (trusted) {
            if (
                !name.matches(arg(0)) ||
                    placeholders
                        .fold(arg(1)) { text, token -> text.replace(token, "") }
                        .contains('$')
            ) {
                return null
            }
            if (
                arg(2) !in setOf("", "1day", "1year") &&
                    !(seconds.matches(arg(2)) && arg(2).toLong() <= Int.MAX_VALUE)
            ) {
                return null
            }
        } else if (!AdditionalScriptlets.safeValue(arg(1))) {
            return null
        }
        val path = arg(prefix - 1)
        if (path !in setOf("", "/", "none")) {
            return null
        }
        val pairs = input.drop(prefix).chunked(2)
        if (pairs.map { it[0] }.distinct().size != pairs.size) {
            return null
        }
        val options = linkedMapOf<String, String>()
        for ((key, value) in pairs) {
            options[key] =
                when (key) {
                    "reload",
                    "dontOverwrite" -> value.takeIf { it in setOf("0", "1") }
                    "domain" ->
                        if (trusted && value.isNotEmpty()) {
                            normalizedHost(value.removePrefix(".")).takeIf {
                                it != null && ':' !in it
                            }
                        } else {
                            null
                        }
                    else -> null
                } ?: return null
        }
        val args = input.take(2).toMutableList()
        if (trusted) {
            args +=
                if (seconds.matches(arg(2))) {
                    arg(2).toLong().toString()
                } else {
                    arg(2)
                }
        }
        args +=
            if (path == "none") {
                "none"
            } else {
                ""
            }
        options["domain"]?.let { args += listOf("domain", it) }
        val reloadDefault =
            type == ScriptletType.SET_COOKIE_RELOAD ||
                type == ScriptletType.TRUSTED_SET_COOKIE_RELOAD
        for (key in listOf("reload", "dontOverwrite")) {
            val default =
                if (key == "reload" && reloadDefault) {
                    "1"
                } else {
                    "0"
                }
            val value = options[key] ?: default
            if (value != default) {
                args += listOf(key, value)
            }
        }
        return ScriptletInstruction(type, args)
    }
}
