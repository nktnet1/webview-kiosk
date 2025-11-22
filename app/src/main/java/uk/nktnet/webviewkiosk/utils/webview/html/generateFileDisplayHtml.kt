package com.nktnet.webview_kiosk.utils.webview.html

import android.content.Context
import android.text.Html
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.utils.getDisplayName
import com.nktnet.webview_kiosk.utils.getUUID
import com.nktnet.webview_kiosk.utils.humanReadableSize
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
          <body style="text-align:center;">
            <h1 style="padding-top:50px">File Not Found</h1>
            <p>You may have renamed or removed this file.</p>
            <hr style="margin-top:10px; margin-bottom:20px;"/>

            <b>File Path</b>
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
                    This page is displayed because ${Constants.APP_NAME} cannot render this file.
                    This is because the mime type, detected from the file extension, does not
                    indicate a HTML, image, audio or video file. PDF files are not supported.
                    <br><br>
                    You can try renaming the file extension if you think the detected mime type
                    is incorrect. Otherwise, if you believe this to be a bug, please create an
                    <a href="${Constants.GITHUB_URL}/issues">issue on GitHub</a>.
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
