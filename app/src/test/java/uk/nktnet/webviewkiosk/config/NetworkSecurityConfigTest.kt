package uk.nktnet.webviewkiosk.config

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption

/**
 * Guard the packaged WebView trust policy, not just an XML fixture in test sources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NetworkSecurityConfigTest {
    @Test
    fun appWideTrustIncludesSystemAndAndroidInstalledUserCAs() {
        val config = parseNetworkSecurityConfig()

        assertEquals(listOf("system", "user"), config.baseTrustAnchors)
        assertEquals(1, config.baseConfigCount)
        assertFalse("Unexpected domain-specific policy", config.hasDomainConfig)
    }

    @Test
    fun customAnchorsDoNotOverrideCertificatePinsOrIntroduceDebugOverrides() {
        val config = parseNetworkSecurityConfig()

        assertEquals(listOf(null, null), config.pinOverrides)
        assertFalse("Unexpected debug-only trust anchors", config.hasDebugOverrides)
    }

    @Test
    fun existingCleartextPolicyRemainsUnchanged() {
        val config = parseNetworkSecurityConfig()

        assertEquals("true", config.cleartextTrafficPermitted)
    }

    @Test
    fun sslErrorsRemainBlockedByDefault() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(UserSettingsKeys.WebEngine.SSL_ERROR_MODE)
            .commit()

        assertEquals(SslErrorModeOption.BLOCK, UserSettings(context).sslErrorMode)
    }

    private data class ParsedConfig(
        val baseConfigCount: Int,
        val cleartextTrafficPermitted: String?,
        val baseTrustAnchors: List<String>,
        val pinOverrides: List<String?>,
        val hasDomainConfig: Boolean,
        val hasDebugOverrides: Boolean,
    )

    private fun parseNetworkSecurityConfig(): ParsedConfig {
        val resources = RuntimeEnvironment.getApplication().resources
        val parser = resources.getXml(R.xml.network_security_config)
        try {
            var inBaseConfig = false
            var inTrustAnchors = false
            var baseConfigCount = 0
            var cleartextTrafficPermitted: String? = null
            val baseTrustAnchors = mutableListOf<String>()
            val pinOverrides = mutableListOf<String?>()
            var hasDomainConfig = false
            var hasDebugOverrides = false

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG ->
                        when (parser.name) {
                            "base-config" -> {
                                inBaseConfig = true
                                baseConfigCount++
                                cleartextTrafficPermitted =
                                    parser.getAttributeValue(
                                        null,
                                        "cleartextTrafficPermitted",
                                    )
                            }
                            "trust-anchors" ->
                                if (inBaseConfig) {
                                    inTrustAnchors = true
                                }
                            "certificates" ->
                                if (inBaseConfig && inTrustAnchors) {
                                    baseTrustAnchors +=
                                        requireNotNull(
                                            parser.getAttributeValue(null, "src")
                                        )
                                    pinOverrides +=
                                        parser.getAttributeValue(
                                            null,
                                            "overridePins",
                                        )
                                }
                            "domain-config" -> hasDomainConfig = true
                            "debug-overrides" -> hasDebugOverrides = true
                        }
                    XmlPullParser.END_TAG ->
                        when (parser.name) {
                            "trust-anchors" -> inTrustAnchors = false
                            "base-config" -> inBaseConfig = false
                        }
                }
                parser.next()
            }
            return ParsedConfig(
                baseConfigCount,
                cleartextTrafficPermitted,
                baseTrustAnchors,
                pinOverrides,
                hasDomainConfig,
                hasDebugOverrides,
            )
        } finally {
            parser.close()
        }
    }
}
