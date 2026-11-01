package com.example.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.ui.theme.BurgundyRibbon
import com.example.ui.theme.BurgundyRibbonLight
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import kotlinx.coroutines.launch

/** Visible phases of the hardcover opening transition (§4.G). */
enum class BookCoverPhase { CLOSED, OPENING, OPEN }

@Composable
fun BookCoverView(
    document: DocumentEntity,
    onOpenBook: () -> Unit,
    showAddToLibrary: Boolean = false,
    onAddToLibrary: () -> Unit = {},
    onPhaseChange: (BookCoverPhase) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val openRotation = remember { Animatable(0f) }
    var isOpening by remember { mutableStateOf(false) }

    // Dynamic shadow that changes with the cover angle (§4.B)
    val coverShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp)
    val dynamicElevation by remember {
        derivedStateOf {
            val progress = (kotlin.math.abs(openRotation.value) / 110f).coerceIn(0f, 1f)
            (28f - 20f * progress).dp
        }
    }

    // Pulsing subtle glow for "Tap to Open" affordance
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .clickable {
                // Guard against duplicate taps while opening (§4.E: never create a
                // duplicate reader session or change the page index accidentally).
                if (isOpening) return@clickable
                isOpening = true
                coroutineScope.launch {
                    onPhaseChange(BookCoverPhase.OPENING)
                    openRotation.animateTo(
                        targetValue = -110f,
                        animationSpec = tween(650, easing = FastOutSlowInEasing)
                    )
                    onPhaseChange(BookCoverPhase.OPEN)
                    onOpenBook()
                }
            }
            .testTag("book_cover_surface"),
        contentAlignment = Alignment.Center
    ) {
            // Perspective book container — full-bleed: occupies the entire portrait
            // viewport (edge-to-edge), with internal safe padding for typography so
            // no large blank margins surround the cover (P5/P6).
            //
            // §4.A/B: the cover rotates around the LEFT SPINE EDGE (transformOrigin
            // x=0) with a real perspective cue, instead of spinning flat around its
            // center like a card.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = openRotation.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 8 * density
                    }
                    .shadow(elevation = dynamicElevation, shape = coverShape)
                    .clip(coverShape)
                    .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF090E1A), // Spine edge
                            NavyDeep,
                            Color(0xFF131D31),
                            NavyDark
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(GoldFoil, Color(0x33D4AF37), GoldFoil)
                    ),
                    shape = coverShape
                )
        ) {
            // Leather texture & spine emboss lines
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Spine shadow band on left edge
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0x99000000), Color(0x33000000), Color.Transparent),
                                startX = 0f,
                                endX = 36.dp.toPx()
                            )
                        )
                        // Embossed vertical book spine creases
                        drawLine(
                            color = Color(0x22D4AF37),
                            start = Offset(24.dp.toPx(), 0f),
                            end = Offset(24.dp.toPx(), size.height),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color(0x44000000),
                            start = Offset(28.dp.toPx(), 0f),
                            end = Offset(28.dp.toPx(), size.height),
                            strokeWidth = 3f
                        )
                    }
            )

            // Burgundy Satin Bookmark Ribbon (Shown ONLY when saved reading progress exists!)
            if (document.hasProgress && document.bookmarkPage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-32).dp, y = (-8).dp)
                        .width(28.dp)
                        .height(110.dp)
                        .shadow(8.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(BurgundyRibbonLight, BurgundyRibbon, Color(0xFF5A0014))
                            ),
                            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Saved Bookmark at Page ${document.bookmarkPage}",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp).padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // Cover Inner Foil Frame & Typography
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp, end = 28.dp, top = 36.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top format badge
                Surface(
                    color = Color(0x26D4AF37),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44D4AF37))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (document.format == DocumentFormat.SCRIPT) Icons.Default.Movie else Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (document.format) {
                                DocumentFormat.SCRIPT -> "ORIGINAL SCREENPLAY"
                                DocumentFormat.PDF -> "DOCUMENT EDITION"
                                DocumentFormat.EPUB -> "EPUB VOLUME"
                                DocumentFormat.DOCX -> "DIRECTOR'S DRAFT"
                                else -> "CLASSIC TEXT"
                            },
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Center Title & Author
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = document.title,
                        color = GoldFoil,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(1.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, GoldFoil, Color.Transparent)
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "BY " + document.author.uppercase(),
                        color = Color(0xFFC0CADC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Tap Affordance & Page Count Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (document.hasProgress && document.lastReadPage > 1) {
                        Text(
                            text = "Resume from Page ${document.lastReadPage} of ${document.totalPages}",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Explicit "Add sample to Library" action (§1.D) — only for demo
                    // samples that are NOT already part of the Library.
                    if (showAddToLibrary) {
                        Surface(
                            onClick = onAddToLibrary,
                            color = Color(0x22D4AF37),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55D4AF37)),
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .testTag("add_to_library_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryAdd,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add sample to Library",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = pulseAlpha }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GoldFoil,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TAP COVER TO OPEN",
                            color = GoldFoil,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
