package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.VocabularyEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persistent vocabulary notebook with a canonical JSON store and a regenerated TXT notebook.
 *
 * Persistence model (addendum §6.D):
 *  - The canonical store is `<sanitized title>_Vocabulary.json` next to the TXT file.
 *  - Every mutation writes the JSON snapshot first, then regenerates the human-readable
 *    `<sanitized title>_Vocabulary.txt` from that same snapshot. Share/Copy/Preview read
 *    the TXT, which therefore can never be staler than the saved data.
 *  - Legacy notebooks that only have a TXT file are parsed once and migrated to JSON.
 *
 * Merge policy for repeated lookups (addendum §6.G, §21):
 *  - New non-empty field values replace the previous ones; empty/failed fields never clear
 *    previously saved values.
 *  - The set of requested fields is accumulated across lookups (a once-requested field stays
 *    tracked and is shown as "Not provided by this dictionary" until a lookup fills it).
 *  - Duplicate dictionary sections are never appended; each word has exactly one note block.
 */
object VocabularyManager {

    private const val JSON_VERSION = 2
    private const val UNAVAILABLE_PREFIX = "Unavailable — "
    private const val NOT_PROVIDED = "Not provided by this dictionary"
    private const val NO_LOOKUPS_MESSAGE =
        "[No dictionary lookups yet. Use 'Dictionary Lookup' in Edit Vocabulary.]"

    /** Canonical order of requestable dictionary fields. */
    private val knownFieldLabels = listOf("Meaning", "Synonyms", "Antonyms", "Example Sentence")

    /** TXT prefix used for each canonical field label (spec §6.I uses "Example:"). */
    private fun txtPrefixFor(canonicalLabel: String): String =
        if (canonicalLabel == "Example Sentence") "Example" else canonicalLabel

    // In-memory cache keyed by documentId, preserving chronological insertion order
    private val memoryStore = mutableMapOf<String, MutableList<VocabularyEntry>>()

    // Retained orphaned notes when word entries are deleted but user chose to keep notes
    private val orphanedNotes = mutableMapOf<String, MutableList<VocabularyEntry>>()

    private fun getVocabDir(context: Context): File {
        return File(context.filesDir, "vocabulary").apply { mkdirs() }
    }

    fun sanitizeFilename(title: String): String {
        val clean = title.replace(Regex("[^a-zA-Z0-9_ -]"), "").trim().replace(" ", "_")
        return if (clean.isBlank()) "Book" else clean
    }

    fun getVocabularyFile(context: Context, documentId: String, bookTitle: String): File {
        val sanitized = sanitizeFilename(bookTitle)
        return File(getVocabDir(context), "${sanitized}_Vocabulary.txt")
    }

    fun getJsonFile(context: Context, documentId: String, bookTitle: String): File {
        val sanitized = sanitizeFilename(bookTitle)
        return File(getVocabDir(context), "${sanitized}_Vocabulary.json")
    }

    /** Simulates an app restart for tests: drops every in-memory cache. */
    fun clearCacheForTests() {
        memoryStore.clear()
        orphanedNotes.clear()
    }

    @Synchronized
    fun getEntries(context: Context, documentId: String, bookTitle: String): List<VocabularyEntry> {
        return ensureLoaded(context, documentId, bookTitle).toList()
    }

    /**
     * Ensures the in-memory store for [documentId] exists, hydrating it from the canonical
     * JSON store first, then from a legacy TXT notebook (which is migrated to JSON).
     */
    private fun ensureLoaded(
        context: Context,
        documentId: String,
        bookTitle: String
    ): MutableList<VocabularyEntry> {
        memoryStore[documentId]?.let { return it }

        val jsonFile = getJsonFile(context, documentId, bookTitle)
        if (jsonFile.exists()) {
            val snapshot = runCatching { parseFromJsonFile(jsonFile, documentId) }.getOrNull()
            if (snapshot != null) {
                memoryStore[documentId] = snapshot.entries
                orphanedNotes[documentId] = snapshot.orphans
                return snapshot.entries
            }
        }

        val txtFile = getVocabularyFile(context, documentId, bookTitle)
        val loaded: MutableList<VocabularyEntry>
        if (txtFile.exists()) {
            val snapshot = parseFromTxtFile(txtFile, documentId)
            loaded = snapshot.entries
            orphanedNotes[documentId] = snapshot.orphans
        } else {
            loaded = mutableListOf()
        }

        memoryStore[documentId] = loaded
        // Migrate legacy/no JSON state to the canonical store immediately
        saveAll(context, documentId, bookTitle, loaded)
        return loaded
    }

