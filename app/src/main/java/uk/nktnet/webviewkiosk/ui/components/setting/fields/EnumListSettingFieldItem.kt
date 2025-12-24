package com.nktnet.webview_kiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import sh.calvin.reorderable.ReorderableColumn
import com.nktnet.webview_kiosk.R

@Composable
fun <T : Enum<T>> EnumListSettingFieldItem(
    label: String,
    infoText: String,
    entries: List<T>,
    getLabel: (T) -> String,
    getDefault: () -> List<T>,
    initialValue: List<T>,
    settingKey: String,
    restricted: Boolean,
    onSave: (List<T>) -> Unit
) {
    var items by remember { mutableStateOf(initialValue) }
    var savedItems by remember { mutableStateOf(initialValue) }
    var addExpanded by remember { mutableStateOf(false) }
    val availableToAdd = entries.filter { it !in items }

    CustomSettingFieldItem(
        label = label,
        infoText = infoText,
        value = JSONArray(savedItems.map { it.name }).toString(),
        settingKey = settingKey,
        restricted = restricted,
        onDismissCallback = {},
        onSave = {
            onSave(items)
            savedItems = items
        },
        bodyContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ReorderableColumn(
                    list = items,
                    onSettle = { from, to ->
                        items = items.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { index, item, _ ->
                    key(item.name) {
                        ReorderableItem {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp)
                            ) {
                                if (!restricted) {
                                    IconButton(
                                        onClick = { items = items.toMutableList().apply { remove(item) } },
                                        modifier = Modifier.offset((-10).dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_clear_24),
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Text(getLabel(item), modifier = Modifier.weight(1f))
                                if (!restricted) {
                                    Icon(
                                        painter = painterResource(R.drawable.drag_indicator_24),
                                        contentDescription = "Drag Handle",
                                        modifier = Modifier.draggableHandle()
                                    )
                                }
                            }
                        }
                    }
                }

                if (!restricted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        Button(
                            onClick = { items = getDefault() },
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
                                availableToAdd.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(getLabel(entry)) },
                                        onClick = {
                                            items = items.toMutableList().apply { add(entry) }
                                            addExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
