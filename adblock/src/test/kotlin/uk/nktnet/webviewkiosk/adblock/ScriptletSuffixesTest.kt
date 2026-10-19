package uk.nktnet.webviewkiosk.adblock

import java.io.DataInputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletSuffixes
import uk.nktnet.webviewkiosk.adblock.engine.normalizedHost

class ScriptletSuffixesTest {
    @Test
    fun bundledSuffixesMatchFullHostnameNormalization() {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/adblock/publicsuffix.list"))
        DataInputStream(stream).use { input ->
            fun readList(): String {
                val length = input.readInt()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                return bytes
                    .toString(Charsets.UTF_8)
                    .lineSequence()
                    .filter { it.isNotEmpty() }
                    .map { suffix ->
                        val wildcard = suffix.startsWith("*.")
                        val rawHost = suffix.removePrefix("*.")
                        val canonical = checkNotNull(normalizedHost(rawHost))
                        if (wildcard) "*.$canonical" else canonical
                    }
                    .distinct()
                    .sorted()
                    .joinToString("\n", postfix = "\n")
            }
            val expected =
                "[${ScriptletIndex.json(readList())}," +
                    "${ScriptletIndex.json(readList())}]"
            assertEquals(-1, input.read())
            assertEquals(expected, ScriptletSuffixes.serialized)
        }
    }
}
