package com.example.ui.screens

import android.app.TimePickerDialog
import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserNote
import com.example.notifications.NotificationHelper
import com.example.ui.components.CuteAddButton
import com.example.ui.components.LocalParticleController
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    notes: List<UserNote>,
    onAddNote: (String, String, Long?) -> Unit,
    onUpdateReminder: (Long, Long?) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onSendTestNotification: (UserNote?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current

    var noteText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("thought") }
    var filterTag by remember { mutableStateOf("all") }
    var composerReminderMillis by remember { mutableStateOf<Long?>(null) }
    var showComposerReminderPicker by remember { mutableStateOf(false) }

    // Dialog state for setting/modifying reminder on existing note
    var editingReminderNote by remember { mutableStateOf<UserNote?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                particleController.showToast(coroutineScope, "Notifications active! You'll receive note reminders 🔔🌸")
            } else {
                particleController.showToast(coroutineScope, "Enable notifications in device Settings to get note alerts ℹ️")
            }
        }
    )

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val tags = listOf(
        "thought" to "💭 Thought",
        "reminder" to "⏰ To-Do",
        "symptom" to "🩺 Symptom",
        "gossip" to "💬 Gossip",
        "question" to "❓ Question"
    )

    val activeRemindersCount = notes.count {
        it.reminderTimeMillis != null && it.reminderTimeMillis > System.currentTimeMillis() && !it.reminderFired
    }

    val filteredNotes = when (filterTag) {
        "all" -> notes
        "reminders" -> notes.filter {
            it.reminderTimeMillis != null && it.reminderTimeMillis > System.currentTimeMillis() && !it.reminderFired
        }
        else -> notes.filter { it.tag == filterTag }
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
        // Screen Title Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Private Journal",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(18.dp)
                    )
                }

                if (activeRemindersCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$activeRemindersCount reminder${if (activeRemindersCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Text(
                text = "Safe space for reflections, questions, symptoms, and customizable reminder notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // New Note Composer Card with 3D Depth
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .cute3DCard(maxTilt = 3f, cornerRadius = 22.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Write a private note 💌",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesRose.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "🔒 Private",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PhasesRose,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = {
                            Text("A fleeting thought, symptom, doctor question, or reminder...")
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_content_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tag Selector Row with Haptic Feedback
                    Text(
                        text = "Category tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedTag == key,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTag = key
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reminder Notification Attachment Section
                    ComposerReminderSection(
                        reminderMillis = composerReminderMillis,
                        onSetPreset = { presetMillis ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ensureNotificationPermission()
                            composerReminderMillis = presetMillis
                        },
                        onOpenCustomPicker = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ensureNotificationPermission()
                            showComposerReminderPicker = true
                        },
                        onClearReminder = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            composerReminderMillis = null
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CuteAddButton(
                            text = if (composerReminderMillis != null) "Save & Set Reminder" else "Add Note",
                            icon = "💌",
                            sparkleEmoji = "🌸",
                            badgeText = if (composerReminderMillis != null) "⏰ Alert" else null,
                            enabled = noteText.isNotBlank(),
                            onClick = {
                                if (noteText.isNotBlank()) {
                                    ensureNotificationPermission()
                                    onAddNote(noteText, selectedTag, composerReminderMillis)
                                    val feedback = if (composerReminderMillis != null) {
                                        "Note saved with reminder notification! ⏰🌸"
                                    } else {
                                        "Private note safely saved 🌸"
                                    }
                                    particleController.showToast(
                                        coroutineScope = coroutineScope,
                                        message = feedback,
                                        icon = if (composerReminderMillis != null) "⏰" else "🔒"
                                    )
                                    noteText = ""
                                    composerReminderMillis = null
                                }
                            },
                            testTag = "save_note_button"
                        )
                    }
                }
            }
        }

        // Filter Tags Bar with Haptics (Includes Reminders Filter)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterTag == "all",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        filterTag = "all"
                    },
                    label = { Text("All Notes (${notes.size})") }
                )

                if (activeRemindersCount > 0) {
                    FilterChip(
                        selected = filterTag == "reminders",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            filterTag = "reminders"
                        },
                        label = { Text("⏰ Reminders ($activeRemindersCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                tags.forEach { (key, label) ->
                    val countForTag = notes.count { it.tag == key }
                    FilterChip(
                        selected = filterTag == key,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            filterTag = key
                        },
                        label = { Text("$label ($countForTag)") }
                    )
                }
            }
        }

        // Notes List
        if (filteredNotes.isEmpty() && filterTag == "reminders") {
            item {
                Text(
                    text = "No active note reminders yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        } else {
            items(filteredNotes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onOpenReminderDialog = {
                        ensureNotificationPermission()
                        editingReminderNote = note
                    },
                    onDelete = { onDeleteNote(note.id) },
                    modifier = Modifier.cute3DCard(maxTilt = 2f, cornerRadius = 18.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        }
    }

    // Modal Dialog to Edit or Set Reminder on an Existing Note
    editingReminderNote?.let { targetNote ->
        NoteReminderDialog(
            note = targetNote,
            onDismiss = { editingReminderNote = null },
            onSaveReminder = { reminderMillis ->
                ensureNotificationPermission()
                onUpdateReminder(targetNote.id, reminderMillis)
                particleController.spawn(500f, 400f, listOf("⏰", "🔔", "✨"), 12)
                particleController.showToast(
                    coroutineScope,
                    if (reminderMillis != null) "Reminder notification scheduled! ⏰🌸" else "Reminder cleared"
                )
                editingReminderNote = null
            }
        )
    }

    // Modal Dialog to Pick Custom Time for Note in Composer
    if (showComposerReminderPicker) {
        CustomTimeReminderPicker(
            initialMillis = composerReminderMillis ?: (System.currentTimeMillis() + 60 * 60 * 1000L),
            onDismiss = { showComposerReminderPicker = false },
            onConfirm = { chosenMillis ->
                composerReminderMillis = chosenMillis
                showComposerReminderPicker = false
            }
        )
    }
}

/**
 * Section inside composer card to attach reminder notification before saving
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerReminderSection(
    reminderMillis: Long?,
    onSetPreset: (Long) -> Unit,
    onOpenCustomPicker: () -> Unit,
    onClearReminder: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (reminderMillis != null) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = if (reminderMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Notification Reminder",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (reminderMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            if (reminderMillis != null) {
                TextButton(
                    onClick = onClearReminder,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (reminderMillis != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⏰", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Reminder Scheduled",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatReminderTime(reminderMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearReminder,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear reminder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            // Quick Preset Chips Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetReminderChip(
                    label = "+1 Hour",
                    emoji = "⚡",
                    onClick = {
                        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                        onSetPreset(cal.timeInMillis)
                    }
                )

                PresetReminderChip(
                    label = "Tonight (8 PM)",
                    emoji = "🌆",
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onSetPreset(cal.timeInMillis)
                    }
                )

                PresetReminderChip(
                    label = "Tomorrow (9 AM)",
                    emoji = "☀️",
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onSetPreset(cal.timeInMillis)
                    }
                )

                PresetReminderChip(
                    label = "Custom Time...",
                    emoji = "📅",
                    onClick = onOpenCustomPicker
                )
            }
        }
    }
}

@Composable
private fun PresetReminderChip(
    label: String,
    emoji: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: UserNote,
    onOpenReminderDialog: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tagDisplay = when (note.tag) {
        "gossip" -> "💬 Gossip"
        "question" -> "❓ Question"
        "symptom" -> "🩺 Symptom"
        "reminder" -> "⏰ To-Do"
        else -> "💭 Thought"
    }

    val timeFormatted = formatTimeWithDate(note.timestampMillis)
    val now = System.currentTimeMillis()
    val isUpcomingReminder = note.reminderTimeMillis != null && note.reminderTimeMillis > now && !note.reminderFired
    val isFiredReminder = note.reminderFired || (note.reminderTimeMillis != null && note.reminderTimeMillis <= now)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isUpcomingReminder) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Tag, Timestamp, and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = tagDisplay,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Delete Note button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_note_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note Content
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Reminder Status / Trigger Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUpcomingReminder) {
                    // Active Reminder Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable(onClick = onOpenReminderDialog)
                            .testTag("reminder_pill_${note.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Reminding: ${formatReminderTime(note.reminderTimeMillis)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (isFiredReminder) {
                    // Already Reminded Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable(onClick = onOpenReminderDialog)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✅ Reminded • Tap to re-arm", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    // Set Reminder affordance
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable(onClick = onOpenReminderDialog)
                            .testTag("set_reminder_${note.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Set Reminder 🔔",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to set, edit, reschedule, or cancel reminder for an existing note
 */
@Composable
private fun NoteReminderDialog(
    note: UserNote,
    onDismiss: () -> Unit,
    onSaveReminder: (Long?) -> Unit
) {
    var selectedMillis by remember {
        mutableLongStateOf(
            note.reminderTimeMillis?.takeIf { it > System.currentTimeMillis() }
                ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        )
    }

    var showCustomPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔔", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Remind Me of This Note",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "You'll receive a system notification quoting this note at your chosen time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Note preview snippet
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "\"${note.content.take(120)}${if (note.content.length > 120) "..." else ""}\"",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Presets list
                val presets = listOf(
                    "In 1 Hour" to {
                        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
                    },
                    "In 3 Hours" to {
                        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }.timeInMillis
                    },
                    "Tonight (8:00 PM)" to {
                        Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }.timeInMillis
                    },
                    "Tomorrow Morning (9:00 AM)" to {
                        Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                    },
                    "Tomorrow Evening (8:00 PM)" to {
                        Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, calculator) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedMillis = calculator() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = formatReminderTime(calculator()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showCustomPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Custom Date & Time")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target time indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⏰", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Selected reminder time:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatReminderTime(selectedMillis),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveReminder(selectedMillis) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum, contentColor = Color.White)
            ) {
                Text("Save Reminder ⏰", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (note.reminderTimeMillis != null) {
                    TextButton(
                        onClick = { onSaveReminder(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showCustomPicker) {
        CustomTimeReminderPicker(
            initialMillis = selectedMillis,
            onDismiss = { showCustomPicker = false },
            onConfirm = { chosen ->
                selectedMillis = chosen
                showCustomPicker = false
            }
        )
    }
}

/**
 * Custom Date and Time picker dialog with steppers
 */
@Composable
private fun CustomTimeReminderPicker(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    val initialCal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    var dayOffset by remember {
        val nowCal = Calendar.getInstance()
        val diffDays = ((initialCal.timeInMillis - nowCal.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
        mutableIntStateOf(diffDays)
    }
    var hour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((initialCal.get(Calendar.MINUTE) / 5) * 5) }

    fun computeResultMillis(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pick Reminder Time ⏰",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Day selector
                Column {
                    Text(
                        text = "Day",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dayOptions = listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2 Days", 3 to "+3 Days", 7 to "+1 Wk")
                        dayOptions.forEach { (offset, label) ->
                            FilterChip(
                                selected = dayOffset == offset,
                                onClick = { dayOffset = offset },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                val isPm = hour >= 12
                val display12Hour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour

                // Hour adjustment (1-12)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hour (1-12)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:00 (%s)", display12Hour, if (isPm) "PM" else "AM"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val current12 = if (hour % 12 == 0) 12 else hour % 12
                                val new12 = if (current12 == 1) 12 else current12 - 1
                                hour = if (isPm) (if (new12 == 12) 12 else new12 + 12) else (if (new12 == 12) 0 else new12)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(width = 58.dp, height = 48.dp).testTag("notes_hour_decrement_button")
                        ) {
                            Text("-1", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", display12Hour),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = {
                                val current12 = if (hour % 12 == 0) 12 else hour % 12
                                val new12 = if (current12 == 12) 1 else current12 + 1
                                hour = if (isPm) (if (new12 == 12) 12 else new12 + 12) else (if (new12 == 12) 0 else new12)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(width = 58.dp, height = 48.dp).testTag("notes_hour_increment_button")
                        ) {
                            Text("+1", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Minute adjustment
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minute",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = String.format(Locale.getDefault(), ":%02d", minute),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { minute = (minute - 5 + 60) % 60 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(width = 58.dp, height = 48.dp).testTag("notes_minute_decrement_button")
                        ) {
                            Text("-5", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", minute),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = { minute = (minute + 5) % 60 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(width = 58.dp, height = 48.dp).testTag("notes_minute_increment_button")
                        ) {
                            Text("+5", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dedicated AM / PM Selector
                Column {
                    Text(
                        text = "Alarm Period (AM / PM)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Open Clock Dial (AM / PM)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, pickedHour, pickedMinute ->
                                    hour = pickedHour
                                    minute = pickedMinute
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
                            .testTag("notes_open_clock_picker_button")
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
                }

                // Live Preview
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Will remind at:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatReminderTime(computeResultMillis()),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(computeResultMillis()) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum, contentColor = Color.White)
            ) {
                Text("Confirm Time", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimeWithDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatReminderTime(millis: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(millis))

    return when {
        isToday -> "Today, $timeStr"
        isTomorrow -> "Tomorrow, $timeStr"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}
