package uk.nktnet.webviewkiosk.utils.webview.html

import android.content.Context
import android.text.Html
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.getLocalFileLink
import uk.nktnet.webviewkiosk.utils.getUUID
import uk.nktnet.webviewkiosk.utils.humanReadableSize

/** A plain HTML chooser for manually entered aliases with duplicate names. */
internal fun generateLocalFileSelectionHtml(
    context: Context,
    name: String,
    matches: List<File>,
): String {
    val appName = Html.escapeHtml(context.getString(R.string.app_name))
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val rows =
        matches.joinToString("\n") { file ->
            val size = Html.escapeHtml(humanReadableSize(context, file.length()))
            val lastModified =
                Html.escapeHtml(dateFormat.format(Date(file.lastModified())))
            """
                <li>
                  <dl>
                    <dt>UUID</dt><dd><a href="${Html.escapeHtml(file.getLocalFileLink())}">${Html.escapeHtml(file.getUUID())}</a></dd>
                    <dt>Size</dt><dd>$size</dd>
                    <dt>Time</dt><dd>$lastModified</dd>
                  </dl>
                </li>
            """
                .trimIndent()
        }
    return """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$appName - Matching files</title>
            <style>
              body { margin: 0; padding: 16px; font-family: sans-serif; line-height: 1.4; }
              main { max-width: 720px; margin: auto; }
              h1 { margin: 0 0 16px; font-size: 1.5rem; }
              p { margin: 0 0 16px; }
              strong, dd a { overflow-wrap: anywhere; }
              dd a { font-family: monospace; }
              ul { margin: 0; padding: 0; list-style: none; }
              li { border-top: 1px solid #ddd; padding: 16px 0; }
              dl { display: grid; grid-template-columns: 3.5rem minmax(0, 1fr); gap: 8px 12px; margin: 0; }
              dt { font-weight: bold; }
              dd { margin: 0; min-width: 0; }
              dd:nth-of-type(2) { white-space: nowrap; }
            </style>
          </head>
          <body>
            <main>
              <h1>$appName</h1>
              <p>Multiple files are named <strong>${Html.escapeHtml(name)}</strong>. Select a UUID:</p>
              <ul>
                $rows
              </ul>
            </main>
          </body>
        </html>
    """
        .trimIndent()
}
