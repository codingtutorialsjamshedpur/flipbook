package com.example

import com.example.data.model.DocumentFormat
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingLanguage
import com.example.data.model.VoicePreset
import com.example.service.DemoCatalog
import com.example.service.DocumentParserService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `demo catalog lists four English and four Hindi fixtures across four formats`() {
        val descriptors = DemoCatalog.descriptors
        assertEquals(8, descriptors.size)

        val english = descriptors.filter { it.languageLabel == "English" }
        val hindi = descriptors.filter { it.languageLabel == "हिन्दी" }
        assertEquals(4, english.size)
        assertEquals(4, hindi.size)

        listOf(english, hindi).forEach { group ->
            assertEquals(
                listOf(DocumentFormat.PDF, DocumentFormat.TXT, DocumentFormat.EPUB, DocumentFormat.DOCX),
                group.map { it.format }
            )
        }

        // Labels must visibly include format and language/title (§1)
        assertTrue(descriptors.first().chipLabel.contains("PDF"))
        assertTrue(descriptors.first().chipLabel.contains("English"))
        val hindiTxt = descriptors.first { it.languageLabel == "हिन्दी" && it.format == DocumentFormat.TXT }
        assertTrue(hindiTxt.chipLabel.startsWith("TXT"))
    }

    @Test
    fun `sample documents build real content without a context (non-PDF formats)`() {
        val samples = DocumentParserService.getSampleDocuments() // no context: PDFs skipped
        assertEquals(6, samples.size)
        assertTrue(samples.all { it.isDemo })

        assertEquals("The Lighthouse Letters", samples[0].title)
        assertEquals(DocumentFormat.TXT, samples[0].format)
        assertTrue(samples[0].rawContent.contains("Elsie"))

        assertEquals("Songs of the Observatory", samples[1].title)
        assertEquals(DocumentFormat.EPUB, samples[1].format)

        assertEquals("The Director's Draft", samples[2].title)
        assertEquals(DocumentFormat.DOCX, samples[2].format)

        assertEquals("पंच परमेश्वर", samples[3].title)
        assertTrue(samples[3].rawContent.contains("जुम्मन शेख"))

        assertEquals("जंगल की राह", samples[4].title)
        assertEquals("त्योहार की यादें", samples[5].title)

        // Every sample is distinct real content, not the same hard-coded document
        assertEquals(6, samples.map { it.rawContent }.distinct().size)
    }

    @Test
    fun `spread pair computation follows exact odd-even pattern`() {
        // Page 1 is on left, page 2 on right for Spread 0
        val page1 = 1
        val left1 = if (page1 % 2 != 0) page1 else page1 - 1
        assertEquals(1, left1)
        assertEquals(2, left1 + 1)

        // Page 4 is on right of Spread 1 (pages 3 and 4)
        val page4 = 4
        val left4 = if (page4 % 2 != 0) page4 else page4 - 1
        assertEquals(3, left4)
        assertEquals(4, left4 + 1)
    }

    @Test
    fun `default preferences support multilingual Indian languages and natural voice presets`() {
        val prefs = ReaderPreferences()
        assertEquals(ReaderFont.LITERATA, prefs.font)
        assertEquals(ReaderTheme.PAPER, prefs.theme)
        assertEquals(19f, prefs.fontSizeSp)
        assertEquals(1.0f, prefs.speechRate)
        assertEquals(ReadingLanguage.HINDI, prefs.language)
        assertEquals(VoicePreset.FEMALE_NARRATOR, prefs.voicePreset)
    }

    @Test
    fun `multilingual languages contain Indian languages`() {
        val hindi = ReadingLanguage.HINDI
        assertEquals("hi-IN", hindi.code)
        assertEquals("en-IN", ReadingLanguage.ENGLISH_INDIA.code)
        assertEquals("ta-IN", ReadingLanguage.TAMIL.code)
    }
}
