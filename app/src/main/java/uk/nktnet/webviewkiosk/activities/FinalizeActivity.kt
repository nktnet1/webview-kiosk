package uk.nktnet.webviewkiosk.activities

import android.os.Bundle
import androidx.activity.ComponentActivity

class FinalizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
