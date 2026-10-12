package uk.nktnet.webviewkiosk.utils

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPermissionErrorTest {
    @Test
    fun olderAndroidNeverQueriesTheCompatFeatureOrErrorDescription() {
        assertFalse(
            matchesLocalNetworkPermissionError(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                supportsDescription = { error("feature support must not be queried") },
                getDescription = { error("description must not be queried") },
            )
        )
    }

    @Test
    fun unsupportedWebViewNeverReadsTheErrorDescription() {
        var checks = 0
        assertFalse(
            matchesLocalNetworkPermissionError(
                Build.VERSION_CODES.CINNAMON_BUN,
                supportsDescription = { checks++; false },
                getDescription = { error("description must not be queried") },
            )
        )
        assertTrue(checks == 1)
    }

    @Test
    fun supportedWebViewRecognisesOnlyTheSpecificPermissionError() {
        assertTrue(
            matchesLocalNetworkPermissionError(
                Build.VERSION_CODES.CINNAMON_BUN,
                supportsDescription = { true },
                getDescription = { "net::ERR_LOCAL_NETWORK_PERMISSION_MISSING" },
            )
        )
        assertFalse(
            matchesLocalNetworkPermissionError(
                Build.VERSION_CODES.CINNAMON_BUN,
                supportsDescription = { true },
                getDescription = { "net::ERR_CONNECTION_REFUSED" },
            )
        )
        assertFalse(
            matchesLocalNetworkPermissionError(
                Build.VERSION_CODES.CINNAMON_BUN,
                supportsDescription = { true },
                getDescription = { null },
            )
        )
    }
}
