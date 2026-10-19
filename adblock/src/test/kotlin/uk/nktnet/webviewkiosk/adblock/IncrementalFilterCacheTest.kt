package uk.nktnet.webviewkiosk.adblock

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.FilterSourceCache
import uk.nktnet.webviewkiosk.adblock.engine.RecoverableCacheFile

class IncrementalFilterCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun onlyChangedSourcesCompileAndUnchangedShardFilesAreNotRewritten() {
        val file = File(folder.root, "rules.bin")
        val engine = KotlinFilterEngine(file)
        engine.replace(listOf("||ads.example^", "##.banner"))
        val directory = File(folder.root, "rules.bin.sources")
        val previous = directory.listFiles()!!.toList()
        previous.forEach { assertTrue(it.setLastModified(1000)) }
        val stages = mutableListOf<RuleLoadStage>()
        engine.replace(listOf("||ads.example^", "##.new-banner"), stages::add)
        assertEquals(1, stages.count { it == RuleLoadStage.COMPILING })
        assertEquals(1, stages.count { it == RuleLoadStage.RESTORING })
        assertEquals(1000L, previous.single { it.exists() }.lastModified())
        assertEquals(2, directory.listFiles()!!.size)
        stages.clear()
        engine.replace(listOf("##.new-banner", "||ads.example^"), stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(2, stages.count { it == RuleLoadStage.RESTORING })
    }

    @Test
    fun incrementalMergeRetainsCrossSourceBadfilterAndScriptletExceptions() {
        val file = File(folder.root, "rules.bin")
        val base = "||ads.example^\nsite.example##+js(set, flag, false)"
        val override =
            $$"||ads.example^$badfilter\nsite.example#@#+js(set, flag, false)"
        val engine = KotlinFilterEngine(file)
        engine.replace(listOf(base, "##.banner"))
        engine.replace(listOf(base, override))
        assertFalse(
            engine.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertTrue(engine.scriptlets("https://site.example/").isEmpty())
        val stages = mutableListOf<RuleLoadStage>()
        engine.replace(listOf(override, base), stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertFalse(
            engine.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        engine.replace(listOf(base))
        assertTrue(
            engine.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertEquals(1, engine.scriptlets("https://site.example/").size)
    }

    @Test
    fun aDamagedShardIsRecompiledWithoutDiscardingOtherSources() {
        val file = File(folder.root, "rules.bin")
        val inputs = listOf("||ads.example^", "##.banner")
        KotlinFilterEngine(file).replace(inputs)
        val key = CompiledFilterCache(file).key(listOf(inputs[0]))
        val shard =
            File(
                folder.root,
                "rules.bin.sources/" +
                    key.joinToString("") { "%02x".format(it) } +
                    ".bin",
            )
        shard.writeBytes(byteArrayOf(1, 2))
        file.delete()
        val stages = mutableListOf<RuleLoadStage>()
        KotlinFilterEngine(file).replace(inputs, stages::add)
        assertEquals(1, stages.count { it == RuleLoadStage.COMPILING })
        assertEquals(1, stages.count { it == RuleLoadStage.RESTORING })
        assertNotNull(CompiledFilterCache(shard).read(key))
    }

    @Test
    fun shardProgressStartsBeforeRestoringEvenWhenItsChecksumFails() {
        val cache = FilterSourceCache(File(folder.root, "progress.bin"), 0)
        val sources = listOf(FilterSource("||ads.example^"))
        val entries = cache.load(sources) {}
        cache.save(entries)
        val file = entries.single().file
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val stages = mutableListOf<RuleLoadStage>()
        val rebuilt = cache.load(sources, stages::add)
        assertEquals(
            listOf(RuleLoadStage.RESTORING, RuleLoadStage.COMPILING),
            stages,
        )
        assertFalse(rebuilt.single().restored)
    }

    @Test
    fun interruptedReplacementRestoresBackupAndIgnoresIncompleteNewBytes() {
        val file = File(folder.root, "rules.bin")
        val inputs = listOf("||ads.example^")
        KotlinFilterEngine(file).replace(inputs)
        val valid = file.readBytes()
        File("${file.path}.bak").writeBytes(valid)
        file.writeBytes(byteArrayOf(1))
        File("${file.path}.new").writeBytes(byteArrayOf(2))
        val cache = CompiledFilterCache(file)
        assertNotNull(cache.read(cache.key(inputs)))
        assertTrue(valid.contentEquals(file.readBytes()))
        assertFalse(File("${file.path}.bak").exists())
        file.delete()
        File("${file.path}.bak").writeBytes(valid)
        assertNotNull(cache.read(cache.key(inputs)))
    }

    @Test
    fun remainingRuleBudgetCountsDuplicatesBeforeRestoringOrParsing() {
        val file = File(folder.root, "rules.bin")
        val text = "||ads.example^\n||ads.example^"
        val cache = CompiledFilterCache(file)
        val rules = FilterCompiler.parseSource(FilterSource(text))
        assertEquals(1, rules.networks.size)
        assertEquals(2, rules.totalRules)
        val key = cache.key(listOf(text))
        cache.write(key, rules)
        assertNull(cache.read(key, maxRules = 1))
        assertNotNull(cache.read(key, maxRules = 2))
        assertThrows(IllegalArgumentException::class.java) {
            FilterCompiler.parseSource(FilterSource(text), limit = 1)
        }
    }

    @Test
    fun failedAndOversizedWritesKeepThePreviousFile() {
        val file = File(folder.root, "rules.bin").apply { writeText("old") }
        val storage = RecoverableCacheFile(file)
        try {
            storage.write(100) {
                it.write(1)
                throw IOException("Full disk")
            }
            throw AssertionError("Expected failure")
        } catch (_: IOException) {}
        assertEquals("old", file.readText())
        try {
            storage.write(1) { it.write(byteArrayOf(1, 2)) }
            throw AssertionError("Expected failure")
        } catch (_: IOException) {}
        assertEquals("old", file.readText())
        assertFalse(File("${file.path}.new").exists())
        storage.write(100) { it.write("new".toByteArray()) }
        assertEquals("new", file.readText())
        assertFalse(File("${file.path}.bak").exists())
    }
}
