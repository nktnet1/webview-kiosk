package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generatePrefersColorSchemeOverrideScript(theme: ThemeOption): String {
    if (theme == ThemeOption.SYSTEM) {
        return ""
    }

    val mediaValue = when (theme) {
        ThemeOption.DARK -> "dark"
        ThemeOption.LIGHT -> "light"
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
