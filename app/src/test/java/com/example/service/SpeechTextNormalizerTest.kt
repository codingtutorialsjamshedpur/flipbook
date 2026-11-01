package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the shared speech normalizer (P2):
 * - decorative symbols never reach the TTS engine,
 * - numeric operators are spoken according to context,
 * - every spoken token maps back to a stable source word index so karaoke never shifts.
 */
class SpeechTextNormalizerTest {

    @Test
    fun `decorative markdown and bullets are stripped from speech`() {
        assertEquals("hello", SpeechTextNormalizer.normalizeSingleToken("**hello**"))
        assertEquals("world", SpeechTextNormalizer.normalizeSingleToken("*world*"))
        assertEquals("code", SpeechTextNormalizer.normalizeSingleToken("`code`"))
        assertEquals("under", SpeechTextNormalizer.normalizeSingleToken("__under__"))
        assertEquals("", SpeechTextNormalizer.normalizeSingleToken("•"))
        assertEquals("", SpeechTextNormalizer.normalizeSingleToken("***"))
        assertEquals("", SpeechTextNormalizer.normalizeSingleToken("~~~"))
    }

    @Test
    fun `in-token numeric operators are spoken contextually`() {
        assertEquals("5 out of 10", SpeechTextNormalizer.normalizeSingleToken("5/10"))
        assertEquals("3 out of 7", SpeechTextNormalizer.normalizeSingleToken("3\\7"))
        assertEquals("5 divided by 10", SpeechTextNormalizer.normalizeSingleToken("5÷10"))
        assertEquals("5 plus 2", SpeechTextNormalizer.normalizeSingleToken("5+2"))
        assertEquals("negative 5", SpeechTextNormalizer.normalizeSingleToken("-5"))
        assertEquals("negative 5", SpeechTextNormalizer.normalizeSingleToken("\u22125"))
        assertEquals("5 to 10", SpeechTextNormalizer.normalizeSingleToken("5-10"))
        assertEquals("10 minus 5", SpeechTextNormalizer.normalizeSingleToken("10-5"))
        assertEquals("5 to 10", SpeechTextNormalizer.normalizeSingleToken("5\u201310"))
    }

    @Test
    fun `hyphenated words are spoken without pronouncing a dash`() {
        assertEquals("cat mouse", SpeechTextNormalizer.normalizeSingleToken("cat-mouse"))
        assertEquals("well known", SpeechTextNormalizer.normalizeSingleToken("well-known"))
    }

    @Test
    fun `standalone operators between numbers merge into one spoken phrase`() {
        assertEquals("5 plus 2", SpeechTextNormalizer.planSpeech(listOf("5", "+", "2"), 0).spokenText)
        assertEquals("3 out of 7", SpeechTextNormalizer.planSpeech(listOf("3", "/", "7"), 0).spokenText)
        assertEquals("5 to 9", SpeechTextNormalizer.planSpeech(listOf("5", "-", "9"), 0).spokenText)
        assertEquals("9 minus 2", SpeechTextNormalizer.planSpeech(listOf("9", "-", "2"), 0).spokenText)
        assertEquals("4 to 8", SpeechTextNormalizer.planSpeech(listOf("4", "\u2013", "8"), 0).spokenText)
        assertEquals("12 divided by 4", SpeechTextNormalizer.planSpeech(listOf("12", "\u00f7", "4"), 0).spokenText)
    }

    @Test
    fun `merged operator phrase maps back to the left operand's source index`() {
        val plan = SpeechTextNormalizer.planSpeech(listOf("5", "+", "2"), 0)
        assertEquals(listOf("5 plus 2"), plan.spokenTokens)
        assertEquals(listOf(0), plan.spokenTokenToSourceIndex)
    }

    @Test
    fun `operator not between numbers is not force-rewritten`() {
        // "-" between non-numeric words is decorative and dropped, neighbors kept
        val plan = SpeechTextNormalizer.planSpeech(listOf("well,", "-", "said"), 0)
        assertEquals("well, said", plan.spokenText)
        assertEquals(listOf(0, 2), plan.spokenTokenToSourceIndex)
    }

    @Test
    fun `decorative-only tokens disappear but neighbors keep original indexes`() {
        val plan = SpeechTextNormalizer.planSpeech(listOf("first", "•", "second"), 0)
        assertEquals(listOf("first", "second"), plan.spokenTokens)
        assertEquals(listOf(0, 2), plan.spokenTokenToSourceIndex)
    }

    @Test
    fun `every spoken token maps to a valid monotonically increasing source index`() {
        val source = listOf("Chapter", "5", "+", "3", "**bold**", "•", "7/8", "end")
        val plan = SpeechTextNormalizer.planSpeech(source, 0)

        assertEquals(plan.spokenTokens.size, plan.spokenTokenToSourceIndex.size)
        assertTrue(plan.spokenTokenToSourceIndex.all { it in source.indices })
        assertEquals(plan.spokenTokenToSourceIndex, plan.spokenTokenToSourceIndex.sorted())
        assertTrue(plan.spokenText.isNotBlank())
        assertTrue(plan.spokenTokens.none { it.isBlank() })
    }

    @Test
    fun `base source index offset is preserved for seek-to-word`() {
        val plan = SpeechTextNormalizer.planSpeech(listOf("alpha", "beta"), 10)
        assertEquals(listOf(10, 11), plan.spokenTokenToSourceIndex)
    }

    @Test
    fun `normalize builds character ranges that exactly cover each spoken token`() {
        val result = SpeechTextNormalizer.normalize("**Hello** world 5/10")
        assertTrue(result.sourceWordMap.isNotEmpty())
        for (mapping in result.sourceWordMap) {
            assertEquals(
                mapping.spokenWord,
                result.spokenText.substring(mapping.spokenCharStart, mapping.spokenCharEnd)
            )
        }
        // Ranges are strictly increasing and never overlap
        for (i in 1 until result.sourceWordMap.size) {
            assertTrue(result.sourceWordMap[i].spokenCharStart >= result.sourceWordMap[i - 1].spokenCharEnd)
        }
    }

    @Test
    fun `empty and blank input produce empty speech with no fabricated tokens`() {
        val plan = SpeechTextNormalizer.planSpeech(emptyList(), 0)
        assertEquals("", plan.spokenText)
        assertTrue(plan.spokenTokens.isEmpty())
        assertTrue(plan.spokenTokenToSourceIndex.isEmpty())

        val blank = SpeechTextNormalizer.normalize("   ")
        assertEquals("", blank.spokenText)
        assertTrue(blank.sourceWordMap.isEmpty())
    }

    @Test
    fun `spoken text never contains raw decorative markers`() {
        val dirty = "**Bold** and _emphasized_ with `code` and • bullets • and # hash"
        val plan = SpeechTextNormalizer.planSpeech(dirty.split(Regex("\\s+")), 0)
        assertFalse(plan.spokenText.contains("*"))
        assertFalse(plan.spokenText.contains("_"))
        assertFalse(plan.spokenText.contains("`"))
        assertFalse(plan.spokenText.contains("•"))
        assertFalse(plan.spokenText.contains("#"))
        assertTrue(plan.spokenText.contains("Bold"))
        assertTrue(plan.spokenText.contains("emphasized"))
    }
}
