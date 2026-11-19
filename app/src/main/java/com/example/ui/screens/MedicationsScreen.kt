package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Medication
import com.example.data.UserProfile
import com.example.notifications.SoundHelper
import com.example.ui.components.CuteAddButton
import com.example.ui.components.CuteAddFab
import com.example.ui.components.FloatingPetalsBackground
import com.example.ui.components.LocalParticleController
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPeach
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesSky
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationsScreen(
    userProfile: UserProfile?,
    medications: List<Medication>,
    onAddMedication: (name: String, dosage: String, hour: Int, min: Int, emoji: String, daysOfWeek: String, sound: String, message: String, instructions: String, colorHex: String) -> Unit,
    onUpdateMedication: (Medication) -> Unit,
    onDeleteMedication: (Long) -> Unit,
    onToggleMedication: (Long, Boolean) -> Unit,
    onMarkTaken: (Long) -> Unit,
    onSendTestAlert: (Medication) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val coroutineScope = rememberCoroutineScope()

    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val todayFormatted = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<Medication?>(null) }
    var medicationToDelete by remember { mutableStateOf<Medication?>(null) }

    val takenCount = medications.count { it.lastTakenDate == todayDateStr }
    val totalActive = medications.count { it.isEnabled }
    val progress = if (totalActive > 0) (takenCount.toFloat() / totalActive.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = modifier.fillMaxSize()) {
        FloatingPetalsBackground()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(PhasesRose.copy(alpha = 0.5f), PhasesLavender.copy(alpha = 0.5f))),
                            RoundedCornerShape(24.dp)
                        )
                        .testTag("medications_hero_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        PhasesRose.copy(alpha = 0.18f),
                                        PhasesLavender.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "💊", fontSize = 26.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Medication Alerts",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = todayFormatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (takenCount == totalActive && totalActive > 0) {
                                        PhasesSage.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (takenCount == totalActive && totalActive > 0) PhasesSage else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Text(
                                        text = if (totalActive == 0) "No Active Meds" else "$takenCount of $totalActive Taken",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (takenCount == totalActive && totalActive > 0) PhasesSage else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Progress indicator
                            Column {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(100.dp)),
                                    color = PhasesRose,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (progress >= 1f && totalActive > 0) "All done for today, queen! 🌸✨" else "Keep glowing and stay consistent 💕",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PhasesRose
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Header & Quick Add
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Schedule",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Audible chimes & cute personalized reminders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            editingMedication = null
                            showAddEditDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhasesRose),
                        modifier = Modifier.testTag("add_medication_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Med", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // List of Medications
            if (medications.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🌸💊✨", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Medication Alerts Scheduled",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add your daily vitamins, iron pills, or wellness prescriptions with custom alert sounds and loving personalized messages!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CuteAddButton(
                                text = "Schedule First Pill Alert",
                                icon = "💊",
                                sparkleEmoji = "🌸",
                                onClick = {
                                    editingMedication = null
                                    showAddEditDialog = true
                                },
                                testTag = "empty_add_medication_button"
                            )
                        }
                    }
                }
            } else {
                items(medications, key = { it.id }) { med ->
                    val isTakenToday = med.lastTakenDate == todayDateStr
                    var cardCenter by remember { mutableStateOf(Offset.Zero) }

                    MedicationCard(
                        medication = med,
                        isTakenToday = isTakenToday,
                        onMarkTaken = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SoundHelper.playSound(context, med.customSound)
                            onMarkTaken(med.id)
                            particleController.spawn(
                                centerX = if (cardCenter != Offset.Zero) cardCenter.x else 500f,
                                centerY = if (cardCenter != Offset.Zero) cardCenter.y else 600f,
                                emojis = listOf(med.categoryEmoji, "✨", "🌸", "💖", "🫧"),
                                count = 16
                            )
                            particleController.showToast(
                                coroutineScope = coroutineScope,
                                message = "${med.name} taken! Stay glowing! 💖",
                                icon = med.categoryEmoji
                            )
                        },
                        onToggle = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleMedication(med.id, enabled)
                        },
                        onEdit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            editingMedication = med
                            showAddEditDialog = true
                        },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            medicationToDelete = med
                        },
                        onSendTestAlert = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendTestAlert(med)
                        },
                        modifier = Modifier
                            .cute3DCard(maxTilt = 2f, cornerRadius = 18.dp)
                            .onGloballyPositioned { coordinates ->
                                cardCenter = coordinates.positionInRoot()
                            }
                            .testTag("medication_card_${med.id}")
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Ultra-cute 3D Floating Action Button
        CuteAddFab(
            onClick = {
                editingMedication = null
                showAddEditDialog = true
            },
            icon = "💊",
            haloColor = PhasesRose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            testTag = "fab_add_medication"
        )
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        AddEditMedicationDialog(
            existing = editingMedication,
            userName = userProfile?.name ?: "Krishi",
            onDismiss = { showAddEditDialog = false },
            onSave = { name, dosage, hour, min, emoji, daysOfWeek, sound, message, instructions, colorHex ->
                if (editingMedication == null) {
                    onAddMedication(name, dosage, hour, min, emoji, daysOfWeek, sound, message, instructions, colorHex)
                } else {
                    onUpdateMedication(
                        editingMedication!!.copy(
                            name = name,
                            dosage = dosage,
                            hour = hour,
                            minute = min,
                            categoryEmoji = emoji,
                            daysOfWeek = daysOfWeek,
                            customSound = sound,
                            customReminderMessage = message,
                            instructions = instructions,
                            colorHex = colorHex
                        )
                    )
                }
                showAddEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (medicationToDelete != null) {
        val med = medicationToDelete!!
        AlertDialog(
            onDismissRequest = { medicationToDelete = null },
            title = {
                Text("Delete ${med.name}?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete this medication reminder? Daily alerts for this medication will be cancelled.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedication(med.id)
                        medicationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MedicationCard(
    medication: Medication,
    isTakenToday: Boolean,
    onMarkTaken: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSendTestAlert: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val timeFormatted = remember(medication.hour, medication.minute) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, medication.hour)
            set(java.util.Calendar.MINUTE, medication.minute)
        }
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    val soundDisplayName = remember(medication.customSound) {
        when (medication.customSound) {
            SoundHelper.SOUND_SPARKLE_BELL -> "✨ Sparkle Bell"
            SoundHelper.SOUND_GENTLE_HARP -> "🎶 Gentle Harp"
            SoundHelper.SOUND_SWEET_BUBBLE -> "🫧 Sweet Bubble"
            else -> "🌸 Sweet Chime"
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTakenToday) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isTakenToday) PhasesSage.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Emoji + Name + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = medication.categoryEmoji, fontSize = 24.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = medication.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${medication.dosage} • $timeFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = medication.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PhasesRose
                    ),
                    modifier = Modifier.testTag("toggle_med_${medication.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sound & Instructions Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sound chip with preview
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = PhasesPlum.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, PhasesPlum.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SoundHelper.playSound(context, medication.customSound)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play sound", modifier = Modifier.size(14.dp), tint = PhasesPlum)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = soundDisplayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PhasesPlum)
                    }
                }

                if (medication.instructions.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = medication.instructions,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Personalized Reminder Message Bubble
            if (medication.customReminderMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PhasesRose.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "💌", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = medication.customReminderMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: Status / Mark Taken & Edit / Delete / Test Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status / Action Button
                if (isTakenToday) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(PhasesSage.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = PhasesSage, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (medication.lastTakenTime.isNotBlank()) "Taken at ${medication.lastTakenTime} ✨" else "Taken Today ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PhasesSage
                        )
                    }
                } else {
                    Button(
                        onClick = onMarkTaken,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhasesRose),
                        modifier = Modifier.testTag("mark_taken_${medication.id}")
                    ) {
                        Text(text = "Mark Taken 💊", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Action buttons: Edit, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationDialog(
    existing: Medication?,
    userName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, dosage: String, hour: Int, min: Int, emoji: String, daysOfWeek: String, sound: String, message: String, instructions: String, colorHex: String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dosage by remember { mutableStateOf(existing?.dosage ?: "1 capsule") }
    var instructions by remember { mutableStateOf(existing?.instructions ?: "Take with water") }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var selectedEmoji by remember { mutableStateOf(existing?.categoryEmoji ?: "💊") }
    var selectedSound by remember { mutableStateOf(existing?.customSound ?: SoundHelper.SOUND_CUTE_CHIME) }
    var reminderMessage by remember { mutableStateOf(existing?.customReminderMessage ?: "") }
    var selectedColor by remember { mutableStateOf(existing?.colorHex ?: "#D4629A") }

    val isPm = hour >= 12
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    val availableEmojis = listOf("💊", "🌸", "🌿", "🩸", "💧", "🩹", "☕", "🧴", "🌙", "🍓")
    val soundOptions = SoundHelper.getAvailableSounds()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Schedule Medication Alert 💊" else "Edit Medication Alert 🌸",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Field
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Medication / Vitamin Name *") },
                        placeholder = { Text("e.g. Daily Multivitamin, Iron, Magnesium") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("med_name_input")
                    )

                    // Quick name suggestion chips
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val suggestions = listOf("Multivitamin", "Iron & Vit C", "Magnesium", "Pain Relief", "Probiotics")
                        suggestions.forEach { sugg ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable {
                                    name = sugg
                                    if (reminderMessage.isBlank()) {
                                        reminderMessage = "Hey $userName! Time for your $sugg ✨ Have a fresh glass of water with it! 💧💕"
                                    }
                                }
                            ) {
                                Text(
                                    text = sugg,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Dosage & Instructions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dosage,
                            onValueChange = { dosage = it },
                            label = { Text("Dosage") },
                            placeholder = { Text("1 capsule") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("med_dosage_input")
                        )
                        OutlinedTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            label = { Text("Instructions") },
                            placeholder = { Text("With water") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("med_instructions_input")
                        )
                    }
                }

                // Category Emoji Picker
                item {
                    Text(text = "Choose Icon:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        availableEmojis.forEach { em ->
                            val isSelected = selectedEmoji == em
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) PhasesRose.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) PhasesRose else Color.Transparent),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedEmoji = em
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = em, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                // Time Picker (Enhanced Hour, Minute, AM/PM Options)
                item {
                    Text(text = "Alert & Alarm Time ⏰:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Hour & Minute adjusters
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hour
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Hour (1-12)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val current12 = if (hour % 12 == 0) 12 else hour % 12
                                                    val new12 = if (current12 == 1) 12 else current12 - 1
                                                    hour = if (isPm) (if (new12 == 12) 12 else new12 + 12) else (if (new12 == 12) 0 else new12)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .testTag("hour_decrement_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(
                                            text = String.format(java.util.Locale.getDefault(), "%02d", displayHour),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val current12 = if (hour % 12 == 0) 12 else hour % 12
                                                    val new12 = if (current12 == 12) 1 else current12 + 1
                                                    hour = if (isPm) (if (new12 == 12) 12 else new12 + 12) else (if (new12 == 12) 0 else new12)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .testTag("hour_increment_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Text(":", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)

                                // Minute
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Minute", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    minute = (minute - 5 + 60) % 60
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .testTag("minute_decrement_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(
                                            text = String.format(java.util.Locale.getDefault(), "%02d", minute),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    minute = (minute + 5) % 60
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .testTag("minute_increment_button")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Dedicated AM / PM Selector with Full 48dp Touch Targets
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // AM Option
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isPm) PhasesRose else Color.Transparent,
                                    border = if (!isPm) BorderStroke(1.5.dp, PhasesRose) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (isPm) hour -= 12
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        .testTag("alarm_am_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "☀️ AM (Morning)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (!isPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!isPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }

                                // PM Option
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isPm) PhasesPlum else Color.Transparent,
                                    border = if (isPm) BorderStroke(1.5.dp, PhasesPlum) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (!isPm) hour += 12
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        .testTag("alarm_pm_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🌙 PM (Evening)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // Open System Clock (AM / PM) Button
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    TimePickerDialog(
                                        context,
                                        { _, pickedHour, pickedMinute ->
                                            hour = pickedHour
                                            minute = pickedMinute
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        hour,
                                        minute,
                                        false // 12-hour AM/PM dialog
                                    ).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("alarm_open_clock_picker_button")
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp), tint = PhasesPlum)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Clock Dial (Pick AM / PM Time)",
                                    fontWeight = FontWeight.SemiBold,
                                    color = PhasesPlum,
                                    fontSize = 13.sp
                                )
                            }

                            // Quick Time Presets
                            Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    Triple(8, 0, "☀️ 8:00 AM"),
                                    Triple(12, 0, "☀️ 12:00 PM"),
                                    Triple(14, 0, "🌙 2:00 PM"),
                                    Triple(20, 0, "🌙 8:00 PM"),
                                    Triple(22, 0, "🌙 10:00 PM")
                                )
                                presets.forEach { (h, m, label) ->
                                    val isSelected = hour == h && minute == m
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable {
                                            hour = h
                                            minute = m
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Sound Picker
                item {
                    Text(text = "Custom Notification Sound 🔔:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        soundOptions.forEach { opt ->
                            val isSelected = selectedSound == opt.key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PhasesPlum.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) PhasesPlum else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedSound = opt.key
                                        SoundHelper.playSound(context, opt.key)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = opt.emoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = opt.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) PhasesPlum else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            SoundHelper.playSound(context, opt.key)
                                        },
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) PhasesPlum else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Listen", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Cute Personalized Reminder Message
                item {
                    val timeGreetingForSelectedHour = when {
                        hour in 5..11 -> "Good morning"
                        hour in 12..16 -> "Good afternoon"
                        hour in 17..20 -> "Good evening"
                        else -> "Good night"
                    }
                    val timeEmojiForSelectedHour = when {
                        hour in 5..11 -> "☀️"
                        hour in 12..16 -> "🌤️"
                        hour in 17..20 -> "🌆"
                        else -> "🌙"
                    }

                    Text(text = "Cute Personalized Reminder Message 💌:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = reminderMessage,
                        onValueChange = { reminderMessage = it },
                        placeholder = { Text("e.g. $timeGreetingForSelectedHour beautiful! Time for your glow vitamins 🌸 Drink fresh water and have a lovely day!") },
                        minLines = 3,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("med_message_input")
                    )

                    // Inspiration message chips
                    Text(
                        text = "Quick templates to tap & inspire ($timeGreetingForSelectedHour $timeEmojiForSelectedHour):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val templates = listOf(
                            "🌸 Glowing Queen" to "Hey $userName! Time for your ${name.ifBlank { "wellness dose" }} ✨ Keep that gorgeous smile on all day! 💕",
                            "💧 Hydrate & Glow" to "Drink up, angel! Fresh glass of water with your ${name.ifBlank { "vitamins" }} 💧 Your body loves you for this! 🫧",
                            "🌙 Sweet Rest" to "Bedtime self-care: Take your ${name.ifBlank { "pill" }}, relax, and sleep like a dream tonight! 😴✨",
                            "🌿 Adaptive Greeting" to "$timeGreetingForSelectedHour superstar! Take your ${name.ifBlank { "dose" }} and stay glowing! $timeEmojiForSelectedHour💪"
                        )
                        templates.forEach { (label, tmpl) ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesRose.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    reminderMessage = tmpl
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PhasesRose,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            CuteAddButton(
                text = if (existing == null) "Schedule Reminder" else "Update Reminder",
                icon = "💊",
                sparkleEmoji = "🌸",
                enabled = name.isNotBlank(),
                onClick = {
                    if (name.isNotBlank()) {
                        val finalDosage = dosage.trim().ifBlank { "1 dose" }
                        val finalMessage = reminderMessage.trim().ifBlank {
                            "Hey $userName! Time for your ${name.trim()} ($finalDosage) ✨ Have a fresh glass of water with it! 💧💕"
                        }
                        onSave(
                            name.trim(),
                            finalDosage,
                            hour,
                            minute,
                            selectedEmoji,
                            "ALL",
                            selectedSound,
                            finalMessage,
                            instructions.trim(),
                            selectedColor
                        )
                    }
                },
                testTag = "save_medication_button"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
