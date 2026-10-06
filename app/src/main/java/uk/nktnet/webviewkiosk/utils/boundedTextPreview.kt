package uk.nktnet.webviewkiosk.utils

/** Bound text before shaping it; limiting visible lines alone still leaves a large input to layout. */
fun boundedTextPreview(text: String, maxChars: Int = 512): String {
    require(maxChars > 0) { "Preview limit must be positive" }
    if (text.length <= maxChars) {
        return text
    }

    var end = maxChars - 1
    if (end > 0 && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
        end--
    }
    return text.substring(0, end) + "\u2026"
}
