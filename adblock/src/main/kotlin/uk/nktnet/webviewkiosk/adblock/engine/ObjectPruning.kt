package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

internal object ObjectPruning {
    val aliases =
        mapOf(
            "trusted-prune-inbound-object" to
                ScriptletType.TRUSTED_PRUNE_INBOUND_OBJECT,
            "trusted-prune-outbound-object" to
                ScriptletType.TRUSTED_PRUNE_OUTBOUND_OBJECT,
        )
    val types = aliases.values.toSet()

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        val inbound = type == ScriptletType.TRUSTED_PRUNE_INBOUND_OBJECT
        val offset =
            if (inbound) {
                2
            } else {
                1
            }
        if (!AdvancedScriptlets.method(args.firstOrNull().orEmpty())) {
            return null
        }
        if (args.size == 6 && args[4] in setOf("", "1")) {
            return if (
                (if (inbound) {
                    args[1].toIntOrNull() in 1..32
                } else {
                    args[1].isEmpty()
                }) &&
                    ScriptletParser.paths(args[2]) &&
                    (args[3].isEmpty() || ScriptletParser.paths(args[3])) &&
                    (inbound || args[4].isEmpty() && args[5].isEmpty()) &&
                    ScriptletParser.pattern(args[5])
            ) {
                ScriptletInstruction(type, args)
            } else {
                null
            }
        }
        if (
            args.size < offset + 1 ||
                args.size > offset + 6 ||
                inbound && args[1].toIntOrNull() !in 1..32 ||
                !ScriptletParser.paths(args[offset])
        ) {
            return null
        }
        val needles = args.getOrElse(offset + 1) { "" }
        if (needles.isNotEmpty() && !ScriptletParser.paths(needles)) {
            return null
        }
        val extras = args.drop(offset + 2)
        if (extras.size % 2 != 0 || !inbound && extras.isNotEmpty()) {
            return null
        }
        val options = extras.chunked(2)
        if (
            options.map { it[0] }.distinct().size != options.size ||
                options.any { (key, value) ->
                    when (key) {
                        "dontOverwrite" -> value != "1"
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
                args[0],
                if (inbound) {
                    args[1]
                } else {
                    ""
                },
                args[offset],
                needles,
                values["dontOverwrite"].orEmpty(),
                values["stackToMatch"].orEmpty(),
            ),
        )
    }
}
