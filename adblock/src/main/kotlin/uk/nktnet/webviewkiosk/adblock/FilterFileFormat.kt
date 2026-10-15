package uk.nktnet.webviewkiosk.adblock

/** Check text integrity for every candidate before the Kotlin engine validates its rules. */
fun validateFilterFileFormat(text: String) {
    var lineNumber = 1
    for (character in text) {
        require(
            !character.isISOControl() ||
                character == '\t' || character == '\r' || character == '\n',
        ) {
            val code = character.code.toString(16)
                .uppercase().padStart(4, '0')
            "Line $lineNumber: Filter list must be plain text " +
                "(unexpected control character U+$code)."
        }
        if (character == '\n') lineNumber++
    }
}
