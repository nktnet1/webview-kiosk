package uk.nktnet.webviewkiosk.adblock.engine

internal enum class CosmeticBucket {
    HOST,
    ENTITY,
    EXCEPTION_HOST,
    EXCEPTION_ENTITY,
    PROCEDURE_HOST,
    PROCEDURE_ENTITY,
    PROCEDURE_EXCEPTION_HOST,
    PROCEDURE_EXCEPTION_ENTITY,
    CLASS,
    ID,
    GENERIC,
    GENERIC_EXCEPTION,
}

internal object CosmeticRouting {
    fun build(rules: List<CosmeticRule>): RuleBuckets =
        RuleBuckets.build(rules.size, CosmeticBucket.entries.size) {
            visit(rules, it)
        }

    fun validFor(buckets: RuleBuckets, rules: List<CosmeticRule>): Boolean =
        buckets.validFor(rules.size, CosmeticBucket.entries.size) {
            visit(rules, it)
        }

    private fun visit(rules: List<CosmeticRule>, emit: BucketVisitor) {
        for ((index, rule) in rules.withIndex()) {
            fun add(bucket: CosmeticBucket, key: String = "") {
                emit(bucket.ordinal, key, index)
            }
            val scoped =
                rule.domains.included.isNotEmpty() ||
                    rule.domains.includedEntities.isNotEmpty()
            if (rule.procedure != null || scoped) {
                val host =
                    when {
                        rule.procedure != null && rule.exception ->
                            CosmeticBucket.PROCEDURE_EXCEPTION_HOST
                        rule.procedure != null -> CosmeticBucket.PROCEDURE_HOST
                        rule.exception -> CosmeticBucket.EXCEPTION_HOST
                        else -> CosmeticBucket.HOST
                    }
                val entity =
                    when {
                        rule.procedure != null && rule.exception ->
                            CosmeticBucket.PROCEDURE_EXCEPTION_ENTITY
                        rule.procedure != null -> CosmeticBucket.PROCEDURE_ENTITY
                        rule.exception -> CosmeticBucket.EXCEPTION_ENTITY
                        else -> CosmeticBucket.ENTITY
                    }
                rule.domains.included.forEach { add(host, it) }
                rule.domains.includedEntities.forEach { add(entity, it) }
            } else if (rule.exception) {
                add(CosmeticBucket.GENERIC_EXCEPTION)
            } else {
                val token = genericToken(rule.selector)
                if (token == null) {
                    add(CosmeticBucket.GENERIC)
                } else {
                    add(
                        if (token.first == '.') {
                            CosmeticBucket.CLASS
                        } else {
                            CosmeticBucket.ID
                        },
                        token.second,
                    )
                }
            }
        }
    }

    private fun genericToken(selector: String): Pair<Char, String>? {
        // A leading class/ID is mandatory unless the selector contains an OR.
        // Complex/escaped selectors take the unconditional CSS path instead.
        if (
            selector.firstOrNull() !in listOf('.', '#') ||
                ',' in selector ||
                '\\' in selector
        ) {
            return null
        }
        val end =
            selector
                .indexOfFirst {
                    it != '.' && it != '#' && !it.isLetterOrDigit() && it !in "_-"
                }
                .let {
                    if (it < 0) {
                        selector.length
                    } else {
                        it
                    }
                }
        // Chained selectors .a.b and #a.b must be indexed by their first token.
        val tokenEnd =
            (1 until end).firstOrNull { selector[it] == '.' || selector[it] == '#' }
                ?: end
        return if (tokenEnd in 2..129) {
            selector[0] to selector.substring(1, tokenEnd)
        } else {
            null
        }
    }
}
