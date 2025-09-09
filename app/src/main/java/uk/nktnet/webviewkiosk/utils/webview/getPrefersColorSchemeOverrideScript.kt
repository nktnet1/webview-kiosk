package uk.nktnet.webviewkiosk.utils.webview

import uk.nktnet.webviewkiosk.config.option.ThemeOption

fun getPrefersColorSchemeOverrideScript(theme: ThemeOption): String {
    if (theme == ThemeOption.SYSTEM) {
        return ""
    }

    val mediaValue = when (theme) {
        ThemeOption.DARK -> "dark"
        ThemeOption.LIGHT -> "light"
        else -> error("Unhandled theme: $theme")
    }

    return """
        (function() {
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
        })();
    """.trimIndent()
}