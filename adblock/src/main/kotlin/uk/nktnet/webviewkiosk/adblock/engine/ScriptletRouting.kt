package uk.nktnet.webviewkiosk.adblock.engine

internal enum class ScriptletBucket {
    HOST,
    ENTITY,
    ANCESTOR_HOST,
    ANCESTOR_ENTITY,
    REGEX,
    GENERIC_EXCEPTION,
}

internal object ScriptletRouting {
    fun build(rules: List<ScriptletRule>): RuleBuckets =
        RuleBuckets.build(rules.size, ScriptletBucket.entries.size) {
            visit(rules, it)
        }

    fun validFor(buckets: RuleBuckets, rules: List<ScriptletRule>): Boolean =
        buckets.validFor(rules.size, ScriptletBucket.entries.size) {
            visit(rules, it)
        }

    private fun visit(rules: List<ScriptletRule>, emit: BucketVisitor) {
        for ((index, rule) in rules.withIndex()) {
            // Several path/regex targets can route the same rule through one
            // bucket. Store that rule ID once; matches() still checks every scope.
            val routes = linkedSetOf<Pair<ScriptletBucket, String>>()
            if (!ScriptletScopes.hasPositive(rule) && rule.exception) {
                routes += ScriptletBucket.GENERIC_EXCEPTION to ""
            }
            rule.domains.included.forEach { routes += ScriptletBucket.HOST to it }
            rule.domains.includedEntities.forEach {
                routes += ScriptletBucket.ENTITY to it
            }
            for ((host, _, regex, entity, ancestor, excluded) in rule.targets) {
                if (excluded) {
                    continue
                }
                val bucket =
                    when {
                        regex -> ScriptletBucket.REGEX
                        ancestor && entity -> ScriptletBucket.ANCESTOR_ENTITY
                        ancestor -> ScriptletBucket.ANCESTOR_HOST
                        entity -> ScriptletBucket.ENTITY
                        else -> ScriptletBucket.HOST
                    }
                routes +=
                    bucket to
                        if (regex) {
                            ""
                        } else {
                            host
                        }
            }
            for ((bucket, key) in routes) {
                emit(bucket.ordinal, key, index)
            }
        }
    }
}
