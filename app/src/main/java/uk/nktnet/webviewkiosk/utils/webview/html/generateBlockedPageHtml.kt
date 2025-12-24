package com.nktnet.webview_kiosk.utils.webview.html

import android.text.Html
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ThemeOption

enum class BlockCause(val label: String) {
    BLACKLIST("URL matches blacklist"),
    LOCAL_FILE("Local files are forbidden");

    override fun toString() = label
}

fun generateBlockedPageHtml(
    theme: ThemeOption,
    blockCause: BlockCause = BlockCause.BLACKLIST,
    userSettings: UserSettings,
    url: String,
): String {
    val themeCss = generateThemeCss(theme)
    val message = Html.escapeHtml(userSettings.blockedMessage)
    val homeUrl = Html.escapeHtml(userSettings.homeUrl)
    val urlDisplay = Html.escapeHtml(url)

    val backButtonHtml = if (userSettings.allowBackwardsNavigation) {
        """<button style="width:100px;" onclick="history.back()">Back</button>"""
    } else {
        ""
    }

    val homeButtonHtml = if (userSettings.allowGoHome) {
        """<button style="width:100px;" onclick="location.href='${homeUrl}'">Home</button>"""
    } else {
        ""
    }

    return """
        <html>
          <head>
            <meta
              name="viewport"
              content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"
            />
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
                .actions {
                    display: flex;
                    gap: 12px;
                    justify-content: center;
                }
                button {
                    font-size: 16px;
                    border-radius: 6px;
                }
                $themeCss
            </style>
          </head>
          <body>
            <h2>🚫 Access Blocked</h2>
            <p>${message}</p>

            <div class="actions" style="margin-top:5;margin-bot:5px;">
              $backButtonHtml
              $homeButtonHtml
            </div>

            <hr />
            <b>URL</b>
            <p>${urlDisplay}</p>

            <hr />
            <b>CAUSE</b>
            <p>${blockCause}</p>
          </body>
        </html>
    """.trimIndent()
}
