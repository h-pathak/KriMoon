package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PeriodLog
import com.example.model.CalendarCyclePhase
import com.example.model.CalendarDayData
import com.example.model.CycleCalculator
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun CyclePhaseCalendar(
    periods: List<PeriodLog>,
    defaultCycleLength: Int = 28,
    defaultPeriodLength: Int = 5,
    onLogPeriodOnDate: (startDateMillis: Long, notes: String, durationDays: Int?, endDateMillis: Long?) -> Unit,
    onOpenMultiDatePicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current

    val cycleStatus = remember(periods, defaultCycleLength, defaultPeriodLength) {
        CycleCalculator.compute(periods, defaultCycleLength, defaultPeriodLength)
    }

    val todayCalendar = remember { Calendar.getInstance() }
    val currentYear = todayCalendar.get(Calendar.YEAR)
    val currentMonth = todayCalendar.get(Calendar.MONTH)
    val todayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    var displayedYear by remember { mutableIntStateOf(currentYear) }
    var displayedMonth by remember { mutableIntStateOf(currentMonth) }
    var selectedDateMillis by remember { mutableStateOf(todayMillis) }
    var highlightedPhaseFilter by remember { mutableStateOf<CalendarCyclePhase?>(null) }
    var showPhaseInfoDialog by remember { mutableStateOf<CalendarCyclePhase?>(null) }
    var showLogPastPeriodDialog by remember { mutableStateOf(false) }
    var logPastPeriodInitialDate by remember { mutableStateOf(todayMillis) }

    // Generate days for the selected month grid
    val daysInMonth by remember(displayedYear, displayedMonth, periods, defaultCycleLength, defaultPeriodLength) {
        derivedStateOf {
            CycleCalculator.generateMonthCalendarDays(
                year = displayedYear,
                month = displayedMonth,
                periods = periods,
                defaultCycleLength = defaultCycleLength,
                defaultPeriodLength = defaultPeriodLength,
                todayMillis = todayMillis
            )
        }
    }

    // Selected day data
    val selectedDayData by remember(selectedDateMillis, daysInMonth, periods, defaultCycleLength, defaultPeriodLength) {
        derivedStateOf {
            val foundInGrid = daysInMonth.find { it.dateMillis == selectedDateMillis }
            if (foundInGrid != null) {
                foundInGrid
            } else {
                val (phase, dayInCycle) = CycleCalculator.computePhaseForDate(
                    targetMillis = selectedDateMillis,
                    periods = periods,
                    defaultCycleLength = defaultCycleLength,
                    defaultPeriodLength = defaultPeriodLength
                )
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                val isPeriodStart = periods.any {
                    val pCal = Calendar.getInstance().apply { timeInMillis = it.startDateMillis }
                    pCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                            pCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                }
                val predWindows = CycleCalculator.computeUpcomingPredictionWindows(
                    periods = periods,
                    defaultCycleLength = defaultCycleLength,
                    defaultPeriodLength = defaultPeriodLength,
                    horizonCycles = 6,
                    referenceTimeMillis = todayMillis
                )
                val matchingPred = predWindows.find { w ->
                    selectedDateMillis in w.earliestDateMillis..w.windowEndDateMillis && selectedDateMillis >= todayMillis
                }
                CalendarDayData(
                    dateMillis = selectedDateMillis,
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH),
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = cal.get(Calendar.MONTH) == displayedMonth && cal.get(Calendar.YEAR) == displayedYear,
                    isToday = selectedDateMillis == todayMillis,
                    phase = phase,
                    dayInCycle = dayInCycle,
                    totalCycleDays = defaultCycleLength,
                    isPeriodStart = isPeriodStart,
                    isPeriodDay = phase == CalendarCyclePhase.MENSTRUATION,
                    isInPredictedWindow = matchingPred != null,
                    isPredictedPeriodStart = matchingPred != null && selectedDateMillis == matchingPred.expectedDateMillis,
                    isPredicted = (selectedDateMillis > todayMillis) || (matchingPred != null)
                )
            }
        }
    }

    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val displayCalendar = remember(displayedYear, displayedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val monthTitle = monthFormatter.format(displayCalendar.time)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2f, cornerRadius = 24.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .testTag("cycle_phase_calendar_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Algorithm Intelligence & Past Period Logging Banner
            AlgorithmTrainingBanner(
                periodsCount = periods.size,
                averageCycleDays = cycleStatus.averageCycleLength,
                sigmaVarianceDays = cycleStatus.sigmaVarianceDays,
                expectedNextMillis = cycleStatus.expectedNextDateMillis,
                predictedWindowStartMillis = cycleStatus.predictedWindowStartMillis,
                predictedWindowEndMillis = cycleStatus.predictedWindowEndMillis,
                onOpenLogPastPeriod = {
                    logPastPeriodInitialDate = if (selectedDateMillis <= todayMillis) selectedDateMillis else todayMillis
                    showLogPastPeriodDialog = true
                },
                onOpenMultiDatePicker = onOpenMultiDatePicker
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Calendar Header with Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cycle Phase Dots & Predictions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Jump to Today button
                    if (displayedYear != currentYear || displayedMonth != currentMonth) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesRose.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    displayedYear = currentYear
                                    displayedMonth = currentMonth
                                    selectedDateMillis = todayMillis
                                }
                                .testTag("calendar_jump_today")
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PhasesRose,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Prev Month Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (displayedMonth == 0) {
                                displayedMonth = 11
                                displayedYear -= 1
                            } else {
                                displayedMonth -= 1
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Next Month Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (displayedMonth == 11) {
                                displayedMonth = 0
                                displayedYear += 1
                            } else {
                                displayedMonth += 1
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Distinct Phase Dots Legend
            PhaseLegendRow(
                highlightedPhase = highlightedPhaseFilter,
                onPhaseClick = { phase ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    highlightedPhaseFilter = if (highlightedPhaseFilter == phase) null else phase
                    showPhaseInfoDialog = phase
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Day of Week Header: Sun Mon Tue Wed Thu Fri Sat
            WeekdayHeaderRow()

            Spacer(modifier = Modifier.height(6.dp))

            // Month Grid of Days (6 rows x 7 columns)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val index = row * 7 + col
                            if (index < daysInMonth.size) {
                                val dayData = daysInMonth[index]
                                val isSelected = dayData.dateMillis == selectedDateMillis
                                val isDimmed = highlightedPhaseFilter != null && dayData.phase != highlightedPhaseFilter

                                CalendarDayCell(
                                    dayData = dayData,
                                    isSelected = isSelected,
                                    isDimmed = isDimmed,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDateMillis = dayData.dateMillis
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Proportional Cycle Phase Timeline Bar
            CyclePhaseDistributionBar(
                cycleLength = defaultCycleLength,
                periodLength = defaultPeriodLength
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Day Phase Detail Card
            SelectedDayPhaseCard(
                dayData = selectedDayData,
                cycleStatus = cycleStatus,
                periodsCount = periods.size,
                onQuickLogPeriod = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundHelper.playCuteChime(context)
                    onLogPeriodOnDate(selectedDayData.dateMillis, "Period recorded via Calendar", null, null)
                    particleController.spawn(
                        centerX = 500f,
                        centerY = 800f,
                        emojis = listOf("🌸", "🩸", "💖", "🌷", "✨"),
                        count = 14
                    )
                    particleController.showToast(
                        coroutineScope = coroutineScope,
                        message = "Period start logged for ${SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedDayData.dateMillis)} 🌸",
                        icon = "🩸"
                    )
                },
                onOpenLogPastPeriod = {
                    logPastPeriodInitialDate = selectedDayData.dateMillis
                    showLogPastPeriodDialog = true
                }
            )
        }
    }

    // Past Period Logging Dialog
    if (showLogPastPeriodDialog) {
        LogPastPeriodDialog(
            initialStartDateMillis = logPastPeriodInitialDate,
            defaultPeriodLength = defaultPeriodLength,
            onDismiss = { showLogPastPeriodDialog = false },
            onOpenMultiDate = onOpenMultiDatePicker,
            onSave = { startMillis, notes, duration, endMillis ->
                showLogPastPeriodDialog = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SoundHelper.playCuteChime(context)
                onLogPeriodOnDate(startMillis, notes, duration, endMillis)
                particleController.spawn(
                    centerX = 500f,
                    centerY = 600f,
                    emojis = listOf("🌸", "🩸", "✨", "🌷", "💖"),
                    count = 16
                )
                particleController.showToast(
                    coroutineScope = coroutineScope,
                    message = "Past period saved! Algorithm updated ✨",
                    icon = "🌸"
                )
            }
        )
    }

    // Phase Information Dialog
    if (showPhaseInfoDialog != null) {
        val phase = showPhaseInfoDialog!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPhaseInfoDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(phase.dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${phase.emoji} ${phase.title}")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = phase.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = phase.containerColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Self-Care & Nutrition Tip",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = phase.onContainerColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = phase.wellnessTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = phase.onContainerColor
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhaseInfoDialog = null }) {
                    Text("Got it 🌸")
                }
            }
        )
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekdays.forEach { dayName ->
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PhaseLegendRow(
    highlightedPhase: CalendarCyclePhase?,
    onPhaseClick: (CalendarCyclePhase) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Predicted Window Legend Chip
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Color(0xFFF3E8FF),
            border = BorderStroke(1.dp, Color(0xFFA855F7)),
            modifier = Modifier.testTag("legend_predicted_window")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(text = "✨", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Predicted Window",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF6B21A8)
                )
            }
        }

        CalendarCyclePhase.values().forEach { phase ->
            val isHighlighted = highlightedPhase == phase
            val isDimmed = highlightedPhase != null && !isHighlighted

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isHighlighted) phase.containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    width = if (isHighlighted) 1.5.dp else 1.dp,
                    color = if (isHighlighted) phase.dotColor else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { onPhaseClick(phase) }
                    .graphicsLayer { alpha = if (isDimmed) 0.45f else 1.0f }
                    .testTag("legend_phase_${phase.shortName.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(phase.dotColor)
                            .shadow(2.dp, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = phase.shortName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isHighlighted) phase.onContainerColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayData: CalendarDayData,
    isSelected: Boolean,
    isDimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cell_scale"
    )

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        dayData.isToday -> PhasesRose
        dayData.isPredictedPeriodStart -> Color(0xFF7E22CE)
        !dayData.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    dayData.isHistoricalLogged -> Color(0xFFE11D48).copy(alpha = 0.14f)
                    dayData.isToday -> PhasesRose.copy(alpha = 0.08f)
                    dayData.isPredictedPeriodStart -> Color(0xFF9333EA).copy(alpha = 0.20f)
                    dayData.isInPredictedWindow -> Color(0xFF9333EA).copy(alpha = 0.10f)
                    dayData.isPredicted && dayData.phase == CalendarCyclePhase.MENSTRUATION -> Color(0xFFE11D48).copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = when {
                    isSelected -> 1.5.dp
                    dayData.isHistoricalLogged -> 1.dp
                    dayData.isToday -> 1.dp
                    dayData.isPredictedPeriodStart -> 1.5.dp
                    dayData.isInPredictedWindow -> 1.dp
                    dayData.isPredicted && dayData.phase == CalendarCyclePhase.MENSTRUATION -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    dayData.isHistoricalLogged -> Color(0xFFE11D48).copy(alpha = 0.45f)
                    dayData.isToday -> PhasesRose.copy(alpha = 0.5f)
                    dayData.isPredictedPeriodStart -> Color(0xFF9333EA)
                    dayData.isInPredictedWindow -> Color(0xFF9333EA).copy(alpha = 0.45f)
                    dayData.isPredicted && dayData.phase == CalendarCyclePhase.MENSTRUATION -> Color(0xFFE11D48).copy(alpha = 0.28f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isDimmed) 0.35f else 1.0f
            }
            .testTag("calendar_day_${dayData.dayOfMonth}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Day Number
            Text(
                text = "${dayData.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isSelected || dayData.isToday || dayData.isPredictedPeriodStart) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Cycle Phase Dot Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (dayData.isInPredictedWindow) Color(0xFF9333EA) else dayData.phase.dotColor)
                        .testTag("phase_dot_${dayData.phase.shortName.lowercase()}_${dayData.dayOfMonth}")
                )

                if (dayData.isHistoricalLogged) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "🩸",
                        fontSize = 7.sp,
                        lineHeight = 7.sp
                    )
                } else if (dayData.isPredictedPeriodStart) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "🔮",
                        fontSize = 7.sp,
                        lineHeight = 7.sp
                    )
                } else if (dayData.isInPredictedWindow || (dayData.isPredicted && dayData.phase == CalendarCyclePhase.MENSTRUATION)) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "✨",
                        fontSize = 7.sp,
                        lineHeight = 7.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CyclePhaseDistributionBar(
    cycleLength: Int,
    periodLength: Int,
    modifier: Modifier = Modifier
) {
    val safeCycle = cycleLength.coerceAtLeast(18)
    val safePeriod = periodLength.coerceIn(1, safeCycle / 2)
    val safeMin = (safePeriod + 2).coerceAtLeast(1)
    val safeMax = (safeCycle - 3).coerceAtLeast(safeMin)
    val ovulationDay = (safeCycle - 14).coerceIn(safeMin, safeMax)
    val follicularDays = maxOf(1, (ovulationDay - 3) - safePeriod)
    val ovulatoryDays = 4
    val lutealDays = maxOf(1, safeCycle - (ovulationDay + 1))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cycle Phase Rhythm (${safeCycle} days)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "4 Distinct Phases",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Visual segmented horizontal bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(maxOf(1f, safePeriod.toFloat()))
                    .background(CalendarCyclePhase.MENSTRUATION.dotColor)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .weight(maxOf(1f, follicularDays.toFloat()))
                    .background(CalendarCyclePhase.FOLLICULAR.dotColor)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .weight(maxOf(1f, ovulatoryDays.toFloat()))
                    .background(CalendarCyclePhase.OVULATORY.dotColor)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .weight(maxOf(1f, lutealDays.toFloat()))
                    .background(CalendarCyclePhase.LUTEAL.dotColor)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sub-labels with days breakdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PhaseBarItem(name = "Menstruation", days = "${safePeriod}d", color = CalendarCyclePhase.MENSTRUATION.dotColor)
            PhaseBarItem(name = "Follicular", days = "${follicularDays}d", color = CalendarCyclePhase.FOLLICULAR.dotColor)
            PhaseBarItem(name = "Ovulatory", days = "${ovulatoryDays}d", color = CalendarCyclePhase.OVULATORY.dotColor)
            PhaseBarItem(name = "Luteal", days = "${lutealDays}d", color = CalendarCyclePhase.LUTEAL.dotColor)
        }
    }
}

