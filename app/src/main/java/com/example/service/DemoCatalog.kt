package com.example.service

import android.content.Context
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Descriptor for one bundled demo fixture. Demo samples are intentionally NOT part of
 * the user's Library: they live in this catalog, are built on demand through the real
 * parser pipeline, and are never auto-inserted into persistent storage.
 */
data class DemoSampleDescriptor(
    val id: String,
    val title: String,
    val author: String,
    val format: DocumentFormat,
    val languageLabel: String
) {
    /** Visible chip label: format + title + language, e.g. "PDF · Quantum Manuscript (English)". */
    val chipLabel: String get() = "${format.name} · $title ($languageLabel)"
}

/**
 * The intentional demo catalog: 8 samples, four English and four Hindi, covering the
 * four supported file formats (PDF, TXT, EPUB, DOCX).
 *
 * Each fixture is real for its declared format:
 *  - PDF samples are genuine PDF files (generated with PDFBox; the Hindi one embeds a
 *    properly shaped page image plus an invisible real text layer),
 *  - EPUB samples are real EPUB zips parsed through the EPUB extractor,
 *  - DOCX samples are real DOCX zips parsed through the DOCX extractor,
 *  - TXT samples are real plain text.
 */
object DemoCatalog {

    val descriptors: List<DemoSampleDescriptor> = listOf(
        // ---- Four English samples ----
        DemoSampleDescriptor(
            id = "sample_pdf_10_pages",
            title = "The Quantum Manuscript",
            author = "Dr. Arthur Vance",
            format = DocumentFormat.PDF,
            languageLabel = "English"
        ),
        DemoSampleDescriptor(
            id = "demo_txt_en_lighthouse",
            title = "The Lighthouse Letters",
            author = "Mara Ellison",
            format = DocumentFormat.TXT,
            languageLabel = "English"
        ),
        DemoSampleDescriptor(
            id = "demo_epub_en_observatory",
            title = "Songs of the Observatory",
            author = "Dr. Nova Reyes",
            format = DocumentFormat.EPUB,
            languageLabel = "English"
        ),
        DemoSampleDescriptor(
            id = "demo_docx_en_draft",
            title = "The Director's Draft",
            author = "E. Vance",
            format = DocumentFormat.DOCX,
            languageLabel = "English"
        ),

        // ---- Four Hindi samples ----
        DemoSampleDescriptor(
            id = "demo_pdf_hi_nadi",
            title = "नदी और नाव",
            author = "किशोर साहू",
            format = DocumentFormat.PDF,
            languageLabel = "हिन्दी"
        ),
        DemoSampleDescriptor(
            id = "sample_hindi_panch",
            title = "पंच परमेश्वर",
            author = "मुंशी प्रेमचंद",
            format = DocumentFormat.TXT,
            languageLabel = "हिन्दी"
        ),
        DemoSampleDescriptor(
            id = "demo_epub_hi_jangal",
            title = "जंगल की राह",
            author = "सुधा मिश्रा",
            format = DocumentFormat.EPUB,
            languageLabel = "हिन्दी"
        ),
        DemoSampleDescriptor(
            id = "demo_docx_hi_tyohar",
            title = "त्योहार की यादें",
            author = "रमेश चंद्र",
            format = DocumentFormat.DOCX,
            languageLabel = "हिन्दी"
        )
    )

    fun findIndexById(id: String): Int = descriptors.indexOfFirst { it.id == id }

    /**
     * Builds the demo fixture for [index] through the real parsing pipeline of its format.
     * The returned entity is an in-memory reader session only — callers must NOT insert it
     * into the Library unless the user explicitly asked to.
     */
    fun buildSample(context: Context?, index: Int): DocumentEntity? {
        val descriptor = descriptors.getOrNull(index) ?: return null
        return buildSample(context, descriptor)
    }

