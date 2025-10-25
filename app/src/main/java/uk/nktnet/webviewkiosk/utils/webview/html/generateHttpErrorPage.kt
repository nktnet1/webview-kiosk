package uk.nktnet.webviewkiosk.utils.webview.html

import android.text.Html
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import uk.nktnet.webviewkiosk.config.option.ThemeOption

private val HTTP_STATUS_MESSAGES = mapOf(
    400 to "Bad Request",
    401 to "Unauthorized",
    403 to "Forbidden",
    404 to "Page Not Found",
    408 to "Request Timeout",
    500 to "Internal Server Error",
    502 to "Bad Gateway",
    503 to "Service Unavailable",
    504 to "Gateway Timeout"
)

fun generateHttpErrorPage(
    theme: ThemeOption,
    request: WebResourceRequest?,
    errorResponse: WebResourceResponse?
): String {
    val statusCode = errorResponse?.statusCode
    val statusMessage = HTTP_STATUS_MESSAGES[statusCode] ?: "Error"
    val url = request?.url?.toString() ?: "N/A"

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
                word-break: break-all;
                overflow-wrap: break-word;
              }
              body {
                padding: 20px
              }
            </style>
          </head>
          <body style="text-align:center;">
            <h1 style="padding-top:50px">⚠️ $statusMessage</h1>
            <hr class="title-hr"/>
            <div class="url-section">
              <p><b>Error Status Code</b></p>
              <p>${statusCode}</p>

              <p style="padding-top:20px"><b>URL</b></p>
              <p>${Html.escapeHtml(url)}</p>
            </div>
          </body>
        </html>
    """.trimIndent()
}
