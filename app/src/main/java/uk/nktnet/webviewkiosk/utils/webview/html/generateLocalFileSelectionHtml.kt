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
                <tr>
                  <td><a href="${Html.escapeHtml(file.getLocalFileLink())}">${Html.escapeHtml(file.getUUID())}</a></td>
                  <td>$size</td>
                  <td>$lastModified</td>
                </tr>
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
              strong, td:first-child a { overflow-wrap: anywhere; }
              td:first-child a { font-family: monospace; }
              table { width: 100%; border-collapse: collapse; }
              th, td { padding: 12px 8px; text-align: left; vertical-align: top; }
              td:nth-child(2), td:nth-child(3) { white-space: nowrap; }
              tbody tr { border-top: 1px solid #ddd; }
              @media (max-width: 560px) {
                thead { display: none; }
                tbody tr { display: grid; grid-template-columns: max-content 1fr; gap: 4px 12px; padding: 12px 0; }
                td { padding: 0; }
                td:first-child { grid-column: 1 / -1; min-width: 0; }
                td:last-child { text-align: right; }
              }
            </style>
          </head>
          <body>
            <main>
              <h1>$appName</h1>
              <p>Multiple files are named <strong>${Html.escapeHtml(name)}</strong>. Select a UUID:</p>
              <table>
                <thead>
                  <tr><th scope="col">UUID</th><th scope="col">Size</th><th scope="col">Last modified</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </main>
          </body>
        </html>
    """
        .trimIndent()
}
