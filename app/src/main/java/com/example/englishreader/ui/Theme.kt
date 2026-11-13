package com.example.englishreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmColors = lightColorScheme(
    primary = Color(0xFF3E6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E7CF),
    onPrimaryContainer = Color(0xFF0B2416),
    secondary = Color(0xFF8A6A2F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0E4C6),
    onSecondaryContainer = Color(0xFF3F3320),
    background = Color(0xFFFBF7EC),
    onBackground = Color(0xFF1F2320),
    surface = Color(0xFFFDFAF2),
    onSurface = Color(0xFF1F2320),
    surfaceVariant = Color(0xFFEDE6D6),
    onSurfaceVariant = Color(0xFF5A5F57),
    surfaceContainerLowest = Color(0xFFFFFDF7),
    surfaceContainerLow = Color(0xFFF9F3E4),
    surfaceContainer = Color(0xFFF5EEDD),
    surfaceContainerHigh = Color(0xFFF1E9D6),
    surfaceContainerHighest = Color(0xFFECE3CE),
    outlineVariant = Color(0xFFD8D0BE),
)

@Composable
fun EnglishReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WarmColors, content = content)
}
