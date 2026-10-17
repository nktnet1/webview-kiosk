package uk.nktnet.webviewkiosk.adblock.engine

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * No Android APIs. URL/IDN/public-suffix handling reuses the app's existing OkHttp.
 */
internal fun httpAddress(value: String): HttpUrl? = value.toHttpUrlOrNull()

internal fun normalizedHost(value: String, allowPrefix: Boolean = false): String? {
    // An unbounded || anchor may contain only the start of a hostname/IP.
    // Keep its final dot: ||ads. must not become the broader ||ads pattern.
    val host =
        (if (allowPrefix) {
                value
            } else {
                value.removeSuffix(".")
            })
            .lowercase(Locale.ROOT)
    if (host.isEmpty() || host.length > 253) {
        return null
    }
    if (host.any { it == ':' }) {
        return "http://[${host.removeSurrounding("[", "]")}]/"
            .toHttpUrlOrNull()
            ?.host
    }
    if (host.all { it in 'a'..'z' || it in '0'..'9' || it in "-._" }) {
        val labels =
            (if (allowPrefix) {
                    host.removeSuffix(".")
                } else {
                    host
                })
                .split('.')
        if (
            labels.withIndex().any { (index, label) ->
                label.isEmpty() ||
                    label.length > 63 ||
                    label.startsWith('-') ||
                    (label.endsWith('-') &&
                        (!allowPrefix || index != labels.lastIndex))
            }
        ) {
            return null
        }
        // Reject invalid numeric IPv4 hosts rather than letting them become URL
        // patterns.
        if (
            !allowPrefix &&
                host.all { it in '0'..'9' || it == '.' } &&
                host.contains('.')
        ) {
            if (
                host.split('.').size != 4 ||
                    host.split('.').any { (it.toIntOrNull() ?: 256) !in 0..255 }
            ) {
                return null
            }
        }
        return host
    }
    if (
        host.any {
            it.isWhitespace() ||
                (it.code < 128 && it !in 'a'..'z' && it !in '0'..'9' && it !in "-._")
        }
    ) {
        return null
    }
    return "http://$host/"
        .toHttpUrlOrNull()
        ?.takeIf { it.username.isEmpty() && it.password.isEmpty() && it.port == 80 }
        ?.host
}

internal fun domainMatches(host: String, domain: String): Boolean =
    host == domain || (!host.contains(':') && host.endsWith(".$domain"))

/** Strip the effective public suffix, retaining all subdomain labels. */
internal fun entityName(host: String, registeredDomain: String?): String? =
    registeredDomain?.let {
        host.removeSuffix(".${it.substringAfter('.')}")
    }

internal inline fun hostSuffixes(host: String, visit: (String) -> Unit) {
    visit(host)
    if (host.contains(':')) {
        return
    }
    var dot = host.indexOf('.')
    while (dot >= 0) {
        visit(host.substring(dot + 1))
        dot = host.indexOf('.', dot + 1)
    }
}

internal fun asciiFold(character: Char): Char =
    if (character in 'A'..'Z') {
        character + ('a' - 'A')
    } else {
        character
    }

/**
 * Four-character mandatory literal token; collisions only cause extra candidate
 * checks.
 */
internal fun tokenHash(text: String, start: Int): Int {
    var hash = 0
    for (i in start until start + 4) hash = hash * 31 + asciiFold(text[i]).code
    return hash
}

/** Bound pathological URL/pattern combinations; an exhausted request fails open. */
internal class MatchBudget {
    private var work = 0
    var exhausted = false
        private set

    fun consume(amount: Int = 1): Boolean {
        work += amount
        if (work > 250_000) {
            exhausted = true
        }
        return !exhausted
    }
}

/** Shared bounded JavaScript-compatible RE2 compiler for network modifiers. */
internal fun compileFilterRegex(text: String, matchCase: Boolean): Pattern {
    require(text.length <= 2048)
    // Bound compilation before RE2/J expands counted repeats into states.
    var escaped = false
    var inClass = false
    var depth = 0
    var groups = 0
    var repeatCost = 0
    text.forEachIndexed { index, char ->
        if (escaped) {
            require(char !in "pPAzQE") { "Non-JavaScript regex escape" }
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == '[') {
            inClass = true
        } else if (char == ']') {
            inClass = false
        } else if (!inClass) {
            if (char == '(') {
                require(++depth <= 32 && ++groups <= 64)
            }
            if (char == ')') {
                require(--depth >= 0)
            }
            if (char == '{') {
                val end = text.indexOf('}', index)
                val bounds =
                    if (end > index) {
                        text.substring(index + 1, end)
                    } else {
                        ""
                    }
                if (
                    bounds.isNotEmpty() && bounds.all { it.isDigit() || it == ',' }
                ) {
                    require(text.getOrNull(index - 1) !in listOf(')', '}'))
                    val counts =
                        bounds
                            .split(',')
                            .filter { it.isNotEmpty() }
                            .map { it.toIntOrNull() ?: Int.MAX_VALUE }
                    require(counts.all { it <= 256 })
                    repeatCost += counts.maxOrNull() ?: 0
                    require(repeatCost <= 8192)
                }
            }
        }
    }
    val pattern =
        try {
            Pattern.compile(
                text,
                if (matchCase) {
                    0
                } else {
                    Pattern.CASE_INSENSITIVE
                },
            )
        } catch (error: PatternSyntaxException) {
            throw IllegalArgumentException(
                "Unsupported regular expression",
                error,
            )
        }
    return pattern.also {
        require(it.programSize() <= 2048) {
            "Regex program exceeds the size limit"
        }
    }
}

