package uk.nktnet.webviewkiosk.utils.webview.html

import android.text.Html
import java.io.File
import uk.nktnet.webviewkiosk.utils.getLocalFileLink
import uk.nktnet.webviewkiosk.utils.getUUID

/** A plain HTML chooser for manually entered aliases with duplicate names. */
internal fun generateLocalFileSelectionHtml(
    name: String,
    matches: List<File>,
): String {
    val links =
        matches.joinToString("\n") { file ->
            """<li><a href="${Html.escapeHtml(file.getLocalFileLink())}">${Html.escapeHtml(file.getUUID())}</a></li>"""
        }
    return """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Choose a local file</title>
          </head>
          <body>
            <h1>Choose a local file</h1>
            <p>Multiple files are named <strong>${Html.escapeHtml(name)}</strong>. Select a UUID:</p>
            <ul>$links</ul>
          </body>
        </html>
    """
        .trimIndent()
}
