package uk.nktnet.webviewkiosk.blocking

import androidx.annotation.Keep

/** Kept injectable so Robolectric tests do not need an Android native library. */
internal interface NetworkFilterEngine {
    fun replace(lists: List<String>)
    fun clear()
    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean
}

/** JNI calls into Brave adblock-rust. Loaded only after the first enabled import. */
@Keep // Prevent R8 from renaming JNI symbols in minified release builds.
internal class NativeAdblockEngine : NetworkFilterEngine {
    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("wk_adblock")
            loaded = true
        }
    }

    override fun replace(lists: List<String>) {
        ensureLoaded()
        check(nativeReplace(lists.toTypedArray())) { "Could not compile the imported filter lists" }
    }

    override fun clear() {
        if (loaded) nativeClear()
    }

    override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean =
        loaded && nativeShouldBlock(url, sourceUrl, type, method)

    private external fun nativeReplace(lists: Array<String>): Boolean
    private external fun nativeClear()
    private external fun nativeShouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean
}
