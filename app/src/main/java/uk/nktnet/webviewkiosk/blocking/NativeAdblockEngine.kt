package uk.nktnet.webviewkiosk.blocking

import androidx.annotation.Keep

/** Kept injectable so Robolectric tests do not need an Android native library. */
internal interface NetworkFilterEngine {
    fun replace(lists: List<String>)
    fun clear()
    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean
}

/** Validation is injectable: JVM storage tests do not load an Android JNI library. */
internal fun interface FilterListValidator {
    fun validate(text: String)
}

/** JNI calls into Brave adblock-rust. Loaded for validation or the first enabled list. */
@Keep // Prevent R8 from renaming JNI symbols in minified release builds.
internal class NativeAdblockEngine : NetworkFilterEngine, FilterListValidator {
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

    /** Parse without replacing or locking the active engine. Call on a background thread. */
    override fun validate(text: String) {
        val error = try {
            ensureLoaded()
            nativeValidationError(text)
        } catch (error: LinkageError) {
            // Never silently accept unvalidated changes when JNI is unavailable.
            throw IllegalStateException("Filter syntax validation is unavailable; no changes were saved", error)
        }
        require(error == null) { error.orEmpty() }
    }

    override fun clear() {
        if (loaded) nativeClear()
    }

    override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean =
        loaded && nativeShouldBlock(url, sourceUrl, type, method)

    private external fun nativeReplace(lists: Array<String>): Boolean
    private external fun nativeValidationError(text: String): String?
    private external fun nativeClear()
    private external fun nativeShouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean
}
