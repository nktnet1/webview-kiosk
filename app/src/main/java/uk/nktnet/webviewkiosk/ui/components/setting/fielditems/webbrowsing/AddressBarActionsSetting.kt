package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.*
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarAction
import uk.nktnet.webviewkiosk.ui.components.setting.fields.CustomSettingFieldItem

@Composable
fun AddressBarActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var actions by remember { mutableStateOf(userSettings.addressBarActions) }
    var savedActions by remember { mutableStateOf(userSettings.addressBarActions) }
    var addExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        actions = actions.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    val availableToAdd = AddressBarAction.entries.filter { it !in actions }

    CustomSettingFieldItem(
        label = "Address Bar Actions",
        infoText = "Reorder and manage visible actins in the address bar.",
        value = savedActions
            .joinToString(", ") { it.label }
            .ifEmpty { "No actions will be shown" },
        restricted = false,
        onDismissCallback = {},
        onSave = {
            userSettings.addressBarActions = actions
            savedActions = actions
        },
        bodyContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 56.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(actions, key = { it.name }) { action ->
                        ReorderableItem(
                            reorderableState,
                            animateItemModifier = Modifier,
                            key = action.name
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        actions = actions.toMutableList().apply {
                                            remove(action)
                                        }
                                    },
                                    modifier = Modifier.offset((-5).dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_clear_24),
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Text(action.label, modifier = Modifier.weight(1f))

                                Icon(
                                    painter = painterResource(R.drawable.drag_indicator_24),
                                    contentDescription = "Drag Handle",
                                    modifier = Modifier.draggableHandle()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { actions = AddressBarAction.getDefault() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset")
                    }

                    Box {
                        Button(
                            onClick = { addExpanded = true },
                            enabled = availableToAdd.isNotEmpty()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_add_24),
                                contentDescription = "Add"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                        DropdownMenu(
                            expanded = addExpanded,
                            onDismissRequest = { addExpanded = false }
                        ) {
                            availableToAdd.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        actions = actions.toMutableList().apply { add(action) }
                                        addExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
