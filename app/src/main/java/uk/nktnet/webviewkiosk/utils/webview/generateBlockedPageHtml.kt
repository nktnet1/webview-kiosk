package com.nktnet.webview_kiosk.utils.webview

import android.text.Html
import com.nktnet.webview_kiosk.config.option.ThemeOption

fun generateBlockedPageHtml(url: String, message: String, theme: ThemeOption): String {
    val style = when (theme) {
        ThemeOption.DARK -> """
            body {
                background-color: #121212;
                color: #ffffff;
            }
            hr {
                border-top: 1px solid #aaaaaa;
            }
        """
        ThemeOption.LIGHT -> """
            body {
                background-color: #ffffff;
                color: #000000;
            }
            hr {
                border-top: 1px solid #555555;
            }
        """
        ThemeOption.SYSTEM -> """
            body {
                background-color: #ffffff;
                color: #000000;
            }
            hr {
                border-top: 1px solid #555555;
            }
            @media (prefers-color-scheme: dark) {
                body {
                    background-color: #121212;
                    color: #ffffff;
                }
                hr {
                    border-top: 1px solid #aaaaaa;
                }
            }
        """
    }

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
                $style
            </style>
          </head>
          <body>
            <h2>🚫 Access Blocked</h2>
            <p>${Html.escapeHtml(message)}</p>
            <hr />
            <b>URL:</b>
            <p>${Html.escapeHtml(url)}</p>
          </body>
        </html>
    """.trimIndent()
}
