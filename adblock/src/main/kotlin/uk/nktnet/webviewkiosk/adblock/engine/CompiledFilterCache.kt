package uk.nktnet.webviewkiosk.adblock.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/**
 * One disposable, versioned binary snapshot of normalized rules, never Java object
 * serialization. The source digest prevents stale activation; a checksum rejects
 * damaged cache data. Production rebuilds indexes from these records. Experiments
 * can also restore bounded network bucket IDs, without cached executable source.
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
    ): CompiledRuleSet? {
        return try {
            storage.recover()
            if (!file.isFile || file.length() !in 1..MAX_BYTES) {
                return null
            }
            val checksum = CRC32()
            DataInputStream(
                    CheckedInputStream(
                        file.inputStream().buffered(BUFFER_SIZE),
                        checksum,
                    )
                )
                .use { input ->
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
                    val strings = StringReader(input)
                    val total = input.count(maxRules)
                    val skipped = input.count(total)
                    val networkEntries =
                        List(input.count(total - skipped)) {
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
                                        throw IOException(
                                            "Invalid party restriction"
                                        )
                                }
                            val conditions =
                                Conditions(
                                        domains,
                                        targets,
                                        types,
                                        excludedTypes,
                                        party,
                                        strings.set(),
                                        strings.set(),
                                    )
                                    .let {
                                        if (it == Conditions.ANY) {
                                            Conditions.ANY
                                        } else {
                                            it
                                        }
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
                            ) to disabled
                        }
                    val networks =
                        networkEntries.filterNot { it.second }.map { it.first }
                    val disabled =
                        networkEntries.filter { it.second }.map { it.first }
                    val cosmetics =
                        List(input.count(total - skipped - networkEntries.size)) {
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
                                    )
                                } else {
                                    null
                                }
                            CosmeticRule(selector, domains, exception, procedure)
                        }
                    val scriptlets =
                        List(
                            input.count(
                                minOf(
                                    ScriptletIndex.MAX_RULES,
                                    total -
                                        skipped -
                                        networkEntries.size -
                                        cosmetics.size,
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
                    val indexBytes = input.available()
                    var remainingReferences =
                        minOf(FilterCompiler.MAX_RULES * 8, indexBytes / 4)
                    fun routing(): NetworkRouting {
                        val count = input.count(networks.size)
                        fun ids(): IntArray {
                            val size = input.count(remainingReferences)
                            remainingReferences -= size
                            return IntArray(size) { input.count(count - 1) }
                        }
                        fun buckets(): Map<String, IntArray> {
                            val size =
                                input.count(minOf(count * 1024, indexBytes / 8))
                            val result = HashMap<String, IntArray>()
                            repeat(size) {
                                require(result.put(strings.read(), ids()) == null)
                            }
                            return result
                        }
                        val hosts = buckets()
                        val pages = buckets()
                        val entities = buckets()
                        val tokens = HashMap<Int, IntArray>()
                        repeat(input.count(minOf(count, indexBytes / 8))) {
                            require(tokens.put(input.readInt(), ids()) == null)
                        }
                        val fallback = ids()
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
                    val actual = checksum.value
                    if (input.readLong() != actual || input.read() != -1) {
                        return null
                    }
                    require(
                        total >=
                            networkEntries.size +
                                cosmetics.size +
                                scriptlets.size +
                                skipped
                    )
                    CompiledRuleSet(
                        networks,
                        cosmetics,
                        skipped,
                        scriptlets,
                        disabled,
                        networkIndexes = indexes,
                        totalRules = total,
                    )
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
    ) {
        try {
            storage.write(MAX_BYTES) { fileOutput ->
                val checksum = CRC32()
                val output =
                    DataOutputStream(
                        CheckedOutputStream(
                            fileOutput.buffered(BUFFER_SIZE),
                            checksum,
                        )
                    )
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(compatibilityVersion)
                output.write(key)
                val strings = StringWriter(output)
                output.writeInt(rules.totalRules)
                output.writeInt(rules.skippedRules)
                val networks =
                    rules.networks.map { it to false } +
                        rules.disabledNetworks.map { it to true }
                output.writeInt(networks.size)
                for ((rule, disabled) in networks) {
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
                    }
                }
                output.writeInt(rules.scriptlets.size)
                for ((instruction, domains, exception, targets) in
                    rules.scriptlets) {
                    strings.scope(domains)
                    output.writeBoolean(exception)
                    output.writeInt(targets.size)
                    for (target in targets) {
                        strings.write(target.host)
                        output.writeBoolean(target.path != null)
                        target.path?.let(strings::write)
                        output.writeBoolean(target.regex)
                        output.writeBoolean(target.entity)
                        output.writeBoolean(target.ancestor)
                        output.writeBoolean(target.excluded)
                    }
                    output.writeBoolean(instruction != null)
                    if (instruction != null) {
                        strings.write(instruction.type.name)
                        output.writeInt(instruction.arguments.size)
                        instruction.arguments.forEach(strings::write)
                    }
                }
                output.writeBoolean(indexes != null)
                fun routing(value: NetworkRouting) {
                    output.writeInt(value.ruleCount)
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
                output.writeLong(checksum.value)
                output.flush()
            }
        } catch (_: IOException) {
            // Disposable optimization; the original filter lists remain
            // authoritative.
        }
    }

    private class StringWriter(private val output: DataOutputStream) {
        private val strings = HashMap<String, Int>()

        fun write(value: String) {
            val previous = strings[value]
            output.writeInt(previous ?: strings.size)
            if (previous == null) {
                strings[value] = strings.size
                output.writeUTF(value)
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

    private class StringReader(private val input: DataInputStream) {
        private val strings = ArrayList<String>()

        fun read(): String {
            val index = input.count(minOf(strings.size, 2_000_000))
            if (index == strings.size) {
                val value = input.readUTF()
                require(value.length <= 8192)
                strings += value
            }
            return strings[index]
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
            return DomainScope(
                    included,
                    excluded,
                    includedEntities,
                    excludedEntities,
                )
                .let {
                    if (it == DomainScope.ANY) {
                        DomainScope.ANY
                    } else {
                        it
                    }
                }
        }
    }

    companion object {
        private const val MAGIC = 0x574b4346 // WKCF

        // Bump when parsing/matching semantics or the binary schema change.
        private const val VERSION = 15
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024

        private fun DataInputStream.count(max: Int): Int =
            readInt().also {
                require(it in 0..max)
            }
    }
}
