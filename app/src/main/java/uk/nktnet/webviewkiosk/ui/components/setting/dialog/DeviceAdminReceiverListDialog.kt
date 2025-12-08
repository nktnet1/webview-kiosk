package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.DeviceAdmin
import uk.nktnet.webviewkiosk.utils.getDeviceAdminReceivers

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun DeviceAdminReceiverListDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) {
        return
    }

    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    var admins by remember { mutableStateOf(getDeviceAdminReceivers(context, context.packageManager)) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var ascending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var isProcessing by remember { mutableStateOf(false) }
    var selectedAdmin by remember { mutableStateOf<DeviceAdmin?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val filteredAdmins by remember(searchQuery.text, admins, ascending) {
        derivedStateOf {
            admins
                .filter { it.app.name.contains(searchQuery.text, ignoreCase = true) }
                .sortedBy { it.app.name }
                .let { if (ascending) it else it.reversed() }
        }
    }

    val selected = selectedAdmin?.admin
    if (showConfirmDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Ownership Transfer") },
            text = { Text("Are you sure you want to transfer ownership to ${selected.className}?") },
            confirmButton = {
                TextButton(onClick = {
                    isProcessing = true
                    try {
                        dpm.transferOwnership(
                            DeviceOwnerManager.DAR,
                            selected,
                            null
                        )
                        showConfirmDialog = false
                    } catch (e: Exception) {
                        ToastManager.show(context, "Error: ${e.message}")
                    } finally {
                        isProcessing = false
                    }
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Admin Receivers", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery.text,
                        onValueChange = { searchQuery = TextFieldValue(it) },
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(43.dp),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 12.dp)
                            ) {
                                if (searchQuery.text.isEmpty()) {
                                    Text(
                                        text = "Search ${admins.size} admin receivers",
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                            fontStyle = FontStyle.Italic,
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    IconButton(
                        onClick = { ascending = !ascending },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .graphicsLayer(scaleX = 0.9f, scaleY = 0.9f)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                MaterialTheme.shapes.small
                            )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_sort_24),
                            contentDescription = "Sort Order",
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer(scaleY = if (ascending) -1f else 1f, scaleX = -1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState
                ) {
                    items(filteredAdmins, key = { it.admin.packageName }) { admin ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isProcessing) {
                                    selectedAdmin = admin
                                    showConfirmDialog = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedAdmin == admin) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                contentColor = if (selectedAdmin == admin) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AdminIcon(admin.app.icon)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(admin.app.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        admin.admin.className,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun AdminIcon(icon: Drawable) {
    val bitmap = createBitmap(
        icon.intrinsicWidth.coerceAtLeast(1),
        icon.intrinsicHeight.coerceAtLeast(1)
    )
    val canvas = Canvas(bitmap)
    icon.setBounds(0, 0, canvas.width, canvas.height)
    icon.draw(canvas)

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        contentScale = ContentScale.Fit
    )
}
