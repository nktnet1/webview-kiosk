package uk.nktnet.webviewkiosk.utils

import android.os.Build
import java.security.KeyPairGenerator
import java.security.spec.NamedParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk =
        [
            Build.VERSION_CODES.LOLLIPOP,
            Build.VERSION_CODES.LOLLIPOP_MR1,
            Build.VERSION_CODES.M,
        ]
)
class LegacyWebViewSdkCompatibilityTest {
    @Test
    fun hostTlsKeyGenerationWorksAfterLegacySandboxStarts() {
        // SDK downloads use the host TLS provider after earlier sandboxes run.
        val generator = KeyPairGenerator.getInstance("XDH")
        generator.initialize(NamedParameterSpec.X25519)
        val keyPair = generator.generateKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)
    }

    @Test
    fun lollipopAndMarshmallowKeepCompatNavigation() {
        val sdk = Build.VERSION.SDK_INT
        assertFalse(needsFrameworkWebViewClient(sdk, "67.0.3396.87"))
        assertFalse(needsFrameworkWebViewClient(sdk, "72.0.3626.105"))
        assertFalse(needsFrameworkWebViewClient(sdk, null))
    }
}
