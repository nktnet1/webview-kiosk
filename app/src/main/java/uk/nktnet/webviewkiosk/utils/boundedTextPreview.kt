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

/**
 * Small, wrap-friendly dialog labels. Insert breaks between Unicode code points, not
 * UTF-16 chars: splitting a surrogate pair can break emoji and native text shaping.
 * The underlying URL, bookmark or file name is never modified.
 */
fun boundedBreakableTextPreview(text: String, maxChars: Int = 256): String {
    val preview = boundedTextPreview(text, maxChars)
    return buildString(preview.length * 2) {
        var offset = 0
        while (offset < preview.length) {
            val point = Character.codePointAt(preview, offset)
            appendCodePoint(point)
            offset += Character.charCount(point)
            if (offset < preview.length) append('\u200B')
        }
    }
}
