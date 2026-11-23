package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserGender
import com.example.ui.components.FloatingPetalsBackground
import com.example.ui.components.formatFriendlyDate
import com.example.ui.theme.MaleBorder
import com.example.ui.theme.MaleCobalt
import com.example.ui.theme.MaleEmerald
import com.example.ui.theme.MaleInk
import com.example.ui.theme.MaleInkMuted
import com.example.ui.theme.MaleObsidian
import com.example.ui.theme.MaleSky
import com.example.ui.theme.MaleSurface
import com.example.ui.theme.MaleSurfaceElevated
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import java.util.Calendar

@Composable
fun OnboardingScreen(
    onComplete: (
        name: String,
        gender: String,
        lastPeriodMillis: Long,
        cycleLength: Int,
        periodLength: Int
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedGender by remember { mutableStateOf<UserGender?>(null) }
    var name by remember { mutableStateOf("") }
    var cycleLength by remember { mutableIntStateOf(28) }
    var periodLength by remember { mutableIntStateOf(5) }

    val defaultLastPeriodCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -14)
        }
    }
    var lastPeriodDateMillis by remember { mutableStateOf(defaultLastPeriodCal.timeInMillis) }

    val isMale = selectedGender == UserGender.MALE
    val bgColor = if (isMale) MaleObsidian else MaterialTheme.colorScheme.background
    val cardColor = if (isMale) MaleSurface else MaterialTheme.colorScheme.surface
    val titleColor = if (isMale) MaleInk else MaterialTheme.colorScheme.onBackground
    val bodyColor = if (isMale) MaleInkMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isMale) MaleBorder else PhasesRose.copy(alpha = 0.25f)

    fun showDatePicker() {
        val pickerCal = Calendar.getInstance().apply { timeInMillis = lastPeriodDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance()
                chosen.set(year, month, dayOfMonth, 0, 0, 0)
                lastPeriodDateMillis = chosen.timeInMillis
            },
            pickerCal.get(Calendar.YEAR),
            pickerCal.get(Calendar.MONTH),
            pickerCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!isMale) {
            FloatingPetalsBackground()
        }

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Brand Crest (adapts to Male vs Female choice)
            if (isMale) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MaleCobalt.copy(alpha = 0.26f), MaleSurfaceElevated)
                            )
                        )
                        .border(1.5.dp, MaleCobalt.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "KriMoon",
                        tint = MaleSky,
                        modifier = Modifier.size(38.dp)
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        PhasesRose.copy(alpha = 0.28f),
                                        PhasesLavender.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(PhasesRose.copy(alpha = 0.65f), PhasesPlum.copy(alpha = 0.45f))),
                                CircleShape
                            )
                    ) {
                        Text(text = "🌙", fontSize = 38.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isMale) "Welcome to KriMoon" else "Welcome to KriMoon 🌙",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (selectedGender) {
                    UserGender.MALE -> "Stay synced with her cycle, know how to support her, and unwind in Arcade & Couple Lounge."
                    UserGender.FEMALE -> "Lunar cycle intelligence, daily care rituals & encrypted couple sync — private by default ✨"
                    null -> "Choose your profile to tailor your experience. You can switch anytime in Settings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mandatory First-Launch Gender Question (Female / Male — no "Partner Mode" option)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Are you Female or Male?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = titleColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GenderOptionCard(
                            label = "Female",
                            subtitle = "Cycle & Care UI",
                            icon = Icons.Default.Female,
                            selected = selectedGender == UserGender.FEMALE,
                            accent = PhasesPlum,
                            isDarkCanvas = isMale,
                            onClick = { selectedGender = UserGender.FEMALE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_gender_female")
                        )
                        GenderOptionCard(
                            label = "Male",
                            subtitle = "Care Brief & Play",
                            icon = Icons.Default.Male,
                            selected = selectedGender == UserGender.MALE,
                            accent = MaleCobalt,
                            isDarkCanvas = isMale,
                            onClick = { selectedGender = UserGender.MALE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_gender_male")
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedGender != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // Name Card (shared, styled per gender)
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = if (isMale) "Your Name" else "🎀 What should I call you?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = {
                                    Text(
                                        text = if (isMale) "Enter your name" else "Your preferred name or nickname",
                                        color = bodyColor
                                    )
                                },
                                singleLine = true,
                                colors = if (isMale) {
                                    OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaleInk,
                                        unfocusedTextColor = MaleInk,
                                        focusedBorderColor = MaleCobalt,
                                        unfocusedBorderColor = MaleBorder,
                                        cursorColor = MaleSky
                                    )
                                } else {
                                    OutlinedTextFieldDefaults.colors()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_name_input")
                            )
                        }
                    }

                    if (isMale) {
                        // Male 3-Page Overview Card
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaleSurface),
                            border = BorderStroke(1.dp, MaleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Your 3-Page Setup",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaleInk
                                )
                                MaleOnboardingBullet("1. Partner Brief — Live cycle phase, care playbook & next-phase prep")
                                MaleOnboardingBullet("2. Offline Arcade — 8 instant offline mini-games")
                                MaleOnboardingBullet("3. Couple Lounge — Encrypted pairing, co-op games & live nudges")
                            }
                        }
                    } else {
                        // Female Step 2: Last Period Date
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PhasesLavender.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "🗓️ First day of your last period",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Used to calibrate your current lunar cycle phase ✨",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { showDatePicker() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("onboarding_date_picker")
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatFriendlyDate(lastPeriodDateMillis),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Female Step 3: Cycle & Period Steppers
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PhasesRose.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "🌙 Typical Cycle Rhythm",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "You can customize or let it gently adapt over time 🌸",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                StepperRow(
                                    label = "Cycle length",
                                    value = "$cycleLength days",
                                    onDecrement = { if (cycleLength > 20) cycleLength-- },
                                    onIncrement = { if (cycleLength < 50) cycleLength++ }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                StepperRow(
                                    label = "Period length",
                                    value = "$periodLength days",
                                    onDecrement = { if (periodLength > 2) periodLength-- },
                                    onIncrement = { if (periodLength < 12) periodLength++ }
                                )
                            }
                        }
                    }

                    // Privacy-by-Default Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isMale) MaleSurfaceElevated else PhasesRose.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isMale) MaleBorder else PhasesRose.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Private on device",
                                tint = if (isMale) MaleSky else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMale) {
                                    "Private by default · Live partner sync uses end-to-end ECDH-P256 + AES-256-GCM encryption."
                                } else {
                                    "🩷 Private by default · Health logs & alerts stay encrypted locally on your phone."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = bodyColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Complete Onboarding Button
                    val primaryBtnColor = if (isMale) MaleCobalt else PhasesPlum
                    Button(
                        onClick = {
                            val chosenGender = selectedGender ?: return@Button
                            onComplete(
                                name.trim(),
                                chosenGender.storageValue,
                                lastPeriodDateMillis,
                                cycleLength,
                                periodLength
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryBtnColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = primaryBtnColor.copy(alpha = 0.4f))
                            .testTag("onboarding_complete_button")
                    ) {
                        Text(
                            text = if (isMale) "Continue" else "Begin Your Journey 🌸✨",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun GenderOptionCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    isDarkCanvas: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        selected -> accent.copy(alpha = if (isDarkCanvas) 0.24f else 0.14f)
        isDarkCanvas -> MaleSurfaceElevated
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val strokeColor = if (selected) accent else if (isDarkCanvas) MaleBorder else MaterialTheme.colorScheme.outlineVariant
    val primaryTextColor = if (isDarkCanvas) MaleInk else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDarkCanvas) MaleInkMuted else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, strokeColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (selected) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) accent else secondaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = primaryTextColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MaleOnboardingBullet(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaleEmerald,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = MaleInk
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
            }

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}
