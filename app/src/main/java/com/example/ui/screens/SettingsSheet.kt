package com.example.ui.screens

import android.app.TimePickerDialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfile
import com.example.notifications.NotificationHelper
import com.example.notifications.SoundHelper
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesWaterBlue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    userProfile: UserProfile?,
    onSaveSettings: (
        name: String,
        cycleLength: Int,
        periodLength: Int,
        waterGoal: Int,
        waterReminders: Boolean,
        cycleReminders: Boolean,
        fertileReminders: Boolean,
        checkinReminders: Boolean,
        shopReminders: Boolean,
        reminderHour: Int
    ) -> Unit,
    onSendTestNotification: (type: String) -> Unit = {},
    onClearAllData: () -> Unit,
    isSignedIn: Boolean = false,
    onDeleteAccount: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    var name by remember(userProfile) { mutableStateOf(userProfile?.name ?: "") }
    var cycleLength by remember(userProfile) { mutableIntStateOf(userProfile?.cycleLength ?: 28) }
    var periodLength by remember(userProfile) { mutableIntStateOf(userProfile?.periodLength ?: 5) }
    var waterGoal by remember(userProfile) { mutableIntStateOf(userProfile?.waterGoal ?: 8) }

    // Notifications state
    var waterReminders by remember(userProfile) { mutableStateOf(userProfile?.waterRemindersEnabled ?: true) }
    var cycleReminders by remember(userProfile) { mutableStateOf(userProfile?.cycleRemindersEnabled ?: true) }
    var fertileReminders by remember(userProfile) { mutableStateOf(userProfile?.fertileRemindersEnabled ?: true) }
    var checkinReminders by remember(userProfile) { mutableStateOf(userProfile?.checkinRemindersEnabled ?: true) }
    var shopReminders by remember(userProfile) { mutableStateOf(userProfile?.shopRemindersEnabled ?: true) }
    var reminderHour by remember(userProfile) { mutableIntStateOf(userProfile?.reminderHour ?: 20) }
    var celebrationSoundsEnabled by remember { mutableStateOf(SoundHelper.isCelebrationSoundEnabled(context)) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Soundscape",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification Permission Banner
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPermission) PhasesSage.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_permission_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (hasPermission) PhasesSage else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasPermission) "Notifications Allowed" else "Notifications Paused",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasPermission) "Reminders will alert you locally on schedule." else "Enable to receive cycle, water & supply reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!hasPermission) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                            modifier = Modifier.testTag("request_permission_button")
                        ) {
                            Text("Enable", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Cycle Profile
            Text(
                text = "Cycle & Health Profile",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingStepper(
                        label = "Cycle Length",
                        value = "$cycleLength days",
                        onDecrement = { if (cycleLength > 18) cycleLength-- },
                        onIncrement = { if (cycleLength < 60) cycleLength++ }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingStepper(
                        label = "Period Length",
                        value = "$periodLength days",
                        onDecrement = { if (periodLength > 2) periodLength-- },
                        onIncrement = { if (periodLength < 14) periodLength++ }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingStepper(
                        label = "Daily Water Goal",
                        value = "$waterGoal glasses",
                        onDecrement = { if (waterGoal > 4) waterGoal-- },
                        onIncrement = { if (waterGoal < 20) waterGoal++ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Section 2: Notifications & Reminders
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = PhasesPlum,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Smart Notifications (100% Offline)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Period Predictions
                    ReminderSwitchRow(
                        title = "🌸 Period & Cycle Predictions",
                        description = "Alerts 2 days before predicted start and on expected period day",
                        checked = cycleReminders,
                        onCheckedChange = { cycleReminders = it },
                        testTag = "switch_cycle_reminders"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Hydration Reminders
                    ReminderSwitchRow(
                        title = "💧 Hydration Nudges",
                        description = "Daytime alerts (10:30am, 2:30pm, 6pm) with quick '+1 Glass' action",
                        checked = waterReminders,
                        onCheckedChange = { waterReminders = it },
                        testTag = "switch_water_reminders"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Fertile Window
                    ReminderSwitchRow(
                        title = "🌱 Fertile & Ovulation Alerts",
                        description = "Nudge when entering your fertile phase with peak natural vitality",
                        checked = fertileReminders,
                        onCheckedChange = { fertileReminders = it },
                        testTag = "switch_fertile_reminders"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Phase Essentials Checklist
                    ReminderSwitchRow(
                        title = "🛍️ Phase Essentials & To Get",
                        description = "Reminder when entering PMS or period to check tea, pads & comfort items",
                        checked = shopReminders,
                        onCheckedChange = { shopReminders = it },
                        testTag = "switch_shop_reminders"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Daily Reflection & Alarm
                    ReminderSwitchRow(
                        title = "🌙 Daily Reflection & Alarm",
                        description = "Gentle daily reminder to reflect, log symptoms & jot private notes",
                        checked = checkinReminders,
                        onCheckedChange = { checkinReminders = it },
                        testTag = "switch_checkin_reminders"
                    )

                    if (checkinReminders) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isReminderPm = reminderHour >= 12
                            val display12Hour = if (reminderHour == 0) 12 else if (reminderHour > 12) reminderHour - 12 else reminderHour
                            val amPmText = if (isReminderPm) "PM" else "AM"

                            // Current time display badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Daily Reflection Alarm:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$display12Hour:00 $amPmText",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isReminderPm) PhasesPlum else PhasesRose
                                    )
                                }
                            }

                            // Dedicated AM / PM Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // AM Option
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isReminderPm) PhasesRose else Color.Transparent,
                                    border = if (!isReminderPm) BorderStroke(1.5.dp, PhasesRose) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isReminderPm) {
                                                reminderHour = if (reminderHour >= 12) reminderHour - 12 else reminderHour
                                            }
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
                                            color = if (!isReminderPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!isReminderPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }

                                // PM Option
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isReminderPm) PhasesPlum else Color.Transparent,
                                    border = if (isReminderPm) BorderStroke(1.5.dp, PhasesPlum) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!isReminderPm) {
                                                reminderHour = if (reminderHour < 12) reminderHour + 12 else reminderHour
                                            }
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
                                            color = if (isReminderPm) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isReminderPm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // Quick Time Chips for Selected AM or PM with Haptics
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val availableHours = if (!isReminderPm) {
                                    listOf(7 to "7 AM", 8 to "8 AM", 9 to "9 AM", 10 to "10 AM", 11 to "11 AM")
                                } else {
                                    listOf(13 to "1 PM", 18 to "6 PM", 19 to "7 PM", 20 to "8 PM", 21 to "9 PM", 22 to "10 PM")
                                }
                                availableHours.forEach { (h, label) ->
                                    FilterChip(
                                        selected = reminderHour == h,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            reminderHour = h
                                        },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (reminderHour == h) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (isReminderPm) PhasesPlum else PhasesRose,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Open Clock Dial (AM / PM)
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, pickedHour, _ ->
                                            reminderHour = pickedHour
                                        },
                                        reminderHour,
                                        0,
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
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calm Soundscape Card (Selective Sound Controls)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calm_soundscape_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = PhasesRose,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Calm Soundscape",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ReminderSwitchRow(
                        title = "Soft Milestone Chimes",
                        description = "Play a gentle chime only on milestones like 100% hydration or all meds complete. Routine taps stay silently haptic; medication alarms always alert.",
                        checked = celebrationSoundsEnabled,
                        onCheckedChange = { enabled ->
                            celebrationSoundsEnabled = enabled
                            SoundHelper.setCelebrationSoundEnabled(context, enabled)
                            if (enabled) {
                                SoundHelper.playCelebration(context)
                            }
                        },
                        testTag = "switch_celebration_sounds"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Minimal Always-On Privacy Card (12-layer protection runs silently in the background)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PhasesSage.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .testTag("backend_security_firewall_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(PhasesSage.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Always-on privacy protection",
                            tint = PhasesSage,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always-On Privacy",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Health logs are encrypted on-device with Android Keystore AES-256-GCM. Couple Lounge rooms sync end-to-end encrypted turns via ECDH P-256.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Button(
                onClick = {
                    onSaveSettings(
                        name,
                        cycleLength,
                        periodLength,
                        waterGoal,
                        waterReminders,
                        cycleReminders,
                        fertileReminders,
                        checkinReminders,
                        shopReminders,
                        reminderHour
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhasesPlum,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Text("Save Changes 🌸", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_data_button")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear All Local Data")
            }

            if (isSignedIn) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteAccountDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_account_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Account & Erase Cloud Rooms", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Erase All Local Data?") },
            text = {
                Text(
                    "This permanently erases all local period logs, notes, water counts, medicine reminders, and rotates your local encryption key. All scheduled reminders and active notifications will be cancelled."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllData()
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_data_button")
                ) {
                    Text("Clear Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Erase Data?") },
            text = {
                Text(
                    "This will:\n" +
                        "• Erase all local health records, notes, reminders, and encryption keys.\n" +
                        "• Disconnect your Couple Lounge rooms and scrub your display names and public keys from Firestore.\n" +
                        "• Delete your Firebase sign-in account (if recently signed in).\n\n" +
                        "Retained non-identifying records: retired 6-digit room codes stay tombstoned so they can never be reused, and append-only encrypted turn ciphertext becomes unreadable once local keys are erased."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReminderSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { isChecked ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(isChecked)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PhasesPlum
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingStepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDecrement()
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(78.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIncrement()
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
        }
    }
}
