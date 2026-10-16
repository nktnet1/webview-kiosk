package uk.nktnet.webviewkiosk.adblock.engine

import java.util.Locale
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure

internal data class DomainScope(
    val included: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    val includedEntities: Set<String> = emptySet(),
    val excludedEntities: Set<String> = emptySet(),
) {
    val usesEntities: Boolean
        get() = includedEntities.isNotEmpty() || excludedEntities.isNotEmpty()

    fun matches(host: String, registeredDomain: String? = null): Boolean {
        fun entityMatches(name: String): Boolean =
            registeredDomain?.startsWith("$name.") == true

        return excluded.none { domainMatches(host, it) } &&
            excludedEntities.none(::entityMatches) &&
            (included.isEmpty() && includedEntities.isEmpty() ||
                included.any { domainMatches(host, it) } ||
                includedEntities.any(::entityMatches))
    }

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
    val targets: DomainScope = DomainScope.ANY,
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
    val redirect: BlockingResponse? = null,
    val redirectOnly: Boolean = false,
)

internal data class NetworkRule(
    val identity: NetworkIdentity,
    val badfilter: Boolean = false,
)

internal data class CosmeticRule(
    val selector: String,
    val domains: DomainScope,
    val exception: Boolean,
    val procedure: CosmeticProcedure? = null,
)

internal sealed interface ParsedFilter {
    data class Network(val rule: NetworkRule) : ParsedFilter

    data class Cosmetic(val rule: CosmeticRule) : ParsedFilter

    data class Scriptlet(val rule: ScriptletRule) : ParsedFilter

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

    /**
     * Expand only includes in active branches; the downloader controls URL and size
     * limits.
     */
    fun expandIncludes(text: String, loader: (String) -> String): String {
        if ("!#include " !in text) {
            return text
        }
        val branches = ArrayDeque<Branch>()
        val result = StringBuilder(text.length)
        text.lineSequence().forEach { original ->
            val line = original.trim()
            when {
                line.startsWith("!#if ") -> {
                    branches.addLast(
                        Branch(
                            branches.lastOrNull()?.enabled != false,
                            condition(line.removePrefix("!#if ")),
                        )
                    )
                }

                line == "!#else" -> {
                    val branch =
                        branches.lastOrNull()
                            ?: throw IllegalArgumentException("Unexpected !#else")
                    require(!branch.elseSeen) { "Duplicate !#else" }
                    branch.elseSeen = true
                }

                line == "!#endif" -> {
                    require(branches.isNotEmpty()) { "Unexpected !#endif" }
                    branches.removeLast()
                }
            }
            if (
                line.startsWith("!#include ") &&
                    branches.lastOrNull()?.enabled != false
            ) {
                val path = line.removePrefix("!#include ").trim()
                val content = loader(path)
                result.append("! Begin included list: ").append(path).append('\n')
                result.append(content).append('\n')
                result.append("! End included list: ").append(path)
            } else {
                result.append(original)
            }
            result.append('\n')
        }
        require(branches.isEmpty()) { "Unclosed !#if" }
        return result.toString()
    }

