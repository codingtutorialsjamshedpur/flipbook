package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import kotlinx.coroutines.delay

@Composable
fun OrientationRecommendationBanner(
    visible: Boolean,
    targetLandscape: Boolean,
    onSwitchLayout: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(1f) }

    // 3-second countdown timer
    LaunchedEffect(visible, targetLandscape) {
        if (visible) {
            progress = 1f
            val totalSteps = 60
            val stepDelay = 3000L / totalSteps
            for (i in totalSteps downTo 0) {
                progress = i / totalSteps.toFloat()
                delay(stepDelay)
            }
            onTimeout()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NavyDark.copy(alpha = 0.95f), NavyDeep.copy(alpha = 0.98f))
                    )
                )
                .border(1.dp, Color(0x66D4AF37), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("orientation_recommendation_banner")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33D4AF37)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (targetLandscape) "Best viewing angle: 16:9 (Landscape)" else "Best viewing angle: 9:16 (Portrait)",
                                color = GoldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (targetLandscape) "Switch to landscape to read two pages side by side." else "Switch to portrait to focus on this page and use zoom.",
                                color = Color(0xFFB0BACB),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onSwitchLayout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldFoil,
                            contentColor = NavyDeep
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("switch_layout_button")
                    ) {
                        Text(
                            text = if (targetLandscape) "Switch to 16:9" else "Switch to 9:16",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3-second remaining duration indicator bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = GoldFoil,
                    trackColor = Color(0x33FFFFFF)
                )
            }
        }
    }
}