    @Synchronized
    fun addWord(
        context: Context,
        documentId: String,
        bookTitle: String,
        word: String,
        pageNumber: Int,
        contextSentence: String = ""
    ): Pair<VocabularyEntry, Boolean> { // returns (existing entry or new entry, isDuplicate)
        val list = ensureLoaded(context, documentId, bookTitle)
        val cleanWord = word.trim()
        val normalized = cleanWord.lowercase()

        // Honest duplicate handling: never insert a second entry for the same word
        val existing = list.find { it.normalizedWord == normalized }
        if (existing != null) {
            return Pair(existing, true)
        }

        val entry = VocabularyEntry(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            word = cleanWord,
            normalizedWord = normalized,
            pageNumber = pageNumber,
            contextSentence = contextSentence,
            timestamp = System.currentTimeMillis()
        )

        list.add(entry)
        saveAll(context, documentId, bookTitle, list)
        return Pair(entry, false)
    }

    @Synchronized
    fun editWord(
        context: Context,
        documentId: String,
        bookTitle: String,
        entryId: String,
        newWord: String
    ): VocabularyEntry? {
        val list = ensureLoaded(context, documentId, bookTitle)
        val idx = list.indexOfFirst { it.id == entryId }
        if (idx == -1) return null

        val old = list[idx]
        val updated = old.copy(
            word = newWord.trim(),
            normalizedWord = newWord.trim().lowercase()
        )
        list[idx] = updated
        saveAll(context, documentId, bookTitle, list)
        return updated
    }

    @Synchronized
    fun deleteEntries(
        context: Context,
        documentId: String,
        bookTitle: String,
        entryIds: Set<String>,
        retainNotes: Boolean = false
    ): List<VocabularyEntry> {
        val list = ensureLoaded(context, documentId, bookTitle)
        val toRemove = list.filter { entryIds.contains(it.id) }
        if (retainNotes) {
            val orphans = orphanedNotes.getOrPut(documentId) { mutableListOf() }
            orphans.addAll(toRemove.filter { !it.meaning.isNullOrBlank() || it.synonyms.isNotEmpty() })
        }
        list.removeAll { entryIds.contains(it.id) }
        saveAll(context, documentId, bookTitle, list)
        return list.toList()
    }

    /**
     * Attaches a real provider result to the stable entry [entryId].
     *
     * - Merges field-by-field (spec §6.G): new values replace old ones, failures never clear.
     * - Accumulates the requested-field set across lookups so previously requested fields
     *   remain tracked and reported as "Not provided by this dictionary" when still missing.
     * - Records the provider source and the lookup timestamp.
     */
    @Synchronized
    fun enrichEntry(
        context: Context,
        documentId: String,
        bookTitle: String,
        entryId: String,
        result: DictionaryResult,
        preferHindi: Boolean = false
    ): VocabularyEntry? {
        val list = ensureLoaded(context, documentId, bookTitle)
        val idx = list.indexOfFirst { it.id == entryId }
        if (idx == -1) return null

        val old = list[idx]
        val mergedRequested = mergeRequestedLabels(old.requestedInfoType, result.infoType.label)

        val updated = old.copy(
            meaning = result.meaning ?: old.meaning,
            synonyms = if (result.synonyms.isNotEmpty()) result.synonyms else old.synonyms,
            antonyms = if (result.antonyms.isNotEmpty()) result.antonyms else old.antonyms,
            exampleSentence = result.exampleSentence ?: old.exampleSentence,
            hindiMeaning = result.hindiMeaning ?: old.hindiMeaning,
            lookupSource = result.source,
            requestedInfoType = mergedRequested,
            preferHindi = old.preferHindi || preferHindi,
            lookupTimestamp = System.currentTimeMillis()
        )
        list[idx] = updated
        saveAll(context, documentId, bookTitle, list)
        return updated
    }

