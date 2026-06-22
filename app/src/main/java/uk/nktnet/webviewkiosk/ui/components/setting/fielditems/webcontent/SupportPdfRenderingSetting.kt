package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.PdfJsManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun SupportPdfRenderingSetting() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.SUPPORT_PDF_RENDERING
    val restricted = userSettings.isRestricted(settingKey)

    var assetsReady by remember {
        mutableStateOf(PdfJsManager.areAssetsReady(context))
    }

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_content_support_pdf_rendering_title),
        infoText = """
            Set to true to support PDF Rendering using Mozilla's PDF.js:

            - https://github.com/mozilla/pdf.js

            Please note that you will need to also download the relevant assets by clicking
            the "Download PDF.js assets button in the menu for this setting.
        """.trimIndent(),
        initialValue = userSettings.supportPdfRendering,
        settingKey = settingKey,
        restricted = restricted ,
        onSave = { userSettings.supportPdfRendering = it },
        extraContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (assetsReady) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        enabled = !restricted,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        onClick = {
                            PdfJsManager.clearAssets(context)
                            assetsReady = PdfJsManager.areAssetsReady(context)
                        }
                    ) {
                        Text(
                            text = "Delete PDF.js assets",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Button(
                        enabled = !restricted,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                ToastManager.show(context, "Downloading PDF.js...")
                                PdfJsManager.downloadAssets(context)
                                assetsReady = PdfJsManager.areAssetsReady(context)
                            }
                        }
                    ) {
                        Text(
                            text = "Download PDF.js assets",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        text = "PDF rendering cannot be used until PDF.js assets are downloaded.",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    )
}
