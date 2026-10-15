package uk.nktnet.webviewkiosk.blocking

import androidx.annotation.Keep

/** Kept injectable so Robolectric tests do not need an Android native library. */
internal interface NetworkFilterEngine {
    fun replace(lists: List<String>)
    fun clear()
    fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean
    fun hasCosmeticRules(): Boolean = false
    fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> = emptyList()
}

/** Validation is injectable: JVM storage tests do not load an Android JNI library. */
internal fun interface FilterListValidator {
    fun validate(text: String)
}

/** JNI calls into Brave adblock-rust. Loaded for validation or the first enabled list. */
@Keep // Prevent R8 from renaming JNI symbols in minified release builds.
internal class NativeAdblockEngine : NetworkFilterEngine, FilterListValidator {
    private var loaded = false
    @Volatile private var cosmetics = false

    private fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("wk_adblock")
            loaded = true
        }
    }

    override fun replace(lists: List<String>) {
        ensureLoaded()
        check(nativeReplace(lists.toTypedArray())) { "Could not compile the imported filter lists" }
        cosmetics = nativeHasCosmeticRules()
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
        cosmetics = false
    }

    override fun hasCosmeticRules(): Boolean = cosmetics

    override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean =
        loaded && nativeShouldBlock(url, sourceUrl, type, method)

    override fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> =
        if (loaded) nativeCosmeticSelectors(url, classes.toTypedArray(), ids.toTypedArray())?.toList().orEmpty()
        else emptyList()

    private external fun nativeReplace(lists: Array<String>): Boolean
    private external fun nativeValidationError(text: String): String?
    private external fun nativeClear()
    private external fun nativeHasCosmeticRules(): Boolean
    private external fun nativeCosmeticSelectors(url: String, classes: Array<String>, ids: Array<String>): Array<String>?
    private external fun nativeShouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean
}
