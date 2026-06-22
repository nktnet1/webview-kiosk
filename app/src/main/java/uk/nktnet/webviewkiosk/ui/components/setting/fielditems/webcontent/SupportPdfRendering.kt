package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pdfviewer.PdfJsManager
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun SupportPdfRendering() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.SUPPORT_PDF_RENDERING

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_content_support_pdf_rendering_title),
        infoText = """
            Set to true to support PDF Rendering using Mozilla's PDF.js:

            - https://github.com/mozilla/pdf.js
        """.trimIndent(),
        initialValue = userSettings.supportPdfRendering,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.supportPdfRendering = it },
        extraContent = {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = {
                    coroutineScope.launch {
                        if (PdfJsManager.areAssetsReady(context)) {
                            ToastManager.show(context, "PDF.js assets are already downloaded")
                        } else {
                            ToastManager.show(context, "Downloading PDF.js...")
                            PdfJsManager.downloadAssets(context)
                        }
                    }
                }
            ) {
                Text(
                    text = "Download PDF.js",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
