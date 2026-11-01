package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.ReaderLayoutMode
import com.example.data.model.ReaderPage
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingLanguage
import com.example.data.model.StoryboardScene
import com.example.data.model.StoryboardShot
import com.example.data.model.VoicePreset
import com.example.data.model.VocabularyEntry
import com.example.service.DemoCatalog
import com.example.service.DemoSampleDescriptor
import com.example.service.DictionaryInfoType
import com.example.service.DictionaryResult
import com.example.service.DictionaryService
import com.example.service.DocumentParserService
import com.example.service.GeminiStoryboardService
import com.example.service.TtsManager
import com.example.service.VocabularyManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    LIBRARY,
    READER,
    STORYBOARD_STUDIO,
    VOCABULARY,
    PASTE_TEXT
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val docDao = db.documentDao()
    val ttsManager = TtsManager(application)

    // All documents in library. Demo samples are session-only (never persisted) and
    // legacy seeded demo rows are excluded from the user's library (addendum §1.B/§1.F).
    val documents: StateFlow<List<DocumentEntity>> = docDao.getAllDocuments()
        .map { list -> list.filterNot { it.isDemo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active App Screen & Layout Mode
    private val _currentScreen = MutableStateFlow(AppScreen.LIBRARY)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _layoutMode = MutableStateFlow(ReaderLayoutMode.COVER_PORTRAIT)
    val layoutMode: StateFlow<ReaderLayoutMode> = _layoutMode.asStateFlow()

    // Automatic File Parsing & Stage State
    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _parsingStage = MutableStateFlow("")
    val parsingStage: StateFlow<String> = _parsingStage.asStateFlow()

    private val _parsingFilename = MutableStateFlow("")
    val parsingFilename: StateFlow<String> = _parsingFilename.asStateFlow()

    private var parsingJob: Job? = null

    // Active Document & Rendered Pages
    private val _activeDocument = MutableStateFlow<DocumentEntity?>(null)
    val activeDocument: StateFlow<DocumentEntity?> = _activeDocument.asStateFlow()

    private val _pages = MutableStateFlow<List<ReaderPage>>(emptyList())
    val pages: StateFlow<List<ReaderPage>> = _pages.asStateFlow()

    // Active Page (0-based index internally, 1-based for display)
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    // Selected Page Candidate (when tapped in landscape spread to preserve for portrait focus mode)
    private val _pendingFocusPageNumber = MutableStateFlow<Int?>(null)
    val pendingFocusPageNumber: StateFlow<Int?> = _pendingFocusPageNumber.asStateFlow()

    // Orientation Recommendation 3-second toast state
    private val _recommendationVisible = MutableStateFlow(false)
    val recommendationVisible: StateFlow<Boolean> = _recommendationVisible.asStateFlow()

    private val _recommendationTargetLandscape = MutableStateFlow(true)
    val recommendationTargetLandscape: StateFlow<Boolean> = _recommendationTargetLandscape.asStateFlow()

    // Common Long-press Control Panel
    private val _controlPanelVisible = MutableStateFlow(false)
    val controlPanelVisible: StateFlow<Boolean> = _controlPanelVisible.asStateFlow()

    // Reader Preferences
    private val _preferences = MutableStateFlow(ReaderPreferences())
    val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    // Word-level karaoke highlighting states from TtsManager
    val activeWordIndex: StateFlow<Int?> = ttsManager.activeWordIndex
    val activePageNumber: StateFlow<Int?> = ttsManager.activePageNumber

    // Vocabulary Notebook state (Edit Vocabulary screen)
    private val _vocabularyEntries = MutableStateFlow<List<VocabularyEntry>>(emptyList())
    val vocabularyEntries: StateFlow<List<VocabularyEntry>> = _vocabularyEntries.asStateFlow()

    private val _isLookingUpDictionary = MutableStateFlow(false)
    val isLookingUpDictionary: StateFlow<Boolean> = _isLookingUpDictionary.asStateFlow()

    private val _lookupStatusText = MutableStateFlow("")
    val lookupStatusText: StateFlow<String> = _lookupStatusText.asStateFlow()

    // Transient feedback (e.g. "already saved", save confirmation) for the current book
    private val _vocabularyActionMessage = MutableStateFlow<String?>(null)
    val vocabularyActionMessage: StateFlow<String?> = _vocabularyActionMessage.asStateFlow()

    // Storyboard State
    private val _storyboardScenes = MutableStateFlow<List<StoryboardScene>>(emptyList())
    val storyboardScenes: StateFlow<List<StoryboardScene>> = _storyboardScenes.asStateFlow()

    private val _isGeneratingStoryboard = MutableStateFlow(false)
    val isGeneratingStoryboard: StateFlow<Boolean> = _isGeneratingStoryboard.asStateFlow()

    init {
        // NOTE (§1.B): demo samples are deliberately NOT seeded into the persistent
        // Library here. They are built on demand from DemoCatalog and opened as a
        // session-only reference. Deleting user documents therefore stays deleted.

        // Automatic page advancement when page narration ends
        ttsManager.onPageFinished = {
            viewModelScope.launch {
                advancePageFromTts()
            }
        }

        // Setup STT voice commands callback
        ttsManager.onVoiceCommandReceived = { command ->
            when (command) {
                "NEXT" -> onPageTurn(forward = true)
                "PREV" -> onPageTurn(forward = false)
                "PAUSE" -> ttsManager.stop()
                "PLAY" -> {
                    val activePage = _pages.value.getOrNull(_currentPageIndex.value)
                    if (activePage != null) {
                        ttsManager.seekToWord(
                            pageNumber = activePage.pageNumber,
                            wordIndex = 0,
                            fullPageText = activePage.text,
                            rate = _preferences.value.speechRate
                        )
                    }
                }
            }
        }
    }

    /**
     * Automatic File Ingestion without forced manual entry:
     * Reads URI stream, validates, extracts metadata, paginates, and opens directly to cover.
     */
    fun importFileFromUri(uri: Uri) {
        parsingJob?.cancel()
        _isParsing.value = true
        _parsingFilename.value = "Selected Document"
        _parsingStage.value = "Validating file..."

        parsingJob = viewModelScope.launch {
            val result = DocumentParserService.parseFromUri(
                context = getApplication(),
                uri = uri,
                onStageUpdate = { stage ->
                    _parsingStage.value = stage
                },
                onFilenameDiscovered = { filename ->
                    _parsingFilename.value = filename
                }
            )

            result.onSuccess { doc ->
                docDao.insertDocument(doc)
                _isParsing.value = false
                openDocument(doc)
            }.onFailure {
                _parsingStage.value = "Error parsing file: ${it.message ?: "Unknown error"}"
                _isParsing.value = false
            }
        }
    }

    fun cancelParsing() {
        parsingJob?.cancel()
        _isParsing.value = false
    }

    fun openDocument(document: DocumentEntity) {
        _activeDocument.value = document
        val parsedPages = DocumentParserService.paginate(document.rawContent)
        _pages.value = parsedPages

        // Restore to saved page or start at page 1 (0-indexed = 0)
        val initialPageIdx = ((document.lastReadPage - 1)).coerceIn(0, (parsedPages.size - 1).coerceAtLeast(0))
        _currentPageIndex.value = initialPageIdx

        // Initially present as closed hardcover in portrait mode
        _layoutMode.value = ReaderLayoutMode.COVER_PORTRAIT
        _currentScreen.value = AppScreen.READER
        _recommendationVisible.value = false
        _controlPanelVisible.value = false
        _vocabularyActionMessage.value = null
        refreshVocabularyEntries()

        // Automatically prepare initial storyboard breakdown in the background
        viewModelScope.launch {
            val scenes = GeminiStoryboardService.generateLocalScriptBreakdown(document.rawContent)
            _storyboardScenes.value = scenes
        }
    }

    fun openBook() {
        _layoutMode.value = ReaderLayoutMode.PORTRAIT_SINGLE_PAGE
        showOrientationRecommendation(targetLandscape = true)
        updateReadingProgress()
    }

    fun closeBook() {
        ttsManager.stop()
        val doc = _activeDocument.value
        val curPageNum = _currentPageIndex.value + 1

        if (doc != null) {
            viewModelScope.launch {
                docDao.updateReadingProgress(doc.id, curPageNum)
                docDao.updateBookmark(doc.id, curPageNum)
                _activeDocument.value = doc.copy(
                    lastReadPage = curPageNum,
                    bookmarkPage = curPageNum,
                    hasProgress = true
                )
            }
        }

        _layoutMode.value = ReaderLayoutMode.COVER_PORTRAIT
        _controlPanelVisible.value = false
        _recommendationVisible.value = false
    }

    fun exitToLibrary() {
        closeBook()
        _currentScreen.value = AppScreen.LIBRARY
    }

    fun showOrientationRecommendation(targetLandscape: Boolean) {
        _recommendationTargetLandscape.value = targetLandscape
        _recommendationVisible.value = true
    }

    fun dismissRecommendation() {
        _recommendationVisible.value = false
    }

    fun switchLayout(toLandscape: Boolean) {
        _recommendationVisible.value = false

        if (toLandscape) {
            val curPage1Based = _currentPageIndex.value + 1
            val leftPage1Based = if (curPage1Based % 2 != 0) curPage1Based else (curPage1Based - 1).coerceAtLeast(1)
            _currentPageIndex.value = (leftPage1Based - 1).coerceAtLeast(0)
            _layoutMode.value = ReaderLayoutMode.LANDSCAPE_DOUBLE_PAGE
        } else {
            val targetPage = _pendingFocusPageNumber.value ?: (_currentPageIndex.value + 1)
            _currentPageIndex.value = (targetPage - 1).coerceAtLeast(0)
            _pendingFocusPageNumber.value = null
            _layoutMode.value = ReaderLayoutMode.PORTRAIT_SINGLE_PAGE
        }

        updateReadingProgress()
    }

    fun selectPageForFocus(pageNumber: Int) {
        _pendingFocusPageNumber.value = pageNumber
        showOrientationRecommendation(targetLandscape = false)
    }

    /**
     * Seeks and starts reading from an exact double-tapped word on a page.
     */
    fun seekToWord(pageNumber: Int, wordIndex: Int) {
        val safePageIdx = (pageNumber - 1).coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0))
        _currentPageIndex.value = safePageIdx
        val page = _pages.value.getOrNull(safePageIdx) ?: return
        ttsManager.seekToWord(
            pageNumber = pageNumber,
            wordIndex = wordIndex,
            fullPageText = page.text,
            rate = _preferences.value.speechRate
        )
        updateReadingProgress()
    }

    /**
     * Automatic page advancement when speech on a page completes:
     * Continues seamlessly through sequential pages and spreads.
     */
    fun advancePageFromTts() {
        val isLandscape = _layoutMode.value == ReaderLayoutMode.LANDSCAPE_DOUBLE_PAGE
        val curPage1Based = _currentPageIndex.value + 1
        val total = _pages.value.size

        if (!isLandscape) {
            if (_currentPageIndex.value < total - 1) {
                val nextIdx = _currentPageIndex.value + 1
                _currentPageIndex.value = nextIdx
                updateReadingProgress()
                val nextPage = _pages.value.getOrNull(nextIdx)
                if (nextPage != null) {
                    ttsManager.seekToWord(
                        pageNumber = nextPage.pageNumber,
                        wordIndex = 0,
                        fullPageText = nextPage.text,
                        rate = _preferences.value.speechRate
                    )
                }
            } else {
                ttsManager.stop()
            }
        } else {
            val leftPageNum = if (curPage1Based % 2 != 0) curPage1Based else (curPage1Based - 1).coerceAtLeast(1)
            val rightPageNum = leftPageNum + 1
            val activeReadingPage = ttsManager.activePageNumber.value ?: leftPageNum

            if (activeReadingPage == leftPageNum && rightPageNum <= total) {
                val rightPage = _pages.value.find { it.pageNumber == rightPageNum }
                if (rightPage != null) {
                    ttsManager.seekToWord(
                        pageNumber = rightPage.pageNumber,
                        wordIndex = 0,
                        fullPageText = rightPage.text,
                        rate = _preferences.value.speechRate
                    )
                    return
                }
            }

            val nextSpreadLeft = leftPageNum + 2
            if (nextSpreadLeft <= total) {
                val nextLeftIdx = nextSpreadLeft - 1
                _currentPageIndex.value = nextLeftIdx
                updateReadingProgress()
                val nextLeftPage = _pages.value.find { it.pageNumber == nextSpreadLeft }
                if (nextLeftPage != null) {
                    ttsManager.seekToWord(
                        pageNumber = nextLeftPage.pageNumber,
                        wordIndex = 0,
                        fullPageText = nextLeftPage.text,
                        rate = _preferences.value.speechRate
                    )
                }
            } else {
                ttsManager.stop()
            }
        }
    }

    /**
     * Invoked when a page turn is completed by touch-curl gesture or button tap.
     */
    fun onPageTurn(forward: Boolean) {
        val total = _pages.value.size
        val isLandscape = _layoutMode.value == ReaderLayoutMode.LANDSCAPE_DOUBLE_PAGE
        val step = if (isLandscape) 2 else 1

        val newIndex = if (forward) {
            (_currentPageIndex.value + step).coerceAtMost((total - 1).coerceAtLeast(0))
        } else {
            (_currentPageIndex.value - step).coerceAtLeast(0)
        }

        if (newIndex != _currentPageIndex.value) {
            _currentPageIndex.value = newIndex
            _pendingFocusPageNumber.value = null
            updateReadingProgress()

            // Synchronize TTS if playing
            if (ttsManager.isPlaying.value) {
                val activePage = _pages.value.getOrNull(newIndex)
                if (activePage != null) {
                    ttsManager.seekToWord(
                        pageNumber = activePage.pageNumber,
                        wordIndex = 0,
                        fullPageText = activePage.text,
                        rate = _preferences.value.speechRate
                    )
                }
            }
        }
    }

    private fun updateReadingProgress() {
        val doc = _activeDocument.value ?: return
        val curPage1Based = _currentPageIndex.value + 1
        viewModelScope.launch {
            docDao.updateReadingProgress(doc.id, curPage1Based)
            _activeDocument.value = doc.copy(lastReadPage = curPage1Based, hasProgress = true)
        }
    }

    fun openControlPanel() {
        _controlPanelVisible.value = true
    }

    fun dismissControlPanel() {
        _controlPanelVisible.value = false
    }

    fun updatePreferences(newPrefs: ReaderPreferences) {
        _preferences.value = newPrefs
        ttsManager.applyLanguageAndVoice(newPrefs.language, newPrefs.voicePreset)
    }

    /**
     * Live speed change that seamlessly updates active speech without restarting from beginning.
     */
    fun onLiveSpeedChange(newSpeed: Float) {
        _preferences.value = _preferences.value.copy(speechRate = newSpeed)
        val activeText = _pages.value.getOrNull(_currentPageIndex.value)?.text ?: ""
        ttsManager.setLiveSpeechRate(newSpeed, activeText)
    }

    fun previewVoice(language: ReadingLanguage, voicePreset: VoicePreset) {
        ttsManager.previewVoice(language, voicePreset)
    }

    fun startListeningVoiceCommand() {
        ttsManager.startListeningForVoiceCommands()
    }

    fun toggleSpeech() {
        val activePage = _pages.value.getOrNull(_currentPageIndex.value) ?: return
        ttsManager.pauseOrResume(
            textToResume = activePage.text,
            rate = _preferences.value.speechRate,
            pageNumber = activePage.pageNumber
        )
    }

    fun navigateToStoryboardStudio() {
        _currentScreen.value = AppScreen.STORYBOARD_STUDIO
    }

    fun navigateBackToReader() {
        _currentScreen.value = AppScreen.READER
    }

    fun regenerateStoryboard() {
        val doc = _activeDocument.value ?: return
        _isGeneratingStoryboard.value = true
        viewModelScope.launch {
            val newScenes = GeminiStoryboardService.generateStoryboardFromScript(doc.rawContent)
            _storyboardScenes.value = newScenes
            _isGeneratingStoryboard.value = false
        }
    }

    fun addCustomShot(sceneNumber: Int, shot: StoryboardShot) {
        val current = _storyboardScenes.value.toMutableList()
        val sceneIdx = current.indexOfFirst { it.sceneNumber == sceneNumber }
        if (sceneIdx != -1) {
            val sc = current[sceneIdx]
            val updatedShots = sc.shots + shot
            current[sceneIdx] = sc.copy(shots = updatedShots)
            _storyboardScenes.value = current
        }
    }

    /**
     * Opens a demo sample by its catalog index (§1.C). The sample is built on demand from
     * the [DemoCatalog] through its real parser and opened as a session-only reference —
     * it is never inserted into the user's Library.
     */
    fun loadSample(index: Int) {
        val descriptor = DemoCatalog.descriptors.getOrNull(index) ?: return
        openDemoSample(descriptor)
    }

    fun openDemoSample(descriptor: DemoSampleDescriptor) {
        parsingJob?.cancel()
        _isParsing.value = true
        _parsingFilename.value = descriptor.title
        _parsingStage.value = "Preparing demo sample..."
        parsingJob = viewModelScope.launch {
            val sample = withContext(Dispatchers.IO) {
                DemoCatalog.buildSample(getApplication(), descriptor)
            }
            _isParsing.value = false
            if (sample != null) {
                openDocument(sample)
            } else {
                _parsingStage.value =
                    "This demo sample could not be prepared on this device (${descriptor.format.name})."
            }
        }
    }

    /**
     * Explicit user action (§1.D): persists the open demo sample into the Library as a real
     * document. The stable document ID is kept so the vocabulary notebook and reading
     * progress transfer seamlessly to the library copy.
     */
    fun addDemoToLibrary(document: DocumentEntity) {
        viewModelScope.launch {
            val libraryCopy = document.copy(
                isDemo = false,
                lastOpenedAt = System.currentTimeMillis()
            )
            docDao.insertDocument(libraryCopy)
            _activeDocument.value = libraryCopy
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            // Page images live outside Room, so they must be removed explicitly;
            // otherwise every imported PDF leaves its renders behind forever.
            withContext(Dispatchers.IO) {
                val rendersDir = File(getApplication<Application>().filesDir, "pdf_renders")
                rendersDir.listFiles { file ->
                    file.isFile &&
                        file.name.startsWith("${docId}_p") &&
                        file.name.endsWith(".png")
                }?.forEach { it.delete() }
            }
            docDao.deleteDocument(docId)
            if (_activeDocument.value?.id == docId) {
                _activeDocument.value = null
                _currentScreen.value = AppScreen.LIBRARY
            }
        }
    }

    // ==========================================
    // VOCABULARY NOTEBOOK & DICTIONARY (Triple-tap capture)
    // ==========================================

    fun refreshVocabularyEntries() {
        val doc = _activeDocument.value
        if (doc == null) {
            _vocabularyEntries.value = emptyList()
            return
        }
        viewModelScope.launch {
            _vocabularyEntries.value = withContext(Dispatchers.IO) {
                VocabularyManager.getEntries(getApplication(), doc.id, doc.title)
            }
        }
    }

    fun consumeVocabularyActionMessage() {
        _vocabularyActionMessage.value = null
    }

    /** Triple-tap entry point: resolves the surrounding sentence from the page, then saves. */
    fun saveWordFromPage(pageNumber: Int, word: String, wordIndex: Int) {
        val page = _pages.value.find { it.pageNumber == pageNumber }
        val contextSentence = page?.text?.let { buildContextSentence(it, wordIndex) } ?: ""
        saveWordToVocabulary(pageNumber, word, contextSentence)
    }

    private fun buildContextSentence(text: String, wordIndex: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (wordIndex !in words.indices) return ""
        val enders = charArrayOf('.', '!', '?', '।', ';')
        var start = wordIndex
        while (start > 0 && !words[start - 1].any { it in enders }) start--
        var end = wordIndex
        while (end < words.lastIndex && !words[end].any { it in enders }) end++
        return words.subList(start, end + 1).joinToString(" ")
    }

    /**
     * Triple-tap word capture: saves [word] with its page and surrounding sentence
     * into the active book's vocabulary notebook. Honest duplicate feedback —
     * an already-saved word is never inserted twice.
     */
    fun saveWordToVocabulary(pageNumber: Int, word: String, contextSentence: String = "") {
        val cleanWord = word.trim()
        if (cleanWord.isBlank()) return
        val doc = _activeDocument.value
        if (doc == null) {
            _vocabularyActionMessage.value = "No open book — open a book first to save words."
            return
        }
        viewModelScope.launch {
            val (entry, isDuplicate) = withContext(Dispatchers.IO) {
                VocabularyManager.addWord(getApplication(), doc.id, doc.title, cleanWord, pageNumber, contextSentence)
            }
            _vocabularyActionMessage.value = if (isDuplicate) {
                "\"${entry.word}\" is already in your Vocabulary Notebook."
            } else {
                "\"${entry.word}\" saved to Vocabulary Notebook (page $pageNumber)."
            }
            refreshVocabularyEntries()
        }
    }

    fun deleteVocabularyEntries(entryIds: Set<String>, retainNotes: Boolean) {
        val doc = _activeDocument.value ?: return
        if (entryIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                VocabularyManager.deleteEntries(getApplication(), doc.id, doc.title, entryIds, retainNotes)
            }
            refreshVocabularyEntries()
        }
    }

    fun editVocabularyWord(entryId: String, newWord: String) {
        val doc = _activeDocument.value ?: return
        if (newWord.isBlank()) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                VocabularyManager.editWord(getApplication(), doc.id, doc.title, entryId, newWord)
            }
            _vocabularyActionMessage.value = if (updated != null) {
                "Word updated to \"${updated.word}\"."
            } else {
                "Could not update that word — it no longer exists."
            }
            refreshVocabularyEntries()
        }
    }

    /**
     * Enriches the selected vocabulary entries from the real lexical provider.
     *
     * Spec §6/§7:
     *  - one request per selected word, results attached to stable entry IDs,
     *  - genuine progress text based on completed requests,
     *  - partial success: one failed word never discards other successes,
     *  - failures are recorded honestly ("Unavailable — reason"); nothing is invented,
     *  - every result is persisted through [VocabularyManager.enrichEntry] (canonical store
     *    first, TXT regenerated from it).
     */
    fun lookupVocabularyEntries(
        entryIds: Set<String>,
        infoType: DictionaryInfoType,
        preferHindi: Boolean
    ) {
        val doc = _activeDocument.value ?: return
        val targets = _vocabularyEntries.value.filter { entryIds.contains(it.id) }
        if (targets.isEmpty()) return
        if (_isLookingUpDictionary.value) return

        viewModelScope.launch {
            _isLookingUpDictionary.value = true
            var completed = 0
            var succeeded = 0
            var failed = 0

            try {
                for (entry in targets) {
                    _lookupStatusText.value =
                        "Looking up ${completed + 1} of ${targets.size}: \"${entry.word}\"..."

                    val result = try {
                        withContext(Dispatchers.IO) {
                            DictionaryService.lookup(entry.word, infoType, preferHindi)
                        }
                    } catch (e: Exception) {
                        DictionaryResult(
                            word = entry.word,
                            infoType = infoType,
                            source = "Unavailable — ${e.message ?: e.javaClass.simpleName}"
                        )
                    }

                    val isFailure = result.source.startsWith("Unavailable")
                    if (isFailure) failed++ else succeeded++

                    withContext(Dispatchers.IO) {
                        VocabularyManager.enrichEntry(
                            context = getApplication(),
                            documentId = doc.id,
                            bookTitle = doc.title,
                            entryId = entry.id,
                            result = result,
                            preferHindi = preferHindi
                        )
                    }
                    completed++
                }
            } finally {
                _lookupStatusText.value = when {
                    failed == 0 -> "Saved dictionary results for $succeeded word(s)."
                    succeeded == 0 ->
                        "No results could be fetched ($failed word(s) unavailable). Existing notes were kept."
                    else ->
                        "$succeeded word(s) updated; $failed word(s) unavailable. Existing notes were kept."
                }
                _isLookingUpDictionary.value = false
                refreshVocabularyEntries()
            }
        }
    }

    // ==========================================
    // NAVIGATION: Vocabulary editor & Paste-text import
    // ==========================================

    fun openVocabularyEditor() {
        refreshVocabularyEntries()
        _currentScreen.value = AppScreen.VOCABULARY
    }

    fun openPasteText() {
        _currentScreen.value = AppScreen.PASTE_TEXT
    }

    fun navigateToLibrary() {
        _currentScreen.value = AppScreen.LIBRARY
    }

    /** Creates a real book from pasted text (P6, §5) and opens it at the cover. */
    fun createBookFromPastedText(title: String, author: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) {
                DocumentParserService.createDocumentFromText(
                    title = title.trim(),
                    author = author.trim(),
                    content = text,
                    format = DocumentFormat.TXT
                )
            }
            docDao.insertDocument(doc)
            openDocument(doc)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
