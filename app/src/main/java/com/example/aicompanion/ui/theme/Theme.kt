package com.example.aicompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PilotBlueDark,
    onPrimary = Color(0xFF002F68),
    primaryContainer = PilotBlueContainerDark,
    onPrimaryContainer = PilotBlueDark,
    secondary = PilotSlateDark,
    onSecondary = Color(0xFF283041),
    secondaryContainer = PilotSlateContainerDark,
    onSecondaryContainer = PilotSlateDark,
    tertiary = PilotCoralDark,
    onTertiary = Color(0xFF5F1600),
    tertiaryContainer = PilotCoralContainerDark,
    onTertiaryContainer = PilotCoralDark,
    surface = PilotSurfaceDark,
    surfaceVariant = PilotSurfaceVariantDark,
    background = PilotBackgroundDark,
    error = PilotErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = PilotBlue,
    onPrimary = Color.White,
    primaryContainer = PilotBlueContainer,
    onPrimaryContainer = Color(0xFF001A42),
    secondary = PilotSlate,
    onSecondary = Color.White,
    secondaryContainer = PilotSlateContainer,
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = PilotCoral,
    onTertiary = Color.White,
    tertiaryContainer = PilotCoralContainer,
    onTertiaryContainer = Color(0xFF3B0800),
    surface = PilotSurface,
    surfaceVariant = PilotSurfaceVariant,
    background = PilotBackground,
    error = PilotError
)

@Composable
fun AICompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — always use Pocket Pilot's custom palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
