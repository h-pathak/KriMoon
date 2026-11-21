package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfile
import com.example.data.WaterLog
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.components.CutePulseButton
import com.example.ui.components.InteractiveWaterTumbler3D
import com.example.ui.components.LocalParticleController
import com.example.ui.components.TactileHydrationHelper
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesWaterBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterScreen(
    userProfile: UserProfile?,
    cycleStatus: CycleStatus,
    waterLog: WaterLog?,
    onIncrementWater: () -> Unit,
    onDecrementWater: () -> Unit,
    onSetWaterCount: (Int) -> Unit,
    onResetWater: () -> Unit,
    onUpdateWaterGoal: ((Int) -> Unit)? = null,
    onTestWaterNotification: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val tactile = remember(context, haptic) { TactileHydrationHelper(context, haptic) }

    val goal = userProfile?.waterGoal ?: 8
    val count = waterLog?.glassesCount ?: 0
    val isGoalReached = count >= goal

    var showResetDialog by remember { mutableStateOf(false) }
    var isEditingGoal by remember { mutableStateOf(false) }

    // Button animation scales
    val minusScale = remember { Animatable(1f) }
    val bottleScale = remember { Animatable(1f) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hydration Ritual",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Nourish your body and stay in tune with your cycle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        tactile.onGoalAdjusted()
                        isEditingGoal = !isEditingGoal
                    },
                    modifier = Modifier.testTag("water_goal_edit_toggle")
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Customize Goal",
                        tint = if (isEditingGoal) PhasesWaterBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Hero 3D Interactive Tumbler with Liquid Wave Simulation
        item {
            InteractiveWaterTumbler3D(
                count = count,
                goal = goal,
                onLogSip = {
                    val next = count + 1
                    onIncrementWater()
                    if (next >= goal && count < goal) {
                        SoundHelper.playCelebration(context)
                        particleController.showToast(
                            coroutineScope = coroutineScope,
                            message = "Goal reached! Hydrated queen 💧👑",
                            icon = "🎉"
                        )
                    } else {
                        particleController.showToast(
                            coroutineScope = coroutineScope,
                            message = "Sip logged! +1 glass 💧",
                            icon = "💧"
                        )
                    }
                }
            )
        }

        // 2. Goal Customization Panel (collapsible or toggleable)
        item {
            AnimatedVisibility(visible = isEditingGoal) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, PhasesWaterBlue.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cute3DCard(maxTilt = 2f, cornerRadius = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎯 Customize Daily Target",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$goal glasses (${goal * 250} ml)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PhasesWaterBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick target presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(6, 8, 10, 12).forEach { presetGoal ->
                                val isSelected = goal == presetGoal
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) PhasesWaterBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) PhasesWaterBlue else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            tactile.onGoalAdjusted()
                                            onUpdateWaterGoal?.invoke(presetGoal)
                                        }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "$presetGoal",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${presetGoal * 250}ml",
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stepper row for fine adjustment
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Fine-tune target:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        if (goal > 4) {
                                            tactile.onGoalAdjusted()
                                            onUpdateWaterGoal?.invoke(goal - 1)
                                        }
                                    },
                                    enabled = goal > 4,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }

                                Text(
                                    text = "$goal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (goal < 20) {
                                            tactile.onGoalAdjusted()
                                            onUpdateWaterGoal?.invoke(goal + 1)
                                        }
                                    },
                                    enabled = goal < 20,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Intake Presets (+1 Glass, +Bottle 500ml, Undo)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo (-1) Button with warning haptic
                    OutlinedButton(
                        onClick = {
                            if (count > 0) {
                                tactile.onDeduct()
                                coroutineScope.launch {
                                    minusScale.snapTo(1f)
                                    minusScale.animateTo(0.90f, tween(60, easing = FastOutSlowInEasing))
                                    minusScale.animateTo(1.05f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    minusScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                }
                                onDecrementWater()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        enabled = count > 0,
                        modifier = Modifier
                            .weight(0.70f)
                            .height(54.dp)
                            .graphicsLayer {
                                scaleX = minusScale.value
                                scaleY = minusScale.value
                            }
                            .testTag("water_minus_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-1", fontWeight = FontWeight.SemiBold)
                    }

                    // Main +1 Glass Cute Pulse Button
                    CutePulseButton(
                        onClick = {
                            val next = count + 1
                            onIncrementWater()
                            tactile.onDropSip(next >= goal && count < goal)

                            particleController.spawn(
                                centerX = 540f,
                                centerY = 900f,
                                emojis = listOf("💧", "🫧", "✨", "🌊", "💙"),
                                count = 12
                            )
                            if (next >= goal && count < goal) {
                                SoundHelper.playCelebration(context)
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Goal reached! Hydrated queen 💧👑",
                                    icon = "🎉"
                                )
                            } else {
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "Sip logged! +1 glass (250 ml) 💧",
                                    icon = "💧"
                                )
                            }
                        },
                        text = "Log +1 Glass",
                        icon = "💧",
                        badgeText = "${count + 1}/$goal",
                        gradientColors = listOf(
                            Color(0xFF3B82F6),
                            PhasesWaterBlue,
                            Color(0xFF60A5FA)
                        ),
                        glowColor = PhasesWaterBlue.copy(alpha = 0.5f),
                        testTag = "water_plus_button",
                        pulseSpeedMs = 1700,
                        modifier = Modifier.weight(1.30f)
                    )
                }

                // Secondary Quick Preset: Bottle (+500ml / 2 glasses)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, PhasesWaterBlue.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .graphicsLayer {
                                scaleX = bottleScale.value
                                scaleY = bottleScale.value
                            }
                            .clickable {
                                val crossesGoal = count < goal && count + 2 >= goal
                                tactile.onPresetLogged(2)
                                if (crossesGoal) {
                                    SoundHelper.playCelebration(context)
                                }
                                coroutineScope.launch {
                                    bottleScale.snapTo(1f)
                                    bottleScale.animateTo(0.92f, tween(70))
                                    bottleScale.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    bottleScale.animateTo(1.0f)
                                }
                                onSetWaterCount(count + 2)
                                particleController.spawn(
                                    centerX = 540f,
                                    centerY = 900f,
                                    emojis = listOf("💧", "🌊", "🫧", "✨"),
                                    count = 14
                                )
                                particleController.showToast(
                                    coroutineScope = coroutineScope,
                                    message = "+1 Water Bottle logged (500 ml)! 🌊",
                                    icon = "🌊"
                                )
                            }
                            .testTag("water_bottle_preset_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(text = "🌊", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+Bottle (500 ml / 2 glasses)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Daily Milestone & Rhythm Breakdown
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .cute3DCard(maxTilt = 2f, cornerRadius = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📅 Today's Hydration Rhythm",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val morningTarget = (goal * 0.35f).toInt().coerceAtLeast(2)
                    val afternoonTarget = (goal * 0.70f).toInt().coerceAtLeast(4)

                    val isMorningDone = count >= morningTarget
                    val isAfternoonDone = count >= afternoonTarget
                    val isEveningDone = count >= goal

                    MilestoneRow(
                        timeLabel = "Morning",
                        glassesTarget = "1–$morningTarget glasses",
                        benefit = "Awaken metabolism & vitality",
                        isCompleted = isMorningDone
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MilestoneRow(
                        timeLabel = "Afternoon",
                        glassesTarget = "${morningTarget + 1}–$afternoonTarget glasses",
                        benefit = "Sustain focus & prevent 3 PM fatigue",
                        isCompleted = isAfternoonDone
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MilestoneRow(
                        timeLabel = "Evening",
                        glassesTarget = "${afternoonTarget + 1}–$goal glasses",
                        benefit = "Gentle cellular replenishment before rest",
                        isCompleted = isEveningDone
                    )
                }
            }
        }

        // 6. Cycle Phase Hydration Tip
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PhasesWaterBlue.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, PhasesWaterBlue.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🌸", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cycle Wisdom for ${cycleStatus.currentPhase.shortName}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (cycleStatus.currentPhase) {
                                CyclePhase.MENSTRUAL -> "During your period, fluid loss and progesterone drop trigger uterine contractions and headaches. Drinking warm water or herbal teas replaces lost plasma volume and soothes cramping."
                                CyclePhase.FOLLICULAR -> "As estrogen climbs, your metabolic rate and collagen synthesis accelerate. Good hydration supports rapid cell turnover and gives your skin a luminous natural glow."
                                CyclePhase.FERTILE, CyclePhase.OVULATION -> "Optimal hydration is critical during your fertile window to maintain fertile cervical fluid viscosity and regulate baseline body temperature during ovulation."
                                CyclePhase.LUTEAL, CyclePhase.LATE_LUTEAL -> "Progesterone triggers sodium retention and PMS bloating. Counter-intuitively, drinking extra water flushes kidneys and directly eliminates water retention puffiness."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 7. Hydration Nudge Notifications Info
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = PhasesWaterBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userProfile?.waterRemindersEnabled == true) "Hydration Nudges Active 💧" else "Hydration Nudges Off",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (userProfile?.waterRemindersEnabled == true)
                                "Scheduled nudges at 10:30 AM, 2:30 PM & 6:00 PM with direct +1 Glass action."
                            else
                                "Turn on hydration nudges in Settings for scheduled reminders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 8. Reset Button with confirmation safeguard
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        tactile.onDeduct()
                        showResetDialog = true
                    },
                    modifier = Modifier.testTag("water_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset Today's Water Count",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset Water Count?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to reset today's water count back to 0 glasses?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        tactile.onDeduct()
                        showResetDialog = false
                        onResetWater()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MilestoneRow(
    timeLabel: String,
    glassesTarget: String,
    benefit: String,
    isCompleted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCompleted) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isCompleted) PhasesWaterBlue.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) PhasesWaterBlue else MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = glassesTarget,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isCompleted) PhasesWaterBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = benefit,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
