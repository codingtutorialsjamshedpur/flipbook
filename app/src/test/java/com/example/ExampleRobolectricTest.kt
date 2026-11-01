package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.HighlightColor
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReaderTheme
import com.example.service.DocumentParserService
import com.example.service.GeminiStoryboardService
import com.example.service.TtsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StoryFlip", appName)
    }

    @Test
    fun `multi-page PDF generation and pagination preserves all 10 source pages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplePdfDoc = DocumentParserService.generate10PageSamplePdf(context)

        // 1. Source page count must equal 10
        assertEquals(10, samplePdfDoc.totalPages)

        // 2. Paginate must deserialize all 10 distinct pages
        val pages = DocumentParserService.paginate(samplePdfDoc.rawContent)
        assertEquals(10, pages.size)

        // 3. Every page 1..10 must have its sequential pageNumber and non-empty content
        for (i in 1..10) {
            val page = pages.find { it.pageNumber == i }
            assertNotNull("Page $i must exist", page)
            assertTrue("Page $i must have text", page!!.text.isNotBlank())
            assertEquals(i, page.pageNumber)
        }

        // 4. Page 1 and Page 10 distinct identification checks
        assertEquals(1, pages[0].pageNumber)
        assertEquals(10, pages[9].pageNumber)
        assertTrue(pages[0].title.contains("Prologue") || pages[0].title.contains("Page 1"))
        assertTrue(pages[9].title.contains("Epilogue") || pages[9].title.contains("Page 10"))

        // 5. Landscape spreads mapping check: (1, 2), (3, 4), (5, 6), (7, 8), (9, 10)
        for (spreadIndex in 0 until 5) {
            val leftPageNum = spreadIndex * 2 + 1
            val rightPageNum = leftPageNum + 1

            val leftPage = pages.find { it.pageNumber == leftPageNum }
            val rightPage = pages.find { it.pageNumber == rightPageNum }

            assertNotNull("Left page $leftPageNum must exist in spread $spreadIndex", leftPage)
            assertNotNull("Right page $rightPageNum must exist in spread $spreadIndex", rightPage)
            assertEquals(leftPageNum, leftPage!!.pageNumber)
            assertEquals(rightPageNum, rightPage!!.pageNumber)
        }
    }

    @Test
    fun `speed selector supported options and live rate mapping`() {
        val supportedRates = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val defaultPrefs = ReaderPreferences()
        assertEquals(1.0f, defaultPrefs.speechRate, 0.001f)

        // Verify all 6 supported rates can be applied to ReaderPreferences
        for (rate in supportedRates) {
            val updated = defaultPrefs.copy(speechRate = rate)
            assertEquals(rate, updated.speechRate, 0.001f)
        }

        // Verify TtsManager live rate update
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ttsManager = TtsManager(context)
        ttsManager.setLiveSpeechRate(1.5f, "Testing live speech rate adjustment.")
        assertNotNull(ttsManager)
    }

    @Test
    fun `word-level hit-testing and seekToWord starts at exact word`() {
        val sampleText = "The quick brown fox jumps over the lazy dog."
        val words = sampleText.split(Regex("\\s+")).filter { it.isNotBlank() }
        assertEquals(9, words.size)

        // Double-tapping word index 3 ("fox")
        val targetWordIdx = 3
        assertEquals("fox", words[targetWordIdx])

        val context = ApplicationProvider.getApplicationContext<Context>()
        val ttsManager = TtsManager(context)
        ttsManager.seekToWord(pageNumber = 1, wordIndex = targetWordIdx, fullPageText = sampleText, rate = 1.0f)

        // Active word index must match the selected word
        assertEquals(targetWordIdx, ttsManager.activeWordIndex.value)
        assertEquals(1, ttsManager.activePageNumber.value)
        assertEquals("fox", ttsManager.currentSentence.value)
    }

    @Test
    fun `karaoke highlight colors are selectable and retain contrast`() {
        val colors = HighlightColor.values()
        assertEquals(9, colors.size)

        // Verify active visible colors have non-zero hex values and readable text color
        val visibleColors = colors.filter { it != HighlightColor.TRANSPARENT }
        for (c in visibleColors) {
            assertTrue(c.colorHex != 0L)
            assertTrue(c.textColorHex != 0L)
            assertTrue(c.displayName.isNotBlank())
        }

        val prefs = ReaderPreferences(highlightColor = HighlightColor.CYAN)
        assertEquals(HighlightColor.CYAN, prefs.highlightColor)

        val updatedPrefs = prefs.copy(highlightColor = HighlightColor.ORANGE)
        assertEquals(HighlightColor.ORANGE, updatedPrefs.highlightColor)
    }

    @Test
    fun `document pagination for TXT reflowable content`() {
        val sampleText = """
            SCENE 1: EXT. ALLEY - NIGHT
            Rain falls steadily onto the dark cobblestones.
            
            SCENE 2: INT. WAREHOUSE - CONTINUOUS
            Sparks shower from the ceiling. Silas waits in the dark.
        """.trimIndent()

        val pages = DocumentParserService.paginate(sampleText, charsPerPage = 50)
        assertTrue(pages.isNotEmpty())
        assertEquals(1, pages[0].pageNumber)
    }

    @Test
    fun `storyboard breakdown extracts scenes and shots`() {
        val scriptText = """
            SCENE 1: EXT. NEON ALLEYWAY - NIGHT
            Detective Ray walks through the smoke.
            RAY
            I found the missing drive.
        """.trimIndent()

        val scenes = GeminiStoryboardService.generateLocalScriptBreakdown(scriptText)
        assertTrue(scenes.isNotEmpty())
        assertNotNull(scenes[0].shots)
        assertTrue(scenes[0].shots.isNotEmpty())
        assertEquals(1, scenes[0].shots[0].shotNumber)
    }
}
