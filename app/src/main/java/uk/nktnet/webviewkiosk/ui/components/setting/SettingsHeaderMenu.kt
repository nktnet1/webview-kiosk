package uk.nktnet.webviewkiosk.ui.components.setting

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import androidx.core.net.toUri
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.ExportSettingsDialog
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.ImportSettingsDialog
import uk.nktnet.webviewkiosk.utils.openDefaultLauncherSettings
import uk.nktnet.webviewkiosk.utils.openSettings

@Composable
fun SettingsHeaderMenu(
    navController: NavController,
) {
    val context = LocalContext.current

    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    ImportSettingsDialog(
        showDialog = showImportDialog,
        onDismiss = { showImportDialog = false },
    )

    ExportSettingsDialog(
        showDialog = showExportDialog,
        onDismiss = { showExportDialog = false },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(id = R.string.settings_title),
            endIcon = {
                Box(
                    modifier = Modifier.align(Alignment.Bottom)
                ) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_more_vert_24),
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import", color = tintColor) },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_drive_folder_upload_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export", color = tintColor) },
                            onClick = {
                                showMenu = false
                                showExportDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_file_export_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Settings",
                                    color = tintColor
                                )
                            },
                            onClick = {
                                showMenu = false
                                openSettings(context)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_app_settings_alt_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Launcher", color = tintColor) },
                            onClick = {
                                showMenu = false
                                openDefaultLauncherSettings(context)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_rocket_24),
                                    contentDescription = null,
                                    tint = tintColor,
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Help", color = tintColor) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(Intent.ACTION_VIEW, Constants.WEBSITE_URL.toUri())
                                context.startActivity(intent)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_info_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("More", color = tintColor) },
                            onClick = {
                                showMenu = false
                                navController.navigate(Screen.SettingsMoreActions.route)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_widgets_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                    }
                }
            }
        )
    }
}
