package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.ui.theme.EditorialDisplayFont
import com.example.ui.theme.MaleBorder
import com.example.ui.theme.MaleCobalt
import com.example.ui.theme.MaleInk
import com.example.ui.theme.MaleInkMuted
import com.example.ui.theme.MaleObsidian
import com.example.ui.theme.MaleSky
import com.example.ui.theme.MaleSurface
import com.example.ui.theme.MaleSurfaceElevated
import com.example.ui.theme.PhasesInk
import com.example.ui.theme.PhasesInkSoft
import com.example.ui.theme.PhasesLine
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.viewmodel.MaleNavScreen
import com.example.viewmodel.NavScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhasesTopBar(
    title: String,
    subtitle: String? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showLunarCrest = title == "KriMoon"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        PhasesRose.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                        PhasesPlum.copy(alpha = 0.07f)
                    )
                )
            )
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = EditorialDisplayFont,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (showLunarCrest) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🌙✨",
                                fontSize = 15.sp
                            )
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesRose.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            actions = {
                val ctx = LocalContext.current
                val topHaptic = LocalHapticFeedback.current
                IconButton(
                    onClick = {
                        AestheticHaptics.softTick(ctx, topHaptic)
                        onOpenSettings()
                    },
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PhasesRose.copy(alpha = 0.12f))
                            .border(1.dp, PhasesRose.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = PhasesPlum,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        HorizontalDivider(
            thickness = 0.8.dp,
            color = PhasesRose.copy(alpha = 0.18f)
        )
    }
}

@Composable
fun PhasesBottomNav(
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = PhasesRose.copy(alpha = 0.22f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            NavScreen.entries.forEach { screen ->
                val isSelected = currentScreen == screen
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.18f else 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "nav_scale"
                )
                val wiggleDeg by animateFloatAsState(
                    targetValue = if (isSelected) -6f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "nav_wiggle"
                )
                val navEmoji = when (screen) {
                    NavScreen.CYCLE -> if (isSelected) "🌸" else "🌷"
                    NavScreen.PERIODS -> if (isSelected) "🌙" else "🗓️"
                    NavScreen.CARE -> if (isSelected) "🫧" else "💧"
                    NavScreen.NOTES -> if (isSelected) "💌" else "📝"
                    NavScreen.PLAY -> if (isSelected) "🎀" else "🕹️"
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        AestheticHaptics.softTick(context, haptic)
                        onScreenSelected(screen)
                    },
                    icon = {
                        Text(
                            text = navEmoji,
                            fontSize = 20.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                rotationZ = wiggleDeg
                                alpha = if (isSelected) 1f else 0.78f
                            }
                        )
                    },
                    label = {
                        Text(
                            text = screen.label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = PhasesRose.copy(alpha = 0.18f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = PhasesRose,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaleTopBar(
    title: String,
    subtitle: String? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaleObsidian)
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaleInk,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaleCobalt.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, MaleCobalt.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                ),
                                color = MaleSky,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            actions = {
                val ctx = LocalContext.current
                val topHaptic = LocalHapticFeedback.current
                IconButton(
                    onClick = {
                        AestheticHaptics.softTick(ctx, topHaptic)
                        onOpenSettings()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaleSurfaceElevated)
                            .border(1.dp, MaleBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaleSky,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaleBorder
        )
    }
}

@Composable
fun MaleBottomNav(
    currentScreen: MaleNavScreen,
    onScreenSelected: (MaleNavScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaleBorder
        )
        NavigationBar(
            containerColor = MaleSurface,
            tonalElevation = 0.dp
        ) {
            MaleNavScreen.entries.forEach { screen ->
                val isSelected = currentScreen == screen
                val icon = when (screen) {
                    MaleNavScreen.PARTNER_CARE -> if (isSelected) Icons.Filled.Shield else Icons.Outlined.Shield
                    MaleNavScreen.ARCADE -> if (isSelected) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports
                    MaleNavScreen.LOUNGE -> if (isSelected) Icons.Filled.Groups else Icons.Outlined.Groups
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        AestheticHaptics.softTick(context, haptic)
                        onScreenSelected(screen)
                    },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = screen.label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = screen.label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaleCobalt.copy(alpha = 0.22f),
                        selectedIconColor = MaleSky,
                        selectedTextColor = MaleSky,
                        unselectedIconColor = MaleInkMuted,
                        unselectedTextColor = MaleInkMuted
                    ),
                    modifier = Modifier.testTag("male_nav_${screen.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun PhaseHeroCard(
    status: CycleStatus,
    modifier: Modifier = Modifier
) {
    val phase = status.currentPhase
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 3f, cornerRadius = 26.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            phase.accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phase Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = phase.accentColor.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, phase.accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = phase.emoji,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = phase.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = phase.accentColor
                            )
                        }
                    }

                    // Cycle Confidence / Count Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (status.cycleCount > 1) "${status.cycleCount} cycles tracked" else "Cycle 1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Phase Main Title & Day
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = phase.emoji,
                        fontSize = 44.sp,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text(
                            text = if (status.isLate) {
                                "Day ${status.dayInCycle} · ${status.daysLate}d late"
                            } else {
                                "Day ${status.dayInCycle} of ${status.totalCycleDays}"
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = phase.shortName,
                            style = MaterialTheme.typography.titleMedium,
                            color = phase.accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Poetic description
                Text(
                    text = "“${phase.description}”",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CycleProgressGauge(
    status: CycleStatus,
    modifier: Modifier = Modifier
) {
    val phase = status.currentPhase
    val progress = (status.dayInCycle.toFloat() / status.totalCycleDays.toFloat()).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(170.dp)
    ) {
        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        val arcColor = phase.accentColor

        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(diameter, diameter)

            // Background full ring
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Day ${status.dayInCycle}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of ${status.totalCycleDays} days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = phase.emoji,
                fontSize = 18.sp
            )
        }
    }
}

private val friendlyDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
}
private val shortDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d", Locale.getDefault())
}
private val dayOfWeekDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
}

fun formatFriendlyDate(millis: Long): String {
    return friendlyDateFormat.get()?.format(Date(millis)) ?: ""
}

fun formatShortDate(millis: Long): String {
    return shortDateFormat.get()?.format(Date(millis)) ?: ""
}

fun formatDayOfWeekDate(millis: Long): String {
    return dayOfWeekDateFormat.get()?.format(Date(millis)) ?: ""
}
