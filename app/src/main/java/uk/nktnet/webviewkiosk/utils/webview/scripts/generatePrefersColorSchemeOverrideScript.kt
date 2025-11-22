package com.nktnet.webview_kiosk.utils.webview.scripts

import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.utils.webview.wrapJsInIIFE

fun generatePrefersColorSchemeOverrideScript(theme: ThemeOption): String {
    if (theme == ThemeOption.SYSTEM) {
        return ""
    }

    val mediaValue = when (theme) {
        ThemeOption.DARK -> "dark"
        ThemeOption.LIGHT -> "light"
        else -> error("Unhandled theme: $theme")
    }

    val innerScript = """
        window.matchMedia = (function(origMatchMedia) {
            return function(query) {
                if (query === '(prefers-color-scheme: dark)') {
                    return {
                        matches: ${mediaValue == "dark"},
                        media: query,
                        addListener: function() {},
                        removeListener: function() {},
                        onchange: null,
                        addEventListener: function() {},
                        removeEventListener: function() {},
                        dispatchEvent: function() { return false; }
                    };
                }
                if (query === '(prefers-color-scheme: light)') {
                    return {
                        matches: ${mediaValue == "light"},
                        media: query,
                        addListener: function() {},
                        removeListener: function() {},
                        onchange: null,
                        addEventListener: function() {},
                        removeEventListener: function() {},
                        dispatchEvent: function() { return false; }
                    };
                }
                return origMatchMedia.call(window, query);
            };
        })(window.matchMedia);
    """.trimIndent()

    return wrapJsInIIFE(innerScript)
}