@Composable
private fun PhaseBarItem(
    name: String,
    days: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$name ($days)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlgorithmTrainingBanner(
    periodsCount: Int,
    averageCycleDays: Double,
    sigmaVarianceDays: Double,
    expectedNextMillis: Long,
    predictedWindowStartMillis: Long,
    predictedWindowEndMillis: Long,
    onOpenLogPastPeriod: () -> Unit,
    onOpenMultiDatePicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isTrained = periodsCount >= 2
    val windowStartStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(predictedWindowStartMillis)
    val windowEndStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(predictedWindowEndMillis)
    val expNextStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(expectedNextMillis)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isTrained) Color(0xFFF3E8FF).copy(alpha = 0.75f) else Color(0xFFFFF1F2).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, if (isTrained) Color(0xFFC084FC).copy(alpha = 0.5f) else Color(0xFFFDA4AF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isTrained) Color(0xFFE9D5FF) else Color(0xFFFFE4E6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTrained) "🧠" else "🌱",
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTrained) {
                            "Algorithm Active (${periodsCount} Cycles Learned)"
                        } else if (periodsCount == 1) {
                            "Algorithm Training (1 Cycle Logged)"
                        } else {
                            "Train Algorithm with Past Periods"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isTrained) Color(0xFF6B21A8) else Color(0xFF9F1239)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isTrained) {
                            "Predicted Window: $windowStartStr–$windowEndStr (Expected ~$expNextStr) • Avg ${String.format(Locale.US, "%.0f", averageCycleDays)}d ±${String.format(Locale.US, "%.1f", sigmaVarianceDays)}d"
                        } else if (periodsCount == 1) {
                            "Expected ~$expNextStr. Log 1 more cycle so algorithm refines your predicted window range."
                        } else {
                            "Log past periods across last months to detect your personal rhythm and show accurate predicted windows."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = if (isTrained) Color(0xFF581C87) else Color(0xFF881337)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated, Spacious Action Buttons Row Below Content
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenMultiDatePicker != null) {
                    OutlinedButton(
                        onClick = onOpenMultiDatePicker,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("calendar_open_multi_date_picker_button")
                    ) {
                        Text(
                            text = "🗓️ Multi-Date",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onOpenLogPastPeriod,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTrained) PhasesPlum else PhasesRose,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("calendar_open_log_past_period_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Log Past Period",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayPhaseCard(
    dayData: CalendarDayData,
    cycleStatus: CycleStatus,
    periodsCount: Int,
    onQuickLogPeriod: () -> Unit,
    onOpenLogPastPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val formattedDate = dateFormatter.format(dayData.dateMillis)
    val phase = dayData.phase
    val todayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val isPastOrToday = dayData.dateMillis <= todayMillis

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = phase.containerColor.copy(alpha = 0.65f)),
        border = BorderStroke(1.dp, phase.dotColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("selected_day_phase_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with date & status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = phase.onContainerColor
                )

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = phase.dotColor,
                    modifier = Modifier.testTag("selected_phase_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${phase.emoji} ${phase.title}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cycle day indicator & Algorithmic status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Day ${dayData.dayInCycle} of ${dayData.totalCycleDays}-day cycle",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = phase.onContainerColor.copy(alpha = 0.85f)
                )

                if (dayData.isHistoricalLogged) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFFE11D48).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "🩸 Logged Past Flow",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color(0xFF9F1239),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (dayData.isPredictedPeriodStart) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF9333EA).copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color(0xFF9333EA))
                    ) {
                        Text(
                            text = "🔮 Expected Period Start",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color(0xFF6B21A8),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (dayData.isInPredictedWindow) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF9333EA).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF9333EA).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "✨ In Predicted Period Window",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color(0xFF7E22CE),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (dayData.isPredicted) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF9333EA).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF9333EA).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "✨ Algorithm Predicted",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color(0xFF7E22CE),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Biological state & self-care tip
            Text(
                text = phase.description,
                style = MaterialTheme.typography.bodySmall,
                color = phase.onContainerColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "💡 ${phase.wellnessTip}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                color = phase.onContainerColor.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            if (dayData.isHistoricalLogged) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE11D48).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🩸 Period Recorded on this date",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF9F1239)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenLogPastPeriod,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("calendar_add_another_past_button")
                    ) {
                        Text("+ Add Another Past Period", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPastOrToday) {
                        OutlinedButton(
                            onClick = onQuickLogPeriod,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("calendar_log_period_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Quick Start 🌸",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Button(
                        onClick = onOpenLogPastPeriod,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhasesRose,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("calendar_log_past_period_action_button")
                    ) {
                        Text(
                            text = if (isPastOrToday) "Log Past Period 📋" else "Schedule Period 📋",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogPastPeriodDialog(
    initialStartDateMillis: Long,
    defaultPeriodLength: Int = 5,
    onDismiss: () -> Unit,
    onOpenMultiDate: (() -> Unit)? = null,
    onSave: (startDateMillis: Long, notes: String, durationDays: Int, endDateMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var startDateMillis by remember { mutableStateOf(initialStartDateMillis) }
    var durationDays by remember { mutableIntStateOf(defaultPeriodLength.coerceIn(2, 10)) }
    var selectedFlowTag by remember { mutableStateOf("🩸 Normal flow") }
    var notesText by remember { mutableStateOf("") }

    val flowOptions = listOf("🩸 Normal flow", "💧 Light flow", "🌊 Heavy flow", "✨ Spotting")
    val durationOptions = listOf(3, 4, 5, 6, 7, 8)

    val msPerDay = 24L * 60 * 60 * 1000
    val endDateMillis = startDateMillis + ((durationDays - 1).toLong() * msPerDay)

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    fun showStartDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startDateMillis = newCal.timeInMillis
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🩸", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Log Past Period",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Trains cycle algorithm for upcoming predictions",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Educational algorithm explanation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3E8FF).copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color(0xFFD8B4FE).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "✨", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Historical periods calculate your exact cycle length (days between cycles) and bleeding duration, predicting future periods and fertile phases with high accuracy.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF581C87)
                        )
                    }
                }

                if (onOpenMultiDate != null) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                            onOpenMultiDate()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_open_multi_date_picker_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🗓️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Multiple Dates (Past Months) ➔",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PhasesRose
                            )
                        }
                    }
                }

                // Start Date Picker Row
                Column {
                    Text(
                        text = "Period Start Date",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showStartDatePicker()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_pick_start_date_button")
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateFormatter.format(startDateMillis),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Duration selector (Days of bleeding)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bleeding Duration",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$durationDays days (ends ${SimpleDateFormat("MMM d", Locale.getDefault()).format(endDateMillis)})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PhasesRose
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        durationOptions.forEach { days ->
                            val isSel = durationDays == days
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    durationDays = days
                                },
                                label = { Text("$days days", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhasesRose.copy(alpha = 0.2f),
                                    selectedLabelColor = PhasesRose
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSel,
                                    selectedBorderColor = PhasesRose,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }

                // Flow Intensity Quick Selector
                Column {
                    Text(
                        text = "Flow Intensity",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        flowOptions.forEach { flow ->
                            val isSel = selectedFlowTag == flow
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFlowTag = flow
                                },
                                label = { Text(flow, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhasesPlum.copy(alpha = 0.15f),
                                    selectedLabelColor = PhasesPlum
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSel,
                                    selectedBorderColor = PhasesPlum,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }

                // Notes input
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text("Optional notes (e.g. cramps, energy level)...", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_past_period_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val fullNotes = if (notesText.isBlank()) selectedFlowTag else "$selectedFlowTag • ${notesText.trim()}"
                    onSave(startDateMillis, fullNotes, durationDays, endDateMillis)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhasesRose,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("dialog_save_past_period_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save to Algorithm 🌸", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
