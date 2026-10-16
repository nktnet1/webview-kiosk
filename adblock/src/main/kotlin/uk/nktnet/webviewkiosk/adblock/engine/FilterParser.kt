package uk.nktnet.webviewkiosk.adblock.engine

import java.util.Locale

internal data class DomainScope(
    val included: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
) {
    fun matches(host: String): Boolean =
        excluded.none { domainMatches(host, it) } &&
            (included.isEmpty() || included.any { domainMatches(host, it) })

    companion object {
        val ANY = DomainScope()
    }
}

internal object ResourceTypes {
    private val names =
        listOf(
            "other",
            "script",
            "image",
            "stylesheet",
            "font",
            "media",
            "subdocument",
            "xmlhttprequest",
            "object",
            "ping",
            "websocket",
            "document",
        )

    fun known(name: String): Int? =
        names
            .indexOf(
                when (name) {
                    "css" -> "stylesheet"
                    "frame" -> "subdocument"
                    "xhr" -> "xmlhttprequest"
                    else -> name
                }
            )
            .takeIf { it >= 0 }
            ?.let { 1 shl it }

    fun request(name: String): Int = known(name) ?: 1
}

internal data class Conditions(
    val domains: DomainScope = DomainScope.ANY,
    val types: Int = 0,
    val excludedTypes: Int = 0,
    val thirdParty: Boolean? = null,
    val methods: Set<String> = emptySet(),
    val excludedMethods: Set<String> = emptySet(),
) {
    companion object {
        val ANY = Conditions()
    }
}

internal data class NetworkIdentity(
    val pattern: UrlPattern,
    val conditions: Conditions,
    val exception: Boolean,
    val important: Boolean,
    /** 0: request filter; 1: generic hiding exception; 2: all hiding exception. */
    val hide: Int,
)

internal data class NetworkRule(
    val identity: NetworkIdentity,
    val badfilter: Boolean = false,
)

internal data class CosmeticRule(
    val selector: String,
    val domains: DomainScope,
    val exception: Boolean,
)

internal sealed interface ParsedFilter {
    data class Network(val rule: NetworkRule) : ParsedFilter

    data class Cosmetic(val rule: CosmeticRule) : ParsedFilter

    data class Invalid(val message: String) : ParsedFilter

    data object Unsupported : ParsedFilter
}

/**
 * A deliberately bounded ABP subset. Unsupported modifiers always discard the whole
 * rule.
 */
internal object FilterParser {
    private val whitespace = Regex("\\s+")

    private class Invalid(message: String) : RuntimeException(message)

    private class Unsupported : RuntimeException() {
        // The four-argument constructor is unavailable before Android 7.
        override fun fillInStackTrace(): Throwable = this
    }

    private val procedural =
        listOf(
            ":has-text(",
            ":matches-attr(",
            ":matches-css(",
            ":matches-css-before(",
            ":matches-css-after(",
            ":matches-path(",
            ":min-text-length(",
            ":upward(",
            ":xpath(",
            ":contains(",
            ":-abp-contains(",
            ":-abp-has(",
            ":-abp-properties(",
            ":if(",
            ":if-not(",
            ":watch-attr(",
            ":style(",
            ":remove(",
            ":remove-attr(",
            ":remove-class(",
        )

