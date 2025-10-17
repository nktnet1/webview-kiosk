package com.nktnet.webview_kiosk.utils.webview

import com.nktnet.webview_kiosk.config.option.ThemeOption

fun generateThemeCss(theme: ThemeOption): String {
    return when (theme) {
        ThemeOption.DARK -> """
            body {
                background-color: #121212;
                color: #ffffff;
            }
            a {
                color: #64b5f6;
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
            a {
                color: #1a73e8;
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
            a {
                color: #1a73e8;
            }
            hr {
                border-top: 1px solid #555555;
            }
            @media (prefers-color-scheme: dark) {
                body {
                    background-color: #121212;
                    color: #ffffff;
                }
                a {
                    color: #64b5f6;
                }
                hr {
                    border-top: 1px solid #aaaaaa;
                }
            }
        """
    }
}
