package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyCycleLog
import com.example.data.PeriodLog
import com.example.data.UserProfile
import com.example.model.CycleHistoryItem
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.components.CutePulseButton
import com.example.ui.components.CyclePhaseCalendar
import com.example.ui.components.CycleTrendsDashboard
import com.example.ui.components.LocalParticleController
import com.example.ui.components.PeriodDateRangeCalendarPicker
import com.example.ui.components.formatFriendlyDate
import com.example.ui.components.formatShortDate
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import java.util.Calendar
import java.util.Locale

enum class PeriodsViewTab(val label: String, val emoji: String) {
    CALENDAR("Calendar", "📅"),
    TRENDS("Trends", "📊"),
    HISTORY("Logs & Stats", "📋")
}

@Composable
fun PeriodsScreen(
    userProfile: UserProfile?,
    cycleStatus: CycleStatus,
    historyItems: List<CycleHistoryItem>,
    periodLogs: List<PeriodLog> = emptyList(),
    dailyCycleLogs: List<DailyCycleLog> = emptyList(),
    initialTab: PeriodsViewTab = PeriodsViewTab.CALENDAR,
    onAddPeriodDate: (startDateMillis: Long, notes: String, durationDays: Int?, endDateMillis: Long?) -> Unit,
    onUpdatePeriod: (periodId: Long, startDateMillis: Long, endDateMillis: Long?, notes: String) -> Unit = { _, _, _, _ -> },
    onEndPeriodToday: () -> Unit = {},
    onEndPeriodOnDate: (Long, Long) -> Unit = { _, _ -> },
    onDeletePeriod: (Long) -> Unit,
    onSaveDailyCycleLog: (DailyCycleLog) -> Unit = {},
    onDeleteDailyCycleLog: (Long) -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val haptic = LocalHapticFeedback.current
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedEndDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var includeEndDateInNewLog by remember { mutableStateOf(false) }
    var periodNote by remember { mutableStateOf("") }
    var periodToDelete by remember { mutableStateOf<Long?>(null) }
    var periodToEndId by remember { mutableStateOf<Long?>(null) }
    var editingPeriodItem by remember { mutableStateOf<CycleHistoryItem?>(null) }
    var showCancelActivePeriodDialog by remember { mutableStateOf(false) }
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    val effectivePeriodLogs = remember(periodLogs, historyItems) {
        if (periodLogs.isNotEmpty()) {
            periodLogs
        } else {
            historyItems.map {
                PeriodLog(
                    id = it.periodId,
                    startDateMillis = it.startDateMillis,
                    notes = it.notes,
                    durationDays = it.durationDays,
                    endDateMillis = it.endDateMillis
                )
            }
        }
    }

    val calendar = Calendar.getInstance()

    fun showEditActiveStartDatePicker() {
        val activeItem = historyItems.firstOrNull { it.isOngoing }
        val currentStart = cycleStatus.lastPeriodStartDateMillis ?: activeItem?.startDateMillis ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = currentStart }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 0, 0, 0)
                val newStart = c.timeInMillis
                val targetId = cycleStatus.activePeriodId ?: activeItem?.periodId
                if (targetId != null) {
                    val activeNote = activeItem?.notes ?: ""
                    onUpdatePeriod(targetId, newStart, null, activeNote)
                    particleController.showToast(
                        coroutineScope = coroutineScope,
                        message = "Period start date updated! 🌸",
                        icon = "📅"
                    )
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showStartDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 0, 0, 0)
                selectedDateMillis = c.timeInMillis
                if (selectedEndDateMillis < selectedDateMillis) {
                    selectedEndDateMillis = selectedDateMillis + (4L * 24 * 60 * 60 * 1000)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showEndDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedEndDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 23, 59, 59)
                selectedEndDateMillis = c.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showPeriodToEndDatePicker(specificPeriodId: Long? = null) {
        val targetId = specificPeriodId ?: periodToEndId ?: cycleStatus.activePeriodId ?: historyItems.firstOrNull { it.isOngoing }?.periodId
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 23, 59, 59)
                val chosenTime = c.timeInMillis
                if (targetId != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundHelper.playCuteChime(context)
                    onEndPeriodOnDate(targetId, chosenTime)
                    particleController.spawn(
                        centerX = 500f,
                        centerY = 600f,
                        emojis = listOf("🌸", "✨", "🌷", "💖", "🕊️"),
                        count = 16
                    )
                    particleController.showToast(
                        coroutineScope = coroutineScope,
                        message = "Period logged out! Flow complete 🌸",
                        icon = "🌸"
                    )
                }
                periodToEndId = null
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Period & Cycle Tracker",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Visual phase calendar with colored tracking dots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("edit_cycle_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // View Mode Switcher: Calendar vs Logs
        item {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    PeriodsViewTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeTab = tab
                                }
                                .testTag("tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = tab.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        when (activeTab) {
            PeriodsViewTab.CALENDAR -> {
                // Calendar Tab: Phase Calendar Visualization & Anomaly Analysis
                item {
                    CyclePhaseCalendar(
                        periods = effectivePeriodLogs,
                        defaultCycleLength = userProfile?.cycleLength ?: 28,
                        defaultPeriodLength = userProfile?.periodLength ?: 5,
                        onLogPeriodOnDate = { startDate, note, duration, end ->
                            onAddPeriodDate(startDate, note, duration, end)
                        },
                        onOpenMultiDatePicker = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeTab = PeriodsViewTab.HISTORY
                        }
                    )
                }

                // 6-Month Trends & Dashboard Shortcut Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeTab = PeriodsViewTab.TRENDS
                            }
                            .testTag("jump_to_trends_dashboard_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(PhasesRose.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📊", fontSize = 20.sp)
                                }
                                Column {
                                    Text(
                                        text = "6-Month Health Dashboard",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Cycle length variation & symptom clustering",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesRose.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "View ➔",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PhasesRose,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Cycle Statistics Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cycle Parameters",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("edit_cycle_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatItem(
                                        icon = "🗓️",
                                        label = "Cycle Length",
                                        value = "${userProfile?.cycleLength ?: 28}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatItem(
                                        icon = "🩸",
                                        label = "Period Length",
                                        value = "${userProfile?.periodLength ?: 5}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatItem(
                                        icon = "📊",
                                        label = "Learned Avg",
                                        value = String.format(Locale.US, "%.0fd", cycleStatus.averageCycleLength),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatItem(
                                        icon = "🎯",
                                        label = "Natural Variance",
                                        value = "±${String.format(Locale.US, "%.1f", cycleStatus.sigmaVarianceDays)}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PeriodsViewTab.TRENDS -> {
                // Trends & Health Dashboard: 6-Month Cycle Lengths, Period Durations & Predictive Analytics
                item {
                    CycleTrendsDashboard(
                        periodLogs = effectivePeriodLogs,
                        dailyCycleLogs = dailyCycleLogs,
                        userProfile = userProfile,
                        cycleStatus = cycleStatus,
                        onLogPastPeriod = {
                            activeTab = PeriodsViewTab.CALENDAR
                        }
                    )
                }
            }

            PeriodsViewTab.HISTORY -> {
                // History Tab: Flow Controls, Add Period, and Past Period Records
                val isOngoingFlow = cycleStatus.isPeriodCurrentlyActive || historyItems.any { it.isOngoing }
                if (isOngoingFlow) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = PhasesRose.copy(alpha = 0.12f)),
                            border = BorderStroke(1.5.dp, PhasesRose.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_period_banner")
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "🩸", fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Active Flow in Progress",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = PhasesRose
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = PhasesRose
                                            ) {
                                                Text(
                                                    text = "Day ${cycleStatus.activePeriodDays.coerceAtLeast(1)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Period started on ${formatFriendlyDate(cycleStatus.lastPeriodStartDateMillis ?: selectedDateMillis)}. Log out when bleeding ends to capture exact flow duration.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Primary Full-Width Action: Log Out Flow Today
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        SoundHelper.playCuteChime(context)
                                        onEndPeriodToday()
                                        particleController.spawn(
                                            centerX = 500f,
                                            centerY = 600f,
                                            emojis = listOf("🌸", "✨", "🌷", "💖", "🕊️"),
                                            count = 18
                                        )
                                        particleController.showToast(
                                            coroutineScope = coroutineScope,
                                            message = "Period logged out! Flow complete 🌸",
                                            icon = "🌸"
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("logout_period_today_button")
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Log Out Flow Today 🌸", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Secondary Actions Row (Non-overlapping, comfortable touch targets)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            periodToEndId = cycleStatus.activePeriodId ?: historyItems.firstOrNull { it.isOngoing }?.periodId
                                            showPeriodToEndDatePicker(periodToEndId)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("logout_period_date_button")
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick End Date", fontSize = 11.sp, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showEditActiveStartDatePicker()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("active_banner_edit_start_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit Start", fontSize = 11.sp, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showCancelActivePeriodDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("active_banner_cancel_mistake_button")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cancel", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Calendar-Based Selection UI Component: Pick Start & End Dates & Edit in Calendar
                item {
                    PeriodDateRangeCalendarPicker(
                        existingPeriods = effectivePeriodLogs,
                        defaultCycleLength = userProfile?.cycleLength ?: 28,
                        defaultPeriodLength = userProfile?.periodLength ?: 5,
                        onSaveNewPeriod = { startMillis, endMillis, note, duration ->
                            onAddPeriodDate(startMillis, note, duration, endMillis)
                        },
                        onUpdatePeriod = { periodId, startMillis, endMillis, note ->
                            onUpdatePeriod(periodId, startMillis, endMillis, note)
                        },
                        onDeletePeriod = { periodId ->
                            onDeletePeriod(periodId)
                        }
                    )
                }

                // Cycle Statistics Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cycle Parameters",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("edit_cycle_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatItem(
                                        icon = "🗓️",
                                        label = "Cycle Length",
                                        value = "${userProfile?.cycleLength ?: 28}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatItem(
                                        icon = "🩸",
                                        label = "Period Length",
                                        value = "${userProfile?.periodLength ?: 5}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatItem(
                                        icon = "📊",
                                        label = "Learned Avg",
                                        value = String.format(Locale.US, "%.0fd", cycleStatus.averageCycleLength),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatItem(
                                        icon = "🎯",
                                        label = "Natural Variance",
                                        value = "±${String.format(Locale.US, "%.1f", cycleStatus.sigmaVarianceDays)}d",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // History List Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Logged Periods (${historyItems.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (historyItems.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🌸", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No periods recorded yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add your past or current period above to start tracking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(historyItems, key = { it.periodId }) { item ->
                        PeriodHistoryRow(
                            item = item,
                            onLogOut = {
                                periodToEndId = item.periodId
                                showPeriodToEndDatePicker(item.periodId)
                            },
                            onEdit = {
                                editingPeriodItem = item
                            },
                            onDelete = { periodToDelete = item.periodId }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }

    // Cancel Active Period Started by Mistake Dialog
    if (showCancelActivePeriodDialog) {
        val activeTargetId = cycleStatus.activePeriodId ?: historyItems.firstOrNull { it.isOngoing }?.periodId
        AlertDialog(
            onDismissRequest = { showCancelActivePeriodDialog = false },
            title = { Text("Cancel Active Period?", fontWeight = FontWeight.Bold) },
            text = { Text("Did you start this period by mistake? This will cancel the ongoing flow and remove the active period entry.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelActivePeriodDialog = false
                        if (activeTargetId != null) {
                            onDeletePeriod(activeTargetId)
                        }
                        particleController.showToast(
                            coroutineScope = coroutineScope,
                            message = "Active period cancelled & removed 🌸",
                            icon = "🌸"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cancel Period")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelActivePeriodDialog = false }) {
                    Text("Keep Active")
                }
            }
        )
    }

    // Edit Period Dialog
    if (editingPeriodItem != null) {
        val itemToEdit = editingPeriodItem!!
        EditPeriodDialog(
            item = itemToEdit,
            onDismiss = { editingPeriodItem = null },
            onSave = { startMillis, endMillis, notes ->
                onUpdatePeriod(itemToEdit.periodId, startMillis, endMillis, notes)
                particleController.showToast(
                    coroutineScope = coroutineScope,
                    message = "Period dates updated! 🌸",
                    icon = "🌸"
                )
            },
            onDelete = { periodId ->
                onDeletePeriod(periodId)
                particleController.showToast(
                    coroutineScope = coroutineScope,
                    message = "Period entry removed",
                    icon = "🗑️"
                )
            }
        )
    }

    // Delete confirmation dialog
    if (periodToDelete != null) {
        AlertDialog(
            onDismissRequest = { periodToDelete = null },
            title = { Text("Delete Period Entry?") },
            text = { Text("Are you sure you want to remove this logged period? Your cycle averages will recalculate automatically.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        periodToDelete?.let { onDeletePeriod(it) }
                        periodToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { periodToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PeriodHistoryRow(
    item: CycleHistoryItem,
    onLogOut: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "🩸", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatFriendlyDate(item.startDateMillis),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.isOngoing) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesRose.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "● Active Flow",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PhasesRose,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else if (item.isMostRecent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Latest",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Bleeding duration summary
                    if (item.isOngoing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Flow is currently ongoing",
                                style = MaterialTheme.typography.bodySmall,
                                color = PhasesRose
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLogOut() }
                                    .testTag("logout_period_${item.periodId}")
                            ) {
                                Text(
                                    text = "Log Out 🌸",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else if (item.durationDays != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "${item.durationDays}d flow duration",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (item.durationDays >= 8) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "⚠️ Prolonged Flow",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (item.durationDays <= 2) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xFFF5F3FF),
                                    border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "⚡ Short Flow",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (item.cycleDaysToNext != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "${item.cycleDaysToNext} days cycle interval",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (item.notes.isNotBlank()) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_period_${item.periodId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Period Dates",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_period_${item.periodId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Period",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditPeriodDialog(
    item: CycleHistoryItem,
    onDismiss: () -> Unit,
    onSave: (startDateMillis: Long, endDateMillis: Long?, notes: String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    var editStartDateMillis by remember { mutableStateOf(item.startDateMillis) }
    var editEndDateMillis by remember { mutableStateOf(item.endDateMillis ?: (item.startDateMillis + (4L * 24 * 60 * 60 * 1000))) }
    var isOngoing by remember { mutableStateOf(item.isOngoing || item.endDateMillis == null) }
    var editNotes by remember { mutableStateOf(item.notes) }

    fun pickStartDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = editStartDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 0, 0, 0)
                editStartDateMillis = c.timeInMillis
                if (!isOngoing && editEndDateMillis < editStartDateMillis) {
                    editEndDateMillis = editStartDateMillis + (4L * 24 * 60 * 60 * 1000)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickEndDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = editEndDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 23, 59, 59)
                editEndDateMillis = c.timeInMillis
                isOngoing = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Period Dates & Flow", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Correct past or present dates, bleeding duration, or notes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Start Date Row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickStartDate() }
                        .testTag("edit_period_dialog_start_date_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Start Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFriendlyDate(editStartDateMillis),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                // Ongoing toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isOngoing = !isOngoing }
                        .padding(vertical = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOngoing) PhasesRose else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isOngoing) PhasesRose else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(20.dp)
                    ) {
                        if (isOngoing) {
                            Icon(Icons.Default.Done, contentDescription = null, tint = Color.White, modifier = Modifier.padding(2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Period is currently active (no end date yet)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isOngoing) PhasesRose else MaterialTheme.colorScheme.onSurface
                    )
                }

                // End Date Row (if not ongoing)
                if (!isOngoing) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickEndDate() }
                            .testTag("edit_period_dialog_end_date_picker")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "End Date (Flow Complete)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatFriendlyDate(editEndDateMillis),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Notes & Symptoms") },
                    placeholder = { Text("e.g. Mild cramps, light flow...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Delete period button in red
                TextButton(
                    onClick = {
                        onDelete(item.periodId)
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete this entry", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalEnd = if (isOngoing) null else editEndDateMillis
                    onSave(editStartDateMillis, finalEnd, editNotes)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PhasesRose)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: String = "📊",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
