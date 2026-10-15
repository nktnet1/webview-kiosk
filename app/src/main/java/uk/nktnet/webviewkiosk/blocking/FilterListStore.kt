package uk.nktnet.webviewkiosk.blocking

import android.content.Context
import android.annotation.SuppressLint
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
import uk.nktnet.webviewkiosk.blocking.engine.FilterParser
import uk.nktnet.webviewkiosk.blocking.engine.ParsedFilter

internal enum class FilterListType { FILE, URL, PRESET }

internal data class ImportedFilterList(
    val id: String,
    val name: String,
    val enabled: Boolean,
    /** The original HTTPS download URL, if this list was imported from a link. */
    val sourceUrl: String? = null,
    val type: FilterListType = if (sourceUrl == null) FilterListType.FILE else FilterListType.URL,
    /** Last successful source download, including the initial import; absent for local files. */
    val lastRefreshedAt: Long? = null,
)

/** Counts saved entries before deduplication and cross-list rule precedence. */
internal data class FilterListInfo(
    val item: ImportedFilterList,
    val sizeBytes: Long,
    val networkRules: Int,
    val cosmeticRules: Int,
    val unsupportedRules: Int,
    val invalidRules: Int,
) {
    val skippedRules: Int get() = unsupportedRules + invalidRules
    val totalRules: Int get() = networkRules + cosmeticRules + skippedRules
}

