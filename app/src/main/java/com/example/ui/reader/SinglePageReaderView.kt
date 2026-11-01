package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.os.SystemClock
import com.example.data.model.DocumentEntity
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderPage
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReaderTheme
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MidnightBackground
import com.example.ui.theme.MidnightText
import com.example.ui.theme.MidnightTextSecondary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PaperText
import com.example.ui.theme.PaperTextSecondary
import com.example.ui.theme.SepiaBackground
import com.example.ui.theme.SepiaText
import com.example.ui.theme.SepiaTextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SinglePageReaderView(
    document: DocumentEntity,
    currentPage: ReaderPage,
    totalPages: Int,
    preferences: ReaderPreferences,
    activeWordIndex: Int? = null,
    activePageNumber: Int? = null,
    onWordDoubleTapped: (pageNumber: Int, wordIndex: Int) -> Unit = { _, _ -> },
    onWordTripleTapped: (pageNumber: Int, word: String, wordIndex: Int) -> Unit = { _, _, _ -> },
    onPageChange: (newPage: Int) -> Unit,
    onOpenControlPanel: () -> Unit,
    onOpenVocabularyNotebook: () -> Unit = {},
    onRequestLandscape: () -> Unit,
    onOpenStoryboardStudio: () -> Unit,
    onCloseBook: () -> Unit,
    pageCurlState: PageCurlState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var wordActionMenu by remember { mutableStateOf<Triple<String, Int, Int>?>(null) } // word, pageNumber, wordIndex

    // §9: when a rendered PDF page image exists, the user chooses between the visual
    // Page and the extracted Text — never both at once (no duplicate page content).
    var contentMode by remember(currentPage.pageNumber) { mutableStateOf(PageContentMode.PAGE) }
    val pageImageFile = remember(currentPage.imagePath) {
        currentPage.imagePath?.let { File(it) }?.takeIf { it.exists() }
    }
    val hasPageImage = pageImageFile != null
    val isTtsActiveOnThisPage = activePageNumber == currentPage.pageNumber

    // When narration is active on this page, surface the karaoke Text view automatically.
    androidx.compose.runtime.LaunchedEffect(isTtsActiveOnThisPage, currentPage.pageNumber) {
        if (isTtsActiveOnThisPage) contentMode = PageContentMode.TEXT
    }

    val themeBg = when (preferences.theme) {
        ReaderTheme.PAPER -> PaperBackground
        ReaderTheme.SEPIA -> SepiaBackground
        ReaderTheme.MIDNIGHT -> MidnightBackground
    }
    val themeText = when (preferences.theme) {
        ReaderTheme.PAPER -> PaperText
        ReaderTheme.SEPIA -> SepiaText
        ReaderTheme.MIDNIGHT -> MidnightText
    }
    val themeTextSecondary = when (preferences.theme) {
        ReaderTheme.PAPER -> PaperTextSecondary
        ReaderTheme.SEPIA -> SepiaTextSecondary
        ReaderTheme.MIDNIGHT -> MidnightTextSecondary
    }
    val fontFamily = when (preferences.font) {
        ReaderFont.LITERATA, ReaderFont.GEORGIA -> FontFamily.Serif
        ReaderFont.MERRIWEATHER -> FontFamily.Serif
        ReaderFont.ATKINSON -> FontFamily.SansSerif
    }

    val canTurnBackward = currentPage.pageNumber > 1
    val canTurnForward = currentPage.pageNumber < totalPages

    // Accessible long-press word selection menu (5.1 accessible fallback)
    wordActionMenu?.let { (word, pageNum, wordIdx) ->
        AlertDialog(
            onDismissRequest = { wordActionMenu = null },
            containerColor = NavyDark,
            title = {
                Text(
                    text = "Word: \"$word\"",
                    color = GoldFoil,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select an action for this word:", color = Color(0xFFB0BACB), fontSize = 12.sp)

                    Button(
                        onClick = {
                            wordActionMenu = null
                            onWordTripleTapped(pageNum, word, wordIdx)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("accessible_save_vocab_button")
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Vocabulary Notebook")
                    }

                    Button(
                        onClick = {
                            wordActionMenu = null
                            onWordDoubleTapped(pageNum, wordIdx)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33D4AF37), contentColor = GoldLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("accessible_read_from_here_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read from Here")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { wordActionMenu = null }) {
                    Text("Dismiss", color = Color(0xFFB0BACB))
                }
            }
        )
    }

    PageFlipContainer(
        state = pageCurlState,
        isLandscape = false,
        canTurnForward = canTurnForward,
        canTurnBackward = canTurnBackward,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0F1A))
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCloseBook,
                        modifier = Modifier.size(38.dp).testTag("close_book_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close to Library",
                            tint = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            text = document.title,
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Page ${currentPage.pageNumber} of $totalPages",
                            color = Color(0xFF9EAABF),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Vocabulary Notebook Button (P3)
                    IconButton(
                        onClick = onOpenVocabularyNotebook,
                        modifier = Modifier.size(38.dp).testTag("open_vocabulary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Edit Vocabulary Notebook",
                            tint = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onOpenStoryboardStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33D4AF37),
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(34.dp).testTag("storyboard_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = GoldLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Storyboards", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onRequestLandscape,
                        modifier = Modifier.size(38.dp).testTag("portrait_to_landscape_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Switch to Landscape 16:9",
                            tint = GoldLight
                        )
                    }
                }
            }

            // Page / Text content toggle (only when a rendered page image exists)
            if (hasPageImage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContentModeChip(
                        label = "Page",
                        selected = contentMode == PageContentMode.PAGE,
                        testTag = "content_mode_page",
                        onClick = { contentMode = PageContentMode.PAGE }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ContentModeChip(
                        label = "Text",
                        selected = contentMode == PageContentMode.TEXT,
                        testTag = "content_mode_text",
                        onClick = { contentMode = PageContentMode.TEXT }
                    )
                }
            }

            // Main Reading Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(16.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(themeBg)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3.5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // §9: a single tap on the surface performs NO action — the
                        // orientation hint must not re-trigger on every tap. Long-press
                        // still opens the control panel.
                        detectTapGestures(
                            onTap = { },
                            onLongPress = { onOpenControlPanel() }
                        )
                    }
                    .testTag("single_page_reader_surface")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Chapter Name
                    if (!currentPage.chapterName.isNullOrBlank()) {
                        Text(
                            text = currentPage.chapterName.uppercase(),
                            color = themeTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(themeTextSecondary.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // §9: show EITHER the rendered PDF page OR the extracted text,
                    // never both (prevents duplicated-looking page content).
                    val showPageImage = hasPageImage && contentMode == PageContentMode.PAGE
                    val showExtractedText = !hasPageImage || contentMode == PageContentMode.TEXT

                    if (showPageImage && pageImageFile != null) {
                        AsyncImage(
                            model = pageImageFile,
                            contentDescription = "PDF Page ${currentPage.pageNumber}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .shadow(4.dp, RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Interactive Karaoke Reading Text (supports tap sequence: double-tap seek, triple-tap vocabulary capture)
                    if (showExtractedText) {
                        KaraokeInteractiveText(
                            text = currentPage.text,
                            pageNumber = currentPage.pageNumber,
                            activeWordIndex = activeWordIndex,
                            isActivePage = activePageNumber == currentPage.pageNumber,
                            preferences = preferences,
                            themeText = themeText,
                            fontFamily = fontFamily,
                            onWordDoubleTapped = onWordDoubleTapped,
                            onWordTripleTapped = onWordTripleTapped,
                            onOpenControlPanel = onOpenControlPanel,
                            onWordLongPressed = { word, page, wordIdx ->
                                wordActionMenu = Triple(word, page, wordIdx)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("interactive_karaoke_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "— Page ${currentPage.pageNumber} of $totalPages —",
                        color = themeTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Page Turn Arrows
                if (canTurnBackward) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pageCurlState.animateTurn(forward = false)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .shadow(6.dp, CircleShape)
                            .background(Color(0x33000000), CircleShape)
                            .testTag("button_prev_page")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Page",
                            tint = Color.White
                        )
                    }
                }

                if (canTurnForward) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pageCurlState.animateTurn(forward = true)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .size(40.dp)
                            .shadow(6.dp, CircleShape)
                            .background(Color(0x33000000), CircleShape)
                            .testTag("button_next_page")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Page",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** §9: which representation of a rendered PDF page the reader is showing. */
enum class PageContentMode { PAGE, TEXT }

@Composable
private fun ContentModeChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = if (selected) Color(0x55D4AF37) else Color(0x22FFFFFF),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) GoldFoil else Color(0x33D4AF37)
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (selected) GoldFoil else Color(0xFFB0BACB),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}

/**
 * Interactive text element supporting:
 * 1. Double-tap to read from that word (fired only after the tap window passes).
 * 2. Triple-tap to capture the word into the Vocabulary Notebook.
 * 3. Long-press for the accessible word-action menu (or control panel when on empty space).
 * 4. Synchronized karaoke word highlighting; TRANSPARENT highlight renders no styling.
 * Single taps perform NO action (never seeks).
 */
@Composable
fun KaraokeInteractiveText(
    text: String,
    pageNumber: Int,
    activeWordIndex: Int?,
    isActivePage: Boolean,
    preferences: ReaderPreferences,
    themeText: Color,
    fontFamily: FontFamily,
    onWordDoubleTapped: (pageNumber: Int, wordIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenControlPanel: (() -> Unit)? = null,
    onWordTripleTapped: (pageNumber: Int, word: String, wordIndex: Int) -> Unit = { _, _, _ -> },
    onWordLongPressed: ((word: String, pageNumber: Int, wordIndex: Int) -> Unit)? = null
) {
    // Extract words and character ranges
    val wordsWithRanges = remember(text) {
        val list = mutableListOf<Triple<String, Int, Int>>() // word, startChar, endChar
        val regex = Regex("\\S+")
        val matches = regex.findAll(text)
        for (m in matches) {
            list.add(Triple(m.value, m.range.first, m.range.last + 1))
        }
        list
    }

    val annotatedString: AnnotatedString = remember(
        text,
        activeWordIndex,
        isActivePage,
        preferences.highlightColor,
        themeText
    ) {
        buildAnnotatedString {
            append(text)
            val highlight = preferences.highlightColor
            if (isActivePage && activeWordIndex != null && highlight.isVisibleHighlight &&
                activeWordIndex in wordsWithRanges.indices
            ) {
                val (_, start, end) = wordsWithRanges[activeWordIndex]
                addStyle(
                    style = SpanStyle(
                        background = Color(highlight.colorHex),
                        color = Color(highlight.textColorHex),
                        fontWeight = FontWeight.Bold
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tapArbiter = remember(text) { TapSequenceArbiter(windowMillis = 300L) }
    val gestureScope = rememberCoroutineScope()

    Text(
        text = annotatedString,
        color = themeText,
        fontSize = preferences.fontSizeSp.sp,
        fontFamily = fontFamily,
        lineHeight = (preferences.fontSizeSp * 1.55f).sp,
        textAlign = TextAlign.Start,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.pointerInput(text, wordsWithRanges) {
            fun wordIndexAt(offset: Offset): Int? {
                val layout = textLayoutResult ?: return null
                if (offset.x < 0f || offset.y < 0f || offset.x > layout.size.width || offset.y > layout.size.height) return null
                val charOffset = layout.getOffsetForPosition(offset)
                val idx = wordsWithRanges.indexOfFirst { (_, start, end) -> charOffset in start until end }
                return if (idx != -1) idx else null
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downTime = down.uptimeMillis
                val downPos = down.position
                var longPressHandled = false
                var canceled = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val stillPressed = event.changes.any { it.pressed }

                    if (!stillPressed) {
                        if (!canceled && !longPressHandled) {
                            val upChange = event.changes.first()
                            val idx = wordIndexAt(upChange.position)
                            when (val decision = tapArbiter.registerTap(upChange.uptimeMillis)) {
                                is TapSequenceArbiter.Decision.ScheduleDouble -> {
                                    val deadline = decision.deadlineMillis
                                    gestureScope.launch {
                                        val remaining = deadline - SystemClock.uptimeMillis()
                                        if (remaining > 0) delay(remaining)
                                        if (tapArbiter.expire(SystemClock.uptimeMillis()) && idx != null) {
                                            onWordDoubleTapped(pageNumber, idx)
                                        }
                                    }
                                }
                                is TapSequenceArbiter.Decision.FireTriple -> {
                                    if (idx != null) {
                                        onWordTripleTapped(pageNumber, wordsWithRanges[idx].first, idx)
                                    }
                                }
                                is TapSequenceArbiter.Decision.None -> {}
                            }
                        }
                        break
                    }

                    val pressedChange = event.changes.first { it.pressed }
                    if (!longPressHandled) {
                        val distance = (pressedChange.position - downPos).getDistance()
                        if (pressedChange.uptimeMillis - downTime >= viewConfiguration.longPressTimeoutMillis &&
                            distance < viewConfiguration.touchSlop
                        ) {
                            longPressHandled = true
                            pressedChange.consume()
                            val idx = wordIndexAt(pressedChange.position)
                            if (idx != null) {
                                onWordLongPressed?.invoke(wordsWithRanges[idx].first, pageNumber, idx)
                            } else {
                                onOpenControlPanel?.invoke()
                            }
                        } else if (distance > viewConfiguration.touchSlop) {
                            canceled = true
                            break
                        }
                    }
                }
            }
        }
    )
}
