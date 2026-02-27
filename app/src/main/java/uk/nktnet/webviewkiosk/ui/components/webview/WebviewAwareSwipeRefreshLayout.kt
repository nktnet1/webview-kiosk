package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import kotlin.math.abs

class WebviewAwareSwipeRefreshLayout : SwipeRefreshLayout {

    private lateinit var webview: WebView
    private lateinit var customLoadUrl: (String) -> Unit
    private var initialY = 0f

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) {
                return false
            }

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY)) {
                if (
                    abs(diffX) > SWIPE_THRESHOLD
                    && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    val systemSettings = SystemSettings(context)

                    if (diffX > 0) {
                        WebViewNavigation.goForward(customLoadUrl, systemSettings)
                    } else {
                        WebViewNavigation.goBack(customLoadUrl, systemSettings)
                    }
                    return true
                }
            }
            return false
        }
    }

    private val gestureDetector by lazy { GestureDetector(context, gestureListener) }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, webview: WebView, customLoadUrl: (String) -> Unit) : super(context) {
        this.webview = webview
        this.customLoadUrl = customLoadUrl
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)

        if (!isEnabled || ev.pointerCount > 1) {
            return false
        }

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            initialY = ev.y
        }

        if (webview.scrollY != 0 || initialY > height / 4) {
            return false
        }

        return super.onInterceptTouchEvent(ev)
    }
}
