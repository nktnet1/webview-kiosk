package uk.nktnet.webviewkiosk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import uk.nktnet.webviewkiosk.config.Constants

/**
 * HOME entry point kept in a separate task from [MainActivity].
 *
 * Some Android 6 devices immediately exit lock task mode when the locked task is also the
 * system HOME task. Keep this activity as the HOME task and forward to the normal launcher task.
 */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchMainActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchMainActivity()
    }

    private fun launchMainActivity() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            putExtra(Constants.INTENT_HOME_LAUNCH, true)
        }
        startActivity(launchIntent)
    }
}
