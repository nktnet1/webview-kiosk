package uk.nktnet.webviewkiosk.utils

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 22, 23])
class LegacyWebViewSdkCompatibilityTest {
    @Test
    fun lollipopAndMarshmallowKeepCompatNavigation() {
        val sdk = Build.VERSION.SDK_INT
        assertFalse(needsFrameworkWebViewClient(sdk, "67.0.3396.87"))
        assertFalse(needsFrameworkWebViewClient(sdk, "72.0.3626.105"))
        assertFalse(needsFrameworkWebViewClient(sdk, null))
    }
}
