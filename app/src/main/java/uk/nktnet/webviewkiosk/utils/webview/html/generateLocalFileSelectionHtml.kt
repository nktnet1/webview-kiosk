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
              :root { font-family: system-ui, sans-serif; color-scheme: light dark; }
              * { box-sizing: border-box; }
              body { margin: 0; padding: 24px 16px; background: #f3f4f6; color: #111827; line-height: 1.5; }
              main { max-width: 800px; margin: 0 auto; padding: 24px; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; }
              h1 { margin: 0 0 4px; font-size: 1rem; color: #4b5563; }
              h2 { margin: 0 0 12px; font-size: 1.5rem; }
              p { margin: 0 0 20px; }
              strong, a { overflow-wrap: anywhere; }
              .table-wrap { overflow-x: auto; border: 1px solid #e5e7eb; border-radius: 8px; }
              table { width: 100%; min-width: 520px; border-collapse: collapse; text-align: left; }
              th, td { padding: 12px 16px; border-bottom: 1px solid #e5e7eb; }
              th { background: #f9fafb; }
              tbody tr:last-child td { border-bottom: 0; }
              a { color: #1d4ed8; font-weight: 600; }
              a:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
              @media (max-width: 600px) {
                body { padding: 12px; }
                main { padding: 16px; }
              }
              @media (prefers-color-scheme: dark) {
                body { background: #111827; color: #f9fafb; }
                main { background: #1f2937; border-color: #374151; }
                h1 { color: #d1d5db; }
                .table-wrap, th, td { border-color: #4b5563; }
                th { background: #374151; }
                a { color: #93c5fd; }
              }
            </style>
          </head>
          <body>
            <main>
              <h1>$appName</h1>
              <h2>Choose a local file</h2>
              <p>Multiple files are named <strong>${Html.escapeHtml(name)}</strong>. Select a UUID:</p>
              <div class="table-wrap" role="region" aria-label="Matching files" tabindex="0">
                <table>
                  <thead>
                    <tr><th scope="col">UUID</th><th scope="col">Size</th><th scope="col">Last modified</th></tr>
                  </thead>
                  <tbody>
                    $rows
                  </tbody>
                </table>
              </div>
            </main>
          </body>
        </html>
    """
        .trimIndent()
}
