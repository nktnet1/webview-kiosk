package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.SettingsHeaderMenu

@Composable
fun SettingsListScreen(navController: NavController) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    val results =
        if (query.isBlank()) {
            emptyList()
        } else {
            searchSettings(context, query)
        }

    val settingsItems =
        listOf(
            Triple(
                stringResource(R.string.settings_web_content_title),
                stringResource(R.string.settings_web_content_description),
                Screen.SettingsWebContent.route,
            ),
            Triple(
                stringResource(R.string.settings_web_browsing_title),
                stringResource(R.string.settings_web_browsing_description),
                Screen.SettingsWebBrowsing.route,
            ),
            Triple(
                stringResource(R.string.settings_web_engine_title),
                stringResource(R.string.settings_web_engine_description),
                Screen.SettingsWebEngine.route,
            ),
            Triple(
                stringResource(R.string.settings_web_lifecycle_title),
                stringResource(R.string.settings_web_lifecycle_description),
                Screen.SettingsWebLifecycle.route,
            ),
            Triple(
                stringResource(R.string.settings_appearance_title),
                stringResource(R.string.settings_appearance_description),
                Screen.SettingsAppearance.route,
            ),
            Triple(
                stringResource(R.string.settings_device_title),
                stringResource(R.string.settings_device_description),
                Screen.SettingsDevice.route,
            ),
            Triple(
                stringResource(R.string.settings_js_scripts_title),
                stringResource(R.string.settings_js_scripts_description),
                Screen.SettingsJsScript.route,
            ),
            Triple(
                stringResource(R.string.settings_mqtt_title),
                stringResource(R.string.settings_mqtt_description),
                Screen.SettingsMqtt.route,
            ),
            Triple(
                stringResource(R.string.settings_unifiedpush_title),
                stringResource(R.string.settings_unifiedpush_description),
                Screen.SettingsUnifiedPush.route,
            ),
            Triple(
                stringResource(R.string.settings_about_title),
                stringResource(R.string.settings_about_description),
                Screen.SettingsAbout.route,
            ),
        )

    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        SettingsHeaderMenu(navController)
        SettingDivider()

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.settings_search_label)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.baseline_search_24),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription =
                                stringResource(R.string.settings_search_clear),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("settings_search_field"),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isBlank()) {
                items(settingsItems, key = { it.third }) {
                    (title, description, route) ->
                    SettingListItem(
                        title = title,
                        description = description,
                        onClick = { navController.navigate(route) },
                    )
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_search_no_results),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(
                    results,
                    key = { "${it.route}:${it.targetRes ?: "section"}" },
                ) { result ->
                    SettingListItem(
                        title = stringResource(result.titleRes),
                        description =
                            result.sectionTitleRes?.let { stringResource(it) }
                                ?: result.descriptionRes?.let { stringResource(it) }
                                ?: "",
                        onClick = {
                            keyboard?.hide()
                            navController.navigateToSearchResult(result)
                        },
                    )
                }
            }
        }
    }
}
