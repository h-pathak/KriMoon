package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
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
import com.example.model.CycleCalculator
import com.example.model.PredictionWindow
import com.example.notifications.SoundHelper
import com.example.ui.components.CuteAddButton
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class SelectionTarget {
    START_DATE,
    END_DATE
}

enum class CalendarPickerMode(val label: String, val emoji: String) {
    RANGE("Date Range", "↔️"),
    MULTI_DATE("Multiple Dates (Past Months)", "🗓️")
}

data class PeriodCluster(
    val startDateMillis: Long,
    val endDateMillis: Long,
    val durationDays: Int
)

fun clusterConsecutiveDates(dates: Set<Long>): List<PeriodCluster> {
    if (dates.isEmpty()) return emptyList()
    val sorted = dates.map { stripTime(it) }.distinct().sorted()
    val oneDayMillis = 24L * 60 * 60 * 1000

    val clusters = mutableListOf<PeriodCluster>()
    var clusterStart = sorted.first()
    var clusterEnd = clusterStart

    for (i in 1 until sorted.size) {
        val date = sorted[i]
        val diffDays = kotlin.math.round((date - clusterEnd).toDouble() / oneDayMillis).toLong()
        if (diffDays == 1L) {
            clusterEnd = date
        } else {
            val duration = (kotlin.math.round((clusterEnd - clusterStart).toDouble() / oneDayMillis).toInt() + 1).coerceAtLeast(1)
            clusters.add(PeriodCluster(clusterStart, clusterEnd, duration))
            clusterStart = date
            clusterEnd = date
        }
    }
    val finalDuration = (kotlin.math.round((clusterEnd - clusterStart).toDouble() / oneDayMillis).toInt() + 1).coerceAtLeast(1)
    clusters.add(PeriodCluster(clusterStart, clusterEnd, finalDuration))

    return clusters
}

/**
 * An interactive calendar-based selection UI component that allows users to pick
 * their period start and end dates directly on the calendar cells, and edit existing
 * logged period dates in the calendar itself.
 */
