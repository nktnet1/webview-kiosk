package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupMenu
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.P])
class ActionModeSafeWebViewTest {
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var parent: ActionModeParent
    private lateinit var webView: ActionModeSafeWebView
    private var hostActive = true

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activity = activityController.get()
        parent = ActionModeParent(activity)
        webView = ActionModeSafeWebView(activity) { hostActive }
        parent.addView(webView)
        activity.setContentView(parent)
        assertTrue(webView.isAttachedToWindow)
        assertNotNull(webView.windowToken)
    }

    @After
    fun tearDown() {
        webView.disposeActionModes()
        parent.removeView(webView)
        webView.destroy()
        activityController.pause().stop().destroy()
    }

    @Test
    fun layoutToolConstructorsExposeJavaSignaturesAndRejectUnmanagedModes() {
        val type = ActionModeSafeWebView::class.java
        val views =
            listOf(
                type.getConstructor(Context::class.java).newInstance(activity),
                type
                    .getConstructor(Context::class.java, AttributeSet::class.java)
                    .newInstance(activity, null),
                type
                    .getConstructor(
                        Context::class.java,
                        AttributeSet::class.java,
                        Int::class.javaPrimitiveType!!,
                    )
                    .newInstance(activity, null, android.R.attr.webViewStyle),
            )
        val callback = RecordingCallback()
        try {
            for (view in views) {
                parent.addView(view)
                assertTrue(view.isAttachedToWindow)
                assertNotNull(view.windowToken)
                assertNull(view.startActionMode(callback))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    assertNull(
                        view.startActionMode(callback, ActionMode.TYPE_FLOATING)
                    )
                }
            }
            assertEquals(0, parent.starts)
            assertEquals(0, callback.creates)
        } finally {
            for (view in views) {
                view.disposeActionModes()
                parent.removeView(view)
                view.destroy()
            }
        }
    }

    @Test
    fun detachedInactiveAndDisposedViewsNeverAskParentToStartMode() {
        val callback = RecordingCallback()
        parent.removeView(webView)
        assertNull(webView.startActionMode(callback))
        parent.addView(webView)
        hostActive = false
        assertNull(webView.startActionMode(callback))
        hostActive = true
        webView.disposeActionModes()
        assertNull(webView.startActionMode(callback))
        assertEquals(0, parent.starts)
        assertEquals(0, callback.creates)
    }

    @Test
    fun tokenRaceBeforeCreationReturnsNull() {
        parent.failBeforeCreate = true
        val callback = RecordingCallback()
        assertNull(webView.startActionMode(callback))
        assertEquals(1, parent.starts)
        assertEquals(0, callback.creates)
        assertEquals(0, callback.destroys)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun floatingToolbarTokenRaceAfterCreationFinishesPartialMode() {
        parent.failAfterCreate = true
        val callback = RecordingCallback()
        assertNull(webView.startActionMode(callback, ActionMode.TYPE_FLOATING))
        val mode = checkNotNull(parent.lastMode)
        assertEquals(ActionMode.TYPE_FLOATING, parent.lastType)
        assertEquals(1, callback.creates)
        assertEquals(1, callback.destroys)
        assertEquals(1, mode.finishes)
        webView.disposeActionModes()
        assertEquals(1, mode.finishes)
    }

    @Test
    fun normalModeForwardsCallbacksAndIsNotFinishedTwice() {
        val callback = RecordingCallback()
        val mode = checkNotNull(webView.startActionMode(callback)) as TestActionMode
        assertSame(parent.lastMode, mode)
        assertEquals(1, callback.creates)
        mode.invalidate()
        assertEquals(1, callback.prepares)
        assertTrue(mode.click())
        assertEquals(1, callback.clicks)
        mode.finish()
        webView.disposeActionModes()
        assertEquals(1, mode.finishes)
        assertEquals(1, callback.destroys)
    }

    @Test
    fun rejectedModeIsNotTrackedOrFinished() {
        val callback = RecordingCallback(accept = false)
        assertNull(webView.startActionMode(callback))
        webView.disposeActionModes()
        assertEquals(1, callback.creates)
        assertEquals(0, callback.destroys)
        assertEquals(0, parent.lastMode?.finishes)
    }

    @Test
    fun detachFinishesModeAndReattachPermitsNewMode() {
        val callback = RecordingCallback()
        val first = checkNotNull(webView.startActionMode(callback)) as TestActionMode
        parent.removeView(webView)
        assertEquals(1, first.finishes)
        assertNull(webView.startActionMode(callback))
        parent.addView(webView)
        val second =
            checkNotNull(webView.startActionMode(callback)) as TestActionMode
        webView.disposeActionModes()
        webView.disposeActionModes()
        assertEquals(1, second.finishes)
        assertEquals(2, callback.destroys)
        assertNull(webView.startActionMode(callback))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun floatingModePreservesContentRectangleAndType() {
        val callback =
            object :
                ActionMode.Callback2(), ActionMode.Callback by RecordingCallback() {
                override fun onGetContentRect(
                    mode: ActionMode,
                    view: View,
                    outRect: Rect,
                ) {
                    assertSame(webView, view)
                    outRect.set(10, 20, 30, 40)
                }
            }
        val mode =
            checkNotNull(webView.startActionMode(callback, ActionMode.TYPE_FLOATING))
        assertEquals(ActionMode.TYPE_FLOATING, parent.lastType)
        val wrapped = checkNotNull(parent.lastCallback) as ActionMode.Callback2
        val rect = Rect()
        wrapped.onGetContentRect(mode, webView, rect)
        assertEquals(Rect(10, 20, 30, 40), rect)
    }

    @Test
    fun badTokenWhileFinishingDoesNotPreventDisposal() {
        val mode =
            checkNotNull(webView.startActionMode(RecordingCallback()))
                as TestActionMode
        mode.failOnFinish = true
        webView.disposeActionModes()
        assertEquals(1, mode.finishes)
        assertNull(webView.startActionMode(RecordingCallback()))
    }

    private class RecordingCallback(private val accept: Boolean = true) :
        ActionMode.Callback {
        var creates = 0
        var prepares = 0
        var clicks = 0
        var destroys = 0

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            creates++
            return accept
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            prepares++
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            clicks++
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            destroys++
        }
    }

    private class ActionModeParent(context: Context) : FrameLayout(context) {
        var starts = 0
        var lastMode: TestActionMode? = null
        var lastType: Int? = null
        var lastCallback: ActionMode.Callback? = null
        var failBeforeCreate = false
        var failAfterCreate = false

        override fun startActionModeForChild(
            originalView: View,
            callback: ActionMode.Callback,
        ): ActionMode? = start(originalView, callback, null)

        override fun startActionModeForChild(
            originalView: View,
            callback: ActionMode.Callback,
            type: Int,
        ): ActionMode? = start(originalView, callback, type)

        private fun start(
            view: View,
            callback: ActionMode.Callback,
            type: Int?,
        ): ActionMode? {
            starts++
            lastType = type
            lastCallback = callback
            if (failBeforeCreate) {
                throw WindowManager.BadTokenException("window token rejected")
            }
            val mode = TestActionMode(view, callback)
            lastMode = mode
            if (!callback.onCreateActionMode(mode, mode.menu)) {
                return null
            }
            if (failAfterCreate) {
                throw WindowManager.BadTokenException("toolbar token rejected")
            }
            return mode
        }
    }

    private class TestActionMode(
        private val view: View,
        private val callback: Callback,
    ) : ActionMode() {
        private val items = PopupMenu(view.context, view).menu
        var finishes = 0
        var failOnFinish = false

        override fun finish() {
            finishes++
            callback.onDestroyActionMode(this)
            if (failOnFinish) {
                throw WindowManager.BadTokenException("toolbar token rejected")
            }
        }

        override fun invalidate() {
            callback.onPrepareActionMode(this, menu)
        }

        fun click(): Boolean = callback.onActionItemClicked(this, menu.add("Copy"))

        override fun getMenu(): Menu = items

        override fun getMenuInflater(): MenuInflater = MenuInflater(view.context)

        override fun getCustomView(): View? = null

        override fun getTitle(): CharSequence? = null

        override fun getSubtitle(): CharSequence? = null

        override fun setCustomView(view: View?) = Unit

        override fun setTitle(title: CharSequence?) = Unit

        override fun setTitle(resId: Int) = Unit

        override fun setSubtitle(subtitle: CharSequence?) = Unit

        override fun setSubtitle(resId: Int) = Unit
    }
}