    // ================= SAVE / SERIALIZE =================

    /**
     * Canonical save entry point. Writes the JSON snapshot first, then regenerates the TXT
     * notebook from the same in-memory snapshot (spec §6.D: canonical data first, TXT
     * derived from it).
     */
    @Synchronized
    fun saveToTxtFile(
        context: Context,
        documentId: String,
        bookTitle: String,
        entries: List<VocabularyEntry>
    ): File {
        saveAll(context, documentId, bookTitle, entries)
        return getVocabularyFile(context, documentId, bookTitle)
    }

    private fun saveAll(
        context: Context,
        documentId: String,
        bookTitle: String,
        entries: List<VocabularyEntry>
    ) {
        val orphans = orphanedNotes[documentId] ?: emptyList()
        writeJsonFile(context, documentId, bookTitle, entries, orphans)
        writeTxtFile(context, documentId, bookTitle, entries, orphans)
    }

    private fun writeJsonFile(
        context: Context,
        documentId: String,
        bookTitle: String,
        entries: List<VocabularyEntry>,
        orphans: List<VocabularyEntry>
    ) {
        val root = JSONObject().apply {
            put("version", JSON_VERSION)
            put("documentId", documentId)
            put("bookTitle", bookTitle)
            put("savedAt", System.currentTimeMillis())
            put("entries", JSONArray(entries.map { entryToJson(it) }))
            put("orphanedNotes", JSONArray(orphans.map { entryToJson(it) }))
        }
        getJsonFile(context, documentId, bookTitle).writeText(root.toString(), Charsets.UTF_8)
    }

    private fun entryToJson(entry: VocabularyEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("word", entry.word)
        put("normalizedWord", entry.normalizedWord)
        put("pageNumber", entry.pageNumber)
        put("contextSentence", entry.contextSentence)
        put("timestamp", entry.timestamp)
        put("meaning", entry.meaning ?: JSONObject.NULL)
        put("synonyms", JSONArray(entry.synonyms))
        put("antonyms", JSONArray(entry.antonyms))
        put("exampleSentence", entry.exampleSentence ?: JSONObject.NULL)
        put("hindiMeaning", entry.hindiMeaning ?: JSONObject.NULL)
        put("lookupSource", entry.lookupSource ?: JSONObject.NULL)
        put("requestedInfoType", entry.requestedInfoType ?: JSONObject.NULL)
        put("preferHindi", entry.preferHindi)
        put("lookupTimestamp", entry.lookupTimestamp ?: JSONObject.NULL)
    }

    internal data class NotebookSnapshot(
        val entries: MutableList<VocabularyEntry>,
        val orphans: MutableList<VocabularyEntry>
    )