@Composable
fun PeriodDateRangeCalendarPicker(
    existingPeriods: List<PeriodLog>,
    defaultCycleLength: Int = 28,
    defaultPeriodLength: Int = 5,
    onSaveNewPeriod: (startDateMillis: Long, endDateMillis: Long?, notes: String, durationDays: Int?) -> Unit,
    onUpdatePeriod: (periodId: Long, startDateMillis: Long, endDateMillis: Long?, notes: String) -> Unit,
    onDeletePeriod: (periodId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current

    val todayCalendar = remember { Calendar.getInstance() }
    val todayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Algorithmic cycle prediction windows
    val predictionWindows = remember(existingPeriods, defaultCycleLength, defaultPeriodLength, todayMillis) {
        CycleCalculator.computeUpcomingPredictionWindows(
            periods = existingPeriods,
            defaultCycleLength = defaultCycleLength,
            defaultPeriodLength = defaultPeriodLength,
            horizonCycles = 6,
            referenceTimeMillis = todayMillis
        )
    }

    var displayedYear by remember { mutableIntStateOf(todayCalendar.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(todayCalendar.get(Calendar.MONTH)) }

    // Selection state
    var selectedStartMillis by remember { mutableStateOf(todayMillis) }
    var selectedEndMillis by remember {
        mutableStateOf(todayMillis + ((defaultPeriodLength - 1).coerceAtLeast(0) * 24L * 60 * 60 * 1000))
    }
    var activeTarget by remember { mutableStateOf(SelectionTarget.START_DATE) }
    var periodNote by remember { mutableStateOf("") }

    // Multi-Date Past Selection Mode state
    var pickerMode by remember { mutableStateOf(CalendarPickerMode.RANGE) }
    var multiSelectedDates by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Edit state (if user is editing an existing period in the calendar)
    var editingPeriodId by remember { mutableStateOf<Long?>(null) }

    // Synchronize edit period state if user selects one
    fun startEditingPeriod(period: PeriodLog) {
        editingPeriodId = period.id
        selectedStartMillis = period.startDateMillis
        selectedEndMillis = period.endDateMillis ?: (period.startDateMillis + ((period.durationDays ?: defaultPeriodLength) - 1) * 24L * 60 * 60 * 1000)
        periodNote = period.notes
        activeTarget = SelectionTarget.START_DATE

        // Jump displayed calendar to that period's month
        val cal = Calendar.getInstance().apply { timeInMillis = period.startDateMillis }
        displayedYear = cal.get(Calendar.YEAR)
        displayedMonth = cal.get(Calendar.MONTH)
    }

    fun cancelEditing() {
        editingPeriodId = null
        selectedStartMillis = todayMillis
        selectedEndMillis = todayMillis + ((defaultPeriodLength - 1).coerceAtLeast(0) * 24L * 60 * 60 * 1000)
        periodNote = ""
        activeTarget = SelectionTarget.START_DATE
    }

    // Computed duration in days
    val durationDays by remember(selectedStartMillis, selectedEndMillis) {
        derivedStateOf {
            if (selectedEndMillis >= selectedStartMillis) {
                val days = kotlin.math.round((selectedEndMillis - selectedStartMillis).toDouble() / (24L * 60 * 60 * 1000)).toInt() + 1
                days.coerceIn(1, 30)
            } else {
                1
            }
        }
    }

    // Days grid for currently displayed month
    val monthGridDays by remember(displayedYear, displayedMonth) {
        derivedStateOf {
            generateMonthGrid(displayedYear, displayedMonth, todayMillis)
        }
    }

    val monthHeaderTitle = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2f, cornerRadius = 24.dp)
            .testTag("period_calendar_picker_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Card Title & Mode Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PhasesRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (editingPeriodId != null) "✏️" else "🗓️", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (editingPeriodId != null) "Edit Period in Calendar" else "Pick Period Dates",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (editingPeriodId != null) "Adjust start or end date directly on the grid" else "Tap calendar days to select start & end dates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (editingPeriodId != null) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clickable { cancelEditing() }
                            .testTag("cancel_edit_period_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Editing #$editingPeriodId", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = PhasesRose, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selection Mode Tabs: Date Range vs Multiple Dates (Past Months for Algorithm)
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp)
                ) {
                    CalendarPickerMode.values().forEach { mode ->
                        val isSelected = pickerMode == mode
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    pickerMode = mode
                                }
                                .testTag("picker_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = mode.emoji, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Past-Months Shortcuts Bar: Jump directly to past months with 1 tap!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (offset in 0 downTo -5) {
                    val targetCal = (todayCalendar.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
                    val targetYear = targetCal.get(Calendar.YEAR)
                    val targetMonth = targetCal.get(Calendar.MONTH)
                    val isCurrentView = displayedYear == targetYear && displayedMonth == targetMonth
                    val label = when (offset) {
                        0 -> "This Month"
                        -1 -> "Last Month"
                        else -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(targetCal.time)
                    }
                    val countInMonth = multiSelectedDates.count {
                        val c = Calendar.getInstance().apply { timeInMillis = it }
                        c.get(Calendar.YEAR) == targetYear && c.get(Calendar.MONTH) == targetMonth
                    }

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isCurrentView) PhasesRose.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isCurrentView) 1.5.dp else 1.dp,
                            color = if (isCurrentView) PhasesRose else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                displayedYear = targetYear
                                displayedMonth = targetMonth
                            }
                            .testTag("picker_month_jump_${offset}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCurrentView) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCurrentView) PhasesRose else MaterialTheme.colorScheme.onSurface
                            )
                            if (countInMonth > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE11D48)
                                ) {
                                    Text(
                                        text = "$countInMonth",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Month Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthHeaderTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Today button
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = PhasesRose.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                displayedYear = todayCalendar.get(Calendar.YEAR)
                                displayedMonth = todayCalendar.get(Calendar.MONTH)
                            }
                            .testTag("calendar_picker_today_btn")
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PhasesRose,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

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
                        modifier = Modifier.testTag("calendar_picker_prev_month")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                    }

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
                        modifier = Modifier.testTag("calendar_picker_next_month")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prediction Window Banner & Quick Pre-fill
            val upcomingPredictionWindow = remember(predictionWindows, todayMillis) {
                predictionWindows.firstOrNull { it.windowEndDateMillis >= todayMillis }
            }
            if (upcomingPredictionWindow != null) {
                val earlyStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(upcomingPredictionWindow.earliestDateMillis)
                val lateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(upcomingPredictionWindow.latestDateMillis)
                val expStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(upcomingPredictionWindow.expectedDateMillis)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF3E8FF).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedStartMillis = upcomingPredictionWindow.expectedDateMillis
                            selectedEndMillis = upcomingPredictionWindow.expectedDateMillis + ((durationDays - 1).coerceAtLeast(0) * 24L * 60 * 60 * 1000)
                            activeTarget = SelectionTarget.START_DATE
                            val cal = Calendar.getInstance().apply { timeInMillis = upcomingPredictionWindow.expectedDateMillis }
                            displayedYear = cal.get(Calendar.YEAR)
                            displayedMonth = cal.get(Calendar.MONTH)
                        }
                        .testTag("picker_predicted_window_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "✨🔮", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Predicted Window: $earlyStr – $lateStr",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF6B21A8)
                                )
                                Text(
                                    text = "Expected Start ~$expStr • Tap to pre-fill dates in calendar",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF7E22CE)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesPlum,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Pre-fill",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Weekday Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Month Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val index = row * 7 + col
                            if (index < monthGridDays.size) {
                                val day = monthGridDays[index]
                                val dayMillis = day.dateMillis

                                val isStart = dayMillis == selectedStartMillis
                                val isEnd = dayMillis == selectedEndMillis
                                val isInRange = dayMillis in (selectedStartMillis + 1)..<selectedEndMillis

                                // Check if this day corresponds to any existing logged period in database
                                val matchingLoggedPeriod = existingPeriods.find { p ->
                                    val pStart = stripTime(p.startDateMillis)
                                    val pEnd = stripTime(p.endDateMillis ?: (p.startDateMillis + ((p.durationDays ?: defaultPeriodLength) - 1) * 24L * 60 * 60 * 1000))
                                    dayMillis in pStart..pEnd
                                }

                                // Check if this day falls within an algorithm-predicted cycle window
                                val matchingPredictionWindow = predictionWindows.find { w ->
                                    dayMillis in w.earliestDateMillis..w.windowEndDateMillis && dayMillis >= todayMillis
                                }
                                val isInPredictedWindow = matchingPredictionWindow != null && matchingLoggedPeriod == null
                                val isPredictedStart = matchingPredictionWindow != null && dayMillis == matchingPredictionWindow.expectedDateMillis && matchingLoggedPeriod == null

                                val isMultiSelected = multiSelectedDates.contains(dayMillis)

                                CalendarPickerCell(
                                    dayNumber = day.dayOfMonth,
                                    isCurrentMonth = day.isCurrentMonth,
                                    isToday = day.isToday,
                                    isStart = if (pickerMode == CalendarPickerMode.RANGE) isStart else false,
                                    isEnd = if (pickerMode == CalendarPickerMode.RANGE) isEnd else false,
                                    isInRange = if (pickerMode == CalendarPickerMode.RANGE) isInRange else false,
                                    hasExistingPeriod = matchingLoggedPeriod != null,
                                    isExistingPeriodStart = matchingLoggedPeriod != null && stripTime(matchingLoggedPeriod.startDateMillis) == dayMillis,
                                    isInPredictedWindow = isInPredictedWindow,
                                    isPredictedStart = isPredictedStart,
                                    isMultiSelected = if (pickerMode == CalendarPickerMode.MULTI_DATE) isMultiSelected else false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (pickerMode == CalendarPickerMode.MULTI_DATE) {
                                            multiSelectedDates = if (multiSelectedDates.contains(dayMillis)) {
                                                multiSelectedDates - dayMillis
                                            } else {
                                                multiSelectedDates + dayMillis
                                            }
                                        } else {
                                            if (matchingLoggedPeriod != null && editingPeriodId == null && !isStart && !isEnd) {
                                                // Quick select to edit this existing period!
                                                startEditingPeriod(matchingLoggedPeriod)
                                            } else {
                                                if (activeTarget == SelectionTarget.START_DATE) {
                                                    selectedStartMillis = dayMillis
                                                    // If new start is after current end, push end forward
                                                    if (selectedStartMillis > selectedEndMillis) {
                                                        selectedEndMillis = selectedStartMillis + ((defaultPeriodLength - 1).coerceAtLeast(0) * 24L * 60 * 60 * 1000)
                                                    }
                                                    activeTarget = SelectionTarget.END_DATE
                                                } else {
                                                    if (dayMillis >= selectedStartMillis) {
                                                        selectedEndMillis = dayMillis
                                                    } else {
                                                        // If tapped before start, reassign as new start
                                                        selectedStartMillis = dayMillis
                                                    }
                                                    activeTarget = SelectionTarget.START_DATE
                                                }
                                            }
                                        }
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

            if (pickerMode == CalendarPickerMode.MULTI_DATE) {
                val detectedClusters = remember(multiSelectedDates) {
                    clusterConsecutiveDates(multiSelectedDates)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Multi-date past algorithm information banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFDF2F8),
                    border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔮", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Algorithm Training: Past Periods",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF9F1239)
                                )
                            }
                            if (multiSelectedDates.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFFE11D48)
                                ) {
                                    Text(
                                        text = "${multiSelectedDates.size} Days Selected",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (multiSelectedDates.isEmpty()) {
                            Text(
                                text = "Tap any dates across past months (jump using the quick month chips or arrows above) if you remember having your period. The algorithm uses your past cycles to calibrate cycle length, variance, and forecast future fertile & period dates with precision!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF881337)
                            )
                        } else {
                            Text(
                                text = "Detected ${detectedClusters.size} distinct past period interval(s) across selected dates. Tap below to feed these historical cycles into the prediction algorithm:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF881337)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Chips listing the detected clusters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detectedClusters.forEachIndexed { idx, cluster ->
                                    val startStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(cluster.startDateMillis)
                                    val endStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(cluster.endDateMillis)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🩸 #${idx + 1}: $startStr – $endStr (${cluster.durationDays}d)",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = PhasesRose
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Multi-Date Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (multiSelectedDates.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiSelectedDates = emptySet()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("clear_multi_selected_dates_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (detectedClusters.isNotEmpty()) {
                                detectedClusters.forEach { cluster ->
                                    onSaveNewPeriod(
                                        cluster.startDateMillis,
                                        cluster.endDateMillis,
                                        "Past period logged for algorithm",
                                        cluster.durationDays
                                    )
                                }
                                SoundHelper.playCuteChime(context)
                                particleController.spawn(
                                    centerX = 500f,
                                    centerY = 600f,
                                    emojis = listOf("🌸", "🩸", "✨", "🌷", "💖", "🔮"),
                                    count = 20
                                )
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Saved ${detectedClusters.size} past periods! Algorithm recalibrated ✨",
                                    icon = "🌸"
                                )
                                multiSelectedDates = emptySet()
                            }
                        },
                        enabled = detectedClusters.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhasesRose),
                        modifier = Modifier
                            .weight(if (multiSelectedDates.isNotEmpty()) 2f else 1f)
                            .height(48.dp)
                            .testTag("save_multi_periods_algorithm_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (detectedClusters.isEmpty()) "Select Dates on Calendar" else "Add ${detectedClusters.size} Past Period(s) to Algorithm 🌸",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Date Range Selection Controls Bar
                DateRangeSelectorsRow(
                    startMillis = selectedStartMillis,
                    endMillis = selectedEndMillis,
                    activeTarget = activeTarget,
                    durationDays = durationDays,
                    onSelectTarget = { target -> activeTarget = target },
                    onNudgeStart = { deltaDays ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newStart = selectedStartMillis + (deltaDays * 24L * 60 * 60 * 1000)
                        if (newStart <= selectedEndMillis) {
                            selectedStartMillis = newStart
                        }
                    },
                    onNudgeEnd = { deltaDays ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newEnd = selectedEndMillis + (deltaDays * 24L * 60 * 60 * 1000)
                        if (newEnd >= selectedStartMillis) {
                            selectedEndMillis = newEnd
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Period Notes
                OutlinedTextField(
                    value = periodNote,
                    onValueChange = { periodNote = it },
                    placeholder = { Text("Notes (e.g. travel, spotting, flow details)...") },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("period_calendar_picker_note_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons (Save New or Update Existing)
                val currentEditingId = editingPeriodId
                if (currentEditingId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDeletePeriod(currentEditingId)
                                cancelEditing()
                                particleController.showToast(coroutineScope, "Period deleted 🗑️", "🌸")
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("delete_period_in_calendar_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onUpdatePeriod(currentEditingId, selectedStartMillis, selectedEndMillis, periodNote)
                                cancelEditing()
                                particleController.spawn(
                                    centerX = 500f,
                                    centerY = 500f,
                                    emojis = listOf("🌸", "🩸", "✨", "💖"),
                                    count = 16
                                )
                                particleController.showToast(coroutineScope, "Period dates updated! 🌸", "🩸")
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PhasesRose),
                            modifier = Modifier
                                .weight(2f)
                                .height(48.dp)
                                .testTag("update_period_in_calendar_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Period ($durationDays Days) ✨", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    CuteAddButton(
                        text = "Save Period ($durationDays Days)",
                        icon = "🩸",
                        sparkleEmoji = "🌸",
                        onClick = {
                            onSaveNewPeriod(selectedStartMillis, selectedEndMillis, periodNote, durationDays)
                            particleController.spawn(
                                centerX = 500f,
                                centerY = 600f,
                                emojis = listOf("🌸", "🩸", "✨", "🌷", "💖"),
                                count = 18
                            )
                            particleController.showToast(
                                coroutineScope = coroutineScope,
                                message = "Period saved ($durationDays days)! Algorithm recalibrated ✨",
                                icon = "🌸"
                            )
                            periodNote = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "save_period_from_calendar_picker_btn"
                    )
                }
            }

            // Quick list of existing periods with "Edit in Calendar" buttons
            if (existingPeriods.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Tap any period below to edit in calendar:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    existingPeriods.take(6).forEach { p ->
                        val isBeingEdited = editingPeriodId == p.id
                        val startStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(p.startDateMillis)
                        val endStr = if (p.endDateMillis != null) SimpleDateFormat("MMM d", Locale.getDefault()).format(p.endDateMillis) else "Active"
                        val dur = p.durationDays ?: if (p.endDateMillis != null) {
                            val days = kotlin.math.round((p.endDateMillis - p.startDateMillis).toDouble() / (24L * 60 * 60 * 1000)).toInt() + 1
                            days.coerceIn(1, 15)
                        } else null

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBeingEdited) PhasesRose.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isBeingEdited) PhasesRose else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isBeingEdited) {
                                        cancelEditing()
                                    } else {
                                        startEditingPeriod(p)
                                    }
                                }
                                .testTag("quick_edit_period_pill_${p.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🩸 $startStr – $endStr ${if (dur != null) "(${dur}d)" else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isBeingEdited) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isBeingEdited) PhasesRose else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isBeingEdited) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription = "Edit in calendar",
                                    tint = if (isBeingEdited) PhasesRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRangeSelectorsRow(
    startMillis: Long,
    endMillis: Long,
    activeTarget: SelectionTarget,
    durationDays: Int,
    onSelectTarget: (SelectionTarget) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit
) {
    val startStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(startMillis)
    val endStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(endMillis)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Header: Title and Duration Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🩸", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SELECTED FLOW SPAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = PhasesRose.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "🌸 $durationDays Days Flow",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PhasesRose,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two distinct, spacious date cards: Start Date & End Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Start Date Card
                SpaciousDateCard(
                    title = "START DATE",
                    badge = "Start",
                    dateStr = startStr,
                    isActive = activeTarget == SelectionTarget.START_DATE,
                    accentColor = Color(0xFFE11D48),
                    onSelect = { onSelectTarget(SelectionTarget.START_DATE) },
                    onMinus = { onNudgeStart(-1) },
                    onPlus = { onNudgeStart(1) },
                    modifier = Modifier.weight(1f)
                )

                // End Date Card
                SpaciousDateCard(
                    title = "END DATE",
                    badge = "End",
                    dateStr = endStr,
                    isActive = activeTarget == SelectionTarget.END_DATE,
                    accentColor = PhasesRose,
                    onSelect = { onSelectTarget(SelectionTarget.END_DATE) },
                    onMinus = { onNudgeEnd(-1) },
                    onPlus = { onNudgeEnd(1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpaciousDateCard(
    title: String,
    badge: String,
    dateStr: String,
    isActive: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) accentColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shadowElevation = if (isActive) 2.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 9.sp
                    ),
                    color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = accentColor
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stepper controls centered and cleanly proportioned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onMinus() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "-1 day",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "±1d",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onPlus() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+1 day",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPickerCell(
    dayNumber: Int,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isStart: Boolean,
    isEnd: Boolean,
    isInRange: Boolean,
    hasExistingPeriod: Boolean,
    isExistingPeriodStart: Boolean,
    isInPredictedWindow: Boolean = false,
    isPredictedStart: Boolean = false,
    isMultiSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isMultiSelected -> Color(0xFFE11D48) // Crimson
        isStart -> Color(0xFFE11D48)
        isEnd -> PhasesRose
        isInRange -> PhasesRose.copy(alpha = 0.22f)
        hasExistingPeriod -> Color(0xFFFEE2E2)
        isPredictedStart -> Color(0xFF9333EA).copy(alpha = 0.22f)
        isInPredictedWindow -> Color(0xFFF3E8FF).copy(alpha = 0.85f)
        else -> Color.Transparent
    }

    val textColor = when {
        isMultiSelected || isStart || isEnd -> Color.White
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isInRange -> Color(0xFF9F1239)
        hasExistingPeriod -> Color(0xFF9F1239)
        isPredictedStart -> Color(0xFF7E22CE)
        isInPredictedWindow -> Color(0xFF6B21A8)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val shape = when {
        isMultiSelected -> CircleShape
        isStart && isEnd -> CircleShape
        isStart -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp)
        isEnd -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp)
        isInRange -> RoundedCornerShape(4.dp)
        else -> CircleShape
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (isMultiSelected) {
                    Modifier.border(1.5.dp, Color.White, CircleShape)
                } else if (isToday && !isStart && !isEnd) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else if (isPredictedStart && !isStart && !isEnd && !isInRange && !hasExistingPeriod) {
                    Modifier.border(1.5.dp, Color(0xFF9333EA), CircleShape)
                } else if (isInPredictedWindow && !isStart && !isEnd && !isInRange && !hasExistingPeriod) {
                    Modifier.border(1.dp, Color(0xFFC084FC).copy(alpha = 0.65f), CircleShape)
                } else Modifier
            )
            .clickable { onClick() }
            .testTag("calendar_picker_cell_$dayNumber"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isMultiSelected || isStart || isEnd || isToday || isPredictedStart) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = textColor
            )

            // Distinct non-overlapping indicators (only when not start/end to avoid clutter!)
            if (!isStart && !isEnd && !isMultiSelected) {
                if (hasExistingPeriod || isPredictedStart || isInPredictedWindow) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasExistingPeriod -> Color(0xFFE11D48)
                                    isPredictedStart -> Color(0xFF9333EA)
                                    else -> Color(0xFFC084FC)
                                }
                            )
                    )
                }
            } else if (isMultiSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private data class PickerDay(
    val dateMillis: Long,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

private fun generateMonthGrid(year: Int, month: Int, todayMillis: Long): List<PickerDay> {
    val days = mutableListOf<PickerDay>()

    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Days in current month
    val daysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // First day of week (Sunday = 1, Saturday = 7)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday

    // Previous month filler
    val prevMonthCal = (cal.clone() as Calendar).apply {
        add(Calendar.MONTH, -1)
    }
    val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevMonthFillerCount = firstDayOfWeek - Calendar.SUNDAY

    for (i in prevMonthFillerCount - 1 downTo 0) {
        val dayNum = daysInPrevMonth - i
        val dayCal = (prevMonthCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, dayNum)
        }
        val millis = dayCal.timeInMillis
        days.add(
            PickerDay(
                dateMillis = millis,
                dayOfMonth = dayNum,
                isCurrentMonth = false,
                isToday = stripTime(millis) == todayMillis
            )
        )
    }

    // Current month days
    for (d in 1..daysInCurrentMonth) {
        val dayCal = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, d)
        }
        val millis = dayCal.timeInMillis
        days.add(
            PickerDay(
                dateMillis = millis,
                dayOfMonth = d,
                isCurrentMonth = true,
                isToday = stripTime(millis) == todayMillis
            )
        )
    }

    // Next month filler up to multiple of 7 (at least 35 or 42 cells)
    val totalCells = if (days.size > 35) 42 else 35
    val nextMonthCal = (cal.clone() as Calendar).apply {
        add(Calendar.MONTH, 1)
    }
    var nextDayNum = 1
    while (days.size < totalCells) {
        val dayCal = (nextMonthCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, nextDayNum)
        }
        val millis = dayCal.timeInMillis
        days.add(
            PickerDay(
                dateMillis = millis,
                dayOfMonth = nextDayNum,
                isCurrentMonth = false,
                isToday = stripTime(millis) == todayMillis
            )
        )
        nextDayNum++
    }

    return days
}

private fun stripTime(millis: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}
