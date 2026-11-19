package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import android.app.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedicineReminder
import com.example.data.DailyCycleLog
import com.example.data.PeriodLog
import com.example.data.UserProfile
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.components.CuteAddButton
import com.example.ui.components.CutePulseButton
import com.example.ui.components.FloatingPetalsBackground
import com.example.ui.components.LocalParticleController
import com.example.ui.components.MedicineManagerDialog
import com.example.ui.components.PeriodPredictionCard3D
import com.example.ui.components.ThreeDPhaseCard
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import com.example.ui.components.formatDayOfWeekDate
import com.example.ui.components.formatFriendlyDate
import com.example.ui.components.formatShortDate
import com.example.ui.components.formatTime
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPeach
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesWaterBlue
import com.example.viewmodel.CareTab
import kotlinx.coroutines.launch
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userName = userProfile?.name?.ifBlank { "Krishi" } ?: "Krishi"
    val greeting = getGreeting(userName)
    val todayDateFormatted = formatDayOfWeekDate(System.currentTimeMillis())
    val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val haptic = LocalHapticFeedback.current
    var parallaxTilt by remember { mutableStateOf(Offset.Zero) }
    var logButtonCenter by remember { mutableStateOf(Offset.Zero) }
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

    if (showCancelMistakeDialog) {
        AlertDialog(
            onDismissRequest = { showCancelMistakeDialog = false },
            title = { Text("Cancel Active Period?", fontWeight = FontWeight.Bold) },
            text = { Text("Did you click 'Period Started' by mistake? This will cancel the active flow and remove the active period entry.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelMistakeDialog = false
                        onCancelActivePeriod()
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
        // Ambient 3D Floating Petals Background with parallax reaction
        FloatingPetalsBackground(parallaxOffset = parallaxTilt)

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Centered Symmetric Greeting & Day Status Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = todayDateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("cycle_header_status_chip")
                        ) {
                            Text(
                                text = "${cycleStatus.currentPhase.emoji} Day ${cycleStatus.dayInCycle}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Centered 3D Moon Hero Card
            item {
                ThreeDPhaseCard(
                    status = cycleStatus,
                    onTiltChange = { tilt -> parallaxTilt = tilt },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dynamic Period Start (Log In) & Period End (Log Out) Controls
            item {
                val isPeriodActive = cycleStatus.isPeriodCurrentlyActive
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isPeriodActive) {
                        // Ongoing Active Flow Banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PhasesRose.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🩸", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Period Flow · Day ${cycleStatus.activePeriodDays}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PhasesRose
                                    )
                                    Text(
                                        text = "Period started on ${formatFriendlyDate(cycleStatus.lastPeriodStartDateMillis ?: System.currentTimeMillis())}. Tap to log out when done, or edit dates if started by mistake.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Button transforms to "Log Out Period (Flow Ended)"
                        CutePulseButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundHelper.playCuteChime(context)
                                particleController.spawn(
                                    centerX = if (logButtonCenter != Offset.Zero) logButtonCenter.x else 500f,
                                    centerY = if (logButtonCenter != Offset.Zero) logButtonCenter.y else 700f,
                                    emojis = listOf("🌸", "✨", "🌷", "💖", "🌿", "🌺", "🕊️"),
                                    count = 20
                                )
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Period logged out! Flow complete. Moving into renewal 🌸",
                                    icon = "🌸"
                                )
                                onEndPeriodToday()
                            },
                            text = "Log Out Period (Flow Ended)",
                            icon = "🌸",
                            badgeText = "Day ${cycleStatus.activePeriodDays}",
                            gradientColors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF059669),
                                PhasesPlum
                            ),
                            glowColor = Color(0xFF10B981).copy(alpha = 0.45f),
                            testTag = "log_out_period_today_button",
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    logButtonCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
                                }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls to edit start date or cancel if started by mistake
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showEditActiveStartDatePicker()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("edit_active_period_start_date_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Start Date", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCancelMistakeDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("cancel_active_period_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Started by Mistake", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLogPeriodToday()
                            },
                            modifier = Modifier.testTag("log_period_today_button")
                        ) {
                            Text(
                                text = "Re-log today as Day 1 🩸",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        CutePulseButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundHelper.playCuteChime(context)
                                particleController.spawn(
                                    centerX = if (logButtonCenter != Offset.Zero) logButtonCenter.x else 500f,
                                    centerY = if (logButtonCenter != Offset.Zero) logButtonCenter.y else 700f,
                                    emojis = listOf("🌸", "🩸", "💖", "🌷", "✨", "🌺", "🎀"),
                                    count = 18
                                )
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Period start logged. Be extra gentle with yourself 🌸",
                                    icon = "🩸"
                                )
                                onLogPeriodToday()
                            },
                            text = "Log Period Started Today",
                            icon = "🩸",
                            badgeText = "Day 1",
                            gradientColors = listOf(
                                PhasesRose,
                                Color(0xFFE25A94),
                                PhasesPlum
                            ),
                            glowColor = PhasesRose.copy(alpha = 0.5f),
                            testTag = "log_period_today_button",
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    logButtonCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
                                }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onNavigateToPeriods() },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
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
                                text = "Log Past Period Dates 🗓️",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PhasesRose
                            )
                        }
                    }
                }
            }

            // Symmetric 2x2 Equal-Height Bento Grid
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
                    // Row 1: Prediction + 6-Month Trends (Equal Height)
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
                                "✨ Fertile Now"
                            } else {
                                "View Calendar ➔"
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
                            title = "6-Mo Analytics",
                            headline = "${cycleStatus.averageCycleLength.toInt()}d Cycle",
                            detail = "±${String.format(Locale.US, "%.1f", cycleStatus.sigmaVarianceDays)}d var · ${userProfile?.periodLength ?: 5}d flow",
                            footerChip = "View Trends ➔",
                            accentColor = PhasesPlum,
                            onClick = onNavigateToTrends,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            testTag = "home_trends_preview_card"
                        )
                    }

                    // Row 2: Today's Care Snapshot + Cycle Phase Calendar (Equal Height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CycleBentoTile(
                            emoji = "🌿",
                            title = "Today's Care",
                            headline = "💧 $todayWaterCount/$waterGoal · 💊 $takenMedsCount/$activeMedsCount",
                            detail = "Hydration, Meds & Checklist",
                            footerChip = "Open Care ➔",
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
                            border = BorderStroke(1.2.dp, PhasesLavender.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .cute3DCard(maxTilt = 3f, cornerRadius = 22.dp)
                                .cuteBouncyClick(onClick = onNavigateToCalendar)
                                .testTag("home_calendar_preview_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(PhasesLavender.copy(alpha = 0.16f), Color.Transparent)
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
                                    Text("📅", fontSize = 20.sp)
                                    Text(
                                        text = "Calendar ➔",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PhasesPlum
                                    )
                                }

                                Text(
                                    text = "Phase Dots",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        PhaseMiniDot(name = "Period", color = Color(0xFFE11D48))
                                        PhaseMiniDot(name = "Follicular", color = Color(0xFF10B981))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        PhaseMiniDot(name = "Ovulatory", color = Color(0xFF8B5CF6))
                                        PhaseMiniDot(name = "Luteal", color = Color(0xFFF59E0B))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Daily Medicines & Supplements 💊 Section with 3D Depth
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cute3DCard(maxTilt = 2.5f, cornerRadius = 20.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💊", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Daily Medicines & Care",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Timely daily alerts with distinct chime sound 🔔",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showMedicineDialog = true
                                },
                                modifier = Modifier.testTag("manage_medicines_button")
                            ) {
                                Text("+ Manage", fontWeight = FontWeight.Bold, color = PhasesPlum)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (medicines.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "No medicine reminders scheduled yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CuteAddButton(
                                    text = "Set Reminder",
                                    icon = "💊",
                                    sparkleEmoji = "🌸",
                                    onClick = { showMedicineDialog = true },
                                    testTag = "home_empty_set_med_reminder_button"
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                medicines.forEach { med ->
                                    val isTakenToday = med.lastTakenDate == todayDateString
                                    var itemCenter by remember { mutableStateOf(Offset.Zero) }

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isTakenToday) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coordinates ->
                                                val bounds = coordinates.boundsInWindow()
                                                itemCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
                                            }
                                            .border(
                                                1.dp,
                                                if (isTakenToday) Color(0xFF81C784).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(14.dp)
                                            )
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
                                                Text(med.categoryEmoji, fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = med.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${formatTime(med.hour, med.minute)} • ${med.dosage}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (isTakenToday) {
                                                Surface(
                                                    shape = RoundedCornerShape(100.dp),
                                                    color = Color(0xFFE8F5E9)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFF2E7D32),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Taken Today! ✨",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        SoundHelper.playCuteChime(context)
                                                        onMarkMedicineTaken(med.id)
                                                        particleController.spawn(
                                                            centerX = if (itemCenter != Offset.Zero) itemCenter.x else 500f,
                                                            centerY = if (itemCenter != Offset.Zero) itemCenter.y else 600f,
                                                            emojis = listOf("💊", "✨", "🌸", "💧", "💖"),
                                                            count = 14
                                                        )
                                                        particleController.showToast(
                                                            coroutineScope = coroutineScope,
                                                            message = "${med.name} marked as taken! Stay glowing! 💖",
                                                            icon = "💊"
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                                    modifier = Modifier.testTag("mark_taken_med_${med.id}")
                                                ) {
                                                    Text("Mark Taken 💊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            // Recent Periods Preview
            if (recentPeriods.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent Periods",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        recentPeriods.take(2).forEach { period ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🩸", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatFriendlyDate(period.startDateMillis),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = period.notes.ifBlank { "Logged" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun getGreeting(name: String): String {
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning, $name 🌸"
        hour < 17 -> "Good afternoon, $name 🌸"
        else -> "Good evening, $name 🌸"
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
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .cute3DCard(maxTilt = 3f, cornerRadius = 22.dp)
            .cuteBouncyClick(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
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
                Text(text = emoji, fontSize = 20.sp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    maxLines = 1
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = footerChip,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = accentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PhaseMiniDot(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


