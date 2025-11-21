package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.*
import uk.nktnet.webviewkiosk.R

@Composable
fun <T : Enum<T>> EnumListSettingFieldItem(
    label: String,
    infoText: String,
    entries: List<T>,
    getLabel: (T) -> String,
    getDefault: () -> List<T>,
    initialValue: List<T>,
    restricted: Boolean = false,
    onSave: (List<T>) -> Unit
) {
    var items by remember { mutableStateOf(initialValue) }
    var savedItems by remember { mutableStateOf(initialValue) }
    var addExpanded by remember { mutableStateOf(false) }

    val availableToAdd = entries.filter { it !in items }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    CustomSettingFieldItem(
        label = label,
        infoText = infoText,
        value = savedItems.joinToString(", ") { getLabel(it) }.ifEmpty { "(blank)" },
        restricted = restricted,
        onDismissCallback = {},
        onSave = {
            onSave(items)
            savedItems = items
        },
        bodyContent = {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = 56.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items, key = { it.name }) { item ->
                        ReorderableItem(
                            reorderableState,
                            animateItemModifier = Modifier,
                            key = item.name
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                if (!restricted) {
                                    IconButton(
                                        onClick = {
                                            items = items.toMutableList().apply { remove(item) }
                                        },
                                        modifier = Modifier.offset((-5).dp)
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
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
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