    private fun parseFromJsonFile(file: File, documentId: String): NotebookSnapshot {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val entries = mutableListOf<VocabularyEntry>()
        val orphans = mutableListOf<VocabularyEntry>()

        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                entries.add(jsonToEntry(arr.getJSONObject(i), documentId))
            }
        }
        root.optJSONArray("orphanedNotes")?.let { arr ->
            for (i in 0 until arr.length()) {
                orphans.add(jsonToEntry(arr.getJSONObject(i), documentId))
            }
        }
        return NotebookSnapshot(entries, orphans)
    }

    private fun jsonToEntry(obj: JSONObject, documentId: String): VocabularyEntry {
        fun nullableString(key: String): String? =
            if (obj.isNull(key)) null else obj.optString(key, null)

        return VocabularyEntry(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            documentId = documentId,
            word = obj.optString("word"),
            normalizedWord = obj.optString("normalizedWord").ifBlank { obj.optString("word").lowercase() },
            pageNumber = obj.optInt("pageNumber", 1),
            contextSentence = obj.optString("contextSentence", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            meaning = nullableString("meaning"),
            synonyms = obj.optJSONArray("synonyms").toStringList(),
            antonyms = obj.optJSONArray("antonyms").toStringList(),
            exampleSentence = nullableString("exampleSentence"),
            hindiMeaning = nullableString("hindiMeaning"),
            lookupSource = nullableString("lookupSource"),
            requestedInfoType = nullableString("requestedInfoType"),
            preferHindi = obj.optBoolean("preferHindi", false),
            lookupTimestamp = if (obj.isNull("lookupTimestamp")) null else obj.optLong("lookupTimestamp")
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            val value = optString(i)
            if (value.isNotBlank()) out.add(value)
        }
        return out
    }

    /**
     * Regenerates the human-readable notebook. Shape follows spec §6.I: numbered words with
     * inline dictionary notes, then the retained-notes tail section.
     */
    private fun writeTxtFile(
        context: Context,
        documentId: String,
        bookTitle: String,
        entries: List<VocabularyEntry>,
        orphans: List<VocabularyEntry>
    ) {
        val sb = StringBuilder()
        sb.append("Vocabulary — ").append(bookTitle).append("\n")
        sb.append("Source document: ").append(bookTitle).append("\n\n")

        for ((idx, entry) in entries.withIndex()) {
            sb.append("${idx + 1}. ").append(entry.word)
            if (entry.pageNumber > 0) {
                sb.append(" (Page ${entry.pageNumber})")
            }
            sb.append("\n")
            for (line in entryNoteLines(entry)) {
                sb.append(line).append("\n")
            }
            sb.append("\n")
        }

        sb.append("---\n")
        sb.append("Word Notes / Dictionary Results\n\n")

        val enrichedEntries = entries.filter { entryNoteLines(it).isNotEmpty() }
        if (enrichedEntries.isEmpty() && orphans.isEmpty()) {
            sb.append(NO_LOOKUPS_MESSAGE).append("\n")
        } else {
            for (entry in orphans) {
                sb.append("• ").append(entry.word).append(" (Retained note from removed word)\n")
                for (line in retainedNoteLines(entry)) {
                    sb.append(line).append("\n")
                }
                sb.append("\n")
            }
        }
        getVocabularyFile(context, documentId, bookTitle).writeText(sb.toString(), Charsets.UTF_8)
    }

    /** Inline notes for one numbered vocabulary entry (spec §6.I example). */
    internal fun entryNoteLines(entry: VocabularyEntry): List<String> {
        val requested = labelsFromInfoType(entry.requestedInfoType)
        val failureReason = failureReasonOf(entry.lookupSource)
        val failed = failureReason != null
        val lines = mutableListOf<String>()

        if (failed) {
            lines.add("Lookup: $UNAVAILABLE_PREFIX$failureReason")
        }

        fun addField(canonicalLabel: String, value: String?) {
            val prefix = txtPrefixFor(canonicalLabel)
            when {
                !value.isNullOrBlank() -> lines.add("$prefix: $value")
                !failed && canonicalLabel in requested -> lines.add("$prefix: $NOT_PROVIDED")
            }
        }

        addField("Meaning", entry.meaning)
        addField("Synonyms", entry.synonyms.takeIf { it.isNotEmpty() }?.joinToString(", "))
        addField("Antonyms", entry.antonyms.takeIf { it.isNotEmpty() }?.joinToString(", "))
        addField("Example Sentence", entry.exampleSentence)

        val hindi = entry.hindiMeaning
        when {
            !hindi.isNullOrBlank() -> lines.add("Hindi (हिन्दी): $hindi")
            entry.preferHindi && !failed -> lines.add("Hindi (हिन्दी): $NOT_PROVIDED")
        }

        if (!failed && !entry.lookupSource.isNullOrBlank()) {
            lines.add("Source: ${entry.lookupSource}")
        }
        entry.lookupTimestamp?.let {
            lines.add("Looked up: ${formatTimestamp(it)}")
        }
        return lines
    }

    /** Notes retained for deleted words (tail section). */
    private fun retainedNoteLines(entry: VocabularyEntry): List<String> {
        val lines = mutableListOf<String>()
        entry.meaning?.takeIf { it.isNotBlank() }?.let { lines.add("Meaning: $it") }
        entry.hindiMeaning?.takeIf { it.isNotBlank() }?.let { lines.add("Hindi (हिन्दी): $it") }
        entry.lookupSource?.takeIf { it.isNotBlank() && failureReasonOf(it) == null }
            ?.let { lines.add("Source: $it") }
        entry.lookupTimestamp?.let { lines.add("Looked up: ${formatTimestamp(it)}") }
        return lines
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    private fun parseTimestamp(text: String): Long? =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(text.trim())?.time
        }.getOrNull()

    /** Null unless [source] marks an honest provider failure ("Unavailable — reason"). */
    internal fun failureReasonOf(source: String?): String? {
        if (source == null) return null
        return if (source.startsWith("Unavailable")) {
            source.removePrefix(UNAVAILABLE_PREFIX).trim().ifBlank { "unknown reason" }
        } else null
    }

    /** Parses canonical field labels out of a stored "information requested" string. */
    internal fun labelsFromInfoType(label: String?): Set<String> {
        if (label.isNullOrBlank()) return emptySet()
        return buildSet {
            if (label.contains("Meaning")) add("Meaning")
            if (label.contains("Synonyms")) add("Synonyms")
            if (label.contains("Antonyms")) add("Antonyms")
            if (label.contains("Example")) add("Example Sentence")
        }
    }

    private fun mergeRequestedLabels(old: String?, new: String?): String? {
        val labels = linkedSetOf<String>()
        knownFieldLabels.forEach { label -> if (labelsFromInfoType(old).contains(label)) labels.add(label) }
        knownFieldLabels.forEach { label -> if (labelsFromInfoType(new).contains(label)) labels.add(label) }
        return if (labels.isEmpty()) null else labels.joinToString(", ")
    }

    // ================= LEGACY / RECOVERY TXT PARSING =================

    /**
     * Recovers a notebook from its TXT form (legacy P5 format or a §6.I-format file whose
     * JSON store is missing/corrupt). Handles both inline notes and the bulleted tail
     * section, so legacy enriched notes are migrated instead of being discarded.
     */
    internal fun parseFromTxtFile(file: File, documentId: String): NotebookSnapshot {
        val entries = mutableListOf<VocabularyEntry>()
        val orphans = mutableListOf<VocabularyEntry>()
        val lines = file.readLines(Charsets.UTF_8)

        var inOrphanBlock = false
        var currentEntryIndex = -1
        var currentOrphan: VocabularyEntry? = null
        var currentRequested: MutableSet<String> = mutableSetOf()

        fun freezeCurrent() {
            val idx = currentEntryIndex
            if (idx in entries.indices && currentRequested.isNotEmpty()) {
                entries[idx] = entries[idx].copy(requestedInfoType = currentRequested.joinToString(", "))
            }
            currentEntryIndex = -1
            currentRequested = mutableSetOf()
        }

        fun freezeOrphan() {
            currentOrphan?.let { orphans.add(it) }
            currentOrphan = null
        }

        fun beginEntry(index: Int) {
            freezeCurrent()
            freezeOrphan()
            currentEntryIndex = index
            currentRequested = mutableSetOf()
        }

        fun beginOrphan(orphan: VocabularyEntry) {
            freezeCurrent()
            freezeOrphan()
            currentOrphan = orphan
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("Vocabulary —") || line.startsWith("Source document:") ||
                    line == "Word Notes / Dictionary Results" -> {
                    // Structural headers: no current note context
                    freezeCurrent(); freezeOrphan()
                }

                line == "---" -> {
                    freezeCurrent(); freezeOrphan()
                }

                line.startsWith("[Retained Notes from Removed Words]") ||
                    line.startsWith("[No dictionary lookups yet") -> {
                    freezeCurrent(); freezeOrphan()
                    inOrphanBlock = true
                }

                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val rawWord = line.replace(Regex("^\\d+\\.\\s+"), "")
                    val word = rawWord.substringBefore(" (Page ").trim()
                    val page = if (rawWord.contains(" (Page ")) {
                        rawWord.substringAfter(" (Page ").substringBefore(")").toIntOrNull() ?: 1
                    } else 1
                    if (word.isNotBlank()) {
                        entries.add(
                            VocabularyEntry(
                                id = UUID.randomUUID().toString(),
                                documentId = documentId,
                                word = word,
                                normalizedWord = word.lowercase(),
                                pageNumber = page,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        beginEntry(entries.lastIndex)
                        inOrphanBlock = false
                    }
                }

                line.startsWith("• ") -> {
                    val rawWord = line.removePrefix("• ").trim()
                    val word = rawWord
                        .substringBefore(" (Removed word note)")
                        .substringBefore(" (Retained note from removed word)")
                        .substringBefore(" (Page ")
                        .trim()
                    if (word.isNotBlank()) {
                        val isRemoved = rawWord.contains("(Removed word note)") ||
                            rawWord.contains("(Retained note from removed word)") ||
                            inOrphanBlock
                        val targetIndex = if (isRemoved) -1
                        else entries.indexOfFirst { it.normalizedWord == word.lowercase() }

                        if (targetIndex >= 0) {
                            beginEntry(targetIndex)
                        } else {
                            beginOrphan(
                                VocabularyEntry(
                                    id = UUID.randomUUID().toString(),
                                    documentId = documentId,
                                    word = word,
                                    normalizedWord = word.lowercase(),
                                    pageNumber = 1
                                )
                            )
                        }
                    }
                }

                currentEntryIndex >= 0 -> {
                    val updated = applyNoteLine(line, entries[currentEntryIndex], null, currentRequested)
                    if (updated != null) entries[currentEntryIndex] = updated
                }

                currentOrphan != null -> {
                    val updated = applyNoteLine(line, null, currentOrphan, currentRequested)
                    if (updated != null) currentOrphan = updated
                }
            }
        }
        freezeCurrent(); freezeOrphan()
        return NotebookSnapshot(entries, orphans)
    }

    /**
     * Applies one note line ("Meaning: ...", "Lookup: Unavailable — ...", "Looked up: ...", …)
     * to the current entry/orphan. Returns the updated copy.
     */
    private fun applyNoteLine(
        line: String,
        entry: VocabularyEntry?,
        orphan: VocabularyEntry?,
        requested: MutableSet<String>
    ): VocabularyEntry? {
        if (line.isBlank()) return entry ?: orphan
        val current = entry ?: orphan ?: return null

        fun valueFor(prefix: String): String? =
            if (line.startsWith(prefix)) line.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
            else null

        valueFor("Information Requested:")?.let { value ->
            labelsFromInfoType(value).forEach { requested.add(it) }
            return current.copy(requestedInfoType = value)
        }
        valueFor("Lookup:")?.let { reason ->
            val clean = reason.removePrefix(UNAVAILABLE_PREFIX).trim().ifBlank { "unknown reason" }
            return current.copy(lookupSource = "$UNAVAILABLE_PREFIX$clean")
        }
        valueFor("Meaning:")?.let { value ->
            if (value == NOT_PROVIDED) { requested.add("Meaning"); return current }
            return current.copy(meaning = value)
        }
        valueFor("Synonyms:")?.let { value ->
            if (value == NOT_PROVIDED) { requested.add("Synonyms"); return current }
            return current.copy(synonyms = value.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
        valueFor("Antonyms:")?.let { value ->
            if (value == NOT_PROVIDED) { requested.add("Antonyms"); return current }
            return current.copy(antonyms = value.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
        valueFor("Example:")?.let { value ->
            if (value == NOT_PROVIDED) { requested.add("Example Sentence"); return current }
            return current.copy(exampleSentence = value)
        }
        valueFor("Example Sentence:")?.let { value ->
            if (value == NOT_PROVIDED) { requested.add("Example Sentence"); return current }
            return current.copy(exampleSentence = value)
        }
        valueFor("Hindi (हिन्दी):")?.let { value ->
            if (value != NOT_PROVIDED) {
                return current.copy(hindiMeaning = value, preferHindi = true)
            }
            return current.copy(preferHindi = true)
        }
        valueFor("Hindi:")?.let { value ->
            if (value != NOT_PROVIDED) {
                return current.copy(hindiMeaning = value, preferHindi = true)
            }
            return current.copy(preferHindi = true)
        }
        valueFor("Source:")?.let { value ->
            return current.copy(lookupSource = value)
        }
        valueFor("Looked up:")?.let { value ->
            return current.copy(lookupTimestamp = parseTimestamp(value))
        }
        return current
    }

    fun createShareIntent(context: Context, documentId: String, bookTitle: String): Intent {
        val file = getVocabularyFile(context, documentId, bookTitle)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vocabulary — $bookTitle")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, file.readText())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
