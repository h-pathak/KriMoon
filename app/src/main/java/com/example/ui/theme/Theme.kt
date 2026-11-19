package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PhasesLavender,
    onPrimary = PhasesDarkPaper,
    primaryContainer = Color(0xFF3B2654),
    onPrimaryContainer = Color(0xFFE8D4FF),
    secondary = PhasesRoseMid,
    onSecondary = Color(0xFF3D182A),
    secondaryContainer = Color(0xFF4A253A),
    onSecondaryContainer = Color(0xFFFFD8EC),
    tertiary = PhasesSage,
    onTertiary = Color(0xFF14381C),
    tertiaryContainer = Color(0xFF27492D),
    onTertiaryContainer = Color(0xFFD4F5DA),
    background = PhasesDarkPaper,
    onBackground = PhasesDarkInk,
    surface = PhasesDarkPaperRaised,
    onSurface = PhasesDarkInk,
    surfaceVariant = Color(0xFF35254A),
    onSurfaceVariant = PhasesDarkInkSoft,
    outline = PhasesDarkLine,
    outlineVariant = Color(0xFF4A3466)
)

private val LightColorScheme = lightColorScheme(
    primary = PhasesPlum,
    onPrimary = Color.White,
    primaryContainer = PhasesPlumSoft,
    onPrimaryContainer = PhasesPlum,
    secondary = PhasesRose,
    onSecondary = Color.White,
    secondaryContainer = PhasesRoseSoft,
    onSecondaryContainer = PhasesRose,
    tertiary = PhasesSage,
    onTertiary = Color.White,
    tertiaryContainer = PhasesSageSoft,
    onTertiaryContainer = Color(0xFF23542B),
    background = PhasesPaper,
    onBackground = PhasesInk,
    surface = PhasesPaperRaised,
    onSurface = PhasesInk,
    surfaceVariant = Color(0xFFF9EEFD),
    onSurfaceVariant = PhasesInkSoft,
    outline = PhasesLine,
    outlineVariant = Color(0xFFF0E4F9)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
