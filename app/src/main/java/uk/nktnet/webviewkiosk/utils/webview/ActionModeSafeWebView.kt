package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.annotation.RequiresApi
import uk.nktnet.webviewkiosk.config.Constants

/** Keeps native selection toolbars within the lifetime of their host window. */
internal open class ActionModeSafeWebView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyleAttr) {
    // Layout tools have no navigation or dialog lifecycle to validate.
    private var isHostActive: () -> Boolean = { false }

    constructor(
        context: Context,
        isHostActive: () -> Boolean,
    ) : this(context) {
        this.isHostActive = isHostActive
    }

    private val activeModes = mutableSetOf<ActionMode>()
    private var detaching = false
    private var disposed = false

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        // On API 23+ View's untyped overload delegates to the typed one. Wrap
        // the callback only once while preserving the primary action mode type.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActionMode(callback, ActionMode.TYPE_PRIMARY)
        } else {
            startSafely(callback) { super.startActionMode(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun startActionMode(
        callback: ActionMode.Callback,
        type: Int,
    ): ActionMode? = startSafely(callback) { super.startActionMode(it, type) }

    private fun canStart(): Boolean =
        !disposed &&
            !detaching &&
            isAttachedToWindow &&
            windowToken != null &&
            isHostActive()

    private fun startSafely(
        callback: ActionMode.Callback,
        start: (ActionMode.Callback) -> ActionMode?,
    ): ActionMode? {
        if (!canStart()) {
            return null
        }
        val tracking = TrackingCallback(callback)
        val wrapped =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    callback is ActionMode.Callback2
            ) {
                TrackingCallback2(callback, tracking)
            } else {
                tracking
            }
        return try {
            val mode = start(wrapped)
            if (mode == null || !canStart()) {
                tracking.mode?.let(::finishMode)
                null
            } else {
                mode
            }
        } catch (e: WindowManager.BadTokenException) {
            // The window can become invalid after the checks above. DecorView
            // may have accepted the callback before trying to show the toolbar.
            Log.w(Constants.APP_SCHEME, "Unable to start WebView action mode", e)
            tracking.mode?.let(::finishMode)
            null
        }
    }

    override fun onAttachedToWindow() {
        detaching = false
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        // The window token may still be non-null while detachment is in progress.
        detaching = true
        try {
            finishActionModes()
        } finally {
            super.onDetachedFromWindow()
        }
    }

    fun disposeActionModes() {
        disposed = true
        finishActionModes()
    }

    private fun finishActionModes() {
        activeModes.toList().forEach(::finishMode)
    }

    private fun finishMode(mode: ActionMode) {
        if (!activeModes.remove(mode)) {
            return
        }
        try {
            mode.finish()
        } catch (e: WindowManager.BadTokenException) {
            Log.w(Constants.APP_SCHEME, "Unable to finish WebView action mode", e)
        }
    }

    private inner class TrackingCallback(private val callback: ActionMode.Callback) :
        ActionMode.Callback {
        var mode: ActionMode? = null
            private set

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val accepted = callback.onCreateActionMode(mode, menu)
            if (accepted) {
                this.mode = mode
                activeModes.add(mode)
            }
            return accepted
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
            callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            callback.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) {
            activeModes.remove(mode)
            this.mode = null
            callback.onDestroyActionMode(mode)
        }
    }

    // Keep Callback2 out of the legacy callback class for Android 5.x. Dropping
    // the content rectangle callback would break positioning of selection tools.
    @RequiresApi(Build.VERSION_CODES.M)
    private class TrackingCallback2(
        private val callback: ActionMode.Callback2,
        tracking: ActionMode.Callback,
    ) : ActionMode.Callback2(), ActionMode.Callback by tracking {
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) =
            callback.onGetContentRect(mode, view, outRect)
    }
}
