package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyCycleLog
import com.example.data.MedicineReminder
import com.example.data.PeriodLog
import com.example.data.UserProfile
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.ui.components.AestheticHaptics
import com.example.ui.components.CuteAddButton
import com.example.ui.components.CutePulseButton
import com.example.ui.components.LocalParticleController
import com.example.ui.components.MedicineManagerDialog
import com.example.ui.components.ThreeDPhaseCard
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import com.example.ui.components.formatDayOfWeekDate
import com.example.ui.components.formatFriendlyDate
import com.example.ui.components.formatShortDate
import com.example.ui.components.formatTime
import com.example.ui.theme.EditorialDisplayFont
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesWaterBlue
import com.example.viewmodel.CareTab
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CycleScreen(
    userProfile: UserProfile?,
    cycleStatus: CycleStatus,
    recentPeriods: List<PeriodLog>,
    medicines: List<MedicineReminder> = emptyList(),
    todayCycleLog: DailyCycleLog? = null,
    todayWaterCount: Int = 0,
    waterGoal: Int = 8,
    onLogPeriodToday: () -> Unit,
    onMarkMedicineTaken: (Long) -> Unit = {},
    onAddMedicine: (String, String, Int, Int, String) -> Unit = { _, _, _, _, _ -> },
    onUpdateMedicine: (MedicineReminder) -> Unit = {},
    onDeleteMedicine: (Long) -> Unit = {},
    onToggleMedicine: (Long, Boolean) -> Unit = { _, _ -> },
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToTrends: () -> Unit = {},
    onNavigateToPeriods: () -> Unit = {},
    onNavigateToCare: (CareTab) -> Unit = {},
    onEndPeriodToday: () -> Unit = {},
    onCancelActivePeriod: () -> Unit = {},
    onUpdatePeriod: (periodId: Long, startDateMillis: Long, endDateMillis: Long?, notes: String) -> Unit = { _, _, _, _ -> },
    onSaveDailyCycleLog: (DailyCycleLog) -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userName = userProfile?.name?.ifBlank { "Krishi" } ?: "Krishi"
    val greeting = getGreeting(userName)
    val todayDateFormatted = formatDayOfWeekDate(System.currentTimeMillis())
    val todayDateString = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val haptic = LocalHapticFeedback.current
    var showMedicineDialog by remember { mutableStateOf(false) }
    var showCancelMistakeDialog by remember { mutableStateOf(false) }

    fun showEditActiveStartDatePicker() {
        val currentStart = cycleStatus.lastPeriodStartDateMillis ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = currentStart }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 0, 0, 0)
                val newStart = c.timeInMillis
                val activeId = cycleStatus.activePeriodId ?: recentPeriods.firstOrNull()?.id
                if (activeId != null) {
                    val activeNote = recentPeriods.find { it.id == activeId }?.notes ?: ""
                    onUpdatePeriod(activeId, newStart, null, activeNote)
                    particleController.showToast(
                        coroutineScope = coroutineScope,
                        message = "Period start date updated",
                        icon = "📅"
                    )
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showCancelMistakeDialog) {
        AlertDialog(
            onDismissRequest = { showCancelMistakeDialog = false },
            title = { Text("Cancel Active Period?", fontWeight = FontWeight.Bold) },
            text = { Text("Did you tap 'Period Started' by mistake? This will cancel the active flow and remove the entry.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelMistakeDialog = false
                        onCancelActivePeriod()
                        particleController.showToast(
                            coroutineScope = coroutineScope,
                            message = "Active period removed",
                            icon = "🌸"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelMistakeDialog = false }) {
                    Text("Keep Active")
                }
            }
        )
    }

    if (showMedicineDialog) {
        MedicineManagerDialog(
            reminders = medicines,
            onAddReminder = onAddMedicine,
            onUpdateReminder = onUpdateMedicine,
            onDeleteReminder = onDeleteMedicine,
            onToggleReminder = onToggleMedicine,
            onDismiss = { showMedicineDialog = false }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Editorial Greeting & Privacy Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = EditorialDisplayFont
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = todayDateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesRose.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.28f)),
                            modifier = Modifier.testTag("cycle_header_status_chip")
                        ) {
                            Text(
                                text = "${cycleStatus.currentPhase.emoji} Day ${cycleStatus.dayInCycle} ✨",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesSage.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PhasesSage.copy(alpha = 0.28f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Private on-device data",
                                    tint = PhasesSage,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Private 🌿",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    ),
                                    color = PhasesSage,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. Centerpiece Lunar Phase Card (strictly bounded, calm haptics)
            item {
                ThreeDPhaseCard(
                    status = cycleStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Period Logging Action Bar (Zero Overlap, Silent Haptic)
            item {
                val isPeriodActive = cycleStatus.isPeriodCurrentlyActive
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isPeriodActive) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = PhasesRose.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🩸", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Flow · Day ${cycleStatus.activePeriodDays}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PhasesRose
                                    )
                                    Text(
                                        text = "Started ${formatFriendlyDate(cycleStatus.lastPeriodStartDateMillis ?: System.currentTimeMillis())}. Log out when your flow ends.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        CutePulseButton(
                            onClick = {
                                AestheticHaptics.warmConfirm(context, haptic)
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Flow complete — moving into gentle renewal 🌸",
                                    icon = "🌸"
                                )
                                onEndPeriodToday()
                            },
                            text = "End Active Period ✨",
                            icon = "🌸",
                            badgeText = "Day ${cycleStatus.activePeriodDays}",
                            gradientColors = listOf(PhasesSage, Color(0xFF428460), PhasesPlum),
                            glowColor = PhasesSage.copy(alpha = 0.35f),
                            testTag = "log_out_period_today_button"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showEditActiveStartDatePicker()
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("edit_active_period_start_date_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Edit Start 🗓️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCancelMistakeDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("cancel_active_period_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Cancel Flow", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLogPeriodToday()
                            },
                            modifier = Modifier.testTag("log_period_today_button")
                        ) {
                            Text(
                                text = "Re-log today as Day 1 🌙",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        CutePulseButton(
                            onClick = {
                                AestheticHaptics.warmConfirm(context, haptic)
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Period logged — be extra gentle with yourself today 🩷",
                                    icon = "🩸"
                                )
                                onLogPeriodToday()
                            },
                            text = "Log Period Started Today",
                            icon = "🩸",
                            badgeText = "Day 1",
                            gradientColors = listOf(PhasesRose, Color(0xFFC94D82), PhasesPlum),
                            glowColor = PhasesRose.copy(alpha = 0.35f),
                            testTag = "log_period_today_button"
                        )

                        OutlinedButton(
                            onClick = {
                                AestheticHaptics.softTick(context, haptic)
                                onNavigateToPeriods()
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("quick_log_past_period_button")
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = PhasesRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Log Past Period Dates 🗓️✨",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PhasesRose
                            )
                        }
                    }
                }
            }

            // 4. Clean 2x2 Bento Snapshot Grid (Equal-Height, Zero Overlap)
            item {
                val activeMedsCount = medicines.count { it.isEnabled }
                val takenMedsCount = medicines.count { it.lastTakenDate == todayDateString }
                val nextDateStr = formatShortDate(cycleStatus.expectedNextDateMillis)
                val countdownLabel = when {
                    cycleStatus.isPeriodCurrentlyActive -> "Active · Day ${cycleStatus.activePeriodDays}"
                    cycleStatus.isLate -> "${cycleStatus.daysLate}d late"
                    cycleStatus.daysUntilNextPeriod <= 0 -> "Due today"
                    else -> "In ${cycleStatus.daysUntilNextPeriod} days"
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CycleBentoTile(
                            emoji = "🔮",
                            title = "Next Period",
                            headline = nextDateStr,
                            detail = countdownLabel,
                            footerChip = if (cycleStatus.currentPhase == CyclePhase.FERTILE || cycleStatus.currentPhase == CyclePhase.OVULATION) {
                                "Fertile Window ✨"
                            } else {
                                "Calendar 🌸 →"
                            },
                            accentColor = PhasesRose,
                            onClick = onNavigateToPeriods,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            testTag = "home_prediction_bento_card"
                        )

                        CycleBentoTile(
                            emoji = "📊",
                            title = "Cycle Insights",
                            headline = "${cycleStatus.averageCycleLength.toInt()}d Avg",
                            detail = "±${String.format(Locale.US, "%.1f", cycleStatus.sigmaVarianceDays)}d var · ${userProfile?.periodLength ?: 5}d flow",
                            footerChip = "6-Mo Trends 📈 →",
                            accentColor = PhasesPlum,
                            onClick = onNavigateToTrends,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            testTag = "home_trends_preview_card"
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CycleBentoTile(
                            emoji = "🫧",
                            title = "Self-Care",
                            headline = "$todayWaterCount/$waterGoal cups",
                            detail = if (activeMedsCount > 0) "Meds $takenMedsCount/$activeMedsCount taken 💊" else "Hydration & daily rituals 💧",
                            footerChip = "Open Care 🫧 →",
                            accentColor = PhasesWaterBlue,
                            onClick = { onNavigateToCare(CareTab.WATER) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            testTag = "home_care_bento_card"
                        )

                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, PhasesLavender.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .cute3DCard(cornerRadius = 22.dp)
                                .cuteBouncyClick(onClick = onNavigateToCalendar)
                                .testTag("home_calendar_preview_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(PhasesLavender.copy(alpha = 0.12f), Color.Transparent)
                                        )
                                    )
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📅", fontSize = 18.sp)
                                    Text(
                                        text = "Phase Key",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PhasesPlum,
                                        maxLines = 1
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PhaseMiniDot(name = "Period", color = Color(0xFFE11D48), modifier = Modifier.weight(1f))
                                        PhaseMiniDot(name = "Rise", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PhaseMiniDot(name = "Ovulate", color = Color(0xFF8B5CF6), modifier = Modifier.weight(1f))
                                        PhaseMiniDot(name = "Luteal", color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Daily Medicines & Supplements (Fixed Header & Empty Row Weights)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cute3DCard(cornerRadius = 22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "💊", fontSize = 19.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Medicines ✨",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Gentle alerts with cute chimes 🔔",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showMedicineDialog = true
                                },
                                modifier = Modifier.testTag("manage_medicines_button")
                            ) {
                                Text("+ Manage", fontWeight = FontWeight.Bold, color = PhasesPlum, maxLines = 1)
                            }
                        }

                        if (medicines.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No vitamin or medicine reminders scheduled yet 🌸",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CuteAddButton(
                                    text = "Set Reminder",
                                    icon = "💊",
                                    sparkleEmoji = "✨",
                                    onClick = { showMedicineDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "home_empty_set_med_reminder_button"
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                medicines.forEach { med ->
                                    val isTakenToday = med.lastTakenDate == todayDateString

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isTakenToday) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isTakenToday) PhasesSage.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(med.categoryEmoji, fontSize = 18.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = med.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${formatTime(med.hour, med.minute)} · ${med.dosage}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            if (isTakenToday) {
                                                Surface(
                                                    shape = RoundedCornerShape(100.dp),
                                                    color = PhasesSage.copy(alpha = 0.16f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = PhasesSage,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Taken 🌸",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = PhasesSage
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        AestheticHaptics.warmConfirm(context, haptic)
                                                        onMarkMedicineTaken(med.id)
                                                        particleController.showToast(
                                                            coroutineScope = coroutineScope,
                                                            message = "${med.name} taken — proud of you! 💊",
                                                            icon = "💊"
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                                    modifier = Modifier.testTag("mark_taken_med_${med.id}")
                                                ) {
                                                    Text("Take ✨", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. Recent Periods Preview
            if (recentPeriods.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent Periods 🌙",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        recentPeriods.take(2).forEach { period ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.22f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🩸", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatFriendlyDate(period.startDateMillis),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = PhasesRose.copy(alpha = 0.10f)
                                    ) {
                                        Text(
                                            text = period.notes.ifBlank { "Logged 🌸" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .widthIn(max = 120.dp)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun getGreeting(name: String): String {
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning, $name ☀️🌸"
        hour < 17 -> "Good afternoon, $name 🌷✨"
        else -> "Good evening, $name 🌙💫"
    }
}

@Composable
private fun CycleBentoTile(
    emoji: String,
    title: String,
    headline: String,
    detail: String,
    footerChip: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .cute3DCard(cornerRadius = 22.dp)
            .cuteBouncyClick(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.11f), Color.Transparent)
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = footerChip,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PhaseMiniDot(
    name: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
