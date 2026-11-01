package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val StoryFlipDarkColorScheme = darkColorScheme(
    primary = GoldFoil,
    onPrimary = NavyDeep,
    primaryContainer = NavyDark,
    onPrimaryContainer = GoldLight,
    secondary = GoldMuted,
    onSecondary = Color.White,
    background = NavyDeep,
    onBackground = Color(0xFFE8EAEE),
    surface = NavyDark,
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = NavySurface,
    onSurfaceVariant = Color(0xFFC4C8D4),
    outline = GlassBorder
)

private val StoryFlipLightColorScheme = lightColorScheme(
    primary = NavyDeep,
    onPrimary = Color.White,
    primaryContainer = GoldLight,
    onPrimaryContainer = NavyDeep,
    secondary = GoldFoil,
    onSecondary = NavyDeep,
    background = PaperBackground,
    onBackground = PaperText,
    surface = Color.White,
    onSurface = PaperText,
    surfaceVariant = PaperSpineShadow,
    onSurfaceVariant = PaperTextSecondary,
    outline = Color(0xFFD4AF37)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to director dark luxury aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StoryFlipDarkColorScheme else StoryFlipLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
