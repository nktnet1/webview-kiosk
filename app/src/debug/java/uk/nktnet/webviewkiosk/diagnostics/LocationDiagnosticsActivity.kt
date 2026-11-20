package uk.nktnet.webviewkiosk.diagnostics

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.R

/** User-started diagnostics remain inside the debug APK and expose no web bridge. */
class LocationDiagnosticsActivity : AppCompatActivity() {
    private lateinit var probe: LocationProviderProbe
    private lateinit var output: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padding + bars.left,
                padding + bars.top,
                padding + bars.right,
                padding + bars.bottom,
            )
            insets
        }
        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
        layout.addView(
            TextView(this).apply {
                setText(R.string.location_diagnostics_help)
            }
        )
        output =
            TextView(this).apply {
                id = R.id.location_diagnostics_report
                setTextIsSelectable(true)
            }
        fun button(label: Int, id: Int, action: () -> Unit) {
            layout.addView(
                Button(this).apply {
                    this.id = id
                    setText(label)
                    setOnClickListener { action() }
                }
            )
        }
        probe =
            LocationProviderProbe(this) { line ->
                output.text =
                    (output.text.toString() +
                            "${SystemClock.elapsedRealtime()}ms $line\n")
                        .takeLast(32_000)
            }
        button(
            R.string.location_diagnostics_criteria,
            R.id.location_diagnostics_criteria,
        ) {
            probe.start(LocationProviderProbe.Mode.WEBVIEW_CRITERIA)
        }
        button(R.string.location_diagnostics_gps, R.id.location_diagnostics_gps) {
            probe.start(LocationProviderProbe.Mode.DIRECT_GPS)
        }
        button(R.string.location_diagnostics_stop, R.id.location_diagnostics_stop) {
            probe.stop()
        }
        button(R.string.location_diagnostics_copy, R.id.location_diagnostics_copy) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.location_diagnostics_title),
                        output.text,
                    )
                )
        }
        layout.addView(output)
        val webView =
            try {
                WebViewCompat.getCurrentWebViewPackage(this)
            } catch (_: Exception) {
                null
            }
        output.text =
            "Package: $packageName\nApp: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid: ${Build.VERSION.SDK_INT}\nModel: ${Build.MANUFACTURER} ${Build.MODEL}\nWebView: ${webView?.packageName ?: "unknown"} ${webView?.versionName ?: "unknown"}\n"
    }

    override fun onPause() {
        probe.stop()
        super.onPause()
    }
}
