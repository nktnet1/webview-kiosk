package uk.nktnet.webviewkiosk.config.option

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

enum class AddressBarSizeOption(
    val label: String,
    val heightDp: Dp,
    val paddingDp: Dp,
    val fontSizeSp: TextUnit,
    val moreVertWidthDp: Dp,
    val searchIconSizeDp: Dp,
    val searchIconPaddingDp: Dp,
) {
    EXTRA_SMALL(
        label = "Extra Small",
        heightDp = 35.dp,
        paddingDp = 4.dp,
        fontSizeSp = 12.sp,
        moreVertWidthDp = 20.dp,
        searchIconSizeDp = 30.dp,
        searchIconPaddingDp = 4.dp,
    ),
    SMALL(
        label = "Small",
        heightDp = 45.dp,
        paddingDp = 6.dp,
        fontSizeSp = 14.sp,
        moreVertWidthDp = 24.dp,
        searchIconSizeDp = 35.dp,
        searchIconPaddingDp = 6.dp,
    ),
    MEDIUM(
        label = "Medium",
        heightDp = 55.dp,
        paddingDp = 8.dp,
        fontSizeSp = 16.sp,
        moreVertWidthDp = 26.dp,
        searchIconSizeDp = 40.dp,
        searchIconPaddingDp = 8.dp,
    ),
    LARGE(
        label = "Large",
        heightDp = 65.dp,
        paddingDp = 10.dp,
        fontSizeSp = 20.sp,
        moreVertWidthDp = 32.dp,
        searchIconSizeDp = 45.dp,
        searchIconPaddingDp = 10.dp,
    ),
    EXTRA_LARGE(
        label = "Extra Large",
        heightDp = 75.dp,
        paddingDp = 12.dp,
        fontSizeSp = 26.sp,
        moreVertWidthDp = 36.dp,
        searchIconSizeDp = 56.dp,
        searchIconPaddingDp = 12.dp,
    );

    companion object {
        fun fromString(value: String?): AddressBarSizeOption {
            return entries.find {
                it.name.equals(value, true)
                || it.label.equals(value, true)
            } ?: MEDIUM
        }
    }
}
