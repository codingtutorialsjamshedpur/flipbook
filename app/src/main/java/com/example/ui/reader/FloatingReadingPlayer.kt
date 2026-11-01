package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep

@Composable
fun FloatingReadingPlayer(
    isPlaying: Boolean,
    currentSentence: String,
    progress: Float,
    equalizerBars: List<Float>,
    speechRate: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSpeedPopup by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Outside dismiss backdrop when speed popup is open
        if (showSpeedPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showSpeedPopup = false
                    }
            )
        }

        // Upward Speed Selector Popup (opens directly above the speed pill)
        AnimatedVisibility(
            visible = showSpeedPopup,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 60.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .border(1.5.dp, Color(0x88D4AF37), RoundedCornerShape(16.dp))
                    .testTag("speed_selector_popup")
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(NavyDark.copy(alpha = 0.98f), NavyDeep.copy(alpha = 0.98f))
                            )
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val supportedRates = listOf(2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f)
                    supportedRates.forEach { rate ->
                        val isSelected = speechRate == rate
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GoldFoil else Color.Transparent)
                                .clickable {
                                    onSpeedChange(rate)
                                    showSpeedPopup = false
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("speed_option_${rate}")
                        ) {
                            Text(
                                text = "${rate}x",
                                color = if (isSelected) NavyDeep else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Floating Player Bar
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(26.dp))
                .border(1.dp, Color(0x55D4AF37), RoundedCornerShape(26.dp))
                .testTag("floating_reading_player"),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NavyDeep.copy(alpha = 0.95f),
                                NavyDark.copy(alpha = 0.95f),
                                NavyDeep.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Animated Equalizer Waveform & Current Segment Text
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(end = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                equalizerBars.take(5).forEach { barLevel ->
                                    val barHeight by animateDpAsState(
                                        targetValue = if (isPlaying) (4.dp + (16.dp * barLevel)) else 4.dp,
                                        label = "bar"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(if (isPlaying) GoldFoil else Color(0x66D4AF37))
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlaying) "READING ALOUD" else "VOICE NARRATION",
                                    color = GoldLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = currentSentence.ifBlank { "Tap play to read aloud or double-tap any word." },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Right: Controls & Speed Selector Pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Speed Pill with Tap & Long-Press to Open Upward Selector
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33D4AF37))
                                    .border(1.dp, Color(0x66D4AF37), RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { showSpeedPopup = !showSpeedPopup },
                                            onLongPress = { showSpeedPopup = true }
                                        )
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("player_speed_pill")
                            ) {
                                Text(
                                    text = "${speechRate}x",
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(34.dp).testTag("player_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Page",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(GoldFoil)
                                    .testTag("player_play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = NavyDeep,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(34.dp).testTag("player_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Page",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = GoldFoil,
                        trackColor = Color(0x22FFFFFF)
                    )
                }
            }
        }
    }
}
