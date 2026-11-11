package uk.nktnet.webviewkiosk.ui.components.setting

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.activation.ActivationResult
import uk.nktnet.webviewkiosk.activation.ShizukuActivation
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.utils.isPackageInstalled

@RequiresApi(Build.VERSION_CODES.M)
@Composable
internal fun ShizukuActivationSection(
    hasNativeOwner: Boolean,
    onActivated: () -> Unit,
    activate: (suspend (Boolean) -> ActivationResult)? = null,
    hasSharedOwnerPermission: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var profileOwner by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ActivationResult?>(null) }
    var resultProfileOwner by remember { mutableStateOf(false) }
    val shizukuAvailable = rememberShizukuAvailable()
    val canActivate =
        shizukuAvailable && !hasNativeOwner && !hasSharedOwnerPermission

    if (canActivate) {
        Button(
            onClick = { showDialog = true },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.ui_activate_with_shizuku))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!running) {
                    showDialog = false
                }
            },
            title = { Text(stringResource(R.string.ui_activate_with_shizuku)) },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.ui_shizuku_activation_info))
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.selectableGroup()) {
                        for (profile in listOf(false, true)) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            selected = profileOwner == profile,
                                            enabled = !running && canActivate,
                                            role = Role.RadioButton,
                                            onClick = { profileOwner = profile },
                                        )
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = profileOwner == profile,
                                    onClick = null,
                                    enabled = !running && canActivate,
                                )
                                Text(
                                    stringResource(
                                        if (profile) {
                                            R.string.about_app_profile_owner
                                        } else {
                                            R.string.about_app_device_owner
                                        }
                                    ),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    if (running) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor =
                                MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(16.dp).semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor =
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.15f
                                        ),
                                )
                                Text(
                                    stringResource(R.string.ui_shizuku_activating),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !running && canActivate,
                    onClick = {
                        if (!running && canActivate) {
                            val requestedProfileOwner = profileOwner
                            running = true
                            scope.launch {
                                try {
                                    result =
                                        activate?.invoke(requestedProfileOwner)
                                            ?: ShizukuActivation.activate(
                                                context.applicationContext,
                                                requestedProfileOwner,
                                            )
                                    resultProfileOwner = requestedProfileOwner
                                    showDialog = false
                                    onActivated()
                                } finally {
                                    running = false
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ui_shizuku_activate))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !running,
                    onClick = { showDialog = false },
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }

    result?.let { outcome ->
        val diagnostics =
            outcome.diagnostics
                .ifBlank {
                    if (outcome.success) {
                        ""
                    } else {
                        outcome.output
                    }
                }
                .trim()
        AlertDialog(
            onDismissRequest = { result = null },
            title = {
                Text(
                    stringResource(
                        if (outcome.success) {
                            R.string.ui_shizuku_activation_success
                        } else if (resultProfileOwner) {
                            R.string.ui_shizuku_profile_owner_failed
                        } else {
                            R.string.ui_shizuku_device_owner_failed
                        }
                    )
                )
            },
            text = {
                Column(Modifier.heightIn(max = 400.dp)) {
                    if (outcome.success) {
                        SelectionContainer { Text(outcome.output) }
                        if (diagnostics.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (diagnostics.isNotBlank()) {
                        Surface(
                            modifier =
                                Modifier.fillMaxWidth().weight(1f, fill = false),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border =
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                        ) {
                            ActivationStacktrace(diagnostics)
                        }
                    }
                }
            },
            confirmButton = {
                // Read the guarded clipboard inside the dialog's Compose root.
                val clipboard = LocalClipboard.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (diagnostics.isNotBlank()) {
                        TextButton(
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.tertiary
                                ),
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText(
                                                context.getString(
                                                    R.string.ui_activate_with_shizuku
                                                ),
                                                diagnostics,
                                            )
                                            .toClipEntry()
                                    )
                                }
                            },
                        ) {
                            Text(stringResource(R.string.ui_copy))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = { result = null }) {
                        Text(stringResource(R.string.ui_close))
                    }
                }
            },
        )
    }
}

@Composable
private fun ActivationStacktrace(diagnostics: String) {
    val scroll = rememberScrollState()
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
        SelectionContainer(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = 12.dp, top = 12.dp, end = 24.dp, bottom = 12.dp)
        ) {
            Text(
                diagnostics,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Keep the thumb visible whenever the trace overflows, including at rest.
        if (scroll.viewportSize > 0 && scroll.maxValue > 0) {
            Canvas(
                Modifier.align(Alignment.CenterEnd)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .testTag("shizuku-activation-scrollbar")
            ) {
                val viewport = scroll.viewportSize
                val maximum = scroll.maxValue
                if (viewport <= 0 || maximum <= 0) {
                    return@Canvas
                }
                val total = viewport.toFloat() + maximum
                val thumbHeight =
                    (size.height * viewport / total).coerceIn(
                        24.dp.toPx().coerceAtMost(size.height),
                        size.height,
                    )
                val offset = (size.height - thumbHeight) * scroll.value / maximum
                val radius = CornerRadius(size.width / 2)
                drawRoundRect(trackColor, cornerRadius = radius)
                drawRoundRect(
                    thumbColor,
                    topLeft = Offset(0f, offset),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = radius,
                )
            }
        }
    }
}

@Composable
private fun rememberShizukuAvailable(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var available by
        remember(context) {
            mutableStateOf(isShizukuAvailable(context))
        }
    DisposableEffect(context, lifecycleOwner) {
        fun refresh() {
            available = isShizukuAvailable(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    refresh()
                }
            }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val died = Shizuku.OnBinderDeadListener { refresh() }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(died)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(died)
            context.unregisterReceiver(receiver)
        }
    }
    return available
}

internal fun isShizukuAvailable(context: Context): Boolean {
    return Shizuku.pingBinder() ||
        try {
            // Discover the app providing the SDK permission, including compatible
            // forks. A stopped manager remains discoverable, without a package
            // allowlist.
            val manager =
                context.packageManager
                    .getPermissionInfo(ShizukuProvider.PERMISSION, 0)
                    .packageName
            manager != null && isPackageInstalled(context, manager)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
