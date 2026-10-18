package uk.nktnet.webviewkiosk.adblock.engine

/**
 * Extended scopes are data; regexes use the same bounded grammar in Kotlin and JS.
 */
internal data class ScriptletTarget(
    val host: String,
    val path: String? = null,
    val regex: Boolean = false,
    val entity: Boolean = false,
    val ancestor: Boolean = false,
    val excluded: Boolean = false,
) {
    private val hostPattern =
        if (regex) {
            Regex(host)
        } else {
            null
        }
    private val pathPattern =
        if (regex && path != null) {
            Regex(path)
        } else {
            null
        }

    fun matches(hostname: String, domain: String?, pathname: String): Boolean {
        val name =
            if (entity) {
                entityName(hostname, domain)
            } else {
                hostname
            }
        return name != null &&
            (hostPattern?.containsMatchIn(name) ?: domainMatches(name, host)) &&
            (pathPattern?.containsMatchIn(pathname)
                ?: (path == null || pathname.startsWith(path)))
    }
}

internal object ScriptletScopes {
    /** Commas in regex character classes and counted repeats are not separators. */
    fun entries(value: String): List<String> {
        val entries = mutableListOf<String>()
        var start = 0
        var regex = false
        var escaped = false
        for ((index, char) in value.withIndex()) {
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                '/' if
                    (regex ||
                        index == start ||
                        index == start + 1 && value[start] == '~')
                 -> regex = !regex
                ',' if !regex -> {
                    entries += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        require(!regex && !escaped)
        entries += value.substring(start)
        return entries
    }

    fun target(entry: String): ScriptletTarget? {
        val raw = entry.removePrefix("~")
        val excluded = entry.startsWith('~')
        if (raw.startsWith('/')) {
            require(raw.endsWith('/') && raw.length > 2)
            val expression = raw.substring(1, raw.lastIndex)
            val slash = expression.indexOf("\\/")
            val host =
                if (slash < 0) {
                    expression
                } else {
                    expression.substring(0, slash) + "$"
                }
            val path =
                if (slash < 0) {
                    null
                } else {
                    "^" + expression.substring(slash)
                }
            require(pattern(host) && (path == null || pattern(path)))
            return ScriptletTarget(host, path, regex = true, excluded = excluded)
        }
        val ancestor = raw.endsWith(">>")
        val name = raw.removeSuffix(">>")
        if (!ancestor && '/' !in name) {
            return null
        }
        require(!ancestor || '/' !in name)
        val hostname = name.substringBefore('/')
        val entity = hostname.endsWith(".*")
        val host = requireNotNull(normalizedHost(hostname.removeSuffix(".*")))
        require(':' !in host)
        return ScriptletTarget(
            host,
            if ('/' in name) {
                name.substring(name.indexOf('/'))
            } else {
                null
            },
            entity = entity,
            ancestor = ancestor,
            excluded = excluded,
        )
    }

    fun pattern(source: String): Boolean {
        if (source.length > 256) {
            return false
        }
        // Small counted repetitions of a single atom are safe on a bounded
        // host/path.
        // Repeated groups, lookarounds and backreferences remain unsupported.
        val counts = Regex("\\{([0-9]{1,2})(?:,([0-9]{1,2}))?}")
        var work = 1
        for (match in counts.findAll(source)) {
            val low = match.groupValues[1].toInt()
            val high = match.groupValues[2].toIntOrNull() ?: low
            val previous = source.getOrNull(match.range.first - 1) ?: return false
            if (high !in low..16 || previous in ")*+?}^$") {
                return false
            }
            work *= maxOf(1, high - low + 1)
            if (work > 64) {
                return false
            }
        }
        return ScriptletParser.pattern("/${source.replace(counts, "")}/") &&
            runCatching { Regex(source) }.isSuccess
    }

    fun hasPositive(rule: ScriptletRule): Boolean =
        rule.domains.included.isNotEmpty() ||
            rule.domains.includedEntities.isNotEmpty() ||
            rule.targets.any { !it.excluded }

    fun matches(
        rule: ScriptletRule,
        url: String,
        ancestors: List<String> = emptyList(),
    ): Boolean {
        if (
            rule.targets.any { it.ancestor } &&
                (ancestors.size > 64 || ancestors.any { httpAddress(it) == null })
        ) {
            return false
        }
        val address = httpAddress(url) ?: return false
        val host = address.host.removeSuffix(".")
        val domain =
            if (rule.domains.usesEntities || rule.targets.any { it.entity }) {
                address.topPrivateDomain()
            } else {
                null
            }
        val path =
            address.encodedPath +
                (address.encodedQuery?.let { "?$it" } ?: "") +
                (address.encodedFragment?.let { "#$it" } ?: "")
        val scope = rule.domains
        val entity = entityName(host, domain)
        if (
            scope.excluded.any { domainMatches(host, it) } ||
                scope.excludedEntities.any {
                    entity != null && domainMatches(entity, it)
                }
        ) {
            return false
        }
        fun match(target: ScriptletTarget): Boolean =
            if (target.ancestor) {
                ancestors.take(64).any { ancestor ->
                    httpAddress(ancestor)?.let {
                        target.matches(
                            it.host.removeSuffix("."),
                            if (target.entity) {
                                it.topPrivateDomain()
                            } else {
                                null
                            },
                            "",
                        )
                    } == true
                }
            } else {
                target.matches(host, domain, path)
            }
        return rule.targets.none { it.excluded && match(it) } &&
            (!hasPositive(rule) ||
                scope.included.any { domainMatches(host, it) } ||
                scope.includedEntities.any {
                    entity != null && domainMatches(entity, it)
                } ||
                rule.targets.any { !it.excluded && match(it) })
    }
}