internal data class UrlPattern(
    val text: String,
    val domainAnchor: Boolean,
    val startAnchor: Boolean,
    val endAnchor: Boolean,
    val matchCase: Boolean,
    val hostIndex: String?,
    val regex: Boolean = false,
) {
    private val compiledRegex =
        if (regex) {
            require(!domainAnchor && !startAnchor && !endAnchor && hostIndex == null)
            compileFilterRegex(text, matchCase)
        } else {
            null
        }

    val plainHost: String?
        get() = hostIndex?.takeIf { domainAnchor && text == "$it^" && !endAnchor }

    // Host-anchored filters use the hostname index. Other patterns get a literal
    // substring index, not a word index: "advert" must also match "myadvert.js".
    val indexToken: Int? =
        if (hostIndex != null || regex) {
            null
        } else {
            run {
                var bestStart = -1
                var bestLength = 0
                var runStart = 0
                for (i in 0..text.length) {
                    if (
                        i == text.length ||
                            !(text[i].isLetterOrDigit() ||
                                text[i] == '_' ||
                                text[i] == '-')
                    ) {
                        if (i - runStart >= 4 && i - runStart >= bestLength) {
                            bestStart = runStart
                            bestLength = i - runStart
                        }
                        runStart = i + 1
                    }
                }
                if (bestStart >= 0) {
                    tokenHash(text, bestStart)
                } else {
                    null
                }
            }
        }

    fun matches(
        address: HttpUrl,
        canonicalUrl: String,
        budget: MatchBudget,
        hostnameOnly: Boolean = false,
    ): Boolean {
        // Like uBO strict blocking, a typeless navigation block must end at the
        // hostname (optionally consuming its separator). A path match such as
        // /ads/ is useful for subresources, but must not deny a whole page.
        if (
            hostnameOnly &&
                text.none {
                    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_'
                }
        ) {
            return false
        }
        plainHost?.let {
            return domainMatches(address.host.removeSuffix("."), it)
        }
        // OkHttp escapes literal path pipes. Match their equivalent spelling only
        // for glob filters which explicitly contain that literal character.
        val url =
            if (!regex && '|' in text && canonicalUrl.contains("%7c", true)) {
                if (!budget.consume(canonicalUrl.length)) {
                    return false
                }
                canonicalUrl.replace("%7c", "|", ignoreCase = true)
            } else {
                canonicalUrl
            }
        val hostStart =
            if (domainAnchor || hostnameOnly) {
                hostnameStart(url)
            } else {
                0
            }
        fun accepts(end: Int): Boolean {
            if (end < 0) {
                return false
            }
            if (!hostnameOnly) {
                return true
            }
            val hostEnd =
                hostStart +
                    address.host.length +
                    if (address.host.contains(':')) {
                        2
                    } else {
                        0
                    }
            val minimumEnd =
                if (address.host.endsWith('.')) {
                    hostEnd - 1
                } else {
                    hostEnd
                }
            return end in minimumEnd..hostEnd + 1
        }

        compiledRegex?.let { pattern ->
            // RE2/J has no backtracking. Also cap aggregate automaton work per
            // request.
            if (
                !budget.consume(
                    (url.length + 1) * maxOf(1, pattern.programSize() / 4)
                )
            ) {
                return false
            }
            val matcher = pattern.matcher(url)
            return matcher.find() && accepts(matcher.end())
        }
        if (!domainAnchor) {
            return accepts(glob(url, 0, !startAnchor, budget))
        }
        if (
            hostIndex != null &&
                !domainMatches(address.host.removeSuffix("."), hostIndex)
        ) {
            return false
        }
        if (accepts(glob(url, hostStart, false, budget))) {
            return true
        }
        if (address.host.contains(':')) {
            return false
        }
        val hostEnd = hostStart + address.host.length
        var dot = url.indexOf('.', hostStart)
        while (dot in hostStart until hostEnd) {
            if (accepts(glob(url, dot + 1, false, budget))) {
                return true
            }
            dot = url.indexOf('.', dot + 1)
        }
        return false
    }

    private fun hostnameStart(url: String): Int {
        val authorityStart = url.indexOf("://") + 3
        val authorityEnd =
            url.indexOf('/', authorityStart).let {
                if (it < 0) {
                    url.length
                } else {
                    it
                }
            }
        // Credentials may contain the same text as the target hostname.
        val at = url.lastIndexOf('@', authorityEnd - 1)
        return if (at >= authorityStart) {
            at + 1
        } else {
            authorityStart
        }
    }

    /**
     * Greedy wildcard matching with one checkpoint: no regex backtracking explosion.
     */
    private fun glob(
        url: String,
        start: Int,
        search: Boolean,
        budget: MatchBudget,
    ): Int {
        var p = 0
        var u = start
        var star =
            if (search) {
                -2
            } else {
                -1
            }
        var retry = start
        while (u < url.length) {
            if (!budget.consume()) {
                return -1
            }
            if (p == text.length && !endAnchor) {
                return u
            }
            if (p < text.length && text[p] == '*') {
                star = p++
                retry = u
            } else if (
                p < text.length &&
                    (if (text[p] == '^') {
                        separator(url[u])
                    } else {
                        text[p].equals(url[u], !matchCase)
                    })
            ) {
                p++
                u++
            } else if (star != -1) {
                p =
                    if (star == -2) {
                        0
                    } else {
                        star + 1
                    }
                u = ++retry
            } else {
                return -1
            }
        }
        while (p < text.length && (text[p] == '*' || text[p] == '^')) p++
        return if (p == text.length) {
            u
        } else {
            -1
        }
    }

    companion object {
        private fun separator(c: Char): Boolean =
            c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c !in "_-.%"
    }
}
