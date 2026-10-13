package uk.nktnet.webviewkiosk.blocking

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class ImportedFilterList(
    val id: String,
    val name: String,
    val enabled: Boolean,
    /** The original HTTPS download URL, if this list was imported from a link. */
    val sourceUrl: String? = null,
)

/** Stores only user-imported plain-text lists in private app storage. No built-in rules. */
internal class FilterListStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "content_blocking_lists", Context.MODE_PRIVATE,
    )
    private val directory = File(context.applicationContext.filesDir, "content-blocking-lists")

    @Synchronized
    fun list(): List<ImportedFilterList> {
        val json = preferences.getString(INDEX_KEY, null) ?: return emptyList()
        // A damaged index must never crash Settings or activate unknown rules.
        val records = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return (0 until records.length()).mapNotNull { index ->
            val entry = records.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id")
            if (!UUID_PATTERN.matches(id)) return@mapNotNull null
            ImportedFilterList(
                id = id,
                name = entry.optString("name").take(MAX_NAME_LENGTH),
                enabled = entry.optBoolean("enabled", true),
                sourceUrl = entry.optString("sourceUrl").takeIf { it.isNotBlank() },
            )
        }
    }

    /** Called on Dispatchers.IO, never from the Compose or request-interception thread. */
    @Synchronized
    fun containsSource(sourceUrl: String): Boolean = list().any { it.sourceUrl == sourceUrl }

    @Synchronized
    fun importFrom(
        name: String,
        input: InputStream,
        sourceUrl: String? = null,
    ): ImportedFilterList {
        val existing = list()
        require(sourceUrl == null || existing.none { it.sourceUrl == sourceUrl }) {
            "This filter list has already been downloaded"
        }
        require(existing.size < MAX_LISTS) { "At most $MAX_LISTS filter lists are supported" }
        val currentBytes = existing.sumOf { fileFor(it.id).length() }
        require(currentBytes < MAX_TOTAL_BYTES) { "Filter-list storage is full" }
        val temporary = writeValidatedTempFile(input, currentBytes)
        try {
            val item = ImportedFilterList(
                id = UUID.randomUUID().toString(),
                name = name.substringAfterLast('/').substringAfterLast('\\')
                    .ifBlank { "Imported filter list" }.take(MAX_NAME_LENGTH),
                enabled = true,
                sourceUrl = sourceUrl,
            )
            val destination = fileFor(item.id)
            if (!temporary.renameTo(destination)) {
                throw IOException("Failed to save imported filter list")
            }
            try {
                save(existing + item)
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
            return item
        } finally {
            temporary.delete()
        }
    }

    /** Renaming is metadata-only: it must not cause recompilation or modify the source URL. */
    @Synchronized
    fun rename(id: String, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "List name cannot be empty" }
        require(normalized.length <= MAX_NAME_LENGTH) { "List name must be at most $MAX_NAME_LENGTH characters" }
        require(normalized.none { it.isISOControl() }) { "List name contains invalid characters" }
        val existing = list()
        require(existing.any { it.id == id }) { "Unknown filter list" }
        save(existing.map { if (it.id == id) it.copy(name = normalized) else it })
    }

    /** Replace a downloaded list in-place; failed downloads cannot damage existing rules. */
    @Synchronized
    fun replaceFromSource(id: String, sourceUrl: String, input: InputStream): ImportedFilterList {
        val existing = list()
        val item = existing.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.sourceUrl != null && item.sourceUrl == sourceUrl) {
            "This list does not have the specified download URL"
        }
        val destination = fileFor(id)
        if (!destination.isFile) throw IOException("Original filter list is missing")
        // Exclude the old copy from the quota so a full store can still refresh a list.
        val otherBytes = existing.filterNot { it.id == id }.sumOf { fileFor(it.id).length() }
        val temporary = writeValidatedTempFile(input, otherBytes)
        try {
            // Same-directory rename replaces the old file atomically on Android filesystems.
            // Existing metadata (including name, ID and enabled state) is untouched.
            if (!temporary.renameTo(destination)) throw IOException("Failed to update filter list")
            return item
        } finally {
            temporary.delete()
        }
    }

    /** Shared import/refresh validation, writing new bytes before changing the active file. */
    private fun writeValidatedTempFile(input: InputStream, otherBytes: Long): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create filter-list directory")
        }
        val temporary = File.createTempFile("import-", ".tmp", directory)
        try {
            var written = 0L
            input.use { source ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= MAX_LIST_BYTES) { "Filter list exceeds 8 MiB" }
                        require(otherBytes + written <= MAX_TOTAL_BYTES) {
                            "Total filter lists exceed 32 MiB"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(written > 0L) { "Filter list is empty" }
            // Reject malformed text/binary files rather than silently loading bad UTF-8.
            val data = temporary.readBytes()
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
            } catch (_: CharacterCodingException) {
                throw IllegalArgumentException("Filter list must be UTF-8 text")
            }
            require(!data.contains(0)) { "Filter list must be plain text" }
            return temporary
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val existing = list()
        require(existing.any { it.id == id }) { "Unknown filter list" }
        save(existing.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun remove(id: String) {
        val existing = list()
        require(existing.any { it.id == id }) { "Unknown filter list" }
        save(existing.filterNot { it.id == id })
        fileFor(id).delete()
    }

    @Synchronized
    fun enabledTexts(): List<String> = list().filter { it.enabled }.map { item ->
        val file = fileFor(item.id)
        if (!file.isFile || file.length() > MAX_LIST_BYTES) {
            throw IOException("Filter list ${item.name} is missing or too large")
        }
        file.readText(Charsets.UTF_8)
    }

    private fun fileFor(id: String): File {
        require(UUID_PATTERN.matches(id)) { "Invalid list ID" }
        return File(directory, "$id.txt")
    }

    private fun save(lists: List<ImportedFilterList>) {
        val array = JSONArray()
        lists.forEach { list ->
            val entry = JSONObject().put("id", list.id).put("name", list.name)
                .put("enabled", list.enabled)
            list.sourceUrl?.let { entry.put("sourceUrl", it) }
            array.put(entry)
        }
        if (!preferences.edit().putString(INDEX_KEY, array.toString()).commit()) {
            throw IOException("Cannot persist filter-list settings")
        }
    }

    companion object {
        private const val INDEX_KEY = "imports"
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_LISTS = 16
        internal const val MAX_LIST_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        private val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }
}
