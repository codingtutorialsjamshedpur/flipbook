package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DocumentEntity
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderPage
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReaderTheme
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MidnightBackground
import com.example.ui.theme.MidnightText
import com.example.ui.theme.MidnightTextSecondary
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
fun DoublePageSpreadView(
    document: DocumentEntity,
    leftPage: ReaderPage?,
    rightPage: ReaderPage?,
    totalPages: Int,
    preferences: ReaderPreferences,
    activeWordIndex: Int? = null,
    activePageNumber: Int? = null,
    onWordDoubleTapped: (pageNumber: Int, wordIndex: Int) -> Unit = { _, _ -> },
    onWordTripleTapped: (pageNumber: Int, word: String, wordIndex: Int) -> Unit = { _, _, _ -> },
    onWordLongPressed: ((word: String, pageNumber: Int, wordIndex: Int) -> Unit)? = null,
    onPageSelectedForFocus: (pageNumber: Int) -> Unit,
    onOpenControlPanel: () -> Unit,
    onRequestPortrait: () -> Unit,
    onOpenStoryboardStudio: () -> Unit,
    onCloseBook: () -> Unit,
    pageCurlState: PageCurlState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

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

    val canTurnBackward = (leftPage?.pageNumber ?: 1) > 1
    val canTurnForward = (rightPage?.pageNumber ?: (leftPage?.pageNumber ?: 1)) < totalPages

    PageFlipContainer(
        state = pageCurlState,
        isLandscape = true,
        canTurnForward = canTurnForward,
        canTurnBackward = canTurnBackward,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B14))
        ) {
            // Landscape Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCloseBook,
                        modifier = Modifier.size(36.dp).testTag("landscape_close_book_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close to Library",
                            tint = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = document.title,
                        color = GoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "• Double-Page Spread (Pages ${leftPage?.pageNumber ?: "-"} & ${rightPage?.pageNumber ?: "-"})",
                        color = Color(0xFF9EAABF),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOpenStoryboardStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33D4AF37),
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp).testTag("landscape_storyboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = GoldLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Storyboards", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onRequestPortrait,
                        modifier = Modifier.size(36.dp).testTag("landscape_to_portrait_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Switch to Portrait 9:16",
                            tint = GoldLight
                        )
                    }
                }
            }

            // Book Open Hardcover Base & Spread Pages
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .shadow(20.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1726))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Page Surface
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(themeBg)
                            .pointerInput(leftPage?.pageNumber) {
                                detectTapGestures(
                                    onTap = { leftPage?.pageNumber?.let { onPageSelectedForFocus(it) } },
                                    onLongPress = { onOpenControlPanel() }
                                )
                            }
                            .testTag("landscape_left_page")
                    ) {
                        if (leftPage != null) {
                            SpreadPageContent(
                                page = leftPage,
                                activeWordIndex = activeWordIndex,
                                activePageNumber = activePageNumber,
                                preferences = preferences,
                                themeText = themeText,
                                themeTextSecondary = themeTextSecondary,
                                fontFamily = fontFamily,
                                onWordDoubleTapped = onWordDoubleTapped,
                                onWordTripleTapped = onWordTripleTapped,
                                onWordLongPressed = onWordLongPressed,
                                onOpenControlPanel = onOpenControlPanel,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 20.dp, end = 26.dp, top = 16.dp, bottom = 14.dp
                                )
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("End of Document", color = themeTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Central Book Spine & Gutter Shadow
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x33000000),
                                        Color(0x77000000),
                                        Color(0x99000000),
                                        Color(0x77000000),
                                        Color(0x33000000)
                                    )
                                )
                            )
                            .drawBehind {
                                drawLine(
                                    color = Color(0x44000000),
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = 2f
                                )
                            }
                    )

                    // Right Page Surface
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(themeBg)
                            .pointerInput(rightPage?.pageNumber) {
                                detectTapGestures(
                                    onTap = { rightPage?.pageNumber?.let { onPageSelectedForFocus(it) } },
                                    onLongPress = { onOpenControlPanel() }
                                )
                            }
                            .testTag("landscape_right_page")
                    ) {
                        if (rightPage != null) {
                            SpreadPageContent(
                                page = rightPage,
                                activeWordIndex = activeWordIndex,
                                activePageNumber = activePageNumber,
                                preferences = preferences,
                                themeText = themeText,
                                themeTextSecondary = themeTextSecondary,
                                fontFamily = fontFamily,
                                onWordDoubleTapped = onWordDoubleTapped,
                                onWordTripleTapped = onWordTripleTapped,
                                onWordLongPressed = onWordLongPressed,
                                onOpenControlPanel = onOpenControlPanel,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 26.dp, end = 20.dp, top = 16.dp, bottom = 14.dp
                                )
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "— Blank Page —",
                                    color = themeTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }

                // Spread Turn Arrows
                if (canTurnBackward) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pageCurlState.animateTurn(forward = false)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 2.dp)
                            .size(38.dp)
                            .shadow(6.dp, CircleShape)
                            .background(Color(0x33000000), CircleShape)
                            .testTag("landscape_button_prev_spread")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Spread",
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
                            .padding(end = 2.dp)
                            .size(38.dp)
                            .shadow(6.dp, CircleShape)
                            .background(Color(0x33000000), CircleShape)
                            .testTag("landscape_button_next_spread")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Spread",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * §9: one spread page shows EITHER its rendered PDF image OR the extracted karaoke text.
 * The image is preferred; the text is shown while TTS is active on this page (so karaoke
 * highlighting stays visible) or when no page image exists. Never both at once.
 */
@Composable
private fun SpreadPageContent(
    page: ReaderPage,
    activeWordIndex: Int?,
    activePageNumber: Int?,
    preferences: ReaderPreferences,
    themeText: Color,
    themeTextSecondary: Color,
    fontFamily: FontFamily,
    onWordDoubleTapped: (pageNumber: Int, wordIndex: Int) -> Unit,
    onWordTripleTapped: (pageNumber: Int, word: String, wordIndex: Int) -> Unit,
    onWordLongPressed: ((word: String, pageNumber: Int, wordIndex: Int) -> Unit)?,
    onOpenControlPanel: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues
) {
    val pageImageFile = remember(page.imagePath) {
        page.imagePath?.let { File(it) }?.takeIf { it.exists() }
    }
    val hasImage = pageImageFile != null
    val ttsActiveOnThisPage = activePageNumber == page.pageNumber
    val showImage = hasImage && !ttsActiveOnThisPage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        if (!page.chapterName.isNullOrBlank()) {
            Text(
                text = page.chapterName.uppercase(),
                color = themeTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        if (showImage && pageImageFile != null) {
            AsyncImage(
                model = pageImageFile,
                contentDescription = "PDF Page ${page.pageNumber}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.FillWidth
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            KaraokeInteractiveText(
                text = page.text,
                pageNumber = page.pageNumber,
                activeWordIndex = activeWordIndex,
                isActivePage = ttsActiveOnThisPage,
                preferences = preferences,
                themeText = themeText,
                fontFamily = fontFamily,
                onWordDoubleTapped = onWordDoubleTapped,
                onWordTripleTapped = onWordTripleTapped,
                onWordLongPressed = onWordLongPressed,
                onOpenControlPanel = onOpenControlPanel,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "— ${page.pageNumber} —",
            color = themeTextSecondary,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
