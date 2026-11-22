package com.yin.term.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RedBlackColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonDim,
    onPrimaryContainer = Color.White,
    secondary = TerminalTextMuted,
    onSecondary = Color.White,
    background = ObsidianBlack,
    onBackground = TerminalTextPrimary,
    surface = ObsidianSurface,
    onSurface = TerminalTextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TerminalTextMuted,
    outline = ObsidianBorder,
    error = CrimsonRed,
    onError = Color.White
)

@Composable
fun YinTermTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RedBlackColorScheme,
        typography = Typography,
        content = content
    )
}