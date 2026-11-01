package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Supported document formats for import and reading.
 */
enum class DocumentFormat {
    PDF, TXT, EPUB, DOCX, SCRIPT
}

/**
 * Reader visual themes.
 */
enum class ReaderTheme(val displayName: String) {
    PAPER("Paper"),
    SEPIA("Sepia"),
    MIDNIGHT("Midnight")
}

/**
 * Reader typography font family options.
 */
enum class ReaderFont(val displayName: String) {
    LITERATA("Literata"),
    MERRIWEATHER("Merriweather"),
    GEORGIA("Georgia"),
    ATKINSON("Atkinson Hyperlegible")
}

/**
 * Camera shot framing types for storyboards.
 */
enum class ShotType(val label: String) {
    EXTREME_WIDE("Extreme Wide"),
    WIDE_SHOT("Wide Shot"),
    MEDIUM_SHOT("Medium Shot"),
    CLOSE_UP("Close Up"),
    EXTREME_CLOSE_UP("Extreme Close Up"),
    OVER_SHOULDER("Over-the-Shoulder"),
    POV("Point of View"),
    DUTCH_ANGLE("Dutch Angle")
}

/**
 * Camera movement styles.
 */
enum class CameraMovement(val label: String) {
    STATIC("Static"),
    PAN_LEFT("Pan Left"),
    PAN_RIGHT("Pan Right"),
    TILT_UP("Tilt Up"),
    TILT_DOWN("Tilt Down"),
    DOLLY_IN("Dolly In"),
    TRACKING("Tracking"),
    CRANE("Crane / Boom")
}

/**
 * Room Entity representing a stored document or script.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String = "Unknown",
    val format: DocumentFormat = DocumentFormat.SCRIPT,
    val rawContent: String,
    val totalPages: Int = 1,
    val lastReadPage: Int = 1,      // 1-based index
    val bookmarkPage: Int? = null,   // 1-based index, null if unread
    val hasProgress: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    /**
     * True for legacy records that were auto-seeded demo fixtures by older app versions.
     * Demo fixtures are never seeded automatically; legacy rows are tagged here by the
     * v1->v2 migration (via their known stable IDs) and hidden from the user Library.
     */
    val isDemo: Boolean = false
)

/**
 * Karaoke word highlight colors.
 */
enum class HighlightColor(val displayName: String, val colorHex: Long, val textColorHex: Long) {
    YELLOW("Yellow", 0xFFFFEB3B, 0xFF1A1A1A),
    ORANGE("Orange", 0xFFFF9800, 0xFF1A1A1A),
    RED("Red", 0xFFE53935, 0xFFFFFFFF),
    PINK("Pink", 0xFFE91E63, 0xFFFFFFFF),
    GREEN("Green", 0xFF4CAF50, 0xFFFFFFFF),
    BLUE("Blue", 0xFF2196F3, 0xFFFFFFFF),
    PURPLE("Purple", 0xFF9C27B0, 0xFFFFFFFF),
    CYAN("Cyan", 0xFF00BCD4, 0xFF1A1A1A),
    TRANSPARENT("Transparent / No Highlight", 0x00000000, 0x00000000);

    val isVisibleHighlight: Boolean get() = this != TRANSPARENT
}

/**
 * Origin of the text carried by a source page.
 */
enum class TextExtractionOrigin {
    NATIVE_PDF_TEXT,
    OCR,
    TXT_FILE,
    DOCX,
    EPUB,
    PASTED_TEXT,
    SCRIPT,
    NONE
}

/**
 * A word-level text block with page-relative bounding box (0..1 fractions of page size),
 * used for selectable text, word hit-testing and karaoke mapping on rendered pages.
 */
data class TextBlockInfo(
    val text: String,
    val wordStartChar: Int,
    val wordEndChar: Int,
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
    val confidence: Float = 1f
)

/**
 * Vocabulary notebook entry stored in chronological order.
 */
