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
            // Domain sets are already unique within their respective buckets.
            // Only extended scopes can overlap, so only they need a temporary set.
            val routes =
                if (rule.targets.isEmpty()) {
                    null
                } else {
                    linkedSetOf<Pair<ScriptletBucket, String>>()
                }
            fun add(bucket: ScriptletBucket, key: String) {
                if (routes == null) {
                    emit(bucket.ordinal, key, index)
                } else {
                    routes.add(bucket to key)
                }
            }
            if (!ScriptletScopes.hasPositive(rule) && rule.exception) {
                add(ScriptletBucket.GENERIC_EXCEPTION, "")
            }
            rule.domains.included.forEach { add(ScriptletBucket.HOST, it) }
            rule.domains.includedEntities.forEach { add(ScriptletBucket.ENTITY, it) }
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
                add(
                    bucket,
                    if (regex) {
                        ""
                    } else {
                        host
                    },
                )
            }
            routes?.forEach { (bucket, key) -> emit(bucket.ordinal, key, index) }
        }
    }
}
