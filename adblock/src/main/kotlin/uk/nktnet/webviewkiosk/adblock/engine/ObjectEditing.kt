package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Object hooks share the data-only JSONPath compiler and its trust boundary. */
internal object ObjectEditing {
    val aliases =
        mapOf(
            "edit-outbound-object" to ScriptletType.EDIT_OUTBOUND_OBJECT,
            "edit-inbound-object" to ScriptletType.EDIT_INBOUND_OBJECT,
            "edit-this-object" to ScriptletType.EDIT_THIS_OBJECT,
            "edit-object-on-getter" to ScriptletType.EDIT_OBJECT_ON_GETTER,
            "edit-object-on-setter" to ScriptletType.EDIT_OBJECT_ON_SETTER,
            "trusted-edit-outbound-object" to
                ScriptletType.TRUSTED_EDIT_OUTBOUND_OBJECT,
            "trusted-edit-inbound-object" to
                ScriptletType.TRUSTED_EDIT_INBOUND_OBJECT,
            "trusted-edit-this-object" to ScriptletType.TRUSTED_EDIT_THIS_OBJECT,
            "trusted-edit-object-on-getter" to
                ScriptletType.TRUSTED_EDIT_OBJECT_ON_GETTER,
            "trusted-edit-object-on-setter" to
                ScriptletType.TRUSTED_EDIT_OBJECT_ON_SETTER,
            "edit-element-object" to ScriptletType.EDIT_ELEMENT_OBJECT,
            "trusted-edit-element-object" to
                ScriptletType.TRUSTED_EDIT_ELEMENT_OBJECT,
            "trusted-edit-inbound-element" to
                ScriptletType.TRUSTED_EDIT_INBOUND_ELEMENT,
        )
    val types = aliases.values.toSet()
    val elementTypes =
        setOf(
            ScriptletType.EDIT_ELEMENT_OBJECT,
            ScriptletType.TRUSTED_EDIT_ELEMENT_OBJECT,
        )

    fun validated(type: ScriptletType, args: List<String>): ScriptletInstruction? {
        if (type in elementTypes) {
            if (args.size !in 2..4 || !AdditionalScriptlets.selector(args[0])) {
                return null
            }
            val program =
                JsonEditing.compile(args[1], type.requiresTrust) ?: return null
            val timeout =
                args
                    .getOrNull(
                        if (args.size == 4) {
                            3
                        } else {
                            2
                        }
                    )
                    .orEmpty()
            if (
                timeout.isNotEmpty() && timeout.toIntOrNull() !in 0..30000 ||
                    args.size == 4 && args[2] != program
            ) {
                return null
            }
            return ScriptletInstruction(
                type,
                listOf(args[0], args[1], program, timeout),
            )
        }
        if (type == ScriptletType.TRUSTED_EDIT_INBOUND_ELEMENT) {
            if (
                args.size !in 4..5 ||
                    !AdvancedScriptlets.method(args[0]) ||
                    args[1] != "this" && args[1].toIntOrNull() !in -31..31 ||
                    !AdditionalScriptlets.selector(args[2])
            ) {
                return null
            }
            val program = JsonEditing.compile(args[3], true) ?: return null
            if (args.size == 5 && args[4] != program) {
                return null
            }
            return ScriptletInstruction(type, args.take(4) + program)
        }
        val name = type.name.removePrefix("TRUSTED_")
        val inbound = name == "EDIT_INBOUND_OBJECT"
        val receiver = name == "EDIT_THIS_OBJECT"
        val queryIndex =
            if (inbound) {
                2
            } else {
                1
            }
        if (!AdvancedScriptlets.method(args.firstOrNull().orEmpty())) {
            return null
        }
        if (args.size == 5) {
            val program =
                JsonEditing.compile(args[2], type.requiresTrust) ?: return null
            return if (
                args[4] == program &&
                    (if (inbound) {
                        args[1].toIntOrNull() in -31..31
                    } else {
                        args[1].isEmpty()
                    }) &&
                    args[3] in setOf("", "after") &&
                    (receiver || args[3].isEmpty())
            ) {
                ScriptletInstruction(type, args)
            } else {
                null
            }
        }
        if (inbound && args.getOrNull(1)?.toIntOrNull() !in -31..31) {
            return null
        }
        val query = args.getOrNull(queryIndex) ?: return null
        val program = JsonEditing.compile(query, type.requiresTrust) ?: return null
        val order = args.getOrNull(queryIndex + 1).orEmpty()
        if (
            args.size !in
                (queryIndex + 1)..(queryIndex +
                        if (receiver) {
                            2
                        } else {
                            1
                        }) || receiver && order !in setOf("", "after")
        ) {
            return null
        }
        return ScriptletInstruction(
            type,
            listOf(
                args[0],
                if (inbound) {
                    args[1]
                } else {
                    ""
                },
                query,
                if (receiver) {
                    order
                } else {
                    ""
                },
                program,
            ),
        )
    }
}
