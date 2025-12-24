package com.nktnet.webview_kiosk.utils.webview.html

import com.nktnet.webview_kiosk.config.option.ThemeOption

private const val DARK_CSS = """
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
    button {
        background-color: #1e1e1e;
        color: #ffffff;
        border: 1px solid #888888;
        padding: 12px;
    }
    button:active {
        background-color: #2a2a2a;
    }
"""

private const val LIGHT_CSS = """
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
    button {
        background-color: #f1f3f4;
        color: #000000;
        border: 1px solid #888888;
        padding: 12px;
    }
    button:active {
        background-color: #e0e0e0;
    }
"""

fun generateThemeCss(theme: ThemeOption): String = when (theme) {
    ThemeOption.DARK -> DARK_CSS
    ThemeOption.LIGHT -> LIGHT_CSS
    ThemeOption.SYSTEM -> """
        $LIGHT_CSS
        @media (prefers-color-scheme: dark) {
            $DARK_CSS
        }
    """
}
