package com.example

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.ReaderLayoutMode
import com.example.ui.AppScreen
import com.example.ui.ReaderViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.PasteTextScreen
import com.example.ui.reader.BookCoverView
import com.example.ui.reader.DoublePageSpreadView
import com.example.ui.reader.EditVocabularyScreen
import com.example.ui.reader.FloatingReadingPlayer
import com.example.ui.reader.LongPressControlPanel
import com.example.ui.reader.OrientationRecommendationBanner
import com.example.ui.reader.SinglePageReaderView
import com.example.ui.reader.rememberPageCurlState
import com.example.ui.storyboard.StoryboardStudioView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                StoryFlipApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StoryFlipApp(viewModel: ReaderViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val activeDocument by viewModel.activeDocument.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val recommendationVisible by viewModel.recommendationVisible.collectAsState()
    val targetLandscape by viewModel.recommendationTargetLandscape.collectAsState()
    val controlPanelVisible by viewModel.controlPanelVisible.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val scenes by viewModel.storyboardScenes.collectAsState()
    val isGeneratingStoryboard by viewModel.isGeneratingStoryboard.collectAsState()

    val isParsing by viewModel.isParsing.collectAsState()
    val parsingStage by viewModel.parsingStage.collectAsState()
    val parsingFilename by viewModel.parsingFilename.collectAsState()

    val vocabularyMessage by viewModel.vocabularyActionMessage.collectAsState()
    val appContext = LocalContext.current
    LaunchedEffect(vocabularyMessage) {
        val msg = vocabularyMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeVocabularyActionMessage()
        }
    }

    val ttsPlaying by viewModel.ttsManager.isPlaying.collectAsState()
    val currentSentence by viewModel.ttsManager.currentSentence.collectAsState()
    val equalizerBars by viewModel.ttsManager.equalizerBars.collectAsState()
    val isListeningVoiceCommand by viewModel.ttsManager.isListening.collectAsState()
    val lastVoiceCommand by viewModel.ttsManager.lastVoiceCommand.collectAsState()
    val activeWordIndex by viewModel.activeWordIndex.collectAsState()
    val activePageNumber by viewModel.activePageNumber.collectAsState()

    // Page curl engine state shared by gestures and buttons
    val pageCurlState = rememberPageCurlState { forward ->
        viewModel.onPageTurn(forward)
    }

    // Physical Phone Rotation Detection & Convergence
    val configuration = LocalConfiguration.current
    val isPhysicalLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isPhysicalLandscape, currentScreen, layoutMode) {
        if (currentScreen == AppScreen.READER && layoutMode != ReaderLayoutMode.COVER_PORTRAIT) {
            if (isPhysicalLandscape && layoutMode == ReaderLayoutMode.PORTRAIT_SINGLE_PAGE) {
                // Device rotated to landscape: automatically transition to double-page spread
                viewModel.switchLayout(toLandscape = true)
            } else if (!isPhysicalLandscape && layoutMode == ReaderLayoutMode.LANDSCAPE_DOUBLE_PAGE) {
                // Device rotated to portrait: automatically transition to single-page focus mode
                viewModel.switchLayout(toLandscape = false)
            }
        }
    }

    // Android System Back Navigation Handler
    BackHandler(enabled = true) {
        when {
            controlPanelVisible -> viewModel.dismissControlPanel()
            recommendationVisible -> viewModel.dismissRecommendation()
            currentScreen == AppScreen.STORYBOARD_STUDIO -> viewModel.navigateBackToReader()
            currentScreen == AppScreen.VOCABULARY -> viewModel.navigateBackToReader()
            currentScreen == AppScreen.PASTE_TEXT -> viewModel.navigateToLibrary()
            currentScreen == AppScreen.READER -> {
                if (layoutMode != ReaderLayoutMode.COVER_PORTRAIT) {
                    viewModel.closeBook()
                } else {
                    viewModel.exitToLibrary()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.LIBRARY -> {
                    LibraryScreen(
                        documents = documents,
                        onSelectDocument = { viewModel.openDocument(it) },
                        onDeleteDocument = { viewModel.deleteDocument(it) },
                        onFileSelected = { uri -> viewModel.importFileFromUri(uri) },
                        onLoadSampleDocument = { viewModel.loadSample(it) },
                        onOpenPasteText = { viewModel.openPasteText() },
                        isParsing = isParsing,
                        parsingStage = parsingStage,
                        parsingFilename = parsingFilename,
                        onCancelParsing = { viewModel.cancelParsing() }
                    )
                }

                AppScreen.PASTE_TEXT -> {
                    PasteTextScreen(
                        onCreateBook = { title, author, text ->
                            viewModel.createBookFromPastedText(title, author, text)
                        },
                        onBackToLibrary = { viewModel.navigateToLibrary() }
                    )
                }

                AppScreen.VOCABULARY -> {
                    EditVocabularyScreen(
                        viewModel = viewModel,
                        onBackToReader = { viewModel.navigateBackToReader() }
                    )
                }

                AppScreen.STORYBOARD_STUDIO -> {
                    activeDocument?.let { doc ->
                        StoryboardStudioView(
                            document = doc,
                            scenes = scenes,
                            isGenerating = isGeneratingStoryboard,
                            onRegenerate = { viewModel.regenerateStoryboard() },
                            onAddShot = { sceneNum, shot -> viewModel.addCustomShot(sceneNum, shot) },
                            onBackToReader = { viewModel.navigateBackToReader() },
                            pageCurlState = pageCurlState
                        )
                    }
                }

                AppScreen.READER -> {
                    activeDocument?.let { doc ->
                        val totalPages = pages.size.coerceAtLeast(1)
                        val safeIndex = currentPageIndex.coerceIn(0, totalPages - 1)
                        val currentPage = pages.getOrNull(safeIndex) ?: com.example.data.model.ReaderPage(
                            pageNumber = 1,
                            text = doc.rawContent
                        )

                        when (layoutMode) {
                            ReaderLayoutMode.COVER_PORTRAIT -> {
                                BookCoverView(
                                    document = doc,
                                    onOpenBook = { viewModel.openBook() },
                                    showAddToLibrary = doc.isDemo && documents.none { it.id == doc.id },
                                    onAddToLibrary = { viewModel.addDemoToLibrary(doc) }
                                )
                            }

                            ReaderLayoutMode.PORTRAIT_SINGLE_PAGE -> {
                                SinglePageReaderView(
                                    document = doc,
                                    currentPage = currentPage,
                                    totalPages = totalPages,
                                    preferences = preferences,
                                    activeWordIndex = activeWordIndex,
                                    activePageNumber = activePageNumber,
                                    onWordDoubleTapped = { pageNum, wordIdx -> viewModel.seekToWord(pageNum, wordIdx) },
                                    onWordTripleTapped = { pageNum, word, wordIdx ->
                                        viewModel.saveWordFromPage(pageNum, word, wordIdx)
                                    },
                                    onPageChange = { viewModel.onPageTurn(forward = it > currentPage.pageNumber) },
                                    onOpenControlPanel = { viewModel.openControlPanel() },
                                    onOpenVocabularyNotebook = { viewModel.openVocabularyEditor() },
                                    onRequestLandscape = { viewModel.showOrientationRecommendation(targetLandscape = true) },
                                    onOpenStoryboardStudio = { viewModel.navigateToStoryboardStudio() },
                                    onCloseBook = { viewModel.closeBook() },
                                    pageCurlState = pageCurlState
                                )
                            }

                            ReaderLayoutMode.LANDSCAPE_DOUBLE_PAGE -> {
                                val leftPage1Based = if (currentPage.pageNumber % 2 != 0) currentPage.pageNumber else (currentPage.pageNumber - 1).coerceAtLeast(1)
                                val rightPage1Based = leftPage1Based + 1

                                val leftPage = pages.find { it.pageNumber == leftPage1Based }
                                val rightPage = pages.find { it.pageNumber == rightPage1Based }

                                DoublePageSpreadView(
                                    document = doc,
                                    leftPage = leftPage,
                                    rightPage = rightPage,
                                    totalPages = totalPages,
                                    preferences = preferences,
                                    activeWordIndex = activeWordIndex,
                                    activePageNumber = activePageNumber,
                                    onWordDoubleTapped = { pageNum, wordIdx -> viewModel.seekToWord(pageNum, wordIdx) },
                                    onWordTripleTapped = { pageNum, word, wordIdx ->
                                        viewModel.saveWordFromPage(pageNum, word, wordIdx)
                                    },
                                    onPageSelectedForFocus = { selectedPageNum ->
                                        viewModel.selectPageForFocus(selectedPageNum)
                                    },
                                    onOpenControlPanel = { viewModel.openControlPanel() },
                                    onRequestPortrait = { viewModel.showOrientationRecommendation(targetLandscape = false) },
                                    onOpenStoryboardStudio = { viewModel.navigateToStoryboardStudio() },
                                    onCloseBook = { viewModel.closeBook() },
                                    pageCurlState = pageCurlState
                                )
                            }
                        }

                        // Floating Reading Player (only shown during open reading modes)
                        if (layoutMode != ReaderLayoutMode.COVER_PORTRAIT) {
                            FloatingReadingPlayer(
                                isPlaying = ttsPlaying,
                                currentSentence = currentSentence,
                                progress = (currentPage.pageNumber.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f),
                                equalizerBars = equalizerBars,
                                speechRate = preferences.speechRate,
                                onPlayPause = { viewModel.toggleSpeech() },
                                onPrevious = { viewModel.onPageTurn(forward = false) },
                                onNext = { viewModel.onPageTurn(forward = true) },
                                onSpeedChange = { newSpeed -> viewModel.onLiveSpeedChange(newSpeed) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        // Orientation Recommendation 3-second Toast Banner
                        OrientationRecommendationBanner(
                            visible = recommendationVisible,
                            targetLandscape = targetLandscape,
                            onSwitchLayout = { viewModel.switchLayout(toLandscape = targetLandscape) },
                            onTimeout = { viewModel.dismissRecommendation() },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        // Shared Long-Press Glass Control Panel
                        LongPressControlPanel(
                            visible = controlPanelVisible,
                            preferences = preferences,
                            onPreferencesChange = { viewModel.updatePreferences(it) },
                            onSpeedLiveChange = { newSpeed -> viewModel.onLiveSpeedChange(newSpeed) },
                            onPreviewVoice = { lang, preset -> viewModel.previewVoice(lang, preset) },
                            onStartVoiceCommand = { viewModel.startListeningVoiceCommand() },
                            isListeningVoiceCommand = isListeningVoiceCommand,
                            lastVoiceCommand = lastVoiceCommand,
                            onResetZoom = { viewModel.dismissControlPanel() },
                            onCloseBook = {
                                viewModel.dismissControlPanel()
                                viewModel.closeBook()
                            },
                            onDismiss = { viewModel.dismissControlPanel() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}