    fun buildSample(context: Context?, descriptor: DemoSampleDescriptor): DocumentEntity? {
        return when (descriptor.format) {
            DocumentFormat.PDF -> {
                // PDF fixtures require an Android context (asset font + page raster storage).
                if (context == null) return null
                if (descriptor.id == "demo_pdf_hi_nadi") {
                    DocumentParserService.generateHindiSamplePdf(context)
                } else {
                    DocumentParserService.generate10PageSamplePdf(context)
                }
            }

            DocumentFormat.TXT -> {
                val text = when (descriptor.id) {
                    "demo_txt_en_lighthouse" -> lighthouseLettersText
                    "sample_hindi_panch" -> panchParmeshwarText
                    else -> return null
                }
                plainTextDocument(descriptor, text)
            }

            DocumentFormat.EPUB -> {
                val chapters = when (descriptor.id) {
                    "demo_epub_en_observatory" -> observatoryChapters
                    "demo_epub_hi_jangal" -> jangalChapters
                    else -> return null
                }
                val bytes = buildEpubBytes(
                    title = descriptor.title,
                    author = descriptor.author,
                    language = if (descriptor.languageLabel == "हिन्दी") "hi" else "en",
                    chapters = chapters
                )
                val extracted = DocumentParserService.extractEpub(ByteArrayInputStream(bytes))
                DocumentEntity(
                    id = descriptor.id,
                    title = extracted.metadata.title?.takeIf { it.isNotBlank() } ?: descriptor.title,
                    author = extracted.metadata.author?.takeIf { it.isNotBlank() } ?: descriptor.author,
                    format = DocumentFormat.EPUB,
                    rawContent = extracted.bodyText,
                    totalPages = DocumentParserService.paginate(extracted.bodyText).size.coerceAtLeast(1),
                    lastReadPage = 1,
                    isDemo = true
                )
            }

            DocumentFormat.DOCX -> {
                val paragraphs = when (descriptor.id) {
                    "demo_docx_en_draft" -> directorDraftParagraphs
                    "demo_docx_hi_tyohar" -> tyoharParagraphs
                    else -> return null
                }
                val bytes = buildDocxBytes(
                    title = descriptor.title,
                    author = descriptor.author,
                    paragraphs = paragraphs
                )
                val extracted = DocumentParserService.extractDocx(ByteArrayInputStream(bytes))
                DocumentEntity(
                    id = descriptor.id,
                    title = extracted.metadata.title?.takeIf { it.isNotBlank() } ?: descriptor.title,
                    author = extracted.metadata.author?.takeIf { it.isNotBlank() } ?: descriptor.author,
                    format = DocumentFormat.DOCX,
                    rawContent = extracted.bodyText,
                    totalPages = DocumentParserService.paginate(extracted.bodyText).size.coerceAtLeast(1),
                    lastReadPage = 1,
                    isDemo = true
                )
            }

            else -> null
        }
    }

    private fun plainTextDocument(descriptor: DemoSampleDescriptor, text: String): DocumentEntity {
        return DocumentEntity(
            id = descriptor.id,
            title = descriptor.title,
            author = descriptor.author,
            format = DocumentFormat.TXT,
            rawContent = text,
            totalPages = DocumentParserService.paginate(text).size.coerceAtLeast(1),
            lastReadPage = 1,
            isDemo = true
        )
    }

    // ---------------------------------------------------------------------
    // Real fixture builders (valid EPUB / DOCX zip containers)
    // ---------------------------------------------------------------------

