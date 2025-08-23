package com.nktnet.webview_kiosk.utils.webview

import android.text.Html

fun generateBlockedPageHtml(url: String, message: String): String {
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
                  border-top: 1px solid #555555;
                  margin: 20px 0 30px 0px;
                }
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