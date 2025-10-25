package uk.nktnet.webviewkiosk.utils.webview.html

import android.text.Html
import uk.nktnet.webviewkiosk.config.option.ThemeOption

enum class BlockCause(val label: String) {
    BLACKLIST("URL matches blacklist"),
    INTENT_URL_SCHEME("Intent URL scheme are forbidden"),
    LOCAL_FILE("Local files are forbidden");

    override fun toString() = label
}
fun generateBlockedPageHtml(
    theme: ThemeOption,
    blockCause: BlockCause = BlockCause.BLACKLIST,
    message: String,
    url: String,
): String {
    val themeCss = generateThemeCss(theme)

    return """
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
                body {
                    margin: 0;
                    padding-top: 50px;
                    padding-left: 20px;
                    padding-right: 20px;
                    font-family: sans-serif;
                    overflow-wrap: break-word;
                    box-sizing: border-box;
                    display: flex;
                    flex-direction: column;
                    text-align: center;
                    justify-content: center;
                    white-space: pre-wrap;
                }
                hr {
                    border: none;
                    margin: 20px 0 30px 0px;
                }
                $themeCss
            </style>
          </head>
          <body>
            <h2>🚫 Access Blocked</h2>
            <p>${Html.escapeHtml(message)}</p>
            <hr />

            <b>URL</b>
            <p>${Html.escapeHtml(url)}</p>
            <hr />

            <b>CAUSE</b>
            <p>${blockCause}</p>
          </body>
        </html>
    """.trimIndent()
}
