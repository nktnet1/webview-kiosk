package uk.nktnet.webviewkiosk.ui.components.auth

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import kotlin.math.roundToInt

@Composable
internal fun Android6CustomAuthPasswordDialog(userSettings: UserSettings) {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
        return
    }
    val activity = LocalActivity.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    DisposableEffect(activity, lifecycle, darkTheme) {
        val prompt = Android6PasswordDialog(
            activity = activity,
            lifecycle = lifecycle,
            darkTheme = darkTheme,
            readPassword = { userSettings.customAuthPassword },
            isPromptActive = {
                AuthenticationManager.showCustomAuth.value
                    && AuthenticationManager.promptResults.value ==
                    AuthenticationManager.AuthenticationResult.Pending
            },
            onAuthenticated = { AuthenticationManager.customAuthSuccess() },
            onCancelled = { AuthenticationManager.customAuthCancel() },
        )
        prompt.start()
        onDispose {
            // Lifecycle/composition disposal is not a user cancellation and must not change
            // another activity's authentication result.
            prompt.dispose()
        }
    }
}

/**
 * A View-only password prompt for API 23. Neither its field nor its scroll container uses
 * Compose's ContentInViewNode, including when the IME resizes the window or a user taps the field.
 * The controller is also lifecycle-bound: the background HOME activity cannot own a live prompt.
 */
internal class Android6PasswordDialog(
    private val activity: Activity,
    private val lifecycle: Lifecycle,
    darkTheme: Boolean,
    private val readPassword: () -> String,
    private val isPromptActive: () -> Boolean,
    private val onAuthenticated: () -> Unit,
    private val onCancelled: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var observing = false
    private var disposed = false
    private var waiting = false
    private val builder = AlertDialog.Builder(
        activity,
        if (darkTheme) {
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        } else {
            androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
        },
    )
    private val context = builder.context
    private val passwordField = AppCompatEditText(context).apply {
        id = android.R.id.edit
        hint = "Enter your password"
        setSingleLine(true)
        inputType = if (readPassword().all { it.isDigit() }) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        transformationMethod = PasswordTransformationMethod.getInstance()
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isSaveEnabled = false
    }
    private val showPassword = AppCompatCheckBox(context).apply {
        id = android.R.id.checkbox
        text = "Show password"
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (24 * resources.displayMetrics.density).roundToInt()
        setPadding(padding, padding / 2, padding, padding / 2)
        isFocusableInTouchMode = true
        addView(AppCompatTextView(context).apply {
            text = "Password"
            labelFor = passwordField.id
        })
        addView(
            passwordField,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(showPassword)
    }
    internal val dialog: AlertDialog = builder
        .setTitle("Authentication Required")
        .setView(ScrollView(context).apply { addView(content) })
        .setPositiveButton("Unlock", null)
        .setNegativeButton("Cancel", null)
        .setNeutralButton("Clear", null)
        .create()

    private val wrongPassword = Runnable {
        if (canInteract()) {
            waiting = false
            passwordField.error = "Incorrect password"
            updateControls()
            ToastManager.show(activity, "Incorrect password")
        }
    }
    private val observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            showIfResumed()
        }

        override fun onPause(owner: LifecycleOwner) {
            hide()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            dispose()
        }
    }

    init {
        dialog.setOwnerActivity(activity)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            if (canInteract()) {
                dispose()
                onCancelled()
            }
        }
        passwordField.doAfterTextChanged {
            passwordField.error = null
            updateControls()
        }
        passwordField.setOnEditorActionListener { _, actionId, event ->
            if (
                actionId == EditorInfo.IME_ACTION_DONE
                || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                submit()
                true
            } else {
                false
            }
        }
        showPassword.setOnCheckedChangeListener { _, visible ->
            val selection = passwordField.selectionEnd.coerceAtLeast(0)
            passwordField.transformationMethod = if (visible) {
                null
            } else {
                PasswordTransformationMethod.getInstance()
            }
            passwordField.setSelection(selection.coerceAtMost(passwordField.length()))
        }
    }

    fun start() {
        if (disposed || observing || Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
            return
        }
        observing = true
        lifecycle.addObserver(observer)
        showIfResumed()
    }

    private fun canInteract(): Boolean = !disposed
        && !activity.isFinishing
        && !activity.isDestroyed
        && lifecycle.currentState == Lifecycle.State.RESUMED
        && isPromptActive()

    private fun showIfResumed() {
        if (!canInteract() || dialog.isShowing) {
            return
        }
        dialog.window?.apply {
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        dialog.show()
        // Install these synchronously after show() creates the buttons. OnShowListener is
        // dispatched asynchronously; until then its default handlers would dismiss on any PIN.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { submit() }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            if (!waiting) {
                dialog.cancel()
            }
        }
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (!waiting) {
                passwordField.text?.clear()
            }
        }
        // Keep API 23's tap-to-focus behaviour without relying on any Compose focus delay.
        content.requestFocus()
        updateControls()
    }

    private fun submit() {
        if (waiting || !canInteract() || !dialog.isShowing) {
            return
        }
        val expected = readPassword()
        if (expected.isNotEmpty() && passwordField.text.toString() == expected) {
            // Remove the dialog and IME before publishing success, which may change lock-task
            // state and resize both activity windows. Never publish success twice.
            dispose()
            onAuthenticated()
        } else {
            waiting = true
            updateControls()
            handler.postDelayed(wrongPassword, 1000L)
        }
    }

    private fun updateControls() {
        passwordField.isEnabled = !waiting
        showPassword.isEnabled = !waiting
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = !waiting
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = !waiting
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.isEnabled =
            !waiting && !passwordField.text.isNullOrEmpty()
    }

    private fun hide() {
        handler.removeCallbacks(wrongPassword)
        waiting = false
        passwordField.windowToken?.let { token ->
            val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(token, 0)
        }
        passwordField.clearFocus()
        passwordField.text?.clear()
        showPassword.isChecked = false
        dialog.dismiss()
    }

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        if (observing) {
            lifecycle.removeObserver(observer)
            observing = false
        }
        dialog.setOnCancelListener(null)
        hide()
    }
}
