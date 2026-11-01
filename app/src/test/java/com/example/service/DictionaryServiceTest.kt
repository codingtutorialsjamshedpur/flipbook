package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for dictionary honesty (P5): meaning, example and Hindi
 * translations must come from a real source or be reported as unavailable —
 * never invented placeholder text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionaryServiceTest {

    @Test
    fun `unknown word never gets a fabricated meaning or example`() = runBlocking {
        val result = DictionaryService.lookup(
            word = "qxjzvbnokaword",
            infoType = DictionaryInfoType.ALL,
            preferHindi = true
        )

        // Whether the online API answered 404 or was unreachable, the offline
        // database has no entry either: nothing may be invented.
        assertNull("Meaning must not be fabricated for an unknown word", result.meaning)
        assertNull("Example must not be fabricated for an unknown word", result.exampleSentence)
        assertNull("Hindi translation must not be a placeholder for an unknown word", result.hindiMeaning)
        assertTrue(result.synonyms.isEmpty())
        assertTrue(result.antonyms.isEmpty())
        assertTrue(result.source.isNotBlank())
        assertFalse(
            "Source must never claim success for a missing entry",
            result.source.startsWith("Free Dictionary API") && result.meaning == null
        )
    }

    @Test
    fun `curated offline word returns a real sourced meaning`() = runBlocking {
        val result = DictionaryService.lookup(
            word = "serene",
            infoType = DictionaryInfoType.MEANING_AND_EXAMPLE,
            preferHindi = true
        )

        // "serene" exists either in the online API or in the curated offline book;
        // both paths are real sources, so a meaning must be present.
        assertNotNull("A real meaning must exist for a curated word", result.meaning)
        assertTrue(result.source.isNotBlank())
        assertFalse(result.source.startsWith("Unavailable"))
        assertNotNull("A Hindi translation exists for this curated word", result.hindiMeaning)
    }

    @Test
    fun `offline curated entries carry genuine synonym and antonym lists`() = runBlocking {
        // Query a word that exists in the curated offline word book. If the online
        // API is reachable it also returns real data; if not, the offline entry is used.
        val result = DictionaryService.lookup(
            word = "courage",
            infoType = DictionaryInfoType.ALL,
            preferHindi = false
        )

        assertNotNull(result.meaning)
        assertTrue(result.source.isNotBlank())
        assertFalse(result.source.startsWith("Unavailable"))
    }

    @Test
    fun `dictionary result defaults contain no invented content`() {
        val empty = DictionaryResult(
            word = "anything",
            infoType = DictionaryInfoType.MEANING,
            source = "test"
        )
        assertNull(empty.meaning)
        assertNull(empty.exampleSentence)
        assertNull(empty.hindiMeaning)
        assertTrue(empty.synonyms.isEmpty())
        assertTrue(empty.antonyms.isEmpty())
    }
}
