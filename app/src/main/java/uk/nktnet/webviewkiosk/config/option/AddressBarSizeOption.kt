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
    val moreVertWidth: Dp,
) {
    SMALL(
        "Small",
        45.dp,
        6.dp,
        14.sp,
        24.dp,
    ),
    MEDIUM(
        "Medium",
        55.dp,
        8.dp,
        16.sp,
        28.dp,
    ),
    LARGE(
        "Large",
        65.dp,
        10.dp,
        18.sp,
        32.dp,
    );

    companion object {
        fun fromString(value: String?): AddressBarSizeOption {
            return entries.find {
                it.name.equals(value, true)
                || it.label.equals(value, true)
            } ?: SMALL
        }
    }
}
