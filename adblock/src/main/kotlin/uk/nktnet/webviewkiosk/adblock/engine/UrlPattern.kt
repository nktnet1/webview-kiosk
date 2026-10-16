package uk.nktnet.webviewkiosk.adblock.engine

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
            labels.any {
                it.isEmpty() ||
                    it.length > 63 ||
                    it.startsWith('-') ||
                    it.endsWith('-')
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

internal data class UrlPattern(
    val text: String,
    val domainAnchor: Boolean,
    val startAnchor: Boolean,
    val endAnchor: Boolean,
    val matchCase: Boolean,
    val hostIndex: String?,
) {
    val plainHost: String?
        get() = hostIndex?.takeIf { domainAnchor && text == "$it^" && !endAnchor }

    // Host-anchored filters use the hostname index. Other patterns get a literal
    // substring index, not a word index: "advert" must also match "myadvert.js".
    val indexToken: Int? =
        if (hostIndex != null) {
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

    fun matches(address: HttpUrl, url: String, budget: MatchBudget): Boolean {
        if (!domainAnchor) {
            return glob(url, 0, !startAnchor, budget)
        }
        if (hostIndex != null && !domainMatches(address.host, hostIndex)) {
            return false
        }
        val authorityStart = url.indexOf("://") + 3
        val authorityEnd =
            url.indexOf('/', authorityStart).let {
                if (it < 0) {
                    url.length
                } else {
                    it
                }
            }
        val hostStart =
            url.lastIndexOf('@', authorityEnd - 1).let {
                if (it >= authorityStart) {
                    it + 1
                } else {
                    authorityStart
                }
            }
        if (glob(url, hostStart, false, budget)) {
            return true
        }
        if (address.host.contains(':')) {
            return false
        }
        val hostEnd = hostStart + address.host.length
        var dot = url.indexOf('.', hostStart)
        while (dot in hostStart until hostEnd) {
            if (glob(url, dot + 1, false, budget)) {
                return true
            }
            dot = url.indexOf('.', dot + 1)
        }
        return false
    }

    /**
     * Greedy wildcard matching with one checkpoint: no regex backtracking explosion.
     */
    private fun glob(
        url: String,
        start: Int,
        search: Boolean,
        budget: MatchBudget,
    ): Boolean {
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
                return false
            }
            if (p == text.length && !endAnchor) {
                return true
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
                return false
            }
        }
        while (p < text.length && (text[p] == '*' || text[p] == '^')) p++
        return p == text.length
    }

    companion object {
        private fun separator(c: Char): Boolean =
            c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c !in "_-.%"
    }
}