data class VocabularyEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val documentId: String,
    val word: String,
    val normalizedWord: String = word.lowercase().trim(),
    val pageNumber: Int = 1,
    val contextSentence: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val meaning: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val exampleSentence: String? = null,
    val hindiMeaning: String? = null,
    val lookupSource: String? = null,
    val requestedInfoType: String? = null,
    /** True once the user requested Hindi explanations for this word (sticky per §6.H). */
    val preferHindi: Boolean = false,
    /** Wall-clock time of the last dictionary lookup attempt, or null if never looked up. */
    val lookupTimestamp: Long? = null
)

/**
 * In-memory representation of a single rendered page.
 */
data class ReaderPage(
    val pageNumber: Int,            // 1-based page number
    val title: String = "",
    val text: String,
    val lines: List<String> = emptyList(),
    val chapterName: String? = null,
    val imagePath: String? = null,
    val extractionOrigin: TextExtractionOrigin = TextExtractionOrigin.SCRIPT,
    val textUnavailable: Boolean = false,
    val extractionWarning: String? = null,
    val textBlocks: List<TextBlockInfo> = emptyList()
)

/**
 * Storyboard shot item for cinematic pre-visualization.
 */
data class StoryboardShot(
    val id: String,
    val sceneNumber: Int,
    val shotNumber: Int,
    val shotType: ShotType,
    val cameraMovement: CameraMovement,
    val visualDescription: String,
    val actionNotes: String,
    val dialogue: String? = null,
    val visualPrompt: String,
    val imageUrl: String? = null,
    val sketchSeed: Int = 1
)

/**
 * A scene grouping storyboard shots.
 */
data class StoryboardScene(
    val sceneNumber: Int,
    val heading: String,
    val location: String,
    val timeOfDay: String,
    val summary: String,
    val shots: List<StoryboardShot>
)

/**
 * 6 grab zones for the natural 3D page curl physics.
 */
enum class PageCurlAnchor {
    TOP_LEFT,
    MIDDLE_LEFT,
    BOTTOM_LEFT,
    TOP_RIGHT,
    MIDDLE_RIGHT,
    BOTTOM_RIGHT
}

/**
 * Orientation layout modes.
 */
enum class ReaderLayoutMode {
    COVER_PORTRAIT,
    PORTRAIT_SINGLE_PAGE,
    LANDSCAPE_DOUBLE_PAGE
}

/**
 * Supported reading narration languages with emphasis on Indian languages and English variants.
 */
enum class ReadingLanguage(val code: String, val displayName: String, val flag: String) {
    HINDI("hi-IN", "हिन्दी (Hindi)", "🇮🇳"),
    ENGLISH_INDIA("en-IN", "English (India)", "🇮🇳"),
    ENGLISH_US("en-US", "English (US)", "🇺🇸"),
    ENGLISH_UK("en-GB", "English (UK)", "🇬🇧"),
    BENGALI("bn-IN", "বাংলা (Bengali)", "🇮🇳"),
    TAMIL("ta-IN", "தமிழ் (Tamil)", "🇮🇳"),
    TELUGU("te-IN", "తెలుగు (Telugu)", "🇮🇳"),
    MARATHI("mr-IN", "मराठी (Marathi)", "🇮🇳")
}

/**
 * Natural voice personas.
 */
enum class VoicePreset(val id: String, val title: String, val persona: String) {
    FEMALE_NARRATOR("female_narrator", "Priya / Elena", "Clear, elegant expressive narrator"),
    MALE_NARRATOR("male_narrator", "Aarav / Marcus", "Warm, authoritative deep voice"),
    STORYTELLER("storyteller", "Dada-ji (Elder)", "Gentle, wise fireside storyteller"),
    YOUTHFUL("youthful", "Kabir / Maya", "Energetic, bright youthful cadence")
}

/**
 * User reader preferences.
 */
data class ReaderPreferences(
    val font: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Float = 19f,     // Range 14 to 32
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val speechRate: Float = 1.0f,    // 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
    val language: ReadingLanguage = ReadingLanguage.HINDI,
    val voicePreset: VoicePreset = VoicePreset.FEMALE_NARRATOR,
    val turnSound: Boolean = true,
    val autoBookmark: Boolean = true,
    val highlightColor: HighlightColor = HighlightColor.YELLOW
)
