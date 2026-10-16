package uk.nktnet.webviewkiosk.blocking

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.FilterListValidator
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine

/**
 * Storage/validator boundary tests. Real filter syntax is exercised in
 * KotlinFilterEngineTest.
 */
@RunWith(RobolectricTestRunner::class)
class FilterListValidationTest {
    private lateinit var store: FilterListStore
    private lateinit var directory: File
    private val validated = mutableListOf<String>()
    private var failure: Exception? = null
    private var currentTime = 1_000L

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        directory = File(context.filesDir, "content-blocking-lists")
        directory.deleteRecursively()
        validated.clear()
        failure = null
        store =
            FilterListStore(
                context,
                validator =
                    FilterListValidator { text ->
                        validated += text
                        failure?.let { throw it }
                    },
                now = { currentTime },
            )
    }

    @Test
    fun defaultKotlinValidatorRejectsMalformedReplacementWithoutChangingSavedRules() {
        val realStore = FilterListStore(RuntimeEnvironment.getApplication())
        val item =
            realStore.importFrom(
                "Working",
                "||ads.example^\n||cdn.example^\$future-option".byteInputStream(),
            )
        assertThrows(IllegalArgumentException::class.java) {
            realStore.updateFile(
                item.id,
                "Changed",
                "||ads.example^\$domain=".byteInputStream(),
            )
        }
        assertEquals(listOf(item), realStore.list())
        val engine =
            KotlinFilterEngine().also { it.replace(realStore.enabledTexts()) }
        assertTrue(
            engine.shouldBlock(
                "https://ads.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://cdn.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertEquals(1, engine.unsupportedRuleCount())
    }

    @Test
    fun validationReceivesTheEntireMixedFormatUtf8FileUnchanged() {
        val text =
            "\uFEFF[Adblock Plus 2.0]\r\n! comment\r\n0.0.0.0 ads.example\r\n||例子.测试^\r\n"
        store.importFrom("mixed.txt", text.byteInputStream())
        assertEquals(listOf(text), validated)
        assertEquals(listOf(text), store.enabledTexts())
    }

    @Test
    fun malformedImportReportsParserLinesAndLeavesNoFilesOrMetadata() {
        failure =
            IllegalArgumentException(
                "Invalid filter syntax:\nLine 3: empty redirection"
            )
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                store.importFrom(
                    "bad.txt",
                    "! header\n\n||bad.example^\$redirect=".byteInputStream(),
                )
            }
        assertEquals(failure!!.message, error.message)
        assertEquals(1, validated.size)
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun zeroLengthBinaryAndInvalidUtf8FilesNeverReachTheSyntaxParser() {
        for (bytes in
            listOf(
                byteArrayOf(),
                byteArrayOf(0),
                byteArrayOf(0xc3.toByte(), 0x28),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                store.importFrom("invalid.txt", bytes.inputStream())
            }
        }
        assertTrue(validated.isEmpty())
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun whitespaceAndCommentOnlyErrorsArePreserved() {
        failure =
            IllegalArgumentException(
                "Filter list has no rules (only comments or blank lines)"
            )
        for (text in listOf(" \n\t", "! comment\n# comment\n")) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    store.importFrom("empty.txt", text.byteInputStream())
                }
            assertEquals(failure!!.message, error.message)
        }
        assertEquals(2, validated.size)
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun invalidReplacementPreservesIdentityNameEnablementAndRuleBytes() {
        val text = "||working.example^\n"
        val item = store.importFrom("Working.txt", text.byteInputStream())
        store.setEnabled(item.id, false)
        val original = store.list().single()
        failure =
            IllegalArgumentException(
                "Invalid filter syntax:\nLine 1: empty redirection"
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.updateFile(
                item.id,
                "Changed name",
                "||bad.example^\$redirect=".byteInputStream(),
            )
        }
        assertEquals(original, store.list().single())
        assertEquals(
            original,
            FilterListStore(RuntimeEnvironment.getApplication()).list().single(),
        )
        assertEquals(text, store.editableFileText(item.id))
        assertEquals(
            listOf("${item.id}.txt"),
            directory.listFiles().orEmpty().map { it.name },
        )
        assertFalse(store.list().single().enabled)
    }

    @Test
    fun successfulReplacementIsValidatedBeforeSavingItsNameAndContents() {
        val item = store.importFrom("Original", "||old.example^".byteInputStream())
        val replacement = "0.0.0.0 new.example\n@@||allowed.example^\n"
        val updatingStore =
            FilterListStore(
                RuntimeEnvironment.getApplication(),
                validator =
                    FilterListValidator { text ->
                        // Inspect the saved state while validation is actually
                        // running, before
                        // the replacement (including its new name) has been
                        // committed.
                        assertEquals(listOf(item), store.list())
                        assertEquals(listOf("||old.example^"), store.enabledTexts())
                        validated += text
                    },
            )
        val updated =
            updatingStore.updateFile(
                item.id,
                "Updated",
                replacement.byteInputStream(),
            )
        assertEquals(listOf("||old.example^", replacement), validated)
        assertEquals(item.copy(name = "Updated"), updated)
        assertEquals(listOf(replacement), store.enabledTexts())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun fileCloneValidationAndFailedInlineEditKeepOriginalAndDisabledCopyIntact() {
        val item =
            store.importFrom("Original", "||original.example^".byteInputStream())
        val copy = store.cloneList(item.id)
        assertEquals(listOf("||original.example^", "||original.example^"), validated)
        assertFalse(copy.enabled)
        failure =
            IllegalArgumentException(
                "Invalid filter syntax:\nLine 1: empty redirection"
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.updateFile(
                copy.id,
                "Changed",
                "||bad.example^\$redirect=".byteInputStream(),
            )
        }
        assertEquals(listOf(item, copy), store.list())
        assertEquals("||original.example^", store.editableFileText(copy.id))
        assertEquals(listOf("||original.example^"), store.enabledTexts())
        assertEquals(2, directory.listFiles().orEmpty().size)
    }

    @Test
    fun urlCloneIsRejectedBeforeReadingOrValidatingAnotherCopy() {
        val item =
            store.importFrom(
                "Remote",
                "||working.example^".byteInputStream(),
                "https://example.org/rules.txt",
            )
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                store.cloneList(item.id)
            }
        assertEquals("URL lists cannot be cloned", error.message)
        assertEquals(listOf("||working.example^"), validated)
        assertEquals(listOf(item), store.list())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun presetCloneIsRejectedBeforeReadingOrValidatingAnotherCopy() {
        val item =
            store.importFrom(
                "Preset",
                "||working.example^".byteInputStream(),
                SuggestedFilterLists.all.first().url,
                FilterListType.PRESET,
            )
        // A missing cached file must not obscure the clone restriction or invoke
        // validation.
        File(directory, "${item.id}.txt").delete()
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                store.cloneList(item.id)
            }
        assertEquals("Preset lists cannot be cloned", error.message)
        assertEquals(listOf("||working.example^"), validated)
        assertEquals(listOf(item), store.list())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun invalidLegacyFileCannotBeClonedAndOriginalIsRetained() {
        val item = store.importFrom("Legacy", "||old.example^".byteInputStream())
        val invalid = "||bad.example^\$redirect="
        File(directory, "${item.id}.txt").writeText(invalid)
        failure =
            IllegalArgumentException(
                "Invalid filter syntax:\nLine 1: empty redirection"
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.cloneList(item.id)
        }
        assertEquals(invalid, validated.last())
        assertEquals(listOf(item), store.list())
        assertEquals(invalid, store.editableFileText(item.id))
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun validatorFailureCannotOverwriteAWorkingList() {
        val item =
            store.importFrom("Working", "||working.example^".byteInputStream())
        failure =
            IllegalStateException(
                "Filter syntax validation is unavailable; no changes were saved"
            )
        assertThrows(IllegalStateException::class.java) {
            store.updateFile(item.id, "Changed", "||new.example^".byteInputStream())
        }
        assertEquals(listOf(item), store.list())
        assertEquals(listOf("||working.example^"), store.enabledTexts())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun invalidUrlEditAndRefreshKeepOldSourceMetadataAndRulesTogether() {
        val item =
            store.importFrom(
                "URL",
                "||old.example^".byteInputStream(),
                "https://example.org/old.txt",
            )
        failure =
            IllegalArgumentException(
                "Invalid filter syntax:\nLine 1: empty redirection"
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.updateUrl(
                item.id,
                item.sourceUrl!!,
                "https://example.org/new.txt",
                "Changed",
                "||bad.example^\$redirect=".byteInputStream(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceFromSource(
                item.id,
                item.sourceUrl!!,
                "||bad.example^\$redirect=".byteInputStream(),
            )
        }
        assertEquals(listOf(item), store.list())
        assertEquals(listOf("||old.example^"), store.enabledTexts())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun largeFilesUseOneValidationCallAndKeepOriginalBytes() {
        val text = "||ads.example^\n".repeat(100_000)
        store.importFrom("Large", text.byteInputStream())
        assertEquals(1, validated.size)
        assertEquals(text, validated.single())
        assertEquals(listOf(text), store.enabledTexts())
    }

    @Test
    fun metadataOnlyEditsDoNotRevalidateExistingRules() {
        val item = store.importFrom("Original", "||old.example^".byteInputStream())
        failure = IllegalStateException("Validation unavailable")
        store.updateFile(item.id, "Renamed")
        store.setEnabled(item.id, false)
        assertEquals(1, validated.size)
        assertEquals("Renamed", store.list().single().name)
        assertFalse(store.list().single().enabled)
    }

    @Test
    fun allFileContentsReachTheSameValidatorRegardlessOfFilenameOrDocumentShape() {
        // This injected validator accepts the candidates. The storage layer must
        // defer rule syntax decisions to it, without classifying document formats.
        val candidates =
            listOf(
                "filters.json" to "||ads.example^",
                "filters.xml" to "0.0.0.0 ads.example",
                "settings.txt" to """{"setting":true}""",
                "settings.json" to """{"setting":true}""",
                "array.rules" to """["||ads.example^"]""",
                "document.html" to "<rule>ads.example</rule>",
                "document.csv" to "ad,tracking",
                "document.yaml" to "ad: true",
            )
        for ((name, text) in candidates) {
            val item = store.importFrom(name, text.byteInputStream())
            assertEquals(text, validated.last())
            assertEquals(text, store.editableFileText(item.id))
        }
        assertEquals(candidates.map { it.second }, validated)
        assertEquals(candidates.size, store.list().size)
    }

    @Test
    fun validatorErrorsArePreservedRegardlessOfFilenameOrDocumentShape() {
        failure =
            IllegalArgumentException("Invalid filter syntax:\nLine 1: parser error")
        val candidates =
            listOf(
                "filters.txt" to "||bad.example^\$redirect=",
                "settings.json" to "{}",
                "settings.txt" to "{}",
                "document.xml" to "<rules/>",
                "document.csv" to "ad,tracking",
            )
        for ((name, text) in candidates) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    store.importFrom(name, text.byteInputStream())
                }
            assertEquals(failure!!.message, error.message)
        }
        assertEquals(candidates.map { it.second }, validated)
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun replacementValidatesCompleteContentsWithoutDocumentFormatDetection() {
        val item =
            store.importFrom("Working", "||working.example^".byteInputStream())
        store.setEnabled(item.id, false)
        val replacement = """["||new.example^"]"""
        val updated =
            store.updateFile(item.id, "Changed", replacement.byteInputStream())
        assertEquals(item.copy(name = "Changed", enabled = false), updated)
        assertEquals(listOf(updated), store.list())
        assertEquals(replacement, store.editableFileText(item.id))
        assertEquals(listOf("||working.example^", replacement), validated)
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun editedCloneUsesTheSameValidatorAndRemainsDisabled() {
        val item =
            store.importFrom("Original", "||working.example^".byteInputStream())
        val copy = store.cloneList(item.id)
        val updated = store.updateFile(copy.id, "Changed", "{}".byteInputStream())
        assertEquals(copy.copy(name = "Changed"), updated)
        assertEquals(listOf(item, updated), store.list())
        assertFalse(store.list().last().enabled)
        assertEquals("{}", store.editableFileText(copy.id))
        assertEquals(
            listOf("||working.example^", "||working.example^", "{}"),
            validated,
        )
        assertEquals(listOf("||working.example^"), store.enabledTexts())
        assertEquals(2, directory.listFiles().orEmpty().size)
    }

    @Test
    fun legacyContentsAreRevalidatedByTheSameValidatorWhenCloned() {
        val item = store.importFrom("Legacy", "||working.example^".byteInputStream())
        val text = """{"setting":true}"""
        File(directory, "${item.id}.txt").writeText(text)
        val copy = store.cloneList(item.id)
        assertEquals(listOf("||working.example^", text), validated)
        assertEquals(listOf(item, copy), store.list())
        assertEquals(text, store.editableFileText(item.id))
        assertEquals(text, store.editableFileText(copy.id))
        assertFalse(copy.enabled)
        assertEquals(2, directory.listFiles().orEmpty().size)
    }

    @Test
    fun urlEditsValidateTheSameContentsWithoutDocumentFormatDetection() {
        val item =
            store.importFrom(
                "URL",
                "||working.example^".byteInputStream(),
                "https://example.org/old.txt",
            )
        currentTime = 2_000L
        val updated =
            store.updateUrl(
                item.id,
                item.sourceUrl!!,
                "https://example.org/new.txt",
                "Changed",
                "{}".byteInputStream(),
            )
        assertEquals(
            item.copy(
                name = "Changed",
                sourceUrl = "https://example.org/new.txt",
                lastRefreshedAt = 2_000L,
            ),
            updated,
        )
        assertEquals(listOf(updated), store.list())
        assertEquals(listOf("||working.example^", "{}"), validated)
        assertEquals(listOf("{}"), store.enabledTexts())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun invalidControlCharactersCannotBeImportedAsRules() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                store.importFrom(
                    "unrelated.txt",
                    "||ads.example^\ninvalid\u001Bcontent".byteInputStream(),
                )
            }
        assertTrue(error.message!!.contains("Line 2:"))
        assertTrue(validated.isEmpty())
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun mixedValidAndInvalidContentCannotPartiallyReplaceAWorkingList() {
        val item =
            store.importFrom("Working", "||working.example^".byteInputStream())
        assertThrows(IllegalArgumentException::class.java) {
            store.updateFile(
                item.id,
                "Changed",
                "||new.example^\ninvalid\u0085content".byteInputStream(),
            )
        }
        assertEquals(listOf(item), store.list())
        assertEquals(listOf("||working.example^"), store.enabledTexts())
        assertEquals(listOf("||working.example^"), validated)
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun unsupportedOnlyImportsAreRejectedWithADistinctExplanation() {
        failure =
            IllegalArgumentException(
                "Filter list has no supported network rules. " +
                    "Unsupported rules: 1. No changes were saved."
            )
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                store.importFrom(
                    "cosmetics.txt",
                    "example.com##.ad".byteInputStream(),
                )
            }
        assertTrue(error.message!!.contains("no supported network rules"))
        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun unsupportedOnlyReplacementKeepsTheWorkingList() {
        val item =
            store.importFrom("Working", "||working.example^".byteInputStream())
        failure =
            IllegalArgumentException(
                "Filter list has no supported network rules. " +
                    "Unsupported rules: 1. No changes were saved."
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.updateFile(
                item.id,
                "Changed",
                "example.com##.ad".byteInputStream(),
            )
        }
        assertEquals(listOf(item), store.list())
        assertEquals(listOf("||working.example^"), store.enabledTexts())
        assertEquals(1, directory.listFiles().orEmpty().size)
    }
}
