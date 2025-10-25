package uk.nktnet.webviewkiosk.utils.webview.html

import android.os.Build
import android.text.Html
import android.webkit.WebViewClient
import uk.nktnet.webviewkiosk.config.option.ThemeOption

private val ERROR_HINTS: Map<Int, String> by lazy {
    val base = mapOf(
        WebViewClient.ERROR_UNKNOWN to "A generic error occurred. Try reloading the page.",
        WebViewClient.ERROR_HOST_LOOKUP to "Cannot resolve host. Check the URL or your network connection.",
        WebViewClient.ERROR_CONNECT to "Failed to connect. The server may be offline.",
        WebViewClient.ERROR_TIMEOUT to "Connection timed out. Try again later.",
        WebViewClient.ERROR_AUTHENTICATION to "Authentication failed. Check credentials.",
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE to "SSL handshake failed.",
        WebViewClient.ERROR_FILE to "File error occurred.",
        WebViewClient.ERROR_FILE_NOT_FOUND to "File not found."
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        base + (WebViewClient.ERROR_UNSAFE_RESOURCE to "Blocked unsafe resource.")
    } else base
}

fun generateErrorPage(
    theme: ThemeOption,
    errorCode: Int?,
    description: String?,
    failingUrl: String?
): String {
    val url = failingUrl ?: "N/A"
    val message = description ?: "Unknown error"
    val hint = errorCode?.let { ERROR_HINTS[it] } ?: "Check your network or URL."

    return """
        <html>
          <head>
            <meta
              name="viewport"
              content="width=device-width, initial-scale=1.0, maximum-scale=1.0"
            />
            <style>
              ${generateThemeCss(theme)}
              hr.title-hr {
                width: 80%;
                margin: 8px auto;
                border: none;
                border-top: 2px solid #ccc;
              }
              .url-section {
                margin-top: 24px;
                word-break: break-all;
                overflow-wrap: break-word;
              }
              p {
                word-break: normal;
                overflow-wrap: break-word;
              }
              body {
                padding: 20px;
              }
            </style>
          </head>
          <body style="text-align:center;">
            <h1 style="padding-top:50px">⚠️ Error ($errorCode)</h1>
            <hr class="title-hr"/>
            <div class="url-section">
              <p style="padding-top:20px"><b>Description</b></p>
              <p>${Html.escapeHtml(message)}</p>

              <p style="padding-top:20px"><b>Hint</b></p>
              <p>${Html.escapeHtml(hint)}</p>

              <p style="padding-top:20px"><b>URL</b></p>
              <p>${Html.escapeHtml(url)}</p>
            </div>
          </body>
        </html>
    """.trimIndent()
}
