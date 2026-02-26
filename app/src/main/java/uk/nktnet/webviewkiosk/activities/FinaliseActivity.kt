package uk.nktnet.webviewkiosk.activities

import android.os.Bundle
import androidx.activity.ComponentActivity

class FinaliseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
