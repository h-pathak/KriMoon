package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PhasesRoseMid,
    onPrimary = Color(0xFF331023),
    primaryContainer = Color(0xFF472038),
    onPrimaryContainer = Color(0xFFFFD8E8),
    secondary = PhasesLavender,
    onSecondary = Color(0xFF1F1638),
    secondaryContainer = Color(0xFF32264C),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = Color(0xFF7BC49A),
    onTertiary = Color(0xFF0E321D),
    tertiaryContainer = Color(0xFF1F422D),
    onTertiaryContainer = Color(0xFFD4F5E1),
    background = PhasesDarkPaper,
    onBackground = PhasesDarkInk,
    surface = PhasesDarkPaperRaised,
    onSurface = PhasesDarkInk,
    surfaceVariant = Color(0xFF2D1F34),
    onSurfaceVariant = PhasesDarkInkSoft,
    outline = PhasesDarkLine,
    outlineVariant = Color(0xFF432D4D)
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
    onTertiaryContainer = Color(0xFF24573A),
    background = PhasesPaper,
    onBackground = PhasesInk,
    surface = PhasesPaperRaised,
    onSurface = PhasesInk,
    surfaceVariant = Color(0xFFF9EFF3),
    onSurfaceVariant = PhasesInkSoft,
    outline = PhasesLine,
    outlineVariant = Color(0xFFF3E2E9)
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
