package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleTurnCodec
import com.example.model.LocalCoupleQuestionEngine
import com.example.model.QuestionTheme
import com.example.model.RoundOutcome
import com.example.model.RoundSyncState
import com.example.model.SpiceLevel
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CoupleQuizGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean = false,
    onSaveState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val spice = runCatching { SpiceLevel.valueOf(room.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    val state = remember(room.quizStateJson) { CoupleTurnCodec.roundStateFromJson(room.quizStateJson) }
    val generated = remember(room.coupleCode, state.roundIndex, spice, state.history) {
        LocalCoupleQuestionEngine.generateQuizRound(
            coupleCode = room.coupleCode,
            roundIndex = state.roundIndex,
            baseSpice = spice,
            history = state.history
        )
    }

    SyncedChoiceRoundGame(
        title = "💋 Couple Quiz",
        promptText = generated.question.prompt,
        options = generated.question.options,
        theme = generated.theme,
        heatScore = generated.heatScore,
        adaptiveTag = if (generated.isAdaptiveFollowUp) generated.adaptiveTag else "",
        room = room,
        spice = spice,
        state = state,
        isQuiz = true,
        soundEnabled = soundEnabled,
        isLiveConnected = isLiveConnected,
        onSaveState = onSaveState,
        onSendLiveBlock = onSendLiveBlock,
        modifier = modifier
    )
}

@Composable
fun CoupleWyrGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean = false,
    onSaveState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val spice = runCatching { SpiceLevel.valueOf(room.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    val state = remember(room.wyrStateJson) { CoupleTurnCodec.roundStateFromJson(room.wyrStateJson) }
    val generated = remember(room.coupleCode, state.roundIndex, spice, state.history) {
        LocalCoupleQuestionEngine.generateWyrRound(
            coupleCode = room.coupleCode,
            roundIndex = state.roundIndex,
            baseSpice = spice,
            history = state.history
        )
    }

    SyncedChoiceRoundGame(
        title = "🔥 Would You Rather",
        promptText = "Which would you choose together?",
        options = listOf(generated.prompt.optionA, generated.prompt.optionB),
        theme = generated.theme,
        heatScore = generated.heatScore,
        adaptiveTag = if (generated.isAdaptiveFollowUp) generated.adaptiveTag else "",
        room = room,
        spice = spice,
        state = state,
        isQuiz = false,
        soundEnabled = soundEnabled,
        isLiveConnected = isLiveConnected,
        onSaveState = onSaveState,
        onSendLiveBlock = onSendLiveBlock,
        modifier = modifier
    )
}

@Composable
private fun SyncedChoiceRoundGame(
    title: String,
    promptText: String,
    options: List<String>,
    theme: QuestionTheme,
    heatScore: Int,
    adaptiveTag: String,
    room: CoupleRoomRecord,
    spice: SpiceLevel,
    state: RoundSyncState,
    isQuiz: Boolean,
    soundEnabled: Boolean,
    isLiveConnected: Boolean,
    onSaveState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val scope = rememberCoroutineScope()

    var passPhonePrivacyShield by remember(state.roundIndex, room.isPassAndPlay) {
        mutableStateOf(false)
    }

    val partnerDisplay = state.partnerName.ifBlank { room.partnerName.ifBlank { "Partner" } }
    val bothAnswered = state.myChoice != null && state.partnerChoice != null
    val isMatch = bothAnswered && state.myChoice == state.partnerChoice

    fun transmitLivePick(roundIdx: Int, choiceIdx: Int) {
        if (room.isPassAndPlay) return
        val code = room.coupleCode
        val sender = room.myName
        val prevHash = room.lastBlockHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(code).blockHash }
        val nextIndex = room.blockHeight
        val sharedSecret = room.ecdhSharedSecretB64
        scope.launch {
            val (packet, block) = withContext(Dispatchers.Default) {
                if (isQuiz) {
                    CoupleTurnCodec.encodeQuizTurnWithBlock(code, sender, roundIdx, choiceIdx, spice, prevHash, nextIndex, sharedSecret)
                } else {
                    CoupleTurnCodec.encodeWyrTurnWithBlock(code, sender, roundIdx, choiceIdx, spice, prevHash, nextIndex, sharedSecret)
                }
            }
            onSendLiveBlock(packet, block)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesRose.copy(alpha = 0.15f)) {
                Text(
                    text = "${spice.emoji} ${spice.title} · Round ${state.roundIndex + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PhasesRose,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Compact Chemistry Heat Bar (Local Adaptive Spice Meter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🔥 Heat $heatScore%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (heatScore >= 70) Color(0xFFE11D68) else PhasesRose
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(height = 6.dp, width = 100.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((heatScore / 100f).coerceIn(0.08f, 1f))
                        .size(height = 6.dp, width = 100.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(PhasesPlum, PhasesRose, Color(0xFFE11D68))
                            )
                        )
                )
            }
            Text(
                text = "${theme.emoji} ${theme.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Question / Dilemma Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, PhasesLavender.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .cute3DCard(maxTilt = 3f, cornerRadius = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(PhasesPlum.copy(alpha = 0.12f), Color.Transparent)))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (adaptiveTag.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFFE11D68).copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, Color(0xFFE11D68).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = adaptiveTag,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE11D68),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 28.sp
                    )
                )

                if (room.isPassAndPlay && passPhonePrivacyShield && !bothAnswered) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = PhasesPlum.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PhasesPlum.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🙈 ${room.myName}'s pick is hidden!", fontWeight = FontWeight.Bold)
                            Text(
                                "Pass the phone to $partnerDisplay so they can pick secretly.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { passPhonePrivacyShield = false },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)
                            ) {
                                Text("I'm $partnerDisplay — My Turn 💋")
                            }
                        }
                    }
                } else {
                    val pickingForPartner = room.isPassAndPlay && state.myChoice != null && state.partnerChoice == null
                    if (pickingForPartner) {
                        Text(
                            text = "Now picking: $partnerDisplay 💕",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PhasesPlum
                        )
                    }

                    options.forEachIndexed { idx, opt ->
                        val isMyPick = bothAnswered && state.myChoice == idx
                        val isPartnerPick = bothAnswered && state.partnerChoice == idx
                        val isLockedMyPick = !bothAnswered && !pickingForPartner && state.myChoice == idx

                        val borderColor = when {
                            isMyPick && isPartnerPick -> PhasesSage
                            isMyPick -> PhasesRose
                            isPartnerPick -> PhasesPlum
                            isLockedMyPick -> PhasesRose
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        val canTapOption = !bothAnswered && (room.isPassAndPlay || state.myChoice == null)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isLockedMyPick || isMyPick || isPartnerPick) borderColor.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.5.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .cuteBouncyClick(enabled = canTapOption) {
                                    if (!canTapOption) return@cuteBouncyClick
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (pickingForPartner) {
                                        val updated = state.copy(partnerChoice = idx, partnerName = partnerDisplay)
                                        val matched = updated.myChoice == idx
                                        if (matched) {
                                            if (soundEnabled) SoundHelper.playCuteChime(context)
                                            particleController.spawn(500f, 700f, listOf("💞", "💋", "🔥", "✨"), 18)
                                        }
                                        onSaveState(CoupleTurnCodec.roundStateToJson(updated), matched)
                                    } else {
                                        val updated = state.copy(myChoice = idx)
                                        val matched = updated.partnerChoice == idx
                                        if (matched) {
                                            if (soundEnabled) SoundHelper.playCuteChime(context)
                                            particleController.spawn(500f, 700f, listOf("💞", "💋", "🔥", "✨"), 18)
                                        }
                                        onSaveState(CoupleTurnCodec.roundStateToJson(updated), matched)
                                        if (room.isPassAndPlay && updated.partnerChoice == null) {
                                            passPhonePrivacyShield = true
                                        } else {
                                            transmitLivePick(state.roundIndex, idx)
                                        }
                                    }
                                }
                                .testTag("couple_choice_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                if (isLockedMyPick) {
                                    Text(
                                        "Locked ♥",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PhasesRose,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (bothAnswered) {
                                    val tag = when {
                                        isMyPick && isPartnerPick -> "💞 Both!"
                                        isMyPick -> "🌸 ${room.myName}"
                                        isPartnerPick -> "🌙 $partnerDisplay"
                                        else -> ""
                                    }
                                    if (tag.isNotBlank()) {
                                        Text(
                                            tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = borderColor,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Simultaneous reveal celebration or minimal live status cue
        if (bothAnswered) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isMatch) PhasesSage.copy(alpha = 0.16f) else PhasesRose.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, if (isMatch) PhasesSage else PhasesRose),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isMatch) "💞 Perfect Match! +1 Couple Streak" else "✨ Opposite Vibes — Talk About It!",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isMatch) PhasesSage else PhasesRose,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            val myPick = state.myChoice ?: 0
                            val partnerPick = state.partnerChoice ?: 0
                            // Order-independent choice signature so Phone A & Phone B generate identical follow-ups
                            val symmetricSig = minOf(myPick, partnerPick) * 10 + maxOf(myPick, partnerPick)
                            val outcome = RoundOutcome(
                                roundIndex = state.roundIndex,
                                theme = theme,
                                matched = isMatch,
                                choiceSignature = symmetricSig,
                                spiceLevel = spice
                            )
                            val updatedHistory = (state.history.filterNot { it.roundIndex == state.roundIndex } + outcome)
                                .takeLast(12)
                            val next = RoundSyncState(
                                roundIndex = state.roundIndex + 1,
                                partnerName = partnerDisplay,
                                history = updatedHistory
                            )
                            onSaveState(CoupleTurnCodec.roundStateToJson(next), false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("next_couple_round_btn")
                    ) {
                        Text("Next Round →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (!room.isPassAndPlay) {
            val statusText = when {
                state.myChoice != null && state.partnerChoice == null ->
                    "Pick sent privately — waiting for $partnerDisplay to answer…"
                state.myChoice == null && state.partnerChoice != null ->
                    "$partnerDisplay locked in — pick yours to reveal both 🔥"
                isLiveConnected ->
                    "Live with $partnerDisplay · Tap an answer to lock in"
                else ->
                    "Pick an answer — it syncs live once $partnerDisplay joins #${room.coupleCode}"
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (state.partnerChoice != null) PhasesRose.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLiveConnected) PhasesSage else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (state.partnerChoice != null) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private encrypted turn",
                        tint = PhasesRose.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
