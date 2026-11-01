package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HighlightColor
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingLanguage
import com.example.data.model.VoicePreset
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.SepiaBackground

private enum class ControlTab(val label: String, val icon: ImageVector) {
    FONT("Font", Icons.Default.TextFields),
    SIZE("Size", Icons.Default.FormatSize),
    THEME("Theme", Icons.Default.Palette),
    HIGHLIGHT("Highlight", Icons.Default.FormatColorFill),
    SPEED("Speed", Icons.Default.Speed),
    VOICE("Voice & Lang", Icons.Default.RecordVoiceOver),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun LongPressControlPanel(
    visible: Boolean,
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onSpeedLiveChange: (Float) -> Unit,
    onPreviewVoice: (ReadingLanguage, VoicePreset) -> Unit,
    onStartVoiceCommand: () -> Unit,
    isListeningVoiceCommand: Boolean = false,
    lastVoiceCommand: String? = null,
    onResetZoom: () -> Unit,
    onCloseBook: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(ControlTab.FONT) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NavyDark.copy(alpha = 0.96f), NavyDeep.copy(alpha = 0.98f))
                        )
                    )
                    .border(1.5.dp, Color(0x66D4AF37), RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .testTag("long_press_control_panel")
            ) {
                // Header with Tabs and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ControlTab.values().forEach { tab ->
                            val selected = activeTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) GoldFoil else Color(0x22FFFFFF))
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) NavyDeep else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = tab.label,
                                        color = if (selected) NavyDeep else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_control_panel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Controls",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x22D4AF37))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                when (activeTab) {
                    ControlTab.FONT -> {
                        Text(
                            text = "Select Reading Typography",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFont.values().forEach { font ->
                                val selected = preferences.font == font
                                FilterChip(
                                    selected = selected,
                                    onClick = { onPreferencesChange(preferences.copy(font = font)) },
                                    label = { Text(font.displayName, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldFoil,
                                        selectedLabelColor = NavyDeep,
                                        containerColor = Color(0x1AFFFFFF),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    ControlTab.SIZE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Size", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${preferences.fontSizeSp.toInt()} sp", color = GoldFoil, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("A", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = preferences.fontSizeSp,
                                onValueChange = { onPreferencesChange(preferences.copy(fontSizeSp = it)) },
                                valueRange = 14f..32f,
                                steps = 17,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldFoil,
                                    activeTrackColor = GoldFoil,
                                    inactiveTrackColor = Color(0x33FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("A", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    ControlTab.THEME -> {
                        Text(
                            text = "Reader Color Atmosphere",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ReaderTheme.values().forEach { theme ->
                                val selected = preferences.theme == theme
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onPreferencesChange(preferences.copy(theme = theme)) }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (theme) {
                                                    ReaderTheme.PAPER -> PaperBackground
                                                    ReaderTheme.SEPIA -> SepiaBackground
                                                    ReaderTheme.MIDNIGHT -> Color(0xFF18191D)
                                                }
                                            )
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) GoldFoil else Color(0x44FFFFFF),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = theme.displayName,
                                        color = if (selected) GoldFoil else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    ControlTab.HIGHLIGHT -> {
                        Text(
                            text = "Karaoke Word Highlight Color",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HighlightColor.values().forEach { colorOption ->
                                val selected = preferences.highlightColor == colorOption
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onPreferencesChange(preferences.copy(highlightColor = colorOption)) }
                                        .padding(4.dp)
                                        .testTag("highlight_color_${colorOption.name}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorOption.colorHex))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) GoldFoil else Color(0x55FFFFFF),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = colorOption.displayName,
                                        color = if (selected) GoldFoil else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Live Karaoke Preview:",
                            color = Color(0xFF9EAABF),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val previewBg = when (preferences.theme) {
                            ReaderTheme.PAPER -> PaperBackground
                            ReaderTheme.SEPIA -> SepiaBackground
                            ReaderTheme.MIDNIGHT -> Color(0xFF1E222B)
                        }
                        val previewTextDefault = when (preferences.theme) {
                            ReaderTheme.PAPER -> Color(0xFF2C2416)
                            ReaderTheme.SEPIA -> Color(0xFF3E2723)
                            ReaderTheme.MIDNIGHT -> Color(0xFFE0E0E0)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(previewBg)
                                .border(1.dp, Color(0x33D4AF37), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "The quick ",
                                    color = previewTextDefault,
                                    fontSize = 13.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(preferences.highlightColor.colorHex))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "brown fox",
                                        color = Color(preferences.highlightColor.textColorHex),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = " jumps over the lazy dog.",
                                    color = previewTextDefault,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    ControlTab.SPEED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Live Playback Speech Rate", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${preferences.speechRate}x (Active)", color = GoldFoil, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            speeds.forEach { speed ->
                                val selected = preferences.speechRate == speed
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onPreferencesChange(preferences.copy(speechRate = speed))
                                        onSpeedLiveChange(speed)
                                    },
                                    label = { Text("${speed}x", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldFoil,
                                        selectedLabelColor = NavyDeep,
                                        containerColor = Color(0x1AFFFFFF),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Speed changes update active audio narration immediately in real time.",
                            color = Color(0xFF9EAABF),
                            fontSize = 11.sp
                        )
                    }

                    ControlTab.VOICE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Narration Language (Indian & English)", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReadingLanguage.values().forEach { lang ->
                                    val selected = preferences.language == lang
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onPreferencesChange(preferences.copy(language = lang)) },
                                        label = { Text("${lang.flag} ${lang.displayName}", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldFoil,
                                            selectedLabelColor = NavyDeep,
                                            containerColor = Color(0x1AFFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text("Voice Persona", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VoicePreset.values().forEach { preset ->
                                    val selected = preferences.voicePreset == preset
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onPreferencesChange(preferences.copy(voicePreset = preset)) },
                                        label = { Text(preset.title, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldFoil,
                                            selectedLabelColor = NavyDeep,
                                            containerColor = Color(0x1AFFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onPreviewVoice(preferences.language, preferences.voicePreset) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33D4AF37), contentColor = GoldLight),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Preview Voice", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = onStartVoiceCommand,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isListeningVoiceCommand) Color(0xFFD32F2F) else Color(0x33D4AF37),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isListeningVoiceCommand) "Listening..." else "Voice Command", fontSize = 11.sp)
                                }
                            }

                            if (!lastVoiceCommand.isNullOrBlank()) {
                                Text("Recognized: \"$lastVoiceCommand\"", color = GoldLight, fontSize = 11.sp)
                            }
                        }
                    }

                    ControlTab.SETTINGS -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Page-Turn Sound Effect", color = Color.White, fontSize = 13.sp)
                                }
                                Switch(
                                    checked = preferences.turnSound,
                                    onCheckedChange = { onPreferencesChange(preferences.copy(turnSound = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = GoldFoil,
                                        checkedTrackColor = NavyDark
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResetZoom() }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reset Zoom & Pan", color = Color.White, fontSize = 13.sp)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCloseBook() }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Close Book & Save Bookmark", color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
