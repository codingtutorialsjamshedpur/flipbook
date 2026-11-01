package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentFormat
import com.example.data.model.TextExtractionOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression tests for the document pipeline (P0/P1):
 * - honest pagination (no fabricated text for empty content),
 * - real PDFBox native text extraction round-trip,
 * - real DOCX and EPUB parsing with metadata,
 * - OCR usability threshold (never reports false OCR success).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentParserServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---------- helpers ----------

    private fun buildZip(files: Map<String, String>): ByteArrayInputStream {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    // ---------- pagination honesty ----------

    @Test
    fun `blank content yields a single honest unavailable page`() {
        val pages = DocumentParserService.paginate("   \n  ")
        assertEquals(1, pages.size)
        assertTrue(pages[0].textUnavailable)
        assertEquals("", pages[0].text)
        assertNotNull(pages[0].extractionWarning)
    }

    @Test
    fun `reflowable pagination preserves every word in original order`() {
        val paragraphs = (1..8).joinToString("\n\n") { i ->
            "Paragraph $i with enough words to force sensible page breaks across the document structure."
        }
        val pages = DocumentParserService.paginate(paragraphs, charsPerPage = 120)
        assertTrue(pages.size > 1)

        for ((idx, page) in pages.withIndex()) {
            assertEquals(idx + 1, page.pageNumber)
            assertFalse(page.textUnavailable)
        }

        val reassembled = pages.joinToString(" ") { it.text }
            .replace(Regex("\\s+"), " ")
            .trim()
        val original = paragraphs.replace(Regex("\\s+"), " ").trim()
        assertEquals(original, reassembled)
    }

    // ---------- PDF native text round-trip ----------

    @Test
    fun `generated sample PDF round-trips real native text for all 10 pages`() {
        val doc = DocumentParserService.generate10PageSamplePdf(context)
        assertEquals(10, doc.totalPages)

        val pages = DocumentParserService.paginate(doc.rawContent)
        assertEquals(10, pages.size)

        for (page in pages) {
            assertTrue("Page ${page.pageNumber} must have real extracted text", page.text.isNotBlank())
            assertFalse(page.textUnavailable)
            assertTrue(page.text.contains("THE QUANTUM MANUSCRIPT"))
        }
        // Native extraction must be identified honestly (no OCR claim for text-layer PDFs)
        assertTrue(pages.all { it.extractionOrigin == TextExtractionOrigin.NATIVE_PDF_TEXT })
    }

    // ---------- DOCX ----------

    @Test
    fun `DOCX extraction reads real paragraph text and core metadata`() {
        val documentXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>First paragraph of the draft.</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second paragraph with more detail.</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val coreXml = """
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties">
              <dc:title>The Drafted Screenplay</dc:title>
              <dc:creator>Ada Writer</dc:creator>
            </cp:coreProperties>
        """.trimIndent()

        val zip = buildZip(
            mapOf(
                "[Content_Types].xml" to "<Types/>",
                "word/document.xml" to documentXml,
                "docProps/core.xml" to coreXml
            )
        )

        val result = DocumentParserService.extractDocx(zip)
        assertTrue(result.bodyText.contains("First paragraph of the draft."))
        assertTrue(result.bodyText.contains("Second paragraph with more detail."))
        assertEquals("The Drafted Screenplay", result.metadata.title)
        assertEquals("Ada Writer", result.metadata.author)
    }

    @Test
    fun `null DOCX stream reports empty honestly without fabricating content`() {
        val result = DocumentParserService.extractDocx(null)
        assertTrue(result.bodyText.isBlank() || result.bodyText.contains("Empty"))
        assertEquals(null, result.metadata.title)
        assertEquals(null, result.metadata.author)
    }

    // ---------- EPUB ----------

    @Test
    fun `EPUB extraction follows spine order and reads OPF metadata`() {
        val containerXml = """
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opfXml = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Songs of the Observatory</dc:title>
                <dc:creator>Dr. Nova</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()

        val ch1 = "<html><body><h1>Chapter One</h1><p>First chapter body text.</p></body></html>"
        val ch2 = "<html><body><h1>Chapter Two</h1><p>Second chapter body text.</p></body></html>"

        val zip = buildZip(
            mapOf(
                "META-INF/container.xml" to containerXml,
                "OEBPS/content.opf" to opfXml,
                "OEBPS/ch1.xhtml" to ch1,
                "OEBPS/ch2.xhtml" to ch2
            )
        )

        val result = DocumentParserService.extractEpub(zip)
        assertEquals("Songs of the Observatory", result.metadata.title)
        assertEquals("Dr. Nova", result.metadata.author)
        assertEquals("en", result.metadata.language)

        val firstIdx = result.bodyText.indexOf("First chapter body text.")
        val secondIdx = result.bodyText.indexOf("Second chapter body text.")
        assertTrue("Chapter 1 must be present", firstIdx >= 0)
        assertTrue("Chapter 2 must be present", secondIdx >= 0)
        assertTrue("Spine order must be preserved", firstIdx < secondIdx)
        assertFalse("Script markup must be stripped", result.bodyText.contains("<p>"))
    }

    @Test
    fun `null EPUB stream reports empty honestly`() {
        val result = DocumentParserService.extractEpub(null)
        assertTrue(result.bodyText.contains("Empty"))
        assertEquals(null, result.metadata.title)
    }

    // ---------- OCR usability gate ----------

    @Test
    fun `native text usability gate rejects blank and sparse text`() {
        assertFalse(OcrService.isNativeTextUsable(null))
        assertFalse(OcrService.isNativeTextUsable(""))
        assertFalse(OcrService.isNativeTextUsable("   \n"))
        assertFalse(OcrService.isNativeTextUsable("ab")) // below MIN_USABLE_CHARS
        assertTrue(OcrService.isNativeTextUsable("A perfectly readable sentence with words."))
        assertTrue(OcrService.isNativeTextUsable("\u0939\u093f\u0928\u094d\u0926\u0940 \u092a\u0922\u093c\u094b\u0915\u094d\u0937\u093e \u0915\u093e \u0935\u093e\u0915\u094d\u092f"))
    }

    // ---------- paste-text import ----------

    @Test
    fun `pasted text creates a real book with honest title fallback`() {
        val explicit = DocumentParserService.createDocumentFromText(
            title = "My Pasted Story",
            author = "",
            content = "It was a bright cold day in April.",
            format = DocumentFormat.TXT
        )
        assertEquals("My Pasted Story", explicit.title)
        assertTrue(explicit.rawContent.contains("It was a bright cold day in April."))
        assertTrue(explicit.totalPages >= 1)

        val fallback = DocumentParserService.createDocumentFromText(
            title = "   ",
            author = "",
            content = "Some content to read goes here.",
            format = DocumentFormat.TXT
        )
        assertTrue("Fallback title must never be blank", fallback.title.isNotBlank())
        assertTrue(fallback.rawContent.contains("Some content to read goes here."))
    }
}
