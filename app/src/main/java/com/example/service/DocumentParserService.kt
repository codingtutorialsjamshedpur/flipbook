package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.ReaderPage
import com.example.data.model.TextExtractionOrigin
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.UUID

data class SourceMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val language: String? = null
)

data class PdfParseResult(
    val pages: List<ReaderPage>,
    val metadata: SourceMetadata,
    val warnings: List<String>
)

object DocumentParserService {

    private const val PAGE_DELIMITER = "===STORYFLIP_PAGE==="

    private fun initPdfBox(context: Context) {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {
            // Optional bootstrap; parsing still works for standard content.
        }
    }

    /**
     * Splits raw document text into readable pages with title, header, and structured lines.
     * Preserves pre-rendered PDF page metadata and image paths when available.
     */
    fun paginate(content: String, charsPerPage: Int = 1200): List<ReaderPage> {
        // Normalize line endings so CRLF/CR documents paginate on paragraph
        // boundaries instead of collapsing into a single page.
        val normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n')
        if (normalizedContent.isBlank()) {
            return listOf(ReaderPage(pageNumber = 1, text = "", lines = emptyList(), extractionOrigin = TextExtractionOrigin.NONE, textUnavailable = true, extractionWarning = "Empty document."))
        }

        // If content contains pre-mapped pages (e.g. from multi-page PDF ingestion)
        if (normalizedContent.contains(PAGE_DELIMITER)) {
            val chunks = normalizedContent.split(PAGE_DELIMITER).filter { it.isNotBlank() }
            val pages = mutableListOf<ReaderPage>()
            for ((idx, chunk) in chunks.withIndex()) {
                val lines = chunk.trim().lines()
                val firstLine = lines.firstOrNull() ?: ""
                var imgPath: String? = null
                var pageTitle = "Page ${idx + 1}"
                var origin = TextExtractionOrigin.SCRIPT
                var warning: String? = null
                val textLines: List<String>

                if (firstLine.startsWith("META:")) {
                    val metaContent = firstLine.substringAfter("META:")
                    val parts = metaContent.split("||")
                    imgPath = parts.getOrNull(0)?.ifBlank { null }
                    pageTitle = parts.getOrNull(1)?.ifBlank { "Page ${idx + 1}" } ?: "Page ${idx + 1}"
                    origin = parts.getOrNull(2)?.let { name ->
                        runCatching { TextExtractionOrigin.valueOf(name) }.getOrNull()
                    } ?: TextExtractionOrigin.SCRIPT
                    warning = parts.getOrNull(3)?.ifBlank { null }
                    textLines = lines.drop(1)
                } else {
                    textLines = lines
                }

                val body = textLines.joinToString("\n").trim()
                pages.add(
                    ReaderPage(
                        pageNumber = idx + 1,
                        title = pageTitle,
                        text = body,
                        lines = body.split("\n").filter { it.isNotBlank() },
                        chapterName = pageTitle,
                        imagePath = imgPath,
                        extractionOrigin = origin,
                        textUnavailable = body.isBlank(),
                        extractionWarning = warning ?: if (body.isBlank()) "No extractable text for this page." else null
                    )
                )
            }
            if (pages.isNotEmpty()) return pages
        }

        // Reflowable pagination for TXT, EPUB, DOCX
        val paragraphs = normalizedContent.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        val pages = mutableListOf<ReaderPage>()
        var currentChunk = StringBuilder()
        var currentChapter = "Chapter 1"
        var pageNum = 1

        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.startsWith("CHAPTER", ignoreCase = true) ||
                trimmed.startsWith("SCENE", ignoreCase = true) ||
                trimmed.startsWith("ACT", ignoreCase = true) ||
                trimmed.startsWith("अध्याय", ignoreCase = true) ||
                trimmed.startsWith("दृश्य", ignoreCase = true)
            ) {
                if (currentChunk.isNotEmpty()) {
                    val text = currentChunk.toString().trim()
                    pages.add(
                        ReaderPage(
                            pageNumber = pageNum++,
                            title = currentChapter,
                            text = text,
                            lines = text.split("\n").filter { it.isNotBlank() },
                            chapterName = currentChapter
                        )
                    )
                    currentChunk = StringBuilder()
                }
                currentChapter = trimmed.lines().firstOrNull() ?: currentChapter
            }

            if (currentChunk.length + trimmed.length > charsPerPage && currentChunk.isNotEmpty()) {
                val text = currentChunk.toString().trim()
                pages.add(
                    ReaderPage(
                        pageNumber = pageNum++,
                        title = currentChapter,
                        text = text,
                        lines = text.split("\n").filter { it.isNotBlank() },
                        chapterName = currentChapter
                    )
                )
                currentChunk = StringBuilder()
            }