/** Stores only user-imported plain-text lists in private app storage. No built-in rules. */
internal class FilterListStore(
    context: Context,
    private val validator: FilterListValidator = KotlinFilterEngine(),
    private val now: () -> Long = System::currentTimeMillis,
) {
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
                type = readType(entry),
                lastRefreshedAt = entry.optLong("lastRefreshedAt", 0).takeIf { it > 0 },
            )
        }
    }

    private fun readType(entry: JSONObject): FilterListType {
        val url = entry.optString("sourceUrl").takeIf { it.isNotBlank() }
        val saved = runCatching { FilterListType.valueOf(entry.optString("type")) }.getOrNull()
        // Existing installations never stored a type. Treat matching presets as presets,
        // and all other downloads as editable URL lists; local files remain local.
        return saved ?: when {
            url == null -> FilterListType.FILE
            SuggestedFilterLists.all.any { it.url == url } -> FilterListType.PRESET
            else -> FilterListType.URL
        }
    }

    /** Called on Dispatchers.IO, never from Compose or the request-interception thread. */
    @Synchronized
    fun containsSource(sourceUrl: String): Boolean = list().any { it.sourceUrl == sourceUrl }

    @Synchronized
    fun containsPreset(sourceUrl: String): Boolean = list().any {
        it.type == FilterListType.PRESET && it.sourceUrl == sourceUrl
    }

    @Synchronized
    fun containsUrl(sourceUrl: String): Boolean = list().any {
        it.type == FilterListType.URL && it.sourceUrl == sourceUrl
    }

    @Synchronized
    fun importFrom(
        name: String,
        input: InputStream,
        sourceUrl: String? = null,
        type: FilterListType = if (sourceUrl == null) FilterListType.FILE else FilterListType.URL,
    ): ImportedFilterList = importNew(name, input, sourceUrl, type, enabled = true)

    private fun importNew(
        name: String,
        input: InputStream,
        sourceUrl: String?,
        type: FilterListType,
        enabled: Boolean,
    ): ImportedFilterList {
        require((type == FilterListType.FILE) == (sourceUrl == null)) {
            "A file has no download URL; a URL or preset needs one"
        }
        require(type != FilterListType.PRESET || SuggestedFilterLists.all.any { it.url == sourceUrl }) {
            "Unknown preset filter list"
        }
        val existing = list()
        require(type != FilterListType.PRESET || existing.none {
            it.type == FilterListType.PRESET && it.sourceUrl == sourceUrl
        }) {
            "This preset has already been added"
        }
        require(type != FilterListType.URL || existing.none {
            it.type == FilterListType.URL && it.sourceUrl == sourceUrl
        }) {
            "A URL list with this address already exists"
        }
        require(existing.size < MAX_LISTS) { "At most $MAX_LISTS filter lists are supported" }
        val currentBytes = existing.sumOf { fileFor(it.id).length() }
        require(currentBytes < MAX_TOTAL_BYTES) { "Filter-list storage is full" }
        val temporary = writeValidatedTempFile(input, currentBytes)
        try {
            val item = ImportedFilterList(
                id = UUID.randomUUID().toString(),
                name = validateName(name),
                enabled = enabled,
                sourceUrl = sourceUrl,
                type = type,
                lastRefreshedAt = if (sourceUrl != null) now() else null,
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

    private fun validateName(name: String): String = name.trim().also { normalized ->
        require(normalized.isNotEmpty()) { "List name cannot be empty" }
        require(normalized.length <= MAX_NAME_LENGTH) { "List name must be at most $MAX_NAME_LENGTH characters" }
        require(normalized.none { it.isISOControl() }) { "List name contains invalid characters" }
    }

    /** Preset definitions are immutable. */
    @Synchronized
    fun rename(id: String, name: String) {
        val existing = list()
        val item = existing.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.type != FilterListType.PRESET) { "Preset lists are read-only" }
        val normalized = validateName(name)
        save(existing.map { if (it.id == id) it.copy(name = normalized) else it })
    }

    /** A clone copies the cached rules without network access and starts disabled. */
    @Synchronized
    fun cloneList(id: String, name: String? = null): ImportedFilterList {
        val existing = list()
        val item = existing.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.type == FilterListType.FILE) { "${if (item.type == FilterListType.URL) "URL" else "Preset"} lists cannot be cloned" }
        val file = fileFor(id)
        if (!file.isFile) throw IOException("Original filter list is missing")
        val proposedName = name ?: "${item.name.removeSuffix(".txt")} (copy)".take(MAX_NAME_LENGTH)
        return file.inputStream().use { input ->
            importNew(
                proposedName, input, null, FilterListType.FILE,
                enabled = false,
            )
        }
    }

    /** Read small local lists for inline editing; large lists can be replaced from a file. */
    @Synchronized
    fun editableFileText(id: String): String? {
        val item = list().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.type == FilterListType.FILE) { "Only file lists have editable text" }
        val file = fileFor(id)
        if (!file.isFile) throw IOException("Original filter list is missing")
        return if (file.length() > MAX_INLINE_EDIT_BYTES) null else file.readText(Charsets.UTF_8)
    }

    /** File edits keep their ID and activation state, including name-only edits. */
    @Synchronized
    fun updateFile(id: String, name: String, input: InputStream? = null): ImportedFilterList {
        val existing = list()
        val item = existing.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.type == FilterListType.FILE) { "This is not an editable file list" }
        val updated = item.copy(name = validateName(name))
        if (input == null) {
            save(existing.map { if (it.id == id) updated else it })
            return updated
        }
        return replaceContent(existing, item, updated, input)
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
        return replaceContent(existing, item, item, input)
    }

    /** Apply a changed URL and its downloaded rules as one edit, not two independent saves. */
    @Synchronized
    fun updateUrl(id: String, originalUrl: String, url: String, name: String, input: InputStream): ImportedFilterList {
        val existing = list()
        val item = existing.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        require(item.type == FilterListType.URL && item.sourceUrl == originalUrl) {
            "This is not an editable URL list"
        }
        require(url == originalUrl || existing.none {
            it.id != id && it.type == FilterListType.URL && it.sourceUrl == url
        }) {
            "A URL list with this address already exists"
        }
        return replaceContent(existing, item, item.copy(name = validateName(name), sourceUrl = url), input)
    }

    private fun replaceContent(
        existing: List<ImportedFilterList>,
        item: ImportedFilterList,
        updated: ImportedFilterList,
        input: InputStream,
    ): ImportedFilterList {
        val destination = fileFor(item.id)
        if (!destination.isFile) throw IOException("Original filter list is missing")
        // Exclude the old copy from the quota so full stores can still update a list.
        val otherBytes = existing.filterNot { it.id == item.id }.sumOf { fileFor(it.id).length() }
        val temporary = writeValidatedTempFile(input, otherBytes)
        val refreshed = if (updated.sourceUrl != null) updated.copy(lastRefreshedAt = now()) else updated
        val backup = File.createTempFile("backup-", ".tmp", directory)
        backup.delete()
        try {
            if (!destination.renameTo(backup)) throw IOException("Failed to back up filter list")
            try {
                if (!temporary.renameTo(destination)) throw IOException("Failed to update filter list")
                if (item != refreshed) save(existing.map { if (it.id == item.id) refreshed else it })
            } catch (e: Exception) {
                destination.delete()
                if (!backup.renameTo(destination)) {
                    e.addSuppressed(IOException("Unable to restore original list; backup retained at ${backup.name}"))
                }
                throw e
            }
            backup.delete()
            return refreshed
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
            val text = try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString()
            } catch (_: CharacterCodingException) {
                throw IllegalArgumentException("Filter list must be UTF-8 text")
            }
            require(!data.contains(0)) { "Filter list must be plain text" }
            validateFilterFileFormat(text)
            // Imports, edits, replacements, downloads, and clones all pass this gate
            // before either the index or an existing working file is touched.
            validator.validate(text)
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

    /** Persist one bulk change; callers queue one engine reload after it commits. */
    @Synchronized
    fun setAllEnabled(enabled: Boolean) {
        val existing = list()
        if (existing.any { it.enabled != enabled }) {
            save(existing.map { it.copy(enabled = enabled) })
        }
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

    /** Inspect one saved list on IO without compiling or replacing the active engine. */
    @Synchronized
    fun info(id: String): FilterListInfo {
        val (item, file) = infoFile(id)
        val size = file.length()
        var network = 0
        var cosmetic = 0
        var unsupported = 0
        var invalid = 0
        FilterParser.visit(file.readText(Charsets.UTF_8)) { _, rule ->
            when (rule) {
                is ParsedFilter.Network -> network++
                is ParsedFilter.Cosmetic -> cosmetic++
                ParsedFilter.Unsupported -> unsupported++
                is ParsedFilter.Invalid -> invalid++
            }
        }
        return FilterListInfo(item, size, network, cosmetic, unsupported, invalid)
    }

    /** Available before the rule scan; does not read or parse the file contents. */
    @Synchronized
    fun fileSizeBytes(id: String): Long = infoFile(id).second.length()

    private fun infoFile(id: String): Pair<ImportedFilterList, File> {
        val item = list().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown filter list")
        val file = fileFor(id)
        if (!file.isFile || file.length() > MAX_LIST_BYTES) {
            throw IOException("Filter list ${item.name} is missing or too large")
        }
        return item to file
    }

    private fun fileFor(id: String): File {
        require(UUID_PATTERN.matches(id)) { "Invalid list ID" }
        return File(directory, "$id.txt")
    }

    @SuppressLint("UseKtx") // KTX edit discards commit()'s result; failed saves must be reported.
    private fun save(lists: List<ImportedFilterList>) {
        val array = JSONArray()
        lists.forEach { list ->
            val entry = JSONObject().put("id", list.id).put("name", list.name)
                .put("enabled", list.enabled).put("type", list.type.name)
            list.sourceUrl?.let { entry.put("sourceUrl", it) }
            list.lastRefreshedAt?.let { entry.put("lastRefreshedAt", it) }
            array.put(entry)
        }
        if (!preferences.edit().putString(INDEX_KEY, array.toString()).commit()) {
            throw IOException("Cannot persist filter-list settings")
        }
    }

    companion object {
        private const val INDEX_KEY = "imports"
        private const val MAX_NAME_LENGTH = 100
        internal const val MAX_INLINE_EDIT_BYTES = 128L * 1024
        private const val MAX_LISTS = 16
        internal const val MAX_LIST_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        private val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }
}
