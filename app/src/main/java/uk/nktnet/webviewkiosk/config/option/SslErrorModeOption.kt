package uk.nktnet.webviewkiosk.config.option

enum class SslErrorModeOption {
    BLOCK,
    PROMPT,
    PROCEED;

    companion object {
        fun fromString(value: String?): SslErrorModeOption {
            return entries.find { it.name == value } ?: BLOCK
        }
    }
}
