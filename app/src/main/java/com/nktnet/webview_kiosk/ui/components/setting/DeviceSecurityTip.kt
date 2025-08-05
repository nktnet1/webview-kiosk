package com.nktnet.webview_kiosk.ui.components.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle

@Composable
fun DeviceSecurityTip(modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        append("For better security, go to the App Pinning setting, tap into it to view more options, then enable ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Ask for PIN/pattern/password before unpinning")
        }
        append(" (on some devices, it may be shown as ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Lock device when unpinning")
        }
        append(" or equivalent).")
    }

    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
        textAlign = TextAlign.Center
    )
}
