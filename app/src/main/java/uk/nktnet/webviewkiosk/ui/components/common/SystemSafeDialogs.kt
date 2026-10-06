package uk.nktnet.webviewkiosk.ui.components.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.nktnet.webviewkiosk.utils.ProvideSystemSafeClipboard

@Composable
fun SystemSafeDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        // Dialog creates an owner with its own clipboard, replacing the parent provider.
        ProvideSystemSafeClipboard(content)
    }
}

@Composable
fun SystemSafeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    // Slot content runs inside Material's dialog root, after its owner locals are installed.
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = systemSafeSlot(confirmButton),
        modifier = modifier,
        dismissButton = dismissButton?.let(::systemSafeSlot),
        icon = icon?.let(::systemSafeSlot),
        title = title?.let(::systemSafeSlot),
        text = text?.let(::systemSafeSlot),
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

private fun systemSafeSlot(content: @Composable () -> Unit): @Composable () -> Unit = {
    ProvideSystemSafeClipboard(content)
}
