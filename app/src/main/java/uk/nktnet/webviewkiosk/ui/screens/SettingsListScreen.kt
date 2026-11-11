package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

        SettingsSearchField(query, onQueryChange = { query = it })

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (query.isBlank()) {
                        8.dp
                    } else {
                        0.dp
                    }
                ),
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
                groupSettingsSearchResults(results).forEachIndexed {
                    index,
                    (section, matches) ->
                    val sectionTopPadding =
                        if (index == 0) {
                            12.dp
                        } else {
                            26.dp
                        }
                    item(key = "header:${section.route}") {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(top = sectionTopPadding)
                        ) {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(bottom = 18.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Text(
                                text = section.breadcrumb(context),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(start = 16.dp, bottom = 8.dp)
                                        .semantics { heading() },
                            )
                        }
                    }
                    items(
                        matches,
                        key = { "${it.route}:${it.targetRes ?: "section"}" },
                    ) { result ->
                        SettingsSearchResultRow(result) {
                            keyboard?.hide()
                            navController.navigateToSearchResult(result)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tonal, pill-shaped search field matching the Android Settings search affordance.
 */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 12.dp, bottom = 14.dp)
                .height(56.dp)
                .testTag("settings_search_field"),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(28.dp),
                        )
                        .padding(start = 18.dp, end = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_search_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription =
                                stringResource(R.string.settings_search_clear),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsSearchResultRow(
    result: SettingsSearchResult,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(result.titleRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        val subtitle =
            result.settingKey ?: result.descriptionRes?.let { stringResource(it) }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