    private fun buildEpubBytes(
        title: String,
        author: String,
        language: String,
        chapters: List<Pair<String, String>>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // EPUB spec: "mimetype" must be the first entry and stored uncompressed.
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = java.util.zip.CRC32().apply { update(mimeBytes) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            val manifestItems = chapters.indices.joinToString("\n") { i ->
                """<item id="ch${i + 1}" href="ch${i + 1}.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spineItems = chapters.indices.joinToString("\n") { i ->
                """<itemref idref="ch${i + 1}"/>"""
            }
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">storyflip-demo-${title.hashCode()}</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:language>$language</dc:language>
                  </metadata>
                  <manifest>
                    $manifestItems
                  </manifest>
                  <spine>
                    $spineItems
                  </spine>
                </package>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            for ((i, chapter) in chapters.withIndex()) {
                val (chapterTitle, body) = chapter
                val paragraphs = body.trim().split("\n\n").joinToString("\n") {
                    "<p>${it.trim().replace("\n", " ")}</p>"
                }
                zip.putNextEntry(ZipEntry("OEBPS/ch${i + 1}.xhtml"))
                zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>$chapterTitle</title></head>
                      <body>
                        <h1>$chapterTitle</h1>
                        $paragraphs
                      </body>
                    </html>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun buildDocxBytes(
        title: String,
        author: String,
        paragraphs: List<String>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                </Types>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            val runs = paragraphs.joinToString("\n") { para ->
                val escaped = para
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                "<w:p><w:r><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>"
            }
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    $runs
                  </w:body>
                </w:document>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("docProps/core.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                   xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>$title</dc:title>
                  <dc:creator>$author</dc:creator>
                </cp:coreProperties>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------------
    // Fixture content
    // ---------------------------------------------------------------------

    private val lighthouseLettersText = """
LETTER ONE: THE FIRST WINTER

Dear Elsie,

The lamp is lit and the wind is singing against the glass again. I count the ships by their
foghorns now — three tonight, all of them safe past the Shoals. Mrs. Hartley at the post office
says you write nearly every week, but the mail boat has not come since the ice. I am keeping your
letters in the tin box beside the logbook, and when the spring thaw opens the harbor I will read
them all in one long evening, with the kettle on and the cat asleep on my boots.

The new lens you helped me polish throws a beam almost to the horizon. On clear nights I can see
the village lights across the bay like a little constellation somebody dropped. I think about you
standing at the kitchen window, watching for the same light, and the distance between us feels
smaller than it is.

Your father,
Thomas

LETTER TWO: THE STORM

Dear Elsie,

Forgive the shaky hand. We took a blow last night that tore the weathervane clean off the roof.
The sea came over the west rocks like a grey wall, and for two hours I stood at the lamp and kept
the flame steady, because I knew the S.S. Marigold was still out there running for home. She made
it to the harbor at dawn with her pumps failing and her captain standing at the bow, waving his
hat at the light. That wave was for the lamp. But I am telling myself it was for you, since you
are the reason the lamp is kept.

The barometer is rising. I have patched the roof with tar and prayer. The cat refuses to go
outside, which is the only weather forecast I fully trust.

Your father,
Thomas

LETTER THREE: THE LAST SUMMER

Dear Elsie,

The ice is gone and the mail boat brought your letters in a canvas sack that smelled of city
rain. I read them at the kitchen table until the wick burned low — your examinations, your
professor with the crooked spectacles, the boarding house cat you have named after me, which I
consider an honor and a slight. You ask whether I am lonely here. I am not. The work is good
work. Every night the lamp turns and the sea answers with its long, patient breath, and I
understand my own father a little better for having kept the same watch he kept.

In October I will put out the light for the last time; the electric beacon on the headland
takes over at Michaelmas. I have been asked to train the young keeper, a serious boy from
Porthcawl who polishes the brass like it owes him money. I will teach him the two rules of this
tower. Keep the flame. Keep the letters.

Come home when the term ends. There is a room with your name on the door, and a tin box of
your own letters waiting to be read back to you.

Your father,
Thomas
    """.trimIndent()

    private val observatoryChapters = listOf(
        "Chapter One: The Hearing Room" to
                """
                The observatory's great dome turned by fractions so small that only the instruments
                could feel the motion. Dr. Ilse Marchetti sat in the hearing room below, answering the
                committee's questions in the flat voice she reserved for people who had never looked
                through a telescope.

                "You are telling us," the chairman said, "that the signal is not natural."

                "I am telling you," she replied, "that it is periodic to eleven decimal places, and
                that nature does not keep time that politely."

                Outside the tall windows, the evening star hung over the observatory road like a
                question that had been asked for a very long time.

                That night she returned to the dome alone. The recording played on loop, and she
                listened the way a musician listens to a phrase she is learning: not for what it said,
                but for how it was played.
                """.trimIndent(),

        "Chapter Two: Songs of the Array" to
                """
                Fifty dishes stood in the valley like a field of white flowers, all of them turned to
                the same patch of sky. Ilse walked among them with her hands in her coat pockets while
                the maintenance engineer, Tomas, explained the fault in Dish Twenty-Seven.

                "It is not a fault," he said. "It is answering. The feed moves on its own. Sixteen
                seconds after the signal, every time. We did not program that."

                "Sixteen seconds," Ilse repeated. "The echo delay."

                "The array does not echo. It receives." Tomas looked up at the dish with the expression
                of a man who had stopped asking whether he was imagining things. "Last week it played
                back a chord our transmitter never sent. I checked. Three operators heard it."

                Ilse climbed the maintenance ladder alone. At the focus of Dish Twenty-Seven she sat
                inside the strange hush of the receiver and listened until long after the valley went
                dark. Somewhere below her, the instruments wrote their patient tapes, and the sky
                answered in its own time.
                """.trimIndent(),

        "Chapter Three: The Long Answer" to
                """
                In the spring the committee returned, and this time Ilse brought them the tapes. The
                chairs scraped. The room went quiet. On the wall behind her, a curve of light climbed
                and fell with the precise patience of a drummer counting out the measures of a song
                that was still being written.

                "The pattern has changed," she said. "It carries a second voice now — a reply to what
                we transmitted six years ago. The first contact was never a signal. It was a question
                that took us six years to be able to hear."

                The chairman looked older than he had in the winter. "And what do we say next?"

                Ilse had spent the winter thinking about that. She had watched the dishes turn through
                every clear night, all fifty of them, patient as harps. She remembered her father's
                only astronomy lesson, given on the back steps of a farmhouse: the sky is not empty,
                child; it is waiting for careful listeners.

                "Something honest," she said. "Something that keeps time."

                Outside, the evening star came up over the observatory road, and in the valley below,
                fifty white dishes tilted toward it, and waited.
                """.trimIndent()
    )

    private val directorDraftParagraphs = listOf(
        "SCENE 1: INT. THE EDITING ROOM - NIGHT",
        "Blue light washes the cramped room. MAYA (30s, headphones around her neck) screens the rough cut of her father's final unfinished documentary. She pauses on a frame: a mango tree in an old courtyard, its branches half in shadow.",
        "MAYA",
        "(to herself)",
        "You stopped here on purpose. You always stopped where the light changed.",
        "SCENE 2: EXT. COURTYARD - MEMORY / DAY",
        "The courtyard is sunlit and loud with sparrows. YOUNG MAYA (9) sits on a crate, swinging her legs, while her FATHER (40s, a battered light meter around his neck) rehearses the same slow pan he will film four times and use none.",
        "FATHER",
        "If you watch anything for long enough, it will tell you how it wants to be filmed.",
        "MAYA",
        "(recording him on a toy camera)",
        "And if you film it for long enough, it will tell you to go to bed.",
        "He laughs, and the laugh runs off the end of the frame like a wave going back into the sea.",
        "SCENE 3: INT. THE EDITING ROOM - CONTINUOUS",
        "Maya lifts her fingers from the shuttle control. On the monitor, the tree holds steady for a full minute of unedited silence. She does not cut it. She lets the minutes pass, the way her father let them, and the film — for the first time in twenty years — holds still enough to be finished.",
        "MAYA",
        "(whispering)",
        "Okay. Now I see it."
    )

    private val jangalChapters = listOf(
        "अध्याय 1: जंगल का बुलावा" to
                """
                सुबह की पहली किरण जब पेड़ों की चोटियों को छू रही थी, तब मोहन अपनी पुरानी साइकिल
                पर बस्ती की सड़क से जंगल की ओर मुड़ा। हवा में देवदार की गंध और भीगी मिट्टी की
                महक घुली थी। उसकी माँ ने दरवाज़े तक आकर कहा था — रास्ता भूल जाओ, तो नदी की आवाज़
                सुनना; नदी कभी धोखा नहीं देती।

                जंगल का रास्ता संकरा था, दोनों ओर साल और महुआ के पेड़ आकाश से बातें करते लगते
                थे। मोहन ने साइकिल किनारे खड़ी की और पैदल चलने लगा। हर कदम पर सूखी पत्तियाँ
                कहानी सुनाती थीं। उसे लगा जैसे जंगल उसे नाम से नहीं, धैर्य से पुकार रहा है।

                तभी दूर किसी डाल पर एक मोर बोला, और मोहन को विश्वास हो गया — वह अपने गाँव से
                निकल तो दूर गया है, पर अकेला नहीं है।
                """.trimIndent(),

        "अध्याय 2: नदी का पाठ" to
                """
                दोपहर तक मोहन नदी के किनारे पहुँचा। पानी इतना शांत था कि उसमें आकाश उल्टा
                तैरता दिखता था। बूढ़े मल्लाह रामदीन नाव पर बैठे थे और जाल की गाँठें खोल रहे थे।

                "बैठो," रामदीन ने कहा, जैसे वे वर्षों से एक-दूसरे को जानते हों। "नदी से बड़ी
                कोई पाठशाला नहीं। यह धीरे चलने वालों को दूर तक पहुँचाती है।"

                मोहन नाव पर बैठ गया। चप्पू पानी में पड़ा तो लहरें उसके पैरों तक आकर मानो माफ़ी
                माँगकर लौट गईं। रामदीन ने कहा — इस नदी ने हमारे गाँव के सब रास्ते बनाए हैं;
                किसान को खेत दिए, कुम्हार को मिट्टी दी, और बच्चों को सपने दिए।

                साँझ होते-होते मोहन को समझ आया कि माँ सही कहती थी। जंगल में रास्ता भूलना डर नहीं,
                सीखने की शुरुआत है।
                """.trimIndent(),

        "अध्याय 3: लौटते हुए" to
                """
                अगली सुबह मोहन वापस लौटा, पर अब उसके झोले में केवल रोटियाँ नहीं थीं। उसमें
                रामदीन की दी हुई एक घास की बंसी थी, तीन रंगीन कंकड़ थे, और जंगल के उन रास्तों का
                नक्शा था जो किसी कागज़ पर नहीं, स्मृति में बनते हैं।

                बस्ती पहुँचकर उसने माँ को सब सुनाया — मोर की आवाज़, नदी के पाठ, मल्लाह की
                बातें। माँ मुस्कुराई और बोली — "अब तू जंगल का नहीं, जंगल तेरा है।"

                उस रात मोहन ने अपनी खिड़की खोल रखी। दूर जंगल से हवा आ रही थी, और उस हवा में
                उसे लगा जैसे कोई अपनी बंसी से वही धुन बजा रहा है जो नदी ने उसे सिखाई थी।
                """.trimIndent()
    )

    private val tyoharParagraphs = listOf(
        "त्योहार की यादें",
        "बचपन में त्योहार की तैयारी घर के आँगन से ही शुरू हो जाती थी। दादी आटे की परतें बेलतीं और हम बच्चे उनके पास बैठकर अधसूखी मिठाइयों की गिनती करते। घर में हलचल इतनी होती कि मालूम पड़ता — आने वाला हर पल किसी उत्सव का हिस्सा है।",
        "शाम को पिताजी बाज़ार से रंग-बिरंगे कागज़ लाते। हम उन कागज़ों से झंडियाँ बनाते और छत पर लगाते। हवा चलती तो कागज़ों की फरफराहट आँगन के कोने तक सुनाई देती, जैसे त्योहार खुद दरवाज़े पर दस्तक दे रहा हो।",
        "माँ कहती थीं — 'त्योहार चीज़ों से नहीं, अपनों के साथ से बनता है।' तब यह बात इतनी सरल लगती थी कि उसका अर्थ समझने की ज़रूरत ही नहीं पड़ी।",
        "आज शहर में त्योहार सजी दुकानों और तेज़ रोशनी में मिलता है। पर हर दीवार पर टँगी झंडी में, हर मिठाई की मिठास में, वही आँगन टुकुर-टुकुर देखता रहता है — जहाँ कागज़ की फरफराहट में पूरा परिवार एक साथ हँसता था।",
        "यादें त्योहारों की सबसे बड़ी मिठाई हैं। वे कभी खत्म नहीं होतीं, और हर बार पुरानी होकर भी नई लगती हैं।"
    )

    private val panchParmeshwarText = """
अध्याय 1: जुम्मन शेख और अलगू चौधरी

जुम्मन शेख और अलगू चौधरी में गाढ़ी मित्रता थी। साझे में खेती होती थी। कुछ लेन-देन में भी साझा था। एक को दूसरे पर अटल विश्वास था। जुम्मन जब हज करने गए थे, तब अपना घर अलगू को सौंप गए थे; और अलगू जब कभी बाहर जाते, तो जुम्मन पर अपना घर छोड़ देते थे।

उनमें न खान-पान का व्यवहार था, न धर्म का नाता; केवल विचार मिलते थे। मित्रता का मूलमंत्र भी यही है।

इस मित्रता का जन्म उसी समय हुआ, जब दोनों मित्र बालक थे, और जुम्मन के पूज्य पिता, जुमराती, उन्हें शिक्षा प्रदान करते थे।

अध्याय 2: खालाजान की पंचायत

जुम्मन शेख की एक बूढ़ी खाला (मौसी) थीं। उनके पास थोड़ी-सी मिलकियत थी; परंतु उनके निकट संबंधियों में कोई न था। जुम्मन ने लंबे-चौड़े वादे करके वह मिलकियत अपने नाम लिखवा ली थी। जब तक दानपत्र की रजिस्ट्री न हुई थी, तब तक खालाजान का खूब आदर-सत्कार किया गया। उन्हें खूब स्वादिष्ट भोजन खिलाया गया।

पर रजिस्ट्री की मुहर ने इन खातिरदारियों पर भी मुहर लगा दी। जुम्मन की पत्नी करीमन रोटियों के साथ कड़वे वचनों के तीखे बाण भी देने लगीं।

अंत में एक दिन खाला ने जुम्मन से कहा—"बेटा! तुम्हारे साथ मेरा निबाह न होगा। तुम मुझे रुपए दे दिया करो, मैं अपना अलग पका-खा लूँगी।"

जुम्मन ने धृष्टता के साथ उत्तर दिया—"रुपए क्या यहाँ फलते हैं?"

खाला ने नम्र भाव से पूछा—"मुझे कुछ रूखा-सूखा चाहिए भी कि नहीं?"

जुम्मन ने गंभीर स्वर से जवाब दिया—"तो कोई यह थोड़े ही समझा था कि तुम अमर होकर आई हो!"

अध्याय 3: न्याय का सिंहासन

खाला ने गाँव में पंचायत बुलाई। बूढ़ी खाला लाठी टेकती हुई गाँव के एक-एक आदमी के पास पहुँची। शाम को एक पेड़ के नीचे पंचायत बैठी।

अलगू चौधरी को सरपंच चुना गया। जुम्मन मन ही मन प्रसन्न हुए कि अलगू तो उनका पक्का मित्र है, वह कभी उनके विरुद्ध फैसला नहीं देगा।

परंतु जब अलगू चौधरी पंच के आसन पर बैठे, तो उनके मन में न्याय और धर्म का दायित्व जाग उठा। उन्होंने निष्पक्ष होकर कहा:

"जुम्मन शेख! पंचों की दृष्टि में न कोई मित्र है, न कोई शत्रु। पंच के मुख से जो बात निकलती है, वह परमेश्वर के मुख से निकलती है। खालाजान को माहवार खर्च दिया जाए, नहीं तो रजिस्ट्री रद्द समझी जाए।"

यह फैसला सुनकर गाँव वालों ने कहा—"यह न्याय है! इसे कहते हैं पंच परमेश्वर!"
    """.trimIndent()

    /**
     * Six pages of Hindi prose for the Devanagari PDF fixture ("नदी और नाव").
     * Plain Devanagari + ASCII punctuation keeps the invisible text layer's
     * glyph-to-Unicode mapping complete.
     */
    internal val hindiPdfPages: List<Pair<String, String>> = listOf(
        "नदी और नाव - पृष्ठ 1" to
                """
                सूरज अभी पूरब की पहाड़ियों के पीछे छिपा हुआ था, पर नदी जाग चुकी थी। लहरें किनारे से
                टकराकर लौटती थीं, जैसे कोई बूढ़ा बार-बार अपना हिसाब जोड़ रहा हो। घाट की सीढ़ियों पर
                कुछ लोग स्नान कर रहे थे, कुछ पानी में दीपक छोड़ रहे थे, और कुछ बस बैठे थे - अपनी
                चुप्पी के साथ।

                नाव में बैठे बसंत को नदी की हर करवट याद थी। उसके पिता ने बारह साल पहले यही नाव
                उसे सौंपी थी और केवल तीन बातें कही थीं - नदी से कभी झगड़ना मत, मझधार में डांड छोड़ना
                मत, और यात्री से मुस्कुराहट कभी मत लेना।
                """.trimIndent(),

        "नदी और नाव - पृष्ठ 2" to
                """
                पहला यात्री एक बूढ़ा अध्यापक था। वह बैठा तो सही, पर किसी भी सवाल का जवाब नहीं
                देता। बसंत ने चप्पू उठाया और नदी की धारा पकड़ ली। दोनों किनारों के पेड़ उलटे चलते
                लगे, और बीच धारा में नाव सीधी बहती रही।

                अध्यापक ने अचानक थैली से एक पुरानी पुस्तक निकाली और बोला - "हर नदी किसी गाँव की
                कहानी बहाती है। इस पुस्तक में पचास नदियों की कहानियाँ लिखी हैं, पर कोई भी इस नदी
                के बारे में नहीं जानता। लोग नदी पर सड़कें बना लेते हैं, पर नदी को पढ़ना नहीं सीखते।"

                बसंत मुस्कुराया और बोला - "मैंने तो नदी को पढ़ना बचपन में सीखा था, जब मेरे पिता
                मुझे तैरना सिखाते थे।"
                """.trimIndent(),

        "नदी और नाव - पृष्ठ 3" to
                """
                दूसरा यात्री एक किसान था, जिसके पास बिकी हुई फसल का पूरा हिसाब था और मन में पूरी
                चिंता। उसने बसंत से पूछा - "तुम नाव से डरते नहीं?"

                बसंत ने कहा - "डर तो उस दिन लगा था जब मैं नाव चलाना सीख रहा था। पिताजी ने देखा
                कि मेरे हाथ काँप रहे हैं, तो उन्होंने डांड अपने हाथ में लिया और बोले - नदी को
                अपना दुश्मन मत मानो। नदी हमारी है, हम नदी के हैं। जो नदी पर भरोसा करता है, वह
                मझधार में भी किनारा पा जाता है।"

                किसान सोच में पड़ गया। उसने अपना हिसाब वाला कागज़ मोड़कर थैली में रख दिया।
                """.trimIndent(),

        "नदी और नाव - पृष्ठ 4" to
                """
                दोपहर के समय नदी दूध जैसी सफेद दिखने लगी। दूसरे घाट पर एक मेला लगा था और बसंत
                ने नाव रोक दी। बच्चों की टोली किनारे खड़ी थी - कुछ कागज़ की नाव चलाते, कुछ मल्लाहों
                की बातें सुनते।

                एक छोटी लड़की ने बसंत से पूछा - "काका, नदी कहाँ जाती है?"

                बसंत ने कहा - "नदी वहाँ जाती है जहाँ सबसे चौड़ी धरती होती है। वह सब नदियों से
                मिलती है, फिर समुद्र जाती है। पर वह अपने साथ हमारी बातें भी ले जाती है। चप्पू की
                आवाज़, स्नान के बाद की हँसी, और मेले की शाम।"

                लड़की बोली - "तो नदी सब याद रखती है?"

                बसंत ने हाँ कहा, और धीरे से नाव हटा दी - क्योंकि उस दिन उसे लगा कि नदी सचमुच
                याद रखती है।
                """.trimIndent(),

        "नदी और नाव - पृष्ठ 5" to
                """
                शाम ढलते-ढलते आकाश लाल हो गया और नदी उसका रंग उधार लेकर चमकने लगी। बसंत ने
                नाव किनारे लगाई और दीपक जलाया। उसके पिता की मान्यता थी कि संध्या के समय
                जलाया दीपक न केवल घाट को, बल्कि रास्ता भूल चुके जानवरों को भी रास्ता दिखाता है।

                उस रात पानी पर कुछ लहरें बसंत ने बंसी से बुनी हुई धुन के साथ आती-जाती देखीं।
                लगा जैसे नदी गा रही हो - उसी धुन में, जो हर मल्लाह की माँ ने उसे सुनाई थी।
                नदी की धुन कोई मछली नहीं भूलती, कोई पक्षी नहीं भूलता, कोई भी यात्री नहीं
                भूलता जो एक बार इस किनारे बैठ चुका हो।
                """.trimIndent(),

        "नदी और नाव - पृष्ठ 6" to
                """
                अगली सुबह बसंत ने नाव को उसी घाट से खोला। अध्यापक ने अलविदा कहते हुए कहा - अपनी
                नदी की कहानी कभी लिखना। किसान ने कहा - अगली फसल पर मैं तुम्हें जरूर बुलाऊंगा।
                और छोटी लड़की ने बस एक कागज़ की नाव दी, जो घाट के किनारे रखकर नदी को समर्पित कर
                दी।

                बसंत ने चप्पू उठाया और नाव धारा में आ गई। जगह वही थी, पानी वही, पर नाव हल्की लग
                रही थी - मानो नदी ने फिर से एक नई कहानी की पहली पंक्ति लिख दी हो।

                और बसंत ने मन ही मन सोचा - नदी अकेली नहीं बहती। वह हम सबको साथ लेकर बहती है,
                आने वाली सुबहों की तरफ।
                """.trimIndent()
    )
}
