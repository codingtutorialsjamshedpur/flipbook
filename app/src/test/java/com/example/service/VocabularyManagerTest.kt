package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VocabularyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Regression tests for the Vocabulary Notebook (P5):
 * - entries hydrate from the on-disk TXT notebook before any write (no silent wipes),
 * - duplicates are detected case-insensitively and never inserted twice,
 * - edit/delete persist back to the TXT file,
 * - dictionary enrichment never overwrites real data with invented content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VocabularyManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val docId: String = "vocab_test_${UUID.randomUUID()}"
    private val title: String = "Vocabulary Test Book $docId"

    @Test
    fun `entries hydrate from disk for a document that was never loaded`() {
        // Write a notebook file directly (as a previous app session would have)
        val file = VocabularyManager.getVocabularyFile(context, docId, title)
        val seeded = listOf(
            VocabularyEntry(documentId = docId, word = "serene", pageNumber = 3),
            VocabularyEntry(documentId = docId, word = "resilience", pageNumber = 7)
        )
        VocabularyManager.saveToTxtFile(context, docId, title, seeded)

        // getEntries has never been called for this docId: it must parse the file,
        // not return an empty in-memory list and later wipe the file.
        val loaded = VocabularyManager.getEntries(context, docId, title)
        assertEquals(2, loaded.size)
        assertEquals("serene", loaded[0].word)
        assertEquals(3, loaded[0].pageNumber)
        assertEquals("resilience", loaded[1].word)
        assertEquals(7, loaded[1].pageNumber)
    }

    @Test
    fun `addWord persists and a subsequent add does not wipe earlier entries`() {
        val (first, firstDup) = VocabularyManager.addWord(context, docId, title, "horizon", 2)
        assertFalse(firstDup)

        val (second, secondDup) = VocabularyManager.addWord(context, docId, title, "solitude", 5)
        assertFalse(secondDup)

        val entries = VocabularyManager.getEntries(context, docId, title)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.id == first.id })
        assertTrue(entries.any { it.id == second.id })

        // File content must contain both numbered words in chronological order
        val text = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertTrue(text.contains("1. horizon (Page 2)"))
        assertTrue(text.contains("2. solitude (Page 5)"))
    }

    @Test
    fun `duplicate words are reported honestly and never stored twice`() {
        val (entry, dup1) = VocabularyManager.addWord(context, docId, title, "Alchemist", 1)
        assertFalse(dup1)

        val (dupEntry, dup2) = VocabularyManager.addWord(context, docId, title, "alchemist", 9)
        assertTrue(dup2)
        assertEquals(entry.id, dupEntry.id)

        assertEquals(1, VocabularyManager.getEntries(context, docId, title).size)
    }

    @Test
    fun `editWord updates both memory and the TXT file`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "brave", 4)
        val updated = VocabularyManager.editWord(context, docId, title, entry.id, "courage")
        assertNotNull(updated)
        assertEquals("courage", updated!!.word)
        assertEquals("courage", updated.normalizedWord)

        val fileText = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertTrue(fileText.contains("courage"))
        assertFalse(fileText.contains("brave"))
    }

    @Test
    fun `deleteEntries removes only requested ids and renumbers the file`() {
        val (a, _) = VocabularyManager.addWord(context, docId, title, "alpha", 1)
        val (b, _) = VocabularyManager.addWord(context, docId, title, "beta", 2)
        val (c, _) = VocabularyManager.addWord(context, docId, title, "gamma", 3)

        val remaining = VocabularyManager.deleteEntries(context, docId, title, setOf(b.id))
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == a.id })
        assertTrue(remaining.any { it.id == c.id })

        val fileText = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertFalse(fileText.contains("beta"))
        assertTrue(fileText.contains("1. alpha"))
        assertTrue(fileText.contains("2. gamma"))
    }

    @Test
    fun `dictionary enrichment with unavailable result never fabricates a meaning`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "unknownword", 6)

        // Simulates an honest "Unavailable" lookup result (null meaning, empty lists)
        val unavailable = com.example.service.DictionaryResult(
            word = "unknownword",
            infoType = com.example.service.DictionaryInfoType.MEANING,
            meaning = null,
            synonyms = emptyList(),
            antonyms = emptyList(),
            exampleSentence = null,
            hindiMeaning = null,
            source = "Unavailable — no entry"
        )
        VocabularyManager.enrichEntry(context, docId, title, entry.id, unavailable)

        val after = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals(null, after.meaning)
        assertEquals(null, after.exampleSentence)
        assertEquals(null, after.hindiMeaning)
        assertTrue(after.synonyms.isEmpty())
        assertTrue(after.antonyms.isEmpty())
        assertEquals("Unavailable — no entry", after.lookupSource)
    }

    @Test
    fun `dictionary enrichment fills real data when a provider result exists`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "serene", 8)
        val real = com.example.service.DictionaryResult(
            word = "serene",
            infoType = com.example.service.DictionaryInfoType.ALL,
            meaning = "Calm, peaceful, and untroubled.",
            synonyms = listOf("tranquil", "placid"),
            antonyms = listOf("turbulent"),
            exampleSentence = "The lake remained serene.",
            hindiMeaning = "\u0936\u093e\u0928\u094d\u0924",
            source = "StoryFlip Lexical Database (Offline)"
        )
        VocabularyManager.enrichEntry(context, docId, title, entry.id, real)

        val after = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals("Calm, peaceful, and untroubled.", after.meaning)
        assertEquals(2, after.synonyms.size)
        assertEquals(1, after.antonyms.size)
        assertEquals("The lake remained serene.", after.exampleSentence)
        assertNotNull(after.hindiMeaning)
    }

    // =====================================================================
    // Addendum §6: canonical persistence, merge policy and legacy migration
    // =====================================================================

    private fun enrichReal(
        entryId: String,
        word: String,
        meaning: String,
        synonyms: List<String> = emptyList(),
        antonyms: List<String> = emptyList(),
        example: String? = null,
        source: String = "Free Dictionary API",
        infoType: DictionaryInfoType = DictionaryInfoType.ALL
    ) {
        VocabularyManager.enrichEntry(
            context, docId, title, entryId,
            DictionaryResult(
                word = word,
                infoType = infoType,
                meaning = meaning,
                synonyms = synonyms,
                antonyms = antonyms,
                exampleSentence = example,
                hindiMeaning = null,
                source = source
            )
        )
    }

    private fun enrichUnavailable(entryId: String, word: String, reason: String) {
        VocabularyManager.enrichEntry(
            context, docId, title, entryId,
            DictionaryResult(
                word = word,
                infoType = DictionaryInfoType.ALL,
                meaning = null,
                synonyms = emptyList(),
                antonyms = emptyList(),
                exampleSentence = null,
                hindiMeaning = null,
                source = "Unavailable — $reason"
            )
        )
    }

    @Test
    fun `enrichment survives a simulated app restart with the canonical txt shape`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "vessel", 2)
        enrichReal(
            entry.id, "vessel", "A ship or large boat.",
            synonyms = listOf("craft", "boat"),
            example = "The vessel left the harbor."
        )

        VocabularyManager.clearCacheForTests() // simulated process death

        val after = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals("A ship or large boat.", after.meaning)
        assertEquals(listOf("craft", "boat"), after.synonyms)
        assertEquals("The vessel left the harbor.", after.exampleSentence)
        assertEquals("Free Dictionary API", after.lookupSource)
        assertNotNull(after.lookupTimestamp)

        // Canonical JSON store exists and carries the real data
        val json = VocabularyManager.getJsonFile(context, docId, title)
        assertTrue(json.exists())
        val root = org.json.JSONObject(json.readText())
        assertEquals(2, root.getInt("version"))
        assertEquals(1, root.getJSONArray("entries").length())

        // TXT notebook follows the §6.I shape and is never stale relative to JSON
        val txt = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertTrue(txt.startsWith("Vocabulary — $title"))
        assertTrue(txt.contains("Source document: $title"))
        assertTrue(txt.contains("1. vessel (Page 2)"))
        assertTrue(txt.contains("Meaning: A ship or large boat."))
        assertTrue(txt.contains("Synonyms: craft, boat"))
        assertTrue(txt.contains("Source: Free Dictionary API"))
        assertTrue(txt.contains("Word Notes / Dictionary Results"))
        assertFalse(txt.contains("No dictionary lookups yet"))
        assertEquals(1, txt.split("---").size - 1)
        assertTrue(txt.contains("Looked up: "))
    }

    @Test
    fun `repeated lookups merge fields without duplicating notes and accumulate requests`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "tempest", 5)
        enrichReal(
            entry.id, "tempest", "A violent storm.",
            synonyms = listOf("gale"), antonyms = listOf("calm"),
            example = "The tempest raged all night."
        )
        enrichReal(
            entry.id, "tempest", "A violent windy storm.",
            source = "StoryFlip Lexical Database (Offline)",
            infoType = DictionaryInfoType.MEANING
        )

        val after = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals("A violent windy storm.", after.meaning)          // replaced
        assertEquals(listOf("gale"), after.synonyms)                   // preserved
        assertEquals(listOf("calm"), after.antonyms)                   // preserved
        assertEquals("The tempest raged all night.", after.exampleSentence)
        assertEquals("StoryFlip Lexical Database (Offline)", after.lookupSource)
        assertEquals("Meaning, Synonyms, Antonyms, Example Sentence", after.requestedInfoType)

        val txt = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertEquals("Meaning written more than once", 1, Regex("Meaning:").findAll(txt).count())
        assertEquals("Synonyms written more than once", 1, Regex("Synonyms:").findAll(txt).count())
        assertEquals("duplicate results sections", 1, txt.split("---").size - 1)
    }

    @Test
    fun `an unavailable repeat lookup never clears earlier dictionary results`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "harbor", 3)
        enrichReal(entry.id, "harbor", "A place of safety for ships.")
        enrichUnavailable(entry.id, "harbor", "word not found")

        val after = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals("A place of safety for ships.", after.meaning)
        assertEquals("Unavailable — word not found", after.lookupSource)

        val txt = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertTrue(txt.contains("Lookup: Unavailable — word not found"))
        assertTrue(txt.contains("Meaning: A place of safety for ships."))
        // A failed lookup is never presented as a data source
        assertFalse(txt.contains("Source: Free Dictionary API"))
    }

    @Test
    fun `requested but missing fields are reported as not provided by this dictionary`() {
        val (entry, _) = VocabularyManager.addWord(context, docId, title, "solstice", 9)
        enrichReal(entry.id, "solstice", "The longest or shortest day of the year.")

        val txt = VocabularyManager.getVocabularyFile(context, docId, title).readText()
        assertTrue(txt.contains("Meaning: The longest or shortest day of the year."))
        assertTrue(txt.contains("Synonyms: Not provided by this dictionary"))
        assertTrue(txt.contains("Antonyms: Not provided by this dictionary"))
        assertTrue(txt.contains("Example: Not provided by this dictionary"))
        assertFalse(txt.contains("Hindi"))
    }

    @Test
    fun `legacy bullet notebook migrates to the json store without losing notes`() {
        val txtFile = VocabularyManager.getVocabularyFile(context, docId, title)
        txtFile.writeText(
            """
            Vocabulary — $title
            Source document: $title

            1. serene (Page 3)

            ---
            Word Notes / Dictionary Results

            • serene (Page 3)
              Information Requested: Meaning, Example Sentence
              Meaning: Calm and peaceful.
              Example: The lake was serene.
              Source: Legacy Provider
              Looked up: 2024-05-01 10:30
            """.trimIndent(),
            Charsets.UTF_8
        )

        // No JSON file exists: recovery must parse the legacy TXT and migrate it,
        // never return an empty list that would later wipe the file.
        assertFalse(VocabularyManager.getJsonFile(context, docId, title).exists())
        val loaded = VocabularyManager.getEntries(context, docId, title)
        assertEquals(1, loaded.size)
        assertEquals("serene", loaded.single().word)
        assertEquals(3, loaded.single().pageNumber)
        assertEquals("Calm and peaceful.", loaded.single().meaning)
        assertEquals("The lake was serene.", loaded.single().exampleSentence)
        assertEquals("Legacy Provider", loaded.single().lookupSource)
        assertEquals("Meaning, Example Sentence", loaded.single().requestedInfoType)
        assertNotNull(loaded.single().lookupTimestamp)

        // Migration happened immediately and survives another restart
        assertTrue(VocabularyManager.getJsonFile(context, docId, title).exists())
        VocabularyManager.clearCacheForTests()
        val reloaded = VocabularyManager.getEntries(context, docId, title).single()
        assertEquals("Calm and peaceful.", reloaded.meaning)
        assertEquals("Legacy Provider", reloaded.lookupSource)
    }
}
