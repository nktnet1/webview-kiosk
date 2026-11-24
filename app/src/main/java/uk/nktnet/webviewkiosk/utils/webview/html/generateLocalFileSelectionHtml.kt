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
            <title>$appName - Choose a local file</title>
            <style>
              body { margin: 0; padding: 16px; font-family: sans-serif; }
              main { max-width: 720px; margin: auto; }
              strong { overflow-wrap: anywhere; }
              table { width: 100%; table-layout: fixed; border-collapse: collapse; }
              th, td { padding: 8px; text-align: left; vertical-align: top; }
              th:first-child { width: 38%; }
              th:nth-child(2) { width: 28%; }
              td:first-child { word-break: break-all; }
              td:nth-child(2) { white-space: nowrap; }
            </style>
          </head>
          <body>
            <main>
              <h1>$appName</h1>
              <h2>Choose a local file</h2>
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
