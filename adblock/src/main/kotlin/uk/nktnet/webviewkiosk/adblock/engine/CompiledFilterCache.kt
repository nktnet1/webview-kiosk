package uk.nktnet.webviewkiosk.adblock.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/**
 * One disposable, versioned binary snapshot of normalized rules, never Java object
 * serialization. The source digest prevents stale activation; a checksum rejects
 * damaged cache data. Production restores validated network and page bucket IDs.
 * Normalized records rebuild missing/invalid buckets, without cached executable
 * source.
 */
internal class CompiledFilterCache(
    private val file: File,
    private val compatibilityVersion: Int = 0,
) {
    fun key(lists: List<String>, trusted: Set<Int> = emptySet()): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        fun length(value: Int) {
            for (shift in 24 downTo 0 step 8) {
                digest.update((value ushr shift).toByte())
            }
        }
        length(lists.size)
        for ((index, text) in lists.withIndex()) {
            digest.update(
                if (index in trusted) {
                    1.toByte()
                } else {
                    0.toByte()
                }
            )
            val bytes = text.toByteArray(Charsets.UTF_8)
            length(bytes.size)
            digest.update(bytes)
        }
        return digest.digest()
    }

    private val storage = RecoverableCacheFile(file)

    fun read(
        key: ByteArray,
        allowTrusted: Boolean = false,
        maxRules: Int = FilterCompiler.MAX_RULES,
        onRestore: () -> Unit = {},
        onTimings: (String) -> Unit = {},
    ): CompiledRuleSet? {
        return try {
            storage.recover()
            val size = file.length()
            if (!file.isFile || size !in (CHECKSUM_BYTES + 1)..MAX_BYTES) {
                return null
            }
            val checksum = CRC32()
            file.inputStream().use { source ->
                val input = CacheDataInput(source, size - CHECKSUM_BYTES, checksum)
                input.use {
                    if (
                        input.readInt() != MAGIC ||
                            input.readInt() != VERSION ||
                            input.readInt() != compatibilityVersion
                    ) {
                        return null
                    }
                    val savedKey = ByteArray(32).also(input::readFully)
                    if (!savedKey.contentEquals(key)) {
                        return null
                    }
                    onRestore()
                    val restoreStartedAt = System.nanoTime()
                    val strings = StringReader(input)
                    val total = input.count(maxRules)
                    val skipped = input.count(total)
                    val networkCount = input.count(total - skipped)
                    val networks = ArrayList<NetworkIdentity>(networkCount)
                    val disabledNetworks = ArrayList<NetworkIdentity>()
                    repeat(networkCount) {
                        val disabled = input.readBoolean()
                        val text = strings.read()
                        val flags = input.readUnsignedByte()
                        require(flags and 0xe0 == 0)
                        val host = strings.read().ifEmpty { null }
                        val pattern =
                            UrlPattern(
                                text,
                                flags and 1 != 0,
                                flags and 2 != 0,
                                flags and 4 != 0,
                                flags and 8 != 0,
                                host,
                                flags and 16 != 0,
                            )
                        val domains = strings.scope()
                        val targets = strings.scope()
                        val types = input.readInt()
                        val excludedTypes = input.readInt()
                        require(types in 0..0xfff && excludedTypes in 0..0xfff)
                        val party =
                            when (input.readByte().toInt()) {
                                -1 -> null
                                0 -> false
                                1 -> true
                                else ->
                                    throw IOException("Invalid party restriction")
                            }
                        val methods = strings.set()
                        val excludedMethods = strings.set()
                        val conditions =
                            if (
                                domains === DomainScope.ANY &&
                                    targets === DomainScope.ANY &&
                                    types == 0 &&
                                    excludedTypes == 0 &&
                                    party == null &&
                                    methods.isEmpty() &&
                                    excludedMethods.isEmpty()
                            ) {
                                Conditions.ANY
                            } else {
                                Conditions(
                                    domains,
                                    targets,
                                    types,
                                    excludedTypes,
                                    party,
                                    methods,
                                    excludedMethods,
                                )
                            }
                        val exception = input.readBoolean()
                        val important = input.readBoolean()
                        val hide = input.count(2)
                        val response =
                            input.count(BlockingResponse.entries.size).let {
                                if (it == 0) {
                                    null
                                } else {
                                    BlockingResponse.entries[it - 1]
                                }
                            }
                        require(response != BlockingResponse.ALLOW)
                        val redirectOnly = input.readBoolean()
                        val removeParam =
                            if (input.readBoolean()) {
                                ParameterRemoval(strings.read())
                            } else {
                                null
                            }
                        require(
                            removeParam == null ||
                                response == null && !redirectOnly && hide == 0
                        )
                        val identity =
                            NetworkIdentity(
                                pattern,
                                conditions,
                                exception,
                                important,
                                hide,
                                response,
                                redirectOnly,
                                removeParam,
                                partialAll = input.readBoolean(),
                            )
                        if (disabled) {
                            disabledNetworks += identity
                        } else {
                            networks += identity
                        }
                    }
                    val networksDoneAt = System.nanoTime()
                    val cosmetics =
                        List(input.count(total - skipped - networkCount)) {
                            val selector = strings.read()
                            val domains = strings.scope()
                            val exception = input.readBoolean()
                            val procedure =
                                if (input.readBoolean()) {
                                    CosmeticProcedure(
                                        strings.read(),
                                        strings.read().ifEmpty { null },
                                        input.count(4),
                                        input.readBoolean(),
                                        strings.read().ifEmpty { null },
                                        strings.read().ifEmpty { null },
                                        strings.read().ifEmpty { null },
                                        strings.read().ifEmpty { null },
                                        strings.read().ifEmpty { null },
                                        input.readBoolean(),
                                        strings.read().ifEmpty { null },
                                        strings.read().ifEmpty { null },
                                    )
                                } else {
                                    null
                                }
                            CosmeticRule(selector, domains, exception, procedure)
                                .also { rule ->
                                    require(
                                        rule.procedure == null ||
                                            FilterParser.procedure(
                                                selector,
                                                domains,
                                            ) == rule.procedure
                                    )
                                }
                        }
                    val cosmeticsDoneAt = System.nanoTime()
                    val scriptlets =
                        List(
                            input.count(
                                minOf(
                                    ScriptletIndex.MAX_RULES,
                                    total - skipped - networkCount - cosmetics.size,
                                )
                            )
                        ) {
                            val scope = strings.scope()
                            require(scope.included.none { ':' in it })
                            val exception = input.readBoolean()
                            val targets =
                                List(input.count(1024)) {
                                    val host = strings.read()
                                    val path =
                                        if (input.readBoolean()) {
                                            strings.read()
                                        } else {
                                            null
                                        }
                                    val regex = input.readBoolean()
                                    val entity = input.readBoolean()
                                    val ancestor = input.readBoolean()
                                    val excluded = input.readBoolean()
                                    require(!ancestor || path == null)
                                    require(!regex || !entity && !ancestor)
                                    if (regex) {
                                        require(
                                            ScriptletScopes.pattern(host) &&
                                                (path == null ||
                                                    ScriptletScopes.pattern(path))
                                        )
                                    } else {
                                        require(
                                            normalizedHost(host) == host &&
                                                ':' !in host
                                        )
                                        require(path == null || path.startsWith('/'))
                                    }
                                    ScriptletTarget(
                                        host,
                                        path,
                                        regex,
                                        entity,
                                        ancestor,
                                        excluded,
                                    )
                                }
                            require(
                                exception ||
                                    scope.included.isNotEmpty() ||
                                    scope.includedEntities.isNotEmpty() ||
                                    targets.any { !it.excluded }
                            )
                            val instruction =
                                if (input.readBoolean()) {
                                    val type = ScriptletType.valueOf(strings.read())
                                    val args =
                                        List(
                                            input.count(
                                                if (
                                                    type in
                                                        CookieScriptlets.trustedTypes
                                                ) {
                                                    CookieScriptlets.MAX_ARGUMENTS
                                                } else {
                                                    8
                                                }
                                            )
                                        ) {
                                            strings.read()
                                        }
                                    requireNotNull(
                                        ScriptletParser.validated(
                                            type,
                                            args,
                                            allowTrusted || exception,
                                        )
                                    )
                                } else {
                                    require(exception)
                                    null
                                }
                            ScriptletRule(instruction, scope, exception, targets)
                        }
                    val scriptletsDoneAt = System.nanoTime()
                    val indexBytes = input.available()
                    var remainingReferences =
                        minOf(FilterCompiler.MAX_RULES * 8, indexBytes / 4)
                    fun ids(count: Int): IntArray {
                        val size = input.count(remainingReferences)
                        remainingReferences -= size
                        return IntArray(size) { input.count(count - 1) }
                    }
                    fun buckets(count: Int): Map<String, IntArray> {
                        val size =
                            input.count(minOf(remainingReferences, indexBytes / 8))
                        val result = HashMap<String, IntArray>()
                        repeat(size) {
                            require(result.put(strings.read(), ids(count)) == null)
                        }
                        return result
                    }
                    fun routing(): NetworkRouting {
                        val count = input.count(networks.size)
                        val hosts = buckets(count)
                        val pages = buckets(count)
                        val entities = buckets(count)
                        val tokens = HashMap<Int, IntArray>()
                        repeat(input.count(minOf(count, indexBytes / 8))) {
                            require(tokens.put(input.readInt(), ids(count)) == null)
                        }
                        val fallback = ids(count)
                        require(fallback.size <= 2048)
                        return NetworkRouting(
                            count,
                            hosts,
                            pages,
                            entities,
                            tokens,
                            fallback,
                        )
                    }
                    val indexes =
                        if (input.readBoolean()) {
                            NetworkIndexSnapshot(
                                routing(),
                                routing(),
                                routing(),
                                routing(),
                            )
                        } else {
                            null
                        }
                    val networkBucketsDoneAt = System.nanoTime()
                    fun pageRouting(maxCount: Int, tableCount: Int): RuleBuckets {
                        val count = input.count(maxCount)
                        require(input.count(tableCount) == tableCount)
                        return RuleBuckets(
                            count,
                            List(tableCount) { buckets(count) },
                        )
                    }
                    val pageIndexes =
                        if (input.readBoolean()) {
                            PageIndexSnapshot(
                                pageRouting(
                                    cosmetics.size,
                                    CosmeticBucket.entries.size,
                                ),
                                pageRouting(
                                    scriptlets.size,
                                    ScriptletBucket.entries.size,
                                ),
                            )
                        } else {
                            null
                        }
                    val pageBucketsDoneAt = System.nanoTime()
                    if (input.read() != -1) {
                        return null
                    }
                    val expected = DataInputStream(source).readLong()
                    if (checksum.value != expected || source.read() != -1) {
                        return null
                    }
                    require(
                        total >=
                            networkCount + cosmetics.size + scriptlets.size + skipped
                    )
                    val restored =
                        CompiledRuleSet(
                            networks,
                            cosmetics,
                            skipped,
                            scriptlets,
                            disabledNetworks,
                            networkIndexes = indexes,
                            pageIndexes = pageIndexes,
                            totalRules = total,
                        )
                    val restoredAt = System.nanoTime()
                    fun elapsed(start: Long, end: Long): Long =
                        (end - start) / 1_000_000
                    val networkBucketMs =
                        elapsed(scriptletsDoneAt, networkBucketsDoneAt)
                    onTimings(
                        "NETWORK_RULES=${elapsed(restoreStartedAt, networksDoneAt)}ms " +
                            "COSMETIC_RULES=${elapsed(networksDoneAt, cosmeticsDoneAt)}ms " +
                            "SCRIPTLET_RULES=${elapsed(cosmeticsDoneAt, scriptletsDoneAt)}ms " +
                            "NETWORK_BUCKETS=${networkBucketMs}ms " +
                            "PAGE_BUCKETS=${elapsed(networkBucketsDoneAt, pageBucketsDoneAt)}ms " +
                            "FOOTER=${elapsed(pageBucketsDoneAt, restoredAt)}ms " +
                            "records=${networkCount + cosmetics.size + scriptlets.size} " +
                            "strings=${strings.size} bytes=$size"
                    )
                    restored
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Cache failures never prevent the successfully compiled rules from becoming
     * active.
     */
    fun write(
        key: ByteArray,
        rules: CompiledRuleSet,
        indexes: NetworkIndexSnapshot? = null,
        pageIndexes: PageIndexSnapshot? = null,
    ) {
        try {
            storage.write(MAX_BYTES) { fileOutput ->
                val checksum = CRC32()
                // Buffer the writes before checksumming, just like the reader.
                // Otherwise writeInt()/writeUTF() update CRC32 a byte at a time.
                val output =
                    DataOutputStream(
                        CheckedOutputStream(fileOutput, checksum)
                            .buffered(BUFFER_SIZE)
                    )
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(compatibilityVersion)
                output.write(key)
                val strings = StringWriter(output)
                output.writeInt(rules.totalRules)
                output.writeInt(rules.skippedRules)
                output.writeInt(rules.networks.size + rules.disabledNetworks.size)
                // Stream each group directly instead of allocating a Pair and
                // a combined list for every network rule.
                fun network(rule: NetworkIdentity, disabled: Boolean) {
                    output.writeBoolean(disabled)
                    val (
                        pattern,
                        conditions,
                        exception,
                        important,
                        hide,
                        redirect,
                        redirectOnly,
                        removeParam,
                        partialAll) =
                        rule
                    strings.write(pattern.text)
                    output.writeByte(
                        (if (pattern.domainAnchor) {
                            1
                        } else {
                            0
                        }) or
                            (if (pattern.startAnchor) {
                                2
                            } else {
                                0
                            }) or
                            (if (pattern.endAnchor) {
                                4
                            } else {
                                0
                            }) or
                            (if (pattern.matchCase) {
                                8
                            } else {
                                0
                            }) or
                            (if (pattern.regex) {
                                16
                            } else {
                                0
                            })
                    )
                    strings.write(pattern.hostIndex.orEmpty())
                    strings.scope(conditions.domains)
                    strings.scope(conditions.targets)
                    output.writeInt(conditions.types)
                    output.writeInt(conditions.excludedTypes)
                    output.writeByte(
                        when (conditions.thirdParty) {
                            null -> -1
                            false -> 0
                            true -> 1
                        }
                    )
                    strings.set(conditions.methods)
                    strings.set(conditions.excludedMethods)
                    output.writeBoolean(exception)
                    output.writeBoolean(important)
                    output.writeInt(hide)
                    output.writeInt(
                        if (redirect == null) {
                            0
                        } else {
                            redirect.ordinal + 1
                        }
                    )
                    output.writeBoolean(redirectOnly)
                    output.writeBoolean(removeParam != null)
                    removeParam?.let { strings.write(it.text) }
                    output.writeBoolean(partialAll)
                }
                rules.networks.forEach { network(it, false) }
                rules.disabledNetworks.forEach { network(it, true) }
                output.writeInt(rules.cosmetics.size)
                for ((selector, domains, exception, procedure) in rules.cosmetics) {
                    strings.write(selector)
                    strings.scope(domains)
                    output.writeBoolean(exception)
                    output.writeBoolean(procedure != null)
                    if (procedure != null) {
                        strings.write(procedure.selector)
                        strings.write(procedure.text.orEmpty())
                        output.writeInt(procedure.upward)
                        output.writeBoolean(procedure.remove)
                        strings.write(procedure.style.orEmpty())
                        strings.write(procedure.removeAttribute.orEmpty())
                        strings.write(procedure.removeClass.orEmpty())
                        strings.write(procedure.upwardSelector.orEmpty())
                        strings.write(procedure.textSelector.orEmpty())
                        output.writeBoolean(procedure.textPattern)
                        strings.write(procedure.media.orEmpty())
                        strings.write(procedure.operations.orEmpty())
                    }
                }
                output.writeInt(rules.scriptlets.size)
                for ((instruction, domains, exception, targets) in
                    rules.scriptlets) {
                    strings.scope(domains)
                    output.writeBoolean(exception)
                    output.writeInt(targets.size)
                    for ((host, path, regex, entity, ancestor, excluded) in
                        targets) {
                        strings.write(host)
                        output.writeBoolean(path != null)
                        path?.let(strings::write)
                        output.writeBoolean(regex)
                        output.writeBoolean(entity)
                        output.writeBoolean(ancestor)
                        output.writeBoolean(excluded)
                    }
                    output.writeBoolean(instruction != null)
                    if (instruction != null) {
                        strings.write(instruction.type.name)
                        output.writeInt(instruction.arguments.size)
                        instruction.arguments.forEach(strings::write)
                    }
                }
                output.writeBoolean(indexes != null)
                fun ids(values: IntArray) {
                    output.writeInt(values.size)
                    values.forEach(output::writeInt)
                }
                fun buckets(values: Map<String, IntArray>) {
                    output.writeInt(values.size)
                    for ((key, entries) in values) {
                        strings.write(key)
                        ids(entries)
                    }
                }
                fun routing(value: NetworkRouting) {
                    output.writeInt(value.ruleCount)
                    buckets(value.hosts)
                    buckets(value.pages)
                    buckets(value.entities)
                    output.writeInt(value.tokens.size)
                    for ((key, entries) in value.tokens) {
                        output.writeInt(key)
                        ids(entries)
                    }
                    ids(value.fallback)
                }
                indexes?.let {
                    routing(it.requests)
                    routing(it.hiding)
                    routing(it.redirects)
                    routing(it.parameters)
                }
                output.writeBoolean(pageIndexes != null)
                fun pageRouting(value: RuleBuckets) {
                    output.writeInt(value.ruleCount)
                    output.writeInt(value.tables.size)
                    value.tables.forEach(::buckets)
                }
                pageIndexes?.let {
                    pageRouting(it.cosmetics)
                    pageRouting(it.scriptlets)
                }
                // Flush all payload bytes through CRC32 before writing the footer.
                // The trailing checksum is not itself part of the checksummed data.
                output.flush()
                DataOutputStream(fileOutput).writeLong(checksum.value)
            }
        } catch (_: IOException) {
            // Disposable optimization; the original filter lists remain
            // authoritative.
        }
    }

    private class StringWriter(private val output: DataOutputStream) {
        private val strings = HashMap<String, Int>()
        private val encoding = CacheStringWriter(output)

        fun write(value: String) {
            val previous = strings[value]
            output.writeInt(previous ?: strings.size)
            if (previous == null) {
                strings[value] = strings.size
                encoding.write(value)
            }
        }

        fun set(values: Set<String>) {
            output.writeInt(values.size)
            values.forEach(::write)
        }

        fun scope(value: DomainScope) {
            set(value.included)
            set(value.excluded)
            set(value.includedEntities)
            set(value.excludedEntities)
        }
    }

    private class StringReader(private val input: CacheDataInput) {
        private val values = ArrayList<String>()

        val size: Int
            get() = values.size

        fun read(): String {
            val index = input.count(minOf(values.size, 2_000_000))
            if (index == values.size) {
                val value = input.readString()
                values += value
            }
            return values[index]
        }

        fun set(): Set<String> {
            val count = input.count(8192)
            return if (count == 0) {
                emptySet()
            } else {
                LinkedHashSet<String>(count).apply {
                    repeat(count) { add(read()) }
                }
            }
        }

        fun scope(): DomainScope {
            val included = set()
            val excluded = set()
            val includedEntities = set()
            val excludedEntities = set()
            return if (
                included.isEmpty() &&
                    excluded.isEmpty() &&
                    includedEntities.isEmpty() &&
                    excludedEntities.isEmpty()
            ) {
                DomainScope.ANY
            } else {
                DomainScope(included, excluded, includedEntities, excludedEntities)
            }
        }
    }

    companion object {
        private const val MAGIC = 0x574b4346 // WKCF

        // Bump when parsing/matching semantics or the binary schema change.
        private const val VERSION = 19
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val CHECKSUM_BYTES = Long.SIZE_BYTES.toLong()

        private fun CacheDataInput.count(max: Int): Int =
            readInt().also {
                require(it in 0..max)
            }
    }
}
