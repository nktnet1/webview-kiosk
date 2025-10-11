package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.text.Html
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.getDisplayName
import uk.nktnet.webviewkiosk.utils.getUUID
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private fun fileDisplayCommonCss(theme: ThemeOption): String {
    return """
        body {
            margin: 0;
            padding: 4px 16px 16px 16px;
            font-family: sans-serif;
            overflow-wrap: break-word;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            text-align: left;
            white-space: pre-wrap;
        }
        h1 {
            margin: 4px 0 4px 0;
            text-align: center;
        }
        b {
            margin-top: 6px;
        }
        div {
            margin: 2px 0 4px 0;
        }
        hr {
            border: none;
            border-top: 1px solid #ccc;
            margin: 4px 0;
        }
        ${generateThemeCss(theme)}
    """.trimIndent()
}

fun generateFileMissingPage(file: File, theme: ThemeOption): String {
    return """
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
                ${fileDisplayCommonCss(theme)}
            </style>
          </head>
          <body>
            <h2>File Not Found</h2>
            <hr />
            <p>${Html.escapeHtml(file.absolutePath)}</p>
          </body>
        </html>
    """.trimIndent()
}

fun generateUnsupportedMimeTypePage(
    context: Context,
    file: File,
    mimeType: String?,
    theme: ThemeOption
): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val lastModified = sdf.format(file.lastModified())

    return """
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
                ${fileDisplayCommonCss(theme)}
            </style>
          </head>
          <body>
            <h1>Local File Info</h1>
            <hr />
            
            <p style="font-size:0.8rem; text-align:center; white-space:normal; margin-bottom:25px">
                <i>
                    You are seeing this screen because ${Constants.APP_NAME} may not be able to
                    render this file.
                    This may be because the mime type, detected from the file extension, does not
                    indicate a HTML, image, audio or video file.
                    <br><br>
                    Please try renaming the file extension, or if you believe it to be a bug,
                    please create an issue on GitHub.
                </i>
            </p>
            <hr />
            
            <b>Name</b>
            <div>${Html.escapeHtml(file.getDisplayName())}</div>
            <hr />
            
            <b>Mime Type</b>
            <div>${mimeType ?: "N/A"}</div>
            <hr />
            
            <b>ID</b>
            <div>${Html.escapeHtml(file.getUUID())}</div>
            <hr />
            
            <b>Path</b>
            <div>${Html.escapeHtml(file.absolutePath)}</div>
            <hr />
            
            <b>Size</b>
            <div>${humanReadableSize(context, file.length())}</div>
            <hr />
            
            <b>Last Modified</b>
            <div>$lastModified</div>
            <hr />
            
            <b>Readable</b>
            <div>${file.canRead()}</div>
            <hr />
            
            <b>Writable</b>
            <div>${file.canWrite()}</div>
            <hr />
            
            <b>Executable</b>
            <div>${file.canExecute()}</div>
            <hr />
            
            <b>Is Directory</b>
            <div>${file.isDirectory}</div>
          </body>
        </html>
    """.trimIndent()
}
