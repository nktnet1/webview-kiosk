package uk.nktnet.webviewkiosk.adblock.engine

/** Normalized from OkHttp's pinned suffix list during the Android build. */
internal object ScriptletSuffixes {
    val serialized: String by lazy {
        checkNotNull(
                javaClass.getResourceAsStream(
                    "/adblock/publicsuffix-serialized.json"
                )
            )
            .bufferedReader()
            .use { it.readText() }
    }
}