    /**
     * Pre-process conditional branches before parsing, never as ordinary comments.
     */
    fun visit(text: String, visitor: (line: Int, ParsedFilter) -> Unit) {
        var first = true
        var hosts = false
        val branches = ArrayDeque<Branch>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, original
            ->
            val line = original.trim()
            when {
                line.startsWith("!#if ") -> {
                    val active = condition(line.removePrefix("!#if "))
                    branches.addLast(
                        Branch(branches.lastOrNull()?.enabled != false, active)
                    )
                    return@forEachIndexed
                }

                line == "!#else" -> {
                    val branch = branches.lastOrNull()
                    if (branch == null || branch.elseSeen) {
                        visitor(index + 1, ParsedFilter.Invalid("unexpected !#else"))
                    } else {
                        branch.elseSeen = true
                    }
                    return@forEachIndexed
                }

                line == "!#endif" -> {
                    if (branches.isEmpty()) {
                        visitor(
                            index + 1,
                            ParsedFilter.Invalid("unexpected !#endif"),
                        )
                    } else {
                        branches.removeLast()
                    }
                    return@forEachIndexed
                }
            }
            if (
                branches.lastOrNull()?.enabled == false ||
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
        if (branches.isNotEmpty()) {
            visitor(
                text.lineSequence().count(),
                ParsedFilter.Invalid("unclosed !#if"),
            )
        }
    }

    private class Branch(val parentEnabled: Boolean, val match: Boolean?) {
        var elseSeen = false
        val enabled: Boolean
            get() =
                parentEnabled &&
                    (if (elseSeen) {
                        match == false
                    } else {
                        match == true
                    })
    }

    /** Unknown tokens are indeterminate, including under negation: fail closed. */
    private fun condition(expression: String): Boolean? {
        // Both input and recursion are bounded for untrusted subscription files.
        if (expression.length > 512) {
            return null
        }
        val tokens =
            Regex("\\(|\\)|&&|\\|\\||!|[a-z_0-9]+")
                .findAll(expression)
                .map { it.value }
                .toList()
        if (
            tokens.size > 128 ||
                tokens.joinToString("") != expression.filterNot(Char::isWhitespace)
        ) {
            return null
        }
        var position = 0
        fun primary(): Boolean? {
            val token = tokens.getOrNull(position++) ?: return null
            return when (token) {
                "true",
                "env_mobile",
                "env_chromium" -> true
                "false",
                "cap_html_filtering",
                "cap_user_stylesheet",
                "ext_ublock",
                "ext_ubol",
                "ext_abp",
                "ext_devbuild",
                "env_firefox",
                "env_safari",
                "env_edge",
                "env_mv3",
                "adguard",
                "adguard_app_android",
                "adguard_ext_chromium" -> false
                "(" -> null // Parentheses are parsed by the recursive reader below.
                else -> null
            }
        }
        // The real expression parser is below; it keeps unknown values distinct
        // from false, so !ext_ublock never silently enables extension-only rules.
        fun combineAnd(left: Boolean?, right: Boolean?): Boolean? =
            if (left == false || right == false) {
                false
            } else if (left == true && right == true) {
                true
            } else {
                null
            }
        fun combineOr(left: Boolean?, right: Boolean?): Boolean? =
            if (left == true || right == true) {
                true
            } else if (left == false && right == false) {
                false
            } else {
                null
            }
        // Local recursion is implemented by a class to keep Kotlin/JVM 11
        // compatible.
        class Reader {
            fun atom(): Boolean? {
                val token = tokens.getOrNull(position) ?: return null
                position++
                return when (token) {
                    "!" -> atom()?.not()
                    "(" ->
                        expression().also {
                            if (tokens.getOrNull(position++) != ")") {
                                position = tokens.size + 1
                            }
                        }
                    else -> {
                        position--
                        primary()
                    }
                }
            }

            fun and(): Boolean? {
                var value = atom()
                while (tokens.getOrNull(position) == "&&") {
                    position++
                    value = combineAnd(value, atom())
                }
                return value
            }

            fun expression(): Boolean? {
                var value = and()
                while (tokens.getOrNull(position) == "||") {
                    position++
                    value = combineOr(value, and())
                }
                return value
            }
        }
        val result = Reader().expression()
        return result.takeIf { position == tokens.size }
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
                        text = "$literal^",
                        domainAnchor = true,
                        startAnchor = false,
                        endAnchor = false,
                        matchCase = false,
                        hostIndex = normalized,
                    ),
                    conditions = Conditions.ANY,
                    exception = false,
                    important = false,
                    hide = 0,
                )
            )
        )
    }

    private fun parse(line: String): ParsedFilter =
        try {
            if (line.length > 8192) {
                throw Unsupported()
            }
            val proceduralException = line.indexOf("#@?#")
            val proceduralCosmetic = line.indexOf("#?#")
            val exception = line.indexOf("#@#")
            val cosmetic = line.indexOf("##")
            when {
                proceduralException >= 0 ->
                    parseCosmetic(line, proceduralException, true, 4)

                proceduralCosmetic >= 0 ->
                    parseCosmetic(line, proceduralCosmetic, false, 3)

                exception >= 0 && cosmetic !in 0..exception ->
                    parseCosmetic(line, exception, true, 3)

                cosmetic >= 0 -> parseCosmetic(line, cosmetic, false, 2)

                listOf("#$#", "#@$#", "#%#", "#@%#").any(line::contains) ->
                    throw Unsupported()

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
        delimiterLength: Int,
    ): ParsedFilter {
        val selector = line.substring(separator + delimiterLength).trim()
        if (selector.isEmpty()) {
            throw Invalid("missing CSS selector")
        }
        if (selector.startsWith("+js(")) {
            if (
                (!exception && delimiterLength != 2) ||
                    (exception && delimiterLength != 3)
            ) {
                throw Unsupported()
            }
            val scope = domains(line.substring(0, separator), ',')
            // Entity/regex domains need a public-suffix-aware synchronous frame
            // dispatcher. Do not approximate them with hostname substrings.
            if (
                scope.usesEntities ||
                    !exception && scope.included.isEmpty() ||
                    scope.included.any { ':' in it }
            ) {
                throw Unsupported()
            }
            val instruction =
                if (exception && selector == "+js()") {
                    null
                } else {
                    ScriptletParser.parse(selector) ?: throw Unsupported()
                }
            return ParsedFilter.Scriptlet(
                ScriptletRule(instruction, scope, exception)
            )
        }
        if (selector.length > 4096 || selector.startsWith("^")) {
            throw Unsupported()
        }
        val scope = domains(line.substring(0, separator), ',')
        val action =
            if (procedural.any(selector::contains)) {
                parseProcedure(selector, scope)
            } else {
                null
            }
        return ParsedFilter.Cosmetic(
            CosmeticRule(selector, scope, exception, action)
        )
    }

    /** Do not turn uBO procedural syntax into invalid CSS or executable JS. */
    private fun parseProcedure(
        expression: String,
        scope: DomainScope,
    ): CosmeticProcedure {
        // A global DOM scan is too costly; only target specific host families.
        if (scope.included.isEmpty() && scope.includedEntities.isEmpty()) {
            throw Unsupported()
        }
        var selector = expression
        var style: String? = null
        val styleStart = selector.lastIndexOf(":style(")
        if (styleStart >= 0 && selector.endsWith(')')) {
            style =
                safeStyle(selector.substring(styleStart + 7, selector.length - 1))
            selector = selector.substring(0, styleStart)
        }
        val remove = selector.endsWith(":remove()")
        if (remove) {
            selector = selector.removeSuffix(":remove()")
        }
        var removeAttribute: String? = null
        var removeClass: String? = null
        for ((suffix, attribute) in
            listOf(
                ":remove-attr(" to true,
                ":remove-class(" to false,
            )) {
            val start = selector.lastIndexOf(suffix)
            if (start >= 0 && selector.endsWith(')')) {
                val name =
                    selector.substring(start + suffix.length, selector.length - 1)
                if (!IDENTIFIER.matches(name)) {
                    throw Unsupported()
                }
                if (attribute) {
                    removeAttribute = name
                } else {
                    removeClass = name
                }
                selector = selector.substring(0, start)
            }
        }
        val upward = Regex(":upward\\(([^()]*)\\)$").find(selector)
        var upwardCount = 0
        var upwardSelector: String? = null
        if (upward != null) {
            val argument = upward.groupValues[1]
            upwardCount = argument.toIntOrNull() ?: 0
            if (upwardCount !in 1..4) {
                if (!safeSelector(argument, 128)) {
                    throw Unsupported()
                }
                upwardSelector = argument
                upwardCount = 0
            }
            selector = selector.substring(0, upward.range.first)
        }
        val textMatch = Regex(":has-text\\(([^()]*)\\)$").find(selector)
        val text = textMatch?.groupValues?.get(1)
        if (textMatch != null) {
            selector = selector.substring(0, textMatch.range.first)
        }
        val actions =
            listOfNotNull(
                    style,
                    removeAttribute,
                    removeClass,
                    upwardSelector,
                    text,
                    upward.takeIf { it != null },
                )
                .size +
                if (remove) {
                    1
                } else {
                    0
                }
        if (
            actions == 0 ||
                (style != null && actions != 1) ||
                (removeAttribute != null && actions != 1) ||
                (removeClass != null && actions != 1) ||
                text != null &&
                    (text.isEmpty() ||
                        text.length > 128 ||
                        text.startsWith('/') ||
                        text.startsWith('"')) ||
                !safeSelector(selector, 256) ||
                procedural.any(selector::contains) ||
                (remove && (upwardCount != 0 || upwardSelector != null))
        ) {
            throw Unsupported()
        }
        return CosmeticProcedure(
            selector = selector,
            text = text,
            upward = upwardCount,
            remove = remove,
            style = style,
            removeAttribute = removeAttribute,
            removeClass = removeClass,
            upwardSelector = upwardSelector,
        )
    }

    private val IDENTIFIER = Regex("[a-zA-Z_][a-zA-Z0-9_-]{0,63}")
    private val STYLE_PROPERTY = Regex("[a-z-]{2,32}")
    private val STYLE_VALUE = Regex("[a-zA-Z0-9 #!.,%()+_:/\\-]{1,128}")
    private val SAFE_PROPERTIES =
        setOf(
            "display",
            "height",
            "min-height",
            "max-height",
            "width",
            "min-width",
            "max-width",
            "margin",
            "margin-top",
            "margin-left",
            "margin-right",
            "margin-bottom",
            "padding",
            "padding-top",
            "padding-bottom",
            "position",
            "top",
            "bottom",
            "left",
            "right",
            "overflow",
            "overflow-x",
            "overflow-y",
            "pointer-events",
            "z-index",
            "opacity",
            "background",
            "background-image",
            "color",
            "visibility",
        )

    private fun safeSelector(value: String, maxLength: Int): Boolean =
        value.isNotBlank() &&
            value != "*" &&
            value.length <= maxLength &&
            value.none { it.isISOControl() || it in "{};<" }

    private fun safeStyle(value: String): String {
        if (
            value.length !in 1..256 ||
                value.any { it.isISOControl() || it in "{}<>@\\" }
        ) {
            throw Unsupported()
        }
        val declarations =
            value.split(';').map(String::trim).filter(String::isNotEmpty)
        if (declarations.isEmpty() || declarations.size > 8) {
            throw Unsupported()
        }
        for (declaration in declarations) {
            val property = declaration.substringBefore(':').trim()
            val expression = declaration.substringAfter(':', "").trim()
            if (
                !STYLE_PROPERTY.matches(property) ||
                    property !in SAFE_PROPERTIES ||
                    !STYLE_VALUE.matches(expression) ||
                    listOf("url(", "expression(", "javascript:", "@import").any {
                        expression.contains(it, ignoreCase = true)
                    }
            ) {
                throw Unsupported()
            }
        }
        return value
    }

    private fun domains(value: String, separator: Char): DomainScope {
        if (value.isEmpty()) {
            return DomainScope.ANY
        }
        val included = mutableSetOf<String>()
        val excluded = mutableSetOf<String>()
        val includedEntities = mutableSetOf<String>()
        val excludedEntities = mutableSetOf<String>()
        for (entry in value.split(separator)) {
            if (entry.isEmpty()) {
                throw Invalid("empty domain restriction")
            }
            val raw = entry.removePrefix("~")
            val entity = raw.endsWith(".*")
            if (raw.contains('*') && !entity || raw.startsWith('/')) {
                throw Unsupported()
            }
            val name =
                if (entity) {
                    raw.removeSuffix(".*")
                } else {
                    raw
                }
            val domain =
                normalizedHost(name) ?: throw Invalid("invalid domain restriction")
            if (entity && ('.' in domain || ':' in domain)) {
                throw Unsupported()
            }
            when {
                entity && entry.startsWith('~') -> excludedEntities += domain
                entity -> includedEntities += domain
                entry.startsWith('~') -> excluded += domain
                else -> included += domain
            }
        }
        return DomainScope(included, excluded, includedEntities, excludedEntities)
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
        var targets = DomainScope.ANY
        var types = 0
        var excludedTypes = 0
        var thirdParty: Boolean? = null
        var methods = emptySet<String>()
        var excludedMethods = emptySet<String>()
        var matchCase = false
        var important = false
        var badfilter = false
        var hide = 0
        var redirect: BlockingResponse? = null
        var redirectOnly = false
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
                            "to",
                            "denyallow",
                            "redirect",
                            "redirect-rule",
                            "empty",
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

                "redirect",
                "redirect-rule",
                "empty" -> {
                    if (
                        exception ||
                            negated ||
                            redirect != null ||
                            name == "empty" && option.contains('=')
                    ) {
                        throw Unsupported()
                    }
                    val resource = value.substringBeforeLast(':', value)
                    if (
                        resource != value &&
                            value.substringAfterLast(':').toIntOrNull() == null
                    ) {
                        throw Unsupported()
                    }
                    redirectOnly = name == "redirect-rule"
                    redirect =
                        when {
                            name == "empty" || resource == "empty" ->
                                BlockingResponse.EMPTY_TEXT
                            resource in setOf("noopjs", "noop.js") ->
                                BlockingResponse.EMPTY_SCRIPT
                            resource in setOf("noopcss", "noop.css") ->
                                BlockingResponse.EMPTY_STYLESHEET
                            resource in setOf("noophtml", "noop.html") ->
                                BlockingResponse.EMPTY_HTML
                            resource in setOf("nooptext", "noop.txt") ->
                                BlockingResponse.EMPTY_TEXT
                            resource == "noopframe" -> BlockingResponse.EMPTY_HTML
                            resource == "1x1.gif" -> BlockingResponse.TRANSPARENT_GIF
                            resource == "2x2.png" ->
                                BlockingResponse.TRANSPARENT_2X2_PNG
                            resource == "1x1.png" -> BlockingResponse.TRANSPARENT_PNG
                            else -> throw Unsupported()
                        }
                }

                "to",
                "denyallow" -> {
                    if (negated || value.isEmpty() || targets != DomainScope.ANY) {
                        throw Unsupported()
                    }
                    targets =
                        if (name == "denyallow") {
                            if (value.split('|').any { it.startsWith('~') }) {
                                throw Unsupported()
                            }
                            domains(
                                value.split('|').joinToString("|") { "~$it" },
                                '|',
                            )
                        } else {
                            domains(value, '|')
                        }
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
                targets,
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
                    redirect,
                    redirectOnly,
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
