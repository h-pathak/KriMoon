package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PhasesDarkColorScheme = darkColorScheme(
    primary = PhasesRose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF561D3A),
    onPrimaryContainer = PhasesRoseSoft,
    secondary = Color(0xFFD1A4EE),
    onSecondary = Color(0xFF2C123F),
    secondaryContainer = Color(0xFF42225C),
    onSecondaryContainer = Color(0xFFF3E4FF),
    tertiary = PhasesSage,
    onTertiary = Color.White,
    background = DarkPhasesBg,
    onBackground = DarkPhasesInk,
    surface = DarkPhasesSurface,
    onSurface = DarkPhasesInk,
    surfaceVariant = DarkPhasesSurfaceVariant,
    onSurfaceVariant = DarkPhasesInkSoft,
    outline = DarkPhasesLine,
    outlineVariant = DarkPhasesLine,
    error = Color(0xFFFF6B7A)
)

private val PhasesLightColorScheme = lightColorScheme(
    primary = PhasesRose,
    onPrimary = Color.White,
    primaryContainer = PhasesRoseSoft,
    onPrimaryContainer = Color(0xFF6B1843),
    secondary = PhasesPlum,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E6FA),
    onSecondaryContainer = Color(0xFF4E1B42),
    tertiary = PhasesSage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F5EA),
    onTertiaryContainer = Color(0xFF194B33),
    background = PhasesBg,
    onBackground = PhasesInk,
    surface = PhasesSurface,
    onSurface = PhasesInk,
    surfaceVariant = PhasesSurfaceTint,
    onSurfaceVariant = PhasesInkSoft,
    outline = PhasesLine,
    outlineVariant = PhasesLine,
    error = PhasesCoral
)

private val MasculineDarkColorScheme = darkColorScheme(
    primary = MaleCobalt,
    onPrimary = Color.White,
    primaryContainer = MaleCobaltSoft,
    onPrimaryContainer = MaleSky,
    secondary = MaleEmerald,
    onSecondary = Color(0xFF032318),
    secondaryContainer = Color(0xFF0E2F26),
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = MaleAmber,
    onTertiary = Color(0xFF261700),
    tertiaryContainer = Color(0xFF33240B),
    onTertiaryContainer = Color(0xFFFCD34D),
    background = MaleObsidian,
    onBackground = MaleInk,
    surface = MaleSurface,
    onSurface = MaleInk,
    surfaceVariant = MaleSurfaceElevated,
    onSurfaceVariant = MaleInkMuted,
    outline = MaleBorder,
    outlineVariant = MaleBorder,
    error = MaleCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isMasculine: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isMasculine -> MasculineDarkColorScheme
        darkTheme -> PhasesDarkColorScheme
        else -> PhasesLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val useLightBars = !isMasculine && !darkTheme
                controller.isAppearanceLightStatusBars = useLightBars
                controller.isAppearanceLightNavigationBars = useLightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
