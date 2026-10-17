package uk.nktnet.webviewkiosk.adblock.engine

import java.io.DataInputStream

/** Built from the resolved OkHttp dependency; loaded only on the compile worker. */
internal object ScriptletSuffixes {
    val serialized: String by lazy {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/adblock/publicsuffix.list"))
        DataInputStream(stream).use { input ->
            fun readList(): String {
                val length = input.readInt()
                require(length in 0..1024 * 1024)
                val bytes = ByteArray(length)
                input.readFully(bytes)
                return bytes
                    .toString(Charsets.UTF_8)
                    .lineSequence()
                    .filter { it.isNotEmpty() }
                    .map { suffix ->
                        val wildcard = suffix.startsWith("*.")
                        val host =
                            checkNotNull(normalizedHost(suffix.removePrefix("*.")))
                        if (wildcard) {
                            "*.$host"
                        } else {
                            host
                        }
                    }
                    .distinct()
                    .sorted()
                    .joinToString("\n", postfix = "\n")
            }
            val rules = readList()
            val exceptions = readList()
            require(input.read() == -1) { "Unexpected public suffix data" }
            "[${ScriptletIndex.json(rules)},${ScriptletIndex.json(exceptions)}]"
        }
    }
}
