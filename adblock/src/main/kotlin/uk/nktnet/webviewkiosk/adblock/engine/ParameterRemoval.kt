package uk.nktnet.webviewkiosk.adblock.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Modifier identity is retained for exceptions, badfilter and cache restoration. */
internal data class ParameterRemoval(val text: String) {
    private val negated = text.startsWith('~')
    private val value = text.removePrefix("~")
    private val expression =
        when {
            value.startsWith('/') -> {
                val end = value.lastIndexOf('/')
                require(end > 1 && value.substring(end + 1) in setOf("", "i"))
                compileFilterRegex(value.substring(1, end), !value.endsWith("/i"))
            }
            value.startsWith('|') -> compileFilterRegex("^" + value.drop(1), false)
            else -> {
                require('|' !in value)
                null
            }
        }

    init {
        require(text.length <= 2048)
    }

    // Positive literal names also provide a mandatory token for global rules.
    val indexToken =
        if (
            !negated &&
                expression == null &&
                value.isNotEmpty() &&
                value.all { it.code < 128 }
        ) {
            UrlPattern(value, false, false, false, true, null).indexToken
        } else {
            null
        }

    fun matches(name: String, rawValue: String, budget: MatchBudget): Boolean {
        if (text.isEmpty()) {
            return true
        }
        val matched =
            if (expression != null) {
                val input = name + "=" + decodeValue(rawValue)
                if (
                    !budget.consume(
                        (input.length + 1) * maxOf(1, expression.programSize() / 4)
                    )
                ) {
                    return false
                }
                expression.matcher(input).find()
            } else {
                budget.consume(name.length + 1) && name == value
            }
        return matched != negated
    }

    companion object {
        // Match uBO's decodeURIComponent: '+' stays literal and malformed
        // percent escapes or UTF-8 leave the entire value undecoded.
        private fun decodeValue(value: String): String {
            if ('%' !in value) {
                return value
            }
            return try {
                val bytes = ByteArrayOutputStream(value.length)
                var index = 0
                while (index < value.length) {
                    if (value[index] == '%') {
                        require(index + 2 < value.length)
                        val high = value[index + 1].digitToIntOrNull(16)
                        val low = value[index + 2].digitToIntOrNull(16)
                        require(high != null && low != null)
                        bytes.write(high * 16 + low)
                        index += 3
                    } else {
                        val end =
                            value.indexOf('%', index).let {
                                if (it < 0) {
                                    value.length
                                } else {
                                    it
                                }
                            }
                        bytes.write(value.substring(index, end).toByteArray())
                        index = end
                    }
                }
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString()
            } catch (_: Exception) {
                value
            }
        }
    }
}
