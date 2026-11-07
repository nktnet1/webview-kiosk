package uk.nktnet.webviewkiosk.ui.components.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.config.option.AddressBarSizeOption

class AddressBarSpinnerSizingTest {
    @Test
    fun spinnerMatchesSearchGlyphSizeAtEveryAddressBarSize() {
        AddressBarSizeOption.entries.forEach { size ->
            val searchViewport = size.searchIconSizeDp - size.searchIconPaddingDp * 2
            val spinnerPadding =
                addressBarSpinnerPadding(
                    size.searchIconSizeDp,
                    size.searchIconPaddingDp,
                )
            val spinnerDiameter = size.searchIconSizeDp - spinnerPadding * 2

            assertTrue(spinnerPadding > size.searchIconPaddingDp)
            assertEquals(searchViewport.value * 0.75f, spinnerDiameter.value, 0.001f)
        }
    }
}
