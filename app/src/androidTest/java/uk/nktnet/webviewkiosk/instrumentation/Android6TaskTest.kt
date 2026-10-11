package uk.nktnet.webviewkiosk.instrumentation

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.nktnet.webviewkiosk.Android6KioskActivity
import uk.nktnet.webviewkiosk.MainActivity
import java.util.concurrent.CopyOnWriteArrayList

/** Runs ONLY under Android 6; changing Build.VERSION in a JVM test cannot replace this. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.M, maxSdkVersion = Build.VERSION_CODES.M)
class Android6TaskTest {
    @Test
    fun homeLaunchUsesASeparatePrivateTask() {
        val application = InstalledAppHarness.context.applicationContext as Application
        val seen = CopyOnWriteArrayList<Activity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { seen.add(activity) }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        InstalledAppHarness.resetSettings("about:blank")
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClass(application, MainActivity::class.java)
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            application.startActivity(intent)
            InstalledAppHarness.eventually("HOME router and Android 6 kiosk activities") {
                seen.any { it.javaClass == MainActivity::class.java } &&
                    seen.any { it is Android6KioskActivity }
            }
            val routing = seen.first { it.javaClass == MainActivity::class.java }
            val kiosk = seen.first { it is Android6KioskActivity }
            assertNotEquals(routing.taskId, kiosk.taskId)
            assertTrue(kiosk.taskId > 0)
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                seen.forEach { activity -> if (!activity.isFinishing) activity.finish() }
            }
        }
    }
}