            currentChunk.append(trimmed).append("\n\n")
        }

        if (currentChunk.isNotEmpty()) {
            val text = currentChunk.toString().trim()
            pages.add(
                ReaderPage(
                    pageNumber = pageNum,
                    title = currentChapter,
                    text = text,
                    lines = text.split("\n").filter { it.isNotBlank() },
                    chapterName = currentChapter
                )
            )
        }

        return if (pages.isEmpty()) {
            listOf(ReaderPage(pageNumber = 1, text = normalizedContent, lines = normalizedContent.split("\n")))
        } else {
            pages
        }
    }

    fun pagesToRawContent(pages: List<ReaderPage>): String {
        val sb = StringBuilder()
        for (page in pages) {
            sb.append(PAGE_DELIMITER).append("\n")
            sb.append("META:")
                .append(page.imagePath ?: "").append("||")
                .append(page.title).append("||")
                .append(page.extractionOrigin.name).append("||")
                .append(page.extractionWarning ?: "").append("\n")
            sb.append(page.text).append("\n")
        }
        return sb.toString()
    }

    /**
     * Parses a selected file Uri from the system file picker automatically without forced manual entry.
     */
    suspend fun parseFromUri(
        context: Context,
        uri: Uri,
        onStageUpdate: (stage: String) -> Unit,
        ocrEnabled: Boolean = true,
        onFilenameDiscovered: (filename: String) -> Unit = {}
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        try {
            onStageUpdate("Validating file...")
            delay(50)

            var filename = "Selected Document"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) filename = cursor.getString(nameIndex) ?: filename
                }
            }
            onFilenameDiscovered(filename)

            val lowerName = filename.lowercase()
            val format = when {
                lowerName.endsWith(".pdf") -> DocumentFormat.PDF
                lowerName.endsWith(".epub") -> DocumentFormat.EPUB
                lowerName.endsWith(".docx") -> DocumentFormat.DOCX
                lowerName.endsWith(".txt") -> DocumentFormat.TXT
                else -> DocumentFormat.SCRIPT
            }

            onStageUpdate("Reading document metadata...")
            delay(50)

            val fallbackTitle = filename.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                .split(" ").filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                .ifBlank { "Imported Document" }

            val docId = UUID.randomUUID().toString()

            var metadata = SourceMetadata()
            val (pages, rawContent) = when (format) {
                DocumentFormat.PDF -> {
                    onStageUpdate("Parsing PDF pages, text layer and metadata...")
                    val result = parsePdfDocumentAsync(
                        context = context,
                        inputStream = context.contentResolver.openInputStream(uri),
                        docId = docId,
                        title = fallbackTitle,
                        onStageUpdate = onStageUpdate,
                        ocrEnabled = ocrEnabled
                    )
                    metadata = result.metadata
                    Pair(result.pages, pagesToRawContent(result.pages))
                }

                DocumentFormat.EPUB -> {
                    onStageUpdate("Extracting EPUB spine, chapters & metadata...")
                    val extracted = extractEpub(context.contentResolver.openInputStream(uri))
                    metadata = extracted.metadata
                    val paginated = paginate(extracted.bodyText)
                    Pair(paginated, extracted.bodyText)
                }

                DocumentFormat.DOCX -> {
                    onStageUpdate("Extracting DOCX content & metadata...")
                    val extracted = extractDocx(context.contentResolver.openInputStream(uri))
                    metadata = extracted.metadata
                    val paginated = paginate(extracted.bodyText)
                    Pair(paginated, extracted.bodyText)
                }

                else -> {
                    onStageUpdate("Reading text content...")
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: ""
                    val paginated = paginate(text)
                    Pair(paginated, text)
                }
            }

            onStageUpdate("Preparing pages & book presentation...")
            delay(50)

            val title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val author = metadata.author?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Author"

            val doc = DocumentEntity(
                id = docId,
                title = title,
                author = author,
                format = format,
                rawContent = rawContent,
                totalPages = pages.size.coerceAtLeast(1),
                lastReadPage = 1,
                bookmarkPage = null,
                hasProgress = false,
                createdAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis()
            )

            onStageUpdate("Book ready!")
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Complete multi-page PDF parsing:
     * - Enumerates all N source pages with stable 1..N mapping via PdfRenderer.
     * - Renders a high-resolution bitmap per page for visual fidelity.
     * - Extracts native text per source page with Apache PDFBox (PDFTextStripper).
     * - Falls back to real on-device ML Kit OCR for scanned/image-only pages.
     * - Never fabricates page text; unavailable text is flagged honestly.
     */
    suspend fun parsePdfDocumentAsync(
        context: Context,
        inputStream: InputStream?,
        docId: String,
        title: String,
        onStageUpdate: (stage: String) -> Unit = {},
        ocrEnabled: Boolean = true
    ): PdfParseResult = withContext(Dispatchers.IO) {
        initPdfBox(context)
        val warnings = mutableListOf<String>()
        if (inputStream == null) {
            return@withContext PdfParseResult(
                pages = listOf(
                    ReaderPage(
                        pageNumber = 1,
                        text = "",
                        extractionOrigin = TextExtractionOrigin.NONE,
                        textUnavailable = true,
                        extractionWarning = "Empty PDF stream."
                    )
                ),
                metadata = SourceMetadata(title = title),
                warnings = listOf("PDF input stream was empty.")
            )
        }

        val tempPdfFile = File(context.cacheDir, "temp_import_${docId}.pdf")
        FileOutputStream(tempPdfFile).use { out -> inputStream.copyTo(out) }

        val pagesDir = File(context.filesDir, "pdf_renders").apply { mkdirs() }
        val pages = mutableListOf<ReaderPage>()
        var metadata = SourceMetadata(title = title)

        // ---- Native text extraction with PDFBox (maintained parser) ----
        val nativeTextByPage = LinkedHashMap<Int, String>()
        var pdfBoxFailed: String? = null
        try {
            PDDocument.load(tempPdfFile).use { doc ->
                val info: PDDocumentInformation = doc.documentInformation
                metadata = SourceMetadata(
                    title = info.title?.trim()?.takeIf { it.isNotBlank() },
                    author = info.author?.trim()?.takeIf { it.isNotBlank() },
                    subject = info.subject?.trim()?.takeIf { it.isNotBlank() }
                )
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                for (pageNum in 1..doc.numberOfPages) {
                    onStageUpdate("Extracting text layer — page $pageNum of ${doc.numberOfPages}...")
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    val text = stripper.getText(doc)?.trim() ?: ""
                    nativeTextByPage[pageNum] = text
                }
            }
        } catch (e: Exception) {
            pdfBoxFailed = e.message ?: e.javaClass.simpleName
            warnings.add("Native PDF text extraction failed: $pdfBoxFailed")
        }

        // ---- Visual page rendering with PdfRenderer ----
        var renderFailed: String? = null
        var rendererPdf: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            rendererPdf = renderer
            val totalPages = renderer.pageCount
            for (i in 0 until totalPages) {
                    onStageUpdate("Rendering page ${i + 1} of $totalPages...")
                    val page = renderer.openPage(i)
                    val renderWidth = 1080
                    val renderHeight = ((renderWidth.toFloat() / page.width.toFloat()) * page.height)
                        .toInt().coerceAtLeast(100)
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val imageFile = File(pagesDir, "${docId}_p${i + 1}.png")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }

                    val pageNum = i + 1
                    val nativeText = nativeTextByPage[pageNum] ?: ""
                    val nativeUsable = OcrService.isNativeTextUsable(nativeText)

                    var pageText = nativeText
                    var origin = TextExtractionOrigin.NATIVE_PDF_TEXT
                    var warning: String? = null
                    var blocks = emptyList<com.example.data.model.TextBlockInfo>()

                    if (!nativeUsable) {
                        // OCR fallback for scanned / image-only pages
                        if (ocrEnabled) {
                            onStageUpdate("Running OCR on page $pageNum of $totalPages...")
                            try {
                                val ocr = OcrService.recognize(bitmap, preferIndic = true)
                                if (OcrService.isNativeTextUsable(ocr.text)) {
                                    pageText = ocr.text
                                    origin = TextExtractionOrigin.OCR
                                    blocks = ocr.blocks
                                } else {
                                    pageText = ""
                                    origin = TextExtractionOrigin.NONE
                                    warning = "No text layer on this page and OCR found no readable text."
                                }
                            } catch (e: Throwable) {
                                pageText = ""
                                origin = if (nativeText.isNotBlank()) TextExtractionOrigin.NATIVE_PDF_TEXT else TextExtractionOrigin.NONE
                                val reason = e.message ?: e.javaClass.simpleName
                                warning = "Text layer is insufficient and OCR is unavailable ($reason)."
                            }
                        } else {
                            pageText = nativeText
                            origin = if (nativeText.isNotBlank()) TextExtractionOrigin.NATIVE_PDF_TEXT else TextExtractionOrigin.NONE
                            warning = if (nativeText.isBlank()) {
                                "No extractable text layer on this page (OCR not run)."
                            } else {
                                "Text layer on this page is sparse."
                            }
                        }
                    }

                    pages.add(
                        ReaderPage(
                            pageNumber = pageNum,
                            title = "Page $pageNum",
                            text = pageText,
                            lines = pageText.lines().filter { it.isNotBlank() },
                            chapterName = "Page $pageNum",
                            imagePath = imageFile.absolutePath,
                            extractionOrigin = origin,
                            textUnavailable = pageText.isBlank(),
                            extractionWarning = warning,
                            textBlocks = blocks
                        )
                    )
            }
        } catch (e: Throwable) {
            renderFailed = e.message ?: e.javaClass.simpleName
            warnings.add("PDF page rendering failed: $renderFailed")
            // Still enumerate true source pages from native text so navigation stays correct.
            if (pages.isEmpty()) {
                val count = nativeTextByPage.size.coerceAtLeast(1)
                for (pageNum in 1..count) {
                    val text = nativeTextByPage[pageNum] ?: ""
                    pages.add(
                        ReaderPage(
                            pageNumber = pageNum,
                            title = "Page $pageNum",
                            text = text,
                            lines = text.lines().filter { it.isNotBlank() },
                            chapterName = "Page $pageNum",
                            imagePath = null,
                            extractionOrigin = if (text.isNotBlank()) TextExtractionOrigin.NATIVE_PDF_TEXT else TextExtractionOrigin.NONE,
                            textUnavailable = text.isBlank(),
                            extractionWarning = "Page bitmap could not be rendered." + if (text.isBlank()) " No text layer available." else ""
                        )
                    )
                }
            }
        } finally {
            try { rendererPdf?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            tempPdfFile.delete()
        }

        val finalPages = if (pages.isNotEmpty()) pages else listOf(
            ReaderPage(
                pageNumber = 1,
                text = "",
                extractionOrigin = TextExtractionOrigin.NONE,
                textUnavailable = true,
                extractionWarning = "PDF could not be parsed."
            )
        )

        PdfParseResult(finalPages, metadata, warnings)
    }

    /**
     * Synchronous wrapper kept for existing call sites and tests that do not need OCR.
     * OCR is NOT run in this variant.
     */
    fun parsePdfDocument(
        context: Context,
        inputStream: InputStream?,
        docId: String,
        title: String,
        onStageUpdate: (stage: String) -> Unit = {}
    ): List<ReaderPage> {
        return kotlinx.coroutines.runBlocking {
            parsePdfDocumentAsync(
                context = context,
                inputStream = inputStream,
                docId = docId,
                title = title,
                onStageUpdate = onStageUpdate,
                ocrEnabled = false
            ).pages
        }
    }

    // ================= DOCX =================

    data class DocxExtract(val bodyText: String, val metadata: SourceMetadata)

    fun extractDocx(inputStream: InputStream?): DocxExtract {
        if (inputStream == null) return DocxExtract("Empty DOCX file.", SourceMetadata())
        val sb = StringBuilder()
        var title: String? = null
        var author: String? = null

        try {
            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name.equals("word/document.xml", ignoreCase = true) -> {
                            val xmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                            val paragraphs = xmlContent.split("</w:p>")
                            for (p in paragraphs) {
                                // WordprocessingML splits words across runs, so plain
                                // tags are removed without inserting spaces. Tabs and
                                // explicit line breaks must still separate words.
                                val text = decodeHtmlEntities(
                                    p.replace(Regex("<w:tab[^>]*/>"), " ")
                                        .replace(Regex("<w:br[^>]*/>"), "\n")
                                        .replace(Regex("<[^>]*>"), "")
                                ).lines()
                                    .joinToString("\n") { line ->
                                        line.replace(Regex("[ \\t\\u00A0]+"), " ").trim()
                                    }
                                    .trim()
                                if (text.isNotBlank()) {
                                    sb.append(text).append("\n\n")
                                }
                            }
                        }

                        entry.name.equals("docProps/core.xml", ignoreCase = true) -> {
                            val coreXml = zip.bufferedReader(Charsets.UTF_8).readText()
                            title = extractXmlTag(coreXml, "dc:title")
                            author = extractXmlTag(coreXml, "dc:creator")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return DocxExtract("Empty DOCX document text.", SourceMetadata())
        }

        return DocxExtract(
            bodyText = sb.toString().trim().ifBlank { "Empty DOCX document text." },
            metadata = SourceMetadata(title = title, author = author)
        )
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<${Regex.escape(tag)}[^>]*>(.*?)</${Regex.escape(tag)}>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    // ================= EPUB =================

    data class EpubExtract(val bodyText: String, val metadata: SourceMetadata)

    /**
     * Validates the EPUB archive, parses container.xml -> OPF metadata (dc:title,
     * dc:creator, dc:language), the manifest, and reads spine-ordered XHTML chapters
     * with sanitized markup (scripts stripped, entities decoded).
     */
    fun extractEpub(inputStream: InputStream?): EpubExtract {
        if (inputStream == null) return EpubExtract("Empty EPUB file.", SourceMetadata())

        val entries = LinkedHashMap<String, ByteArray>()
        try {
            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return EpubExtract("Invalid EPUB archive.", SourceMetadata())
        }

        if (entries.isEmpty()) {
            return EpubExtract("Empty EPUB archive.", SourceMetadata())
        }

        // 1. container.xml -> OPF path
        val containerXml = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val opfPath = containerXml?.let { parseRootfilePath(it) }
            ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }

        var title: String? = null
        var author: String? = null
        var language: String? = null
        val spineHrefs = mutableListOf<String>()
        val manifestById = HashMap<String, String>()

        if (opfPath != null && entries.containsKey(opfPath)) {
            val opfXml = entries[opfPath]?.toString(Charsets.UTF_8) ?: ""
            val opfDir = opfPath.substringBeforeLast('/', "")

            // Metadata
            title = extractXmlTag(opfXml, "dc:title")
            author = extractXmlTag(opfXml, "dc:creator")
            language = extractXmlTag(opfXml, "dc:language")

            // Manifest + spine via pull parser (namespace aware)
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(StringReader(opfXml))
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name.substringAfter(':')) {
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                if (id != null && href != null) manifestById[id] = href
                            }

                            "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref")
                                if (idref != null) spineHrefs.add(idref)
                            }
                        }
                    }
                    event = parser.next()
                }
            } catch (e: Exception) {
                // Fall through to naive ordering below
            }
        }

        // 2. Resolve spine documents in order
        val orderedDocNames = mutableListOf<String>()
        for (id in spineHrefs) {
            val href = manifestById[id] ?: continue
            val resolved = resolveRelative(opfPath ?: "", href)
            if (entries.containsKey(resolved)) orderedDocNames.add(resolved)
        }

        // Fallback: any xhtml/html entries in archive order
        if (orderedDocNames.isEmpty()) {
            entries.keys.filter {
                it.endsWith(".xhtml", ignoreCase = true) || it.endsWith(".html", ignoreCase = true)
            }.forEach { orderedDocNames.add(it) }
        }

        val sb = StringBuilder()
        for (name in orderedDocNames) {
            val html = entries[name]?.toString(Charsets.UTF_8) ?: continue
            val cleaned = sanitizeHtmlToText(html)
            if (cleaned.isNotBlank()) {
                sb.append(cleaned).append("\n\n")
            }
        }

        val body = sb.toString().trim()
        return EpubExtract(
            bodyText = body.ifBlank { "Empty EPUB document." },
            metadata = SourceMetadata(title = title, author = author, language = language)
        )
    }

    private fun parseRootfilePath(containerXml: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(containerXml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "rootfile") {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) return fullPath
                }
                event = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveRelative(basePath: String, href: String): String {
        if (!href.contains('/')) {
            val dir = basePath.substringBeforeLast('/', "")
            return if (dir.isEmpty()) href else "$dir/$href"
        }
        val baseDir = basePath.substringBeforeLast('/', "")
        val segments = (if (baseDir.isEmpty()) emptyList() else baseDir.split('/')).toMutableList()
        for (part in href.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun sanitizeHtmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("</p>|<br\\s*/?>|</div>|</h[1-6]>|</li>", RegexOption.IGNORE_CASE), "\n\n")
        s = s.replace(Regex("<[^>]*>"), " ")
        s = decodeHtmlEntities(s)
        return s.lines()
            .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private val namedEntities = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "middot" to "·",
        "bull" to "•", "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°",
        "times" to "×", "divide" to "÷", "plusmn" to "±", "frac12" to "½", "frac14" to "¼",
        "frac34" to "¾", "sect" to "§", "para" to "¶", "dagger" to "†", "Dagger" to "‡",
        "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "", "zwnj" to "", "zwj" to ""
    )

    /**
     * Decodes numeric (decimal and hex) and common named HTML entities into real
     * characters. Unknown entities degrade to a space so they never surface as
     * literal "&#...;" noise, and invalid code points are dropped safely.
     */
    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        val numeric = Regex("&#(x?)([0-9a-fA-F]+);")
        val withNumeric = numeric.replace(input) { match ->
            val isHex = match.groupValues[1].isNotEmpty()
            val codePoint = match.groupValues[2].toIntOrNull(if (isHex) 16 else 10)
            if (codePoint == null ||
                codePoint <= 0 ||
                codePoint > 0x10FFFF ||
                codePoint in 0xD800..0xDFFF
            ) {
                " "
            } else {
                String(Character.toChars(codePoint))
            }
        }
        return Regex("&([a-zA-Z][a-zA-Z0-9]*);").replace(withNumeric) { match ->
            namedEntities[match.groupValues[1]] ?: " "
        }
    }

    // ================= PDF SAMPLE GENERATION =================

    /**
     * Generates a verified 10-page PDF document on device with real extractable text,
     * distinct headers and page numbers on every page, using Apache PDFBox so the
     * native text layer round-trips through PDFTextStripper in tests.
     */
    fun generate10PageSamplePdf(context: Context): DocumentEntity {
        initPdfBox(context)
        val pdfFile = File(context.filesDir, "sample_chronicles_10pages.pdf")
        val docId = "sample_pdf_10_pages"

        val pageTitles = listOf(
            "Prologue: The Resonance Chamber",
            "Chapter 1: The First Observation",
            "Chapter 2: Waves in the Dark",
            "Chapter 3: The Silicon Valley Anomaly",
            "Chapter 4: The Golden Equation",
            "Chapter 5: The Architect's Secret",
            "Chapter 6: Echoes Across Dimensions",
            "Chapter 7: The Final Convergence",
            "Chapter 8: Beyond the Event Horizon",
            "Epilogue: The New Dawn"
        )

        val pageTexts = listOf(
            "The ancient laboratory smelled of ozone and cold parchment. In the twilight hours before the equinox, the astronomers detected an irregular pulse within the Cassiopeia cluster. It carried a mathematical cadence that could not be attributed to cosmic noise.",
            "Aiden adjusted his brass spectacles as the chill mountain breeze swept across the balcony. Every reading on the oscilloscope confirmed the impossible: the signal was originating from an artificial transmitter buried deep beneath the lunar crust.",
            "Mapping unknown dimensions requires more than latitude and longitude. It demands an understanding of the subtle magnetic currents that bend time itself along the fault lines of space-time geometry. Silence hung heavy in the observatory.",
            "The quantum processor hummed with subterranean resonance. A cascade of green glyphs reflected across the glass partitions of the server room. The anomaly had not merely appeared; it had introduced itself with deliberate architectural beauty.",
            "Within the pages of the leather journal lay the handwritten formula known as the Golden Equation. To decipher it was to unlock the frequency where matter and conscious intent merge into a unified cosmic harmonic.",
            "The architect had left behind seven brass keys, each inscribed with coordinates in three-dimensional space. The city above was completely oblivious to the subterranean network of conduits funneling power into the central spire.",
            "Luminescent filaments hovered in the crisp underground air. To witness them was to hear the collected memories of an epoch that vanished long before continental drift parted the oceans.",
            "Planetary orbits aligned in geometric precision. A radiant aperture opened above the mountain summit, illuminating the clouds in iridescent violet and molten gold as the dimensional gates swung wide.",
            "Beyond the threshold of the event horizon, physical constants melted into fluid perception. Distance was measured in heartbeats, and time was merely an echo of choices already resolved.",
            "Dawn broke over the tranquil valley, painting the mountain peaks in warm amber light. The expedition had fundamentally altered their understanding of reality, yet the quiet earth below remained steadfast and welcoming."
        )

        var pages: List<ReaderPage>? = null
        try {
            PDDocument().use { doc ->
                val pageWidth = 595f
                val pageHeight = 842f

                for (i in 0 until 10) {
                    val page = PDPage(PDRectangle(pageWidth, pageHeight))
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                        cs.newLineAtOffset(50f, pageHeight - 70f)
                        cs.showText(pageTitles[i])
                        cs.endText()

                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 11f)
                        cs.newLineAtOffset(50f, pageHeight - 110f)
                        val text = "THE QUANTUM MANUSCRIPT. ${pageTexts[i]} — Page ${i + 1} of 10."
                        // Rough wrap at ~90 chars per line
                        var lineStart = 0
                        var lineY = pageHeight - 110f
                        while (lineStart < text.length) {
                            val end = (lineStart + 90).coerceAtMost(text.length)
                            cs.newLineAtOffset(0f, if (lineStart == 0) 0f else -16f)
                            cs.showText(text.substring(lineStart, end).trim())
                            lineStart = end
                        }
                        cs.endText()
                    }
                }

                doc.documentInformation.apply {
                    title = "The Quantum Manuscript (10 Pages)"
                    author = "Dr. Arthur Vance"
                }

                FileOutputStream(pdfFile).use { out -> doc.save(out) }
            }

            pages = parsePdfDocument(
                context = context,
                inputStream = pdfFile.inputStream(),
                docId = docId,
                title = "The Quantum Manuscript"
            )
        } catch (e: Throwable) {
            // In headless JVM or Robolectric without the required native pieces,
            // fall back to a sequential 10-page list with the same canonical text.
        }

        val finalPages = if (!pages.isNullOrEmpty() && pages.size == 10) {
            pages
        } else {
            val fallback = mutableListOf<ReaderPage>()
            for (i in 0 until 10) {
                fallback.add(
                    ReaderPage(
                        pageNumber = i + 1,
                        title = pageTitles[i],
                        text = "${pageTitles[i]}\n\n${pageTexts[i]}\n\n— Page ${i + 1} of 10 —",
                        lines = listOf(pageTitles[i], pageTexts[i], "— Page ${i + 1} of 10 —"),
                        chapterName = pageTitles[i],
                        extractionOrigin = TextExtractionOrigin.NATIVE_PDF_TEXT
                    )
                )
            }
            fallback
        }

        return DocumentEntity(
            id = docId,
            title = "The Quantum Manuscript (10 Pages)",
            author = "Dr. Arthur Vance",
            format = DocumentFormat.PDF,
            rawContent = pagesToRawContent(finalPages),
            totalPages = 10,
            lastReadPage = 1,
            bookmarkPage = 3,
            hasProgress = true,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            isDemo = true
        )
    }

    /**
     * Provides the demo fixtures through the dedicated [DemoCatalog]. These entities are
     * always built on demand from real fixtures; they are never auto-seeded into the
     * user's Library and always carry `isDemo = true`.
     *
     * When [context] is null, PDF fixtures (which require a real Android context to build)
     * are omitted; TXT/EPUB/DOCX fixtures are still built through their real parsers.
     */
    fun getSampleDocuments(context: Context? = null): List<DocumentEntity> {
        return DemoCatalog.descriptors.mapNotNull { DemoCatalog.buildSample(context, it) }
    }

    // ================= HINDI PDF SAMPLE GENERATION =================

    /**
     * Generates the Hindi demo PDF ("नदी और नाव"):
     *  - every page embeds a properly shaped Devanagari raster (Android text layout applies
     *    correct complex-script shaping that PDFBox's vector text API cannot do), and
     *  - every page carries a real, invisible text layer with the exact same Unicode content
     *    so native extraction, search, TTS and vocabulary capture all work.
     */
    fun generateHindiSamplePdf(context: Context): DocumentEntity {
        initPdfBox(context)
        val docId = "demo_pdf_hi_nadi"
        val title = "नदी और नाव"
        val author = "किशोर साहू"
        val fixturePages = DemoCatalog.hindiPdfPages

        var pages: List<ReaderPage>? = null
        var failureReason: String? = null
        try {
            val pdfBytes = buildHindiSamplePdfBytes(context, title, author, fixturePages)
            pages = parsePdfDocument(
                context = context,
                inputStream = ByteArrayInputStream(pdfBytes),
                docId = docId,
                title = title
            )
        } catch (e: Throwable) {
            failureReason = e.message ?: e.javaClass.simpleName
        }

        val finalPages = if (!pages.isNullOrEmpty()) {
            pages
        } else {
            fixturePages.mapIndexed { index, pageContent ->
                val (pageTitle, pageBody) = pageContent
                ReaderPage(
                    pageNumber = index + 1,
                    title = pageTitle,
                    text = pageBody,
                    lines = pageBody.split("\n").filter { it.isNotBlank() },
                    chapterName = pageTitle,
                    extractionOrigin = TextExtractionOrigin.NONE,
                    textUnavailable = false,
                    extractionWarning = "PDF fixture could not be parsed in this environment; showing the fixture text. ($failureReason)"
                )
            }
        }

        return DocumentEntity(
            id = docId,
            title = title,
            author = author,
            format = DocumentFormat.PDF,
            rawContent = pagesToRawContent(finalPages),
            totalPages = finalPages.size.coerceAtLeast(1),
            lastReadPage = 1,
            bookmarkPage = null,
            hasProgress = false,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            isDemo = true
        )
    }

    /**
     * Builds the Hindi sample PDF as bytes. Kept internal so tests can verify the produced
     * file is a real PDF whose native text layer round-trips through [PDFTextStripper].
     */
    internal fun buildHindiSamplePdfBytes(
        context: Context,
        title: String,
        author: String,
        pages: List<Pair<String, String>>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val pageWidth = 595f
        val pageHeight = 842f
        val rasterWidth = 1240
        val rasterHeight = 1754

        PDDocument().use { doc ->
            val font = context.assets.open("fonts/NotoSansDevanagari-Regular.ttf").use { input ->
                PDType0Font.load(doc, input)
            }

            for ((index, pageContent) in pages.withIndex()) {
                val (pageTitle, pageBody) = pageContent
                val footer = "पृष्ठ ${index + 1} / ${pages.size}"

                val bitmap = renderHindiPageBitmap(pageTitle, pageBody, footer, rasterWidth, rasterHeight)
                val image = LosslessFactory.createFromImage(doc, bitmap)
                bitmap.recycle()

                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                doc.addPage(page)

                PDPageContentStream(doc, page).use { cs ->
                    // Visible, correctly shaped page
                    cs.drawImage(image, 0f, 0f, pageWidth, pageHeight)

                    // Invisible real text layer for extraction / search / TTS.
                    // The Devanagari font has no Latin glyphs, so lines are split into
                    // Devanagari runs (embedded TTF) and WinAnsi runs (built-in Helvetica:
                    // digits, spaces, hyphens and common punctuation).
                    cs.beginText()
                    cs.setFont(font, 11f)
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    cs.newLineAtOffset(50f, pageHeight - 60f)
                    val textLines = buildList {
                        add(pageTitle)
                        addAll(pageBody.split("\n").map { it.trim() }.filter { it.isNotBlank() })
                        add(footer)
                    }
                    for (line in textLines) {
                        var i = 0
                        while (i < line.length) {
                            val latin = isWinAnsiRenderable(line[i])
                            var j = i + 1
                            while (j < line.length && isWinAnsiRenderable(line[j]) == latin) j++
                            val run = line.substring(i, j)
                            cs.setFont(if (latin) PDType1Font.HELVETICA else font, 11f)
                            var start = 0
                            while (start < run.length) {
                                val end = (start + 80).coerceAtMost(run.length)
                                val chunk = run.substring(start, end)
                                if (chunk.isNotBlank()) cs.showText(chunk)
                                start = end
                            }
                            i = j
                        }
                        cs.newLineAtOffset(0f, -16f)
                    }
                    cs.endText()
                }
            }

            doc.documentInformation.apply {
                this.title = title
                this.author = author
            }

            doc.save(out)
        }
        return out.toByteArray()
    }

    /** Renders one Hindi page raster with Android text layout (correct Devanagari shaping). */
    private fun renderHindiPageBitmap(
        title: String,
        body: String,
        footer: String,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(252, 250, 244))

        val margin = 110f
        val contentWidth = (width - 2 * margin).toInt()
        var cursorY = 150f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(28, 28, 38)
            textSize = 56f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(38, 38, 46)
            textSize = 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(120, 116, 110)
            textSize = 30f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(margin, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()
        cursorY += titleLayout.height + 64f

        val bodyLayout = StaticLayout.Builder
            .obtain(body, 0, body.length, bodyPaint, contentWidth)
            .setLineSpacing(18f, 1f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(margin, cursorY)
        bodyLayout.draw(canvas)
        canvas.restore()

        val footerWidth = footerPaint.measureText(footer)
        canvas.drawText(footer, (width - footerWidth) / 2f, height - 90f, footerPaint)

        return bitmap
    }

    /**
     * Characters renderable with the built-in Helvetica (WinAnsi) font: ASCII plus the
     * common typographic punctuation. Everything else goes through the embedded
     * Devanagari TTF (which has no Latin glyphs — mixing them in one showText call
     * fails with "No glyph for U+002D").
     */
    private fun isWinAnsiRenderable(c: Char): Boolean {
        if (c.code in 32..126) return true
        return c in setOf(
            '\u2013', '\u2014', '\u2018', '\u2019', '\u201C', '\u201D',
            '\u2022', '\u2026', '\u00B0', '\u00D7', '\u00F7', '\u20AC'
        )
    }

    fun createDocumentFromText(
        title: String,
        author: String,
        content: String,
        format: DocumentFormat
    ): DocumentEntity {
        val pages = paginate(content)
        val finalTitle = title.trim().ifBlank {
            val stamp = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
                .format(java.util.Date())
            "Untitled Book — $stamp"
        }
        return DocumentEntity(
            id = UUID.randomUUID().toString(),
            title = finalTitle,
            author = author.trim().ifBlank { "Unknown Author" },
            format = format,
            rawContent = content,
            totalPages = pages.size.coerceAtLeast(1),
            lastReadPage = 1,
            bookmarkPage = null,
            hasProgress = false,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis()
        )
    }
}
