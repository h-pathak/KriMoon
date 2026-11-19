package com.example.ui.components

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MedicineReminder
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose

@Composable
fun MedicineManagerDialog(
    reminders: List<MedicineReminder>,
    onAddReminder: (name: String, dosage: String, hour: Int, minute: Int, emoji: String) -> Unit,
    onUpdateReminder: (MedicineReminder) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onToggleReminder: (id: Long, isEnabled: Boolean) -> Unit,
    onTestSound: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isAddingNew by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<MedicineReminder?>(null) }

    var newName by remember { mutableStateOf("") }
    var newDosage by remember { mutableStateOf("1 pill") }
    var newHour by remember { mutableIntStateOf(9) }
    var newMinute by remember { mutableIntStateOf(0) }
    var newEmoji by remember { mutableStateOf("💊") }

    val emojis = listOf("💊", "🌸", "🌿", "🩸", "💧", "🩹", "☕", "🧴")

    fun showTimePicker(initialHour: Int, initialMinute: Int, onTimePicked: (Int, Int) -> Unit) {
        val picker = TimePickerDialog(
            context,
            { _, hour, minute -> onTimePicked(hour, minute) },
            initialHour,
            initialMinute,
            false
        )
        picker.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Medicine Reminders 💊",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Set daily alerts for vitamins, supplements, and medications with KriMoon's distinct chime sound 🔔✨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isAddingNew && editingReminder == null) {
                    // List of existing reminders
                    if (reminders.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No medicine reminders yet 🌸", fontSize = 14.sp)
                                Text("Tap '+ Add Medicine' below to create one!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(reminders, key = { it.id }) { reminder ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(reminder.categoryEmoji, fontSize = 22.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = reminder.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                val formattedTime = formatTime(reminder.hour, reminder.minute)
                                                Text(
                                                    text = "$formattedTime • ${reminder.dosage}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = reminder.isEnabled,
                                                onCheckedChange = { isEnabled ->
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onToggleReminder(reminder.id, isEnabled)
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PhasesPlum),
                                                modifier = Modifier.testTag("toggle_med_${reminder.id}")
                                            )
                                            IconButton(
                                                onClick = {
                                                    editingReminder = reminder
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDeleteReminder(reminder.id)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ultra-cute "+ Add Medicine" button
                    CuteAddButton(
                        text = "Add Medicine / Vitamin",
                        icon = "💊",
                        sparkleEmoji = "🌸",
                        onClick = {
                            newName = ""
                            newDosage = "1 pill"
                            newHour = 9
                            newMinute = 0
                            newEmoji = "💊"
                            isAddingNew = true
                        },
                        testTag = "open_add_medicine_button"
                    )

                    // Test Sound Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTestSound()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notif_sound_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = PhasesPlum)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🔔 Hear Distinct Cute Sound & Test Alert", color = PhasesPlum, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Form for adding or editing
                    val isEdit = editingReminder != null
                    var formName by remember { mutableStateOf(if (isEdit) editingReminder!!.name else newName) }
                    var formDosage by remember { mutableStateOf(if (isEdit) editingReminder!!.dosage else newDosage) }
                    var formHour by remember { mutableIntStateOf(if (isEdit) editingReminder!!.hour else newHour) }
                    var formMinute by remember { mutableIntStateOf(if (isEdit) editingReminder!!.minute else newMinute) }
                    var formEmoji by remember { mutableStateOf(if (isEdit) editingReminder!!.categoryEmoji else newEmoji) }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = formName,
                            onValueChange = { formName = it },
                            label = { Text("Medicine or Supplement Name") },
                            placeholder = { Text("e.g. Iron & Vitamin C, Birth Control, Multivitamin") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("medicine_name_input")
                        )

                        OutlinedTextField(
                            value = formDosage,
                            onValueChange = { formDosage = it },
                            label = { Text("Dosage / Instructions") },
                            placeholder = { Text("e.g. 1 pill with water, 2 drops") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("medicine_dosage_input")
                        )

                        // Time Picker & AM/PM Options
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Daily Reminder Time:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Button(
                                    onClick = {
                                        showTimePicker(formHour, formMinute) { h, m ->
                                            formHour = h
                                            formMinute = m
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                    modifier = Modifier.testTag("medicine_time_picker_button")
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(formatTime(formHour, formMinute), fontWeight = FontWeight.Bold)
                                }
                            }

                            // Dedicated AM / PM Selector
                            val isFormPm = formHour >= 12
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isFormPm) PhasesRose else Color.Transparent,
                                    border = if (!isFormPm) BorderStroke(1.5.dp, PhasesRose) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isFormPm) formHour -= 12
                                        }
                                        .testTag("medicine_am_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "☀️ AM (Morning)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (!isFormPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!isFormPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFormPm) PhasesPlum else Color.Transparent,
                                    border = if (isFormPm) BorderStroke(1.5.dp, PhasesPlum) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!isFormPm) formHour += 12
                                        }
                                        .testTag("medicine_pm_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🌙 PM (Evening)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isFormPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isFormPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // Quick Time Presets with Haptic Feedback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Triple(8, 0, "8 AM"),
                                    Triple(13, 0, "1 PM"),
                                    Triple(20, 0, "8 PM"),
                                    Triple(22, 0, "10 PM")
                                ).forEach { (h, m, label) ->
                                    val isSel = formHour == h && formMinute == m
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                formHour = h
                                                formMinute = m
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Emoji Selector with Haptic Feedback
                        Text("Choose Icon:", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            emojis.forEach { emoji ->
                                FilterChip(
                                    selected = formEmoji == emoji,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        formEmoji = emoji
                                    },
                                    label = { Text(emoji, fontSize = 18.sp) }
                                )
                            }
                        }

                        // Action Buttons for Form with CuteAddButton
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isAddingNew = false
                                    editingReminder = null
                                },
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Text("Cancel")
                            }

                            CuteAddButton(
                                text = if (isEdit) "Update" else "Save Pill",
                                icon = formEmoji,
                                sparkleEmoji = "✨",
                                enabled = formName.isNotBlank(),
                                onClick = {
                                    if (formName.isNotBlank()) {
                                        if (isEdit) {
                                            onUpdateReminder(
                                                editingReminder!!.copy(
                                                    name = formName.trim(),
                                                    dosage = formDosage.trim().ifBlank { "1 dose" },
                                                    hour = formHour,
                                                    minute = formMinute,
                                                    categoryEmoji = formEmoji
                                                )
                                            )
                                        } else {
                                            onAddReminder(
                                                formName.trim(),
                                                formDosage.trim().ifBlank { "1 dose" },
                                                formHour,
                                                formMinute,
                                                formEmoji
                                            )
                                        }
                                        isAddingNew = false
                                        editingReminder = null
                                    }
                                },
                                modifier = Modifier.weight(1.2f),
                                testTag = "save_medicine_button"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isAddingNew && editingReminder == null) {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    )
}

fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val displayMinute = String.format(java.util.Locale.getDefault(), "%02d", minute)
    return "$displayHour:$displayMinute $amPm"
}
