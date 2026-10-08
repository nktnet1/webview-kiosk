package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceVariablesTest {
    @Test
    fun replacesEveryKnownVariableAndTrimsOnlyOuterWhitespace() {
        assertEquals(
            "kiosk/alpha/alpha  beta",
            replaceVariables(
                "  kiosk/\${instance}/\${instance}  \${name}\n",
                mapOf("instance" to "alpha", "name" to "beta"),
            ),
        )
    }

    @Test
    fun preservesUnknownAndMalformedPlaceholders() {
        assertEquals("\${missing}/\${}/\${open", replaceVariables("\${missing}/\${}/\${open"))
    }

    @Test
    fun treatsReplacementValuesLiterallyWithoutRecursiveExpansion() {
        assertEquals(
            "\${second}/$1\\path",
            replaceVariables(
                "\${first}/\${literal}",
                mapOf("first" to "\${second}", "second" to "expanded", "literal" to "$1\\path"),
            ),
        )
    }

    @Test
    fun distinguishesAnEmptyReplacementFromAnUnknownVariable() {
        assertEquals("/\${unknown}", replaceVariables("\${empty}/\${unknown}", mapOf("empty" to "")))
    }
}
