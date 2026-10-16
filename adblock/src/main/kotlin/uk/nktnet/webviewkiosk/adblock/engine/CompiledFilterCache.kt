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

/**
 * One disposable, versioned binary snapshot of normalized rules, never Java object
 * serialization. The source digest prevents stale activation; a checksum rejects
 * damaged cache data. Indexes are rebuilt from these records without parsing or
 * validating filter syntax again.
 */
internal class CompiledFilterCache(
    private val file: File,
    private val buildVersion: Int = 0,
) {
    fun key(lists: List<String>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        fun length(value: Int) {
            for (shift in 24 downTo 0 step 8) {
                digest.update((value ushr shift).toByte())
            }
        }
        length(lists.size)
        for (text in lists) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            length(bytes.size)
            digest.update(bytes)
        }
        return digest.digest()
    }

    fun read(key: ByteArray, onRestore: () -> Unit = {}): CompiledRuleSet? {
        if (!file.isFile || file.length() !in 1..MAX_BYTES) {
            return null
        }
        return try {
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
                            input.readInt() != buildVersion
                    ) {
                        return null
                    }
                    val savedKey = ByteArray(32).also(input::readFully)
                    if (!savedKey.contentEquals(key)) {
                        return null
                    }
                    onRestore()
                    val strings = StringReader(input)
                    val skipped = input.count(FilterCompiler.MAX_RULES)
                    val networks =
                        List(input.count(FilterCompiler.MAX_RULES - skipped)) {
                            val text = strings.read()
                            val flags = input.readUnsignedByte()
                            require(flags and 0xf0 == 0)
                            val host = strings.read().ifEmpty { null }
                            val pattern =
                                UrlPattern(
                                    text,
                                    flags and 1 != 0,
                                    flags and 2 != 0,
                                    flags and 4 != 0,
                                    flags and 8 != 0,
                                    host,
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
                            NetworkIdentity(
                                pattern,
                                conditions,
                                exception,
                                important,
                                hide,
                                response,
                            )
                        }
                    val cosmetics =
                        List(
                            input.count(
                                FilterCompiler.MAX_RULES - skipped - networks.size
                            )
                        ) {
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
                                    )
                                } else {
                                    null
                                }
                            CosmeticRule(selector, domains, exception, procedure)
                        }
                    val actual = checksum.value
                    if (input.readLong() != actual || input.read() != -1) {
                        return null
                    }
                    CompiledRuleSet(networks, cosmetics, skipped)
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
    fun write(key: ByteArray, rules: CompiledRuleSet) {
        var temporary: File? = null
        try {
            val parent = file.parentFile ?: return
            if (!parent.isDirectory && !parent.mkdirs()) {
                return
            }
            temporary = File.createTempFile("compiled-", ".tmp", parent)
            val checksum = CRC32()
            DataOutputStream(
                    CheckedOutputStream(
                        temporary.outputStream().buffered(BUFFER_SIZE),
                        checksum,
                    )
                )
                .use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeInt(buildVersion)
                    output.write(key)
                    val strings = StringWriter(output)
                    output.writeInt(rules.skippedRules)
                    output.writeInt(rules.networks.size)
                    for ((
                        pattern, conditions, exception, important, hide, redirect) in
                        rules.networks) {
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
                    }
                    output.writeInt(rules.cosmetics.size)
                    for ((selector, domains, exception, procedure) in
                        rules.cosmetics) {
                        strings.write(selector)
                        strings.scope(domains)
                        output.writeBoolean(exception)
                        output.writeBoolean(procedure != null)
                        if (procedure != null) {
                            strings.write(procedure.selector)
                            strings.write(procedure.text.orEmpty())
                            output.writeInt(procedure.upward)
                            output.writeBoolean(procedure.remove)
                        }
                    }
                    output.writeLong(checksum.value)
                }
            if (temporary.length() <= MAX_BYTES) {
                temporary.renameTo(file)
            }
        } catch (_: IOException) {
            // Disposable optimization; the original filter lists remain
            // authoritative.
        } finally {
            temporary?.delete()
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
        private const val VERSION = 4
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024

        private fun DataInputStream.count(max: Int): Int =
            readInt().also {
                require(it in 0..max)
            }
    }
}