    fun visit(text: String, visitor: (line: Int, ParsedFilter) -> Unit) {
        var first = true
        var hosts = false
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, original
            ->
            val line = original.trim()
            if (
                line.isEmpty() ||
                    line.startsWith('!') ||
                    line.startsWith("[Adblock") ||
                    (line.startsWith('#') &&
                        !line.substring(1, minOf(line.length, 5)).contains('#'))
            ) {
                return@forEachIndexed
            }
            val fields = line.splitToSequence(whitespace).iterator()
            val firstField = fields.next()
            val hasFields = fields.hasNext()
            val ip = isIp(firstField)
            if (first) {
                hosts = hasFields && ip
                first = false
            }
            // CSS attribute values and procedural selectors can contain several
            // colons before their first space. Only an address-shaped token is
            // a malformed hosts candidate; never route those selectors here.
            val resemblesIp =
                (firstField.count { it == ':' } >= 2 &&
                    firstField.all { it.isLetterOrDigit() || it in ":.[]" }) ||
                    (firstField.contains('.') &&
                        firstField.all { it.isDigit() || it == '.' })
            if (hasFields && (ip || resemblesIp)) {
                if (!ip) {
                    visitor(
                        index + 1,
                        ParsedFilter.Invalid("invalid hosts IP address"),
                    )
                } else {
                    line
                        .substringBefore('#')
                        .splitToSequence(whitespace)
                        .drop(1)
                        .filter { it.isNotEmpty() }
                        .forEach { host ->
                            visitor(index + 1, parseHost(host))
                        }
                }
            } else if (
                hosts &&
                    !line.contains('$') &&
                    normalizedHost(line)?.contains('.') == true
            ) {
                visitor(index + 1, parseHost(line))
            } else {
                visitor(index + 1, parse(line))
            }
        }
    }

    private fun isIp(value: String): Boolean =
        if (value.contains(':')) {
            normalizedHost(value)?.contains(':') == true
        } else {
            value.split('.').let { parts ->
                parts.size == 4 &&
                    parts.all {
                        it.isNotEmpty() &&
                            it.all(Char::isDigit) &&
                            (it.toIntOrNull() ?: 256) in 0..255
                    }
            }
        }

    private fun parseHost(host: String): ParsedFilter {
        val normalized =
            normalizedHost(host)
                ?: return ParsedFilter.Invalid("invalid hosts hostname")
        if (!normalized.contains('.') && !normalized.contains(':')) {
            return ParsedFilter.Unsupported
        }
        val literal =
            if (normalized.contains(':')) {
                "[$normalized]"
            } else {
                normalized
            }
        return ParsedFilter.Network(
            NetworkRule(
                NetworkIdentity(
                    UrlPattern(
                        "$literal^",
                        true,
                        false,
                        false,
                        false,
                        normalized,
                    ),
                    Conditions.ANY,
                    false,
                    false,
                    0,
                )
            )
        )
    }

    private fun parse(line: String): ParsedFilter =
        try {
            if (line.length > 8192) {
                throw Unsupported()
            }
            val cosmetic = line.indexOf("##")
            val exception = line.indexOf("#@#")
            when {
                exception >= 0 && (cosmetic < 0 || exception < cosmetic) ->
                    parseCosmetic(line, exception, true)

                cosmetic >= 0 -> parseCosmetic(line, cosmetic, false)

                listOf("#?#", "#@?#", "#$#", "#@$#", "#%#", "#@%#")
                    .any(line::contains) -> throw Unsupported()

                else -> parseNetwork(line)
            }
        } catch (_: Unsupported) {
            ParsedFilter.Unsupported
        } catch (error: Invalid) {
            ParsedFilter.Invalid(error.message.orEmpty())
        }

    private fun parseCosmetic(
        line: String,
        separator: Int,
        exception: Boolean,
    ): ParsedFilter {
        val selector =
            line
                .substring(
                    separator +
                        if (exception) {
                            3
                        } else {
                            2
                        }
                )
                .trim()
        if (selector.isEmpty()) {
            throw Invalid("missing CSS selector")
        }
        if (
            selector.length > 4096 ||
                selector.startsWith("+js(") ||
                selector.startsWith("^") ||
                procedural.any(selector::contains)
        ) {
            throw Unsupported()
        }
        val scope = domains(line.substring(0, separator), ',')
        return ParsedFilter.Cosmetic(CosmeticRule(selector, scope, exception))
    }

    private fun domains(value: String, separator: Char): DomainScope {
        if (value.isEmpty()) {
            return DomainScope.ANY
        }
        val included = mutableSetOf<String>()
        val excluded = mutableSetOf<String>()
        for (entry in value.split(separator)) {
            if (entry.isEmpty()) {
                throw Invalid("empty domain restriction")
            }
            val raw = entry.removePrefix("~")
            // Entity and regex scopes need distinct semantics; do not approximate
            // them.
            if (raw.contains('*') || raw.startsWith('/')) {
                throw Unsupported()
            }
            val domain =
                normalizedHost(raw) ?: throw Invalid("invalid domain restriction")
            if (entry.startsWith('~')) {
                excluded += domain
            } else {
                included += domain
            }
        }
        return DomainScope(included, excluded)
    }

    private fun parseNetwork(line: String): ParsedFilter {
        val exception = line.startsWith("@@")
        val body =
            if (exception) {
                line.substring(2)
            } else {
                line
            }
        val closingSlash = body.lastIndexOf('/')
        if (
            body.startsWith('/') &&
                closingSlash > 0 &&
                (closingSlash == body.lastIndex ||
                    body.getOrNull(closingSlash + 1) == '$')
        ) {
            throw Unsupported() // Full regex rules are intentionally outside this
            // subset.
        }
        val dollar = body.lastIndexOf('$')
        // Options-only filters (including @@$generichide,domain=...) match
        // every URL, while their options still restrict when they apply.
        val rawPattern =
            if (dollar == 0) {
                "*"
            } else if (dollar > 0) {
                body.substring(0, dollar)
            } else {
                body
            }
        val options =
            if (dollar >= 0) {
                body.substring(dollar + 1).split(',')
            } else {
                emptyList()
            }
        var scope = DomainScope.ANY
        var types = 0
        var excludedTypes = 0
        var thirdParty: Boolean? = null
        var methods = emptySet<String>()
        var excludedMethods = emptySet<String>()
        var matchCase = false
        var important = false
        var badfilter = false
        var hide = 0
        // Check unknown options before interpreting any remaining restriction.
        for (option in options) {
            val name =
                option.substringBefore('=').removePrefix("~").lowercase(Locale.ROOT)
            if (
                ResourceTypes.known(name) == null &&
                    name !in
                        setOf(
                            "domain",
                            "from",
                            "third-party",
                            "3p",
                            "first-party",
                            "1p",
                            "method",
                            "match-case",
                            "important",
                            "badfilter",
                            "generichide",
                            "ghide",
                            "elemhide",
                            "ehide",
                        )
            ) {
                throw Unsupported()
            }
        }
        for (option in options) {
            val negated = option.startsWith('~')
            val name =
                option.substringBefore('=').removePrefix("~").lowercase(Locale.ROOT)
            val value = option.substringAfter('=', "")
            val type = ResourceTypes.known(name)
            if (type != null) {
                if (option.contains('=')) {
                    throw Unsupported()
                }
                if (negated) {
                    excludedTypes = excludedTypes or type
                } else {
                    types = types or type
                }
                continue
            }
            when (name) {
                "domain",
                "from" -> {
                    if (negated || value.isEmpty()) {
                        throw Invalid("missing or negated domain restriction")
                    }
                    if (scope != DomainScope.ANY) {
                        throw Unsupported()
                    }
                    scope = domains(value, '|')
                }

                "method" -> {
                    if (
                        negated ||
                            value.isEmpty() ||
                            methods.isNotEmpty() ||
                            excludedMethods.isNotEmpty()
                    ) {
                        throw Unsupported()
                    }
                    val positive = mutableSetOf<String>()
                    val negative = mutableSetOf<String>()
                    for (method in value.split('|')) {
                        val token = method.removePrefix("~").uppercase(Locale.ROOT)
                        if (
                            token.isEmpty() ||
                                token.any { it !in 'A'..'Z' && it != '-' }
                        ) {
                            throw Unsupported()
                        }
                        if (method.startsWith('~')) {
                            negative += token
                        } else {
                            positive += token
                        }
                    }
                    methods = positive
                    excludedMethods = negative
                }

                "third-party",
                "3p",
                "first-party",
                "1p" -> {
                    if (option.contains('=')) {
                        throw Unsupported()
                    }
                    val restriction =
                        (name == "third-party" || name == "3p") != negated
                    if (thirdParty != null && thirdParty != restriction) {
                        throw Unsupported()
                    }
                    thirdParty = restriction
                }

                else -> {
                    if (negated) {
                        throw Invalid("invalid negation of $name")
                    }
                    if (option.contains('=')) {
                        throw Unsupported()
                    }
                    when (name) {
                        "match-case" -> matchCase = true

                        "important" -> {
                            if (exception) {
                                throw Unsupported()
                            }
                            important = true
                        }

                        "badfilter" -> badfilter = true

                        "generichide",
                        "ghide" -> {
                            if (!exception) {
                                throw Unsupported()
                            }
                            hide = maxOf(hide, 1)
                        }

                        "elemhide",
                        "ehide" -> {
                            if (!exception) {
                                throw Unsupported()
                            }
                            hide = 2
                        }
                    }
                }
            }
        }
        val conditions =
            Conditions(
                scope,
                types,
                excludedTypes,
                thirdParty,
                methods,
                excludedMethods,
            )
        return ParsedFilter.Network(
            NetworkRule(
                NetworkIdentity(
                    pattern(rawPattern, matchCase),
                    conditions,
                    exception,
                    important,
                    hide,
                ),
                badfilter,
            )
        )
    }

    private fun pattern(raw: String, matchCase: Boolean): UrlPattern {
        if (raw.isEmpty()) {
            throw Invalid("missing URL pattern")
        }
        if (raw.length > 4096) {
            throw Unsupported()
        }
        val domainAnchor = raw.startsWith("||")
        val startAnchor = !domainAnchor && raw.startsWith('|')
        val endAnchor = raw.endsWith('|')
        val start =
            if (domainAnchor) {
                2
            } else if (startAnchor) {
                1
            } else {
                0
            }
        val endPattern =
            raw.length -
                if (endAnchor) {
                    1
                } else {
                    0
                }
        if (endPattern <= start) {
            throw Invalid("missing URL pattern")
        }
        var text = raw.substring(start, endPattern)
        if (text.isEmpty()) {
            throw Invalid("missing URL pattern")
        }
        if (text.contains('|') || text.contains('\\')) {
            throw Unsupported()
        }
        var hostIndex: String? = null
        if (domainAnchor) {
            val end =
                if (text.startsWith('[')) {
                    text.indexOf(']').takeIf { it > 0 }?.plus(1)
                        ?: throw Invalid("invalid host anchor")
                } else {
                    text
                        .indexOfFirst { it in "/^*:?" }
                        .let {
                            if (it < 0) {
                                text.length
                            } else {
                                it
                            }
                        }
                }
            val host = text.substring(0, end)
            if (host.isEmpty()) {
                if (text.startsWith('*')) {
                    throw Unsupported()
                }
                throw Invalid("missing host anchor")
            }
            if (!text.startsWith('[') && text.getOrNull(end) == '*') {
                throw Unsupported()
            }
            val normalized =
                normalizedHost(host, allowPrefix = end == text.length && !endAnchor)
                    ?: throw Invalid("invalid host anchor")
            val literal =
                if (normalized.contains(':')) {
                    "[$normalized]"
                } else {
                    normalized
                }
            text = literal + text.substring(end)
            // Without an explicit boundary the pattern can match a hostname prefix.
            if (
                text.getOrNull(literal.length) in listOf('^', '/', ':') || endAnchor
            ) {
                hostIndex = normalized
            }
        }
        if (!matchCase) {
            text = text.lowercase(Locale.ROOT)
        }
        return UrlPattern(
            text,
            domainAnchor,
            startAnchor,
            endAnchor,
            matchCase,
            hostIndex,
        )
    }
}
