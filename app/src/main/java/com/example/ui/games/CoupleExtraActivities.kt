package com.example.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.model.CoupleActivitiesDeck
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleTurnCodec
import com.example.model.DareSyncState
import com.example.model.SecretWhisperNote
import com.example.model.SpiceLevel
import com.example.model.WavelengthSyncState
import com.example.notifications.SoundHelper
import com.example.ui.components.AestheticHaptics
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ActVelvetTop = Color(0xFF1A0E1B)
private val ActVelvetBottom = Color(0xFF2D1225)
private val ActVelvetSurface = Color(0xFF231120)
private val ActVelvetTrack = Color(0xFF140A14)
private val ActRoseGlow = Color(0xFFE11D68)
private val ActVioletGlow = Color(0xFFA855F7)
private val ActChampagne = Color(0xFFFDE68A)
private val ActBorder = Color(0x52F472B6)
private val ActTextPrimary = Color(0xFFFFF5F8)
private val ActTextMuted = Color(0xFFD8B8C8)
private val ActEmerald = Color(0xFF34D399)

// =========================================================================
// 1. VELVET TRUTH OR DARE
// =========================================================================
@Composable
fun CoupleTruthOrDareGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean,
    onSaveState: (stateJson: String, bumpStreak: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spiceLevel = remember(room.spiceLevel) {
        runCatching { SpiceLevel.valueOf(room.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    }

    var state by remember(room.coupleCode, room.dareStateJson) {
        mutableStateOf(CoupleActivitiesDeck.dareStateFromJson(room.dareStateJson))
    }

    fun emitDareUpdate(next: DareSyncState, bumpStreak: Boolean = false) {
        state = next
        onSaveState(CoupleActivitiesDeck.dareStateToJson(next), bumpStreak)
        if (!room.isPassAndPlay) {
            val sharedSecret = room.ecdhSharedSecretB64
            scope.launch {
                val (packet, block) = withContext(Dispatchers.Default) {
                    CoupleTurnCodec.encodeDareTurnWithBlock(
                        coupleCode = room.coupleCode,
                        senderName = room.myName,
                        state = next,
                        previousHash = room.lastBlockHash.ifBlank { CoupleBlockchain.genesisHash(room.coupleCode) },
                        blockIndex = room.blockHeight.coerceAtLeast(1),
                        ecdhSharedSecretB64 = sharedSecret
                    )
                }
                onSendLiveBlock(packet, block)
            }
        }
    }

    fun drawCard(isDare: Boolean) {
        AestheticHaptics.crispClick(context, haptic)
        val pool = CoupleActivitiesDeck.daresForSpice(spiceLevel, isDare)
        val filtered = pool.filter { it.id != state.promptId }.ifEmpty { pool }
        val picked = filtered[(state.roundIndex + Random.nextInt(filtered.size)) % filtered.size]
        val next = DareSyncState(
            roundIndex = state.roundIndex + 1,
            isDare = isDare,
            promptId = picked.id,
            promptText = picked.text,
            challengerName = room.myName.ifBlank { "Me" },
            status = "CHALLENGED",
            completedCount = state.completedCount
        )
        emitDareUpdate(next)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ActVelvetSurface,
                border = BorderStroke(1.dp, ActBorder)
            ) {
                Text(
                    text = "${spiceLevel.emoji} ${spiceLevel.title} Deck",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ActTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ActRoseGlow.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, ActRoseGlow.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "✨ ${state.completedCount} completed",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ActRoseGlow,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Mode Picker: Truth vs Dare
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ActVelvetSurface,
                border = BorderStroke(
                    width = if (!state.isDare && state.promptText.isNotBlank()) 1.5.dp else 1.dp,
                    color = if (!state.isDare && state.promptText.isNotBlank()) ActVioletGlow else ActBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .cute3DCard(maxTilt = 3f, cornerRadius = 20.dp)
                    .cuteBouncyClick(onClick = { drawCard(isDare = false) })
                    .testTag("draw_truth_button")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(ActVioletGlow.copy(alpha = 0.24f), Color.Transparent)
                            )
                        )
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("💋", fontSize = 24.sp)
                    Text(
                        text = "Draw Truth",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = ActTextPrimary
                    )
                    Text(
                        text = "Honest & intimate",
                        style = MaterialTheme.typography.labelSmall,
                        color = ActTextMuted
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ActVelvetSurface,
                border = BorderStroke(
                    width = if (state.isDare && state.promptText.isNotBlank()) 1.5.dp else 1.dp,
                    color = if (state.isDare && state.promptText.isNotBlank()) ActRoseGlow else ActBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .cute3DCard(maxTilt = 3f, cornerRadius = 20.dp)
                    .cuteBouncyClick(onClick = { drawCard(isDare = true) })
                    .testTag("draw_dare_button")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(ActRoseGlow.copy(alpha = 0.26f), Color.Transparent)
                            )
                        )
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🔥", fontSize = 24.sp)
                    Text(
                        text = "Draw Dare",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = ActTextPrimary
                    )
                    Text(
                        text = "Bold & playful",
                        style = MaterialTheme.typography.labelSmall,
                        color = ActTextMuted
                    )
                }
            }
        }

        // Main 3D Velvet Prompt Card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = ActVelvetTop),
            border = BorderStroke(
                1.2.dp,
                if (state.status == "COMPLETED") ActEmerald.copy(alpha = 0.65f) else ActBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .cute3DCard(maxTilt = 3.5f, cornerRadius = 28.dp)
                .testTag("dare_active_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(ActVelvetTop, ActVelvetBottom)))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (state.isDare) ActRoseGlow else ActVioletGlow).copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            radius = 520f
                        )
                    )
                    .padding(24.dp)
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "dare_card_anim"
                ) { current ->
                    if (current.promptText.isBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🎲", fontSize = 36.sp)
                            Text(
                                text = "Tap Draw Truth or Draw Dare",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = ActTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isLiveConnected) {
                                    "Your card appears live on ${room.partnerName.ifBlank { "your partner" }}'s phone instantly."
                                } else {
                                    "Take turns drawing cards and marking each challenge complete."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ActTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = (if (current.isDare) ActRoseGlow else ActVioletGlow).copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = if (current.isDare) "🔥 DARE · #${current.roundIndex}" else "💋 TRUTH · #${current.roundIndex}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = ActTextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                                if (current.challengerName.isNotBlank()) {
                                    Text(
                                        text = "From ${current.challengerName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ActTextMuted
                                    )
                                }
                            }

                            Text(
                                text = current.promptText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = 31.sp
                                ),
                                color = ActTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            if (current.status == "COMPLETED") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ActEmerald.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, ActEmerald.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "✓ Completed! Draw the next card ♥",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = ActEmerald,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { drawCard(current.isDare) },
                                        border = BorderStroke(1.dp, ActBorder),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("dare_swap_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Swap card",
                                            tint = ActTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Swap", color = ActTextPrimary)
                                    }

                                    Button(
                                        onClick = {
                                            AestheticHaptics.warmConfirm(context, haptic)
                                            if (soundEnabled) {
                                                SoundHelper.playCuteChime(context)
                                            }
                                            emitDareUpdate(
                                                current.copy(
                                                    status = "COMPLETED",
                                                    completedCount = current.completedCount + 1
                                                ),
                                                bumpStreak = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ActRoseGlow,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(48.dp)
                                            .testTag("dare_complete_button")
                                    ) {
                                        Text(
                                            text = "Completed ♥",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// =========================================================================
// 2. HEART WAVELENGTH (Telepathy Dial)
// =========================================================================
@Composable
fun CoupleWavelengthGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean,
    onSaveState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spiceLevel = remember(room.spiceLevel) {
        runCatching { SpiceLevel.valueOf(room.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    }

    var state by remember(room.coupleCode, room.wavelengthStateJson) {
        mutableStateOf(CoupleActivitiesDeck.wavelengthStateFromJson(room.wavelengthStateJson))
    }
    var clueInput by remember(state.roundIndex) { mutableStateOf("") }
    var dialGuess by remember(state.roundIndex, state.phase) {
        mutableFloatStateOf(state.guessPos ?: 0.5f)
    }

    // Initialize and persist first target smoothly if brand new
    LaunchedEffect(room.coupleCode) {
        if (state.roundIndex == 0 && state.clueWord.isBlank() && state.phase == "CLUE" && state.targetPos == 0.5f) {
            val initialTarget = 0.18f + Random.nextFloat() * 0.64f
            val initialized = state.copy(targetPos = initialTarget)
            state = initialized
            onSaveState(CoupleActivitiesDeck.wavelengthStateToJson(initialized), false)
        }
    }

    val spectrum = remember(state.spectrumIndex, spiceLevel) {
        CoupleActivitiesDeck.spectrumFor(state.spectrumIndex, spiceLevel)
    }
    val iAmClueAuthor = state.clueAuthor.equals(room.myName.trim(), ignoreCase = true)

    fun emitWavelength(next: WavelengthSyncState, bumpMatch: Boolean = false) {
        state = next
        onSaveState(CoupleActivitiesDeck.wavelengthStateToJson(next), bumpMatch)
        if (!room.isPassAndPlay) {
            val sharedSecret = room.ecdhSharedSecretB64
            scope.launch {
                val (packet, block) = withContext(Dispatchers.Default) {
                    CoupleTurnCodec.encodeWavelengthTurnWithBlock(
                        coupleCode = room.coupleCode,
                        senderName = room.myName,
                        state = next,
                        previousHash = room.lastBlockHash.ifBlank { CoupleBlockchain.genesisHash(room.coupleCode) },
                        blockIndex = room.blockHeight.coerceAtLeast(1),
                        ecdhSharedSecretB64 = sharedSecret
                    )
                }
                onSendLiveBlock(packet, block)
            }
        }
    }

    val showTargetWedge = state.phase == "CLUE" ||
        state.phase == "REVEAL" ||
        (state.phase == "GUESS" && iAmClueAuthor && !room.isPassAndPlay)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Scoreboard strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ActVelvetSurface,
                border = BorderStroke(1.dp, ActBorder)
            ) {
                Text(
                    text = "🌊 Round ${state.roundIndex + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ActTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ActVioletGlow.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, ActVioletGlow.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "✨ ${state.totalSyncPoints} sync pts",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFD8B4FE),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Main 3D Dial Card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = ActVelvetTop),
            border = BorderStroke(1.2.dp, ActBorder),
            modifier = Modifier
                .fillMaxWidth()
                .cute3DCard(maxTilt = 2.5f, cornerRadius = 28.dp)
                .testTag("wavelength_dial_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(ActVelvetTop, ActVelvetBottom)))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Spectrum Poles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(spectrum.leftEmoji, fontSize = 20.sp)
                        Text(
                            text = spectrum.leftLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF9A8D4),
                            maxLines = 2
                        )
                    }
                    Text(
                        text = "↔",
                        color = ActTextMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(spectrum.rightEmoji, fontSize = 20.sp)
                        Text(
                            text = spectrum.rightLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD8B4FE),
                            textAlign = TextAlign.End,
                            maxLines = 2
                        )
                    }
                }

                // Semicircular Glowing Wavelength Dial Canvas
                WavelengthDialCanvas(
                    targetPos = state.targetPos,
                    guessPos = if (state.phase == "REVEAL") (state.guessPos ?: dialGuess) else dialGuess,
                    showTarget = showTargetWedge,
                    showNeedle = state.phase == "GUESS" || state.phase == "REVEAL",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                )

                // Phase-Specific Controls
                when (state.phase) {
                    "CLUE" -> {
                        Text(
                            text = "You see the hidden sweet spot above.\nGive a 1–3 word clue that lands right there:",
                            style = MaterialTheme.typography.bodySmall,
                            color = ActTextMuted,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = clueInput,
                            onValueChange = { if (it.length <= 36) clueInput = it },
                            label = { Text("Your Clue") },
                            placeholder = { Text("e.g. Rainy Paris, Slow jazz…", color = ActTextMuted.copy(alpha = 0.5f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ActTextPrimary,
                                unfocusedTextColor = ActTextPrimary,
                                focusedBorderColor = Color(0xFFF472B6),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedLabelColor = Color(0xFFF9A8D4),
                                unfocusedLabelColor = ActTextMuted,
                                cursorColor = ActRoseGlow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wavelength_clue_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    AestheticHaptics.softTick(context, haptic)
                                    val next = state.copy(
                                        spectrumIndex = state.spectrumIndex + 1,
                                        targetPos = 0.16f + Random.nextFloat() * 0.68f
                                    )
                                    state = next
                                    onSaveState(CoupleActivitiesDeck.wavelengthStateToJson(next), false)
                                },
                                border = BorderStroke(1.dp, ActBorder),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("New Axis", color = ActTextPrimary)
                            }
                            Button(
                                onClick = {
                                    val clean = clueInput.trim()
                                    if (clean.isBlank()) return@Button
                                    AestheticHaptics.gentleThud(context, haptic)
                                    emitWavelength(
                                        state.copy(
                                            clueWord = clean,
                                            clueAuthor = room.myName.ifBlank { "Me" },
                                            guessPos = null,
                                            phase = "GUESS"
                                        )
                                    )
                                },
                                enabled = clueInput.trim().isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActRoseGlow,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                                    .testTag("wavelength_send_clue_btn")
                            ) {
                                Text(
                                    text = if (room.isPassAndPlay) "Hide & Pass ♥" else "Send Clue ♥",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    "GUESS" -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, ActBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${state.clueAuthor.ifBlank { "Partner" }}'s Clue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ActTextMuted
                                )
                                Text(
                                    text = "“${state.clueWord}”",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    color = ActChampagne
                                )
                            }
                        }

                        if (iAmClueAuthor && !room.isPassAndPlay) {
                            Text(
                                text = "Waiting for ${room.partnerName.ifBlank { "your partner" }} to tune the dial…",
                                style = MaterialTheme.typography.bodySmall,
                                color = ActTextMuted,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Slider(
                                value = dialGuess,
                                onValueChange = { dialGuess = it },
                                valueRange = 0.04f..0.96f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF9A8D4),
                                    activeTrackColor = ActRoseGlow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wavelength_dial_slider")
                            )

                            Button(
                                onClick = {
                                    val delta = abs(dialGuess - state.targetPos)
                                    val pts = when {
                                        delta <= 0.06f -> 100
                                        delta <= 0.13f -> 60
                                        delta <= 0.22f -> 30
                                        else -> 10
                                    }
                                    AestheticHaptics.warmConfirm(context, haptic)
                                    if (soundEnabled && pts >= 60) {
                                        SoundHelper.playCuteChime(context)
                                    }
                                    emitWavelength(
                                        state.copy(
                                            guessPos = dialGuess,
                                            guesserName = if (room.isPassAndPlay) room.partnerName.ifBlank { "Partner" } else room.myName,
                                            phase = "REVEAL",
                                            lastScore = pts,
                                            totalSyncPoints = state.totalSyncPoints + pts,
                                            roundsPlayed = state.roundsPlayed + 1
                                        ),
                                        bumpMatch = pts >= 60
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActRoseGlow,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("wavelength_lock_guess_btn")
                            ) {
                                Text("Lock In Wavelength ✨", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        // REVEAL
                        val verdict = when {
                            state.lastScore >= 100 -> "🎯 Bullseye Telepathy! +100 pts"
                            state.lastScore >= 60 -> "💞 Same Frequency! +60 pts"
                            state.lastScore >= 30 -> "✨ Close Wavelength! +30 pts"
                            else -> "🌙 Plot Twist! +10 pts"
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = (if (state.lastScore >= 60) ActEmerald else ActVioletGlow).copy(alpha = 0.18f),
                            border = BorderStroke(
                                1.dp,
                                (if (state.lastScore >= 60) ActEmerald else ActVioletGlow).copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = verdict,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ActTextPrimary
                                )
                                Text(
                                    text = "Clue: “${state.clueWord}”",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ActTextMuted
                                )
                            }
                        }

                        Button(
                            onClick = {
                                AestheticHaptics.crispClick(context, haptic)
                                clueInput = ""
                                dialGuess = 0.5f
                                emitWavelength(
                                    state.copy(
                                        roundIndex = state.roundIndex + 1,
                                        spectrumIndex = state.spectrumIndex + 1,
                                        targetPos = 0.16f + Random.nextFloat() * 0.68f,
                                        clueWord = "",
                                        clueAuthor = "",
                                        guessPos = null,
                                        guesserName = "",
                                        phase = "CLUE",
                                        lastScore = 0
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ActRoseGlow,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wavelength_next_round_btn")
                        ) {
                            Text("Next Spectrum →", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WavelengthDialCanvas(
    targetPos: Float,
    guessPos: Float,
    showTarget: Boolean,
    showNeedle: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeW = size.minDimension * 0.16f
        val radius = (minOf(size.width * 0.45f, size.height - strokeW * 0.85f)).coerceAtLeast(60f)
        val center = Offset(size.width / 2f, size.height - strokeW * 0.55f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Base frosted arc track
        drawArc(
            color = Color.White.copy(alpha = 0.11f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Subtle gradient glow along track
        drawArc(
            brush = Brush.horizontalGradient(
                listOf(Color(0x55F472B6), Color(0x55A855F7), Color(0x55E11D68))
            ),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeW * 0.35f, cap = StrokeCap.Round)
        )

        // Target zone wedges (when visible) — strictly clamped to 180f..360f semicircle
        if (showTarget) {
            val targetAngle = 180f + (targetPos.coerceIn(0.08f, 0.92f) * 180f)
            fun drawClampedBand(color: Color, halfSpan: Float) {
                val start = (targetAngle - halfSpan).coerceIn(180f, 360f)
                val end = (targetAngle + halfSpan).coerceIn(180f, 360f)
                val sweep = (end - start).coerceAtLeast(0f)
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                }
            }
            // Outer 30-pt band
            drawClampedBand(Color(0x88A855F7), 24f)
            // Mid 60-pt band
            drawClampedBand(Color(0xCCE11D68), 13f)
            // Bullseye 100-pt champagne core
            drawClampedBand(ActChampagne, 5f)
        }

        // Luminous guess needle
        if (showNeedle) {
            val angleDeg = 180.0 + (guessPos.coerceIn(0f, 1f) * 180.0)
            val rad = angleDeg * PI / 180.0
            val tip = Offset(
                x = center.x + (cos(rad) * (radius + strokeW * 0.2f)).toFloat(),
                y = center.y + (sin(rad) * (radius + strokeW * 0.2f)).toFloat()
            )
            drawLine(
                color = Color(0x66F9A8D4),
                start = center,
                end = tip,
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = center,
                end = tip,
                strokeWidth = 4.5f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = ActRoseGlow,
                radius = 11f,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = center
            )
        }
    }
}

// =========================================================================
// 3. DATE MATCH & SECRET WHISPERS
// =========================================================================
@Composable
fun CoupleDateWhispersGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean,
    onSaveState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spiceLevel = remember(room.spiceLevel) {
        runCatching { SpiceLevel.valueOf(room.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Date Match, 1 = Secret Whispers
    var state by remember(room.coupleCode, room.whisperStateJson) {
        mutableStateOf(CoupleActivitiesDeck.dateWhisperStateFromJson(room.whisperStateJson))
    }
    var passAndPlayPartnerTurn by remember { mutableStateOf(false) }
    var partnerSkippedIds by remember(room.coupleCode) { mutableStateOf(emptySet<String>()) }
    var whisperInput by remember { mutableStateOf("") }

    val orderedIdeas = remember(spiceLevel) {
        val primary = CoupleActivitiesDeck.dateNightIdeas.filter { it.spiceLevel == spiceLevel }
        val rest = CoupleActivitiesDeck.dateNightIdeas.filter { it.spiceLevel != spiceLevel }
        primary + rest
    }

    fun sendWhisperBlock(likedDateId: String = "", note: SecretWhisperNote? = null) {
        if (!room.isPassAndPlay) {
            val sharedSecret = room.ecdhSharedSecretB64
            scope.launch {
                val (packet, block) = withContext(Dispatchers.Default) {
                    CoupleTurnCodec.encodeWhisperTurnWithBlock(
                        coupleCode = room.coupleCode,
                        senderName = room.myName,
                        likedDateId = likedDateId,
                        whisperNote = note,
                        previousHash = room.lastBlockHash.ifBlank { CoupleBlockchain.genesisHash(room.coupleCode) },
                        blockIndex = room.blockHeight.coerceAtLeast(1),
                        ecdhSharedSecretB64 = sharedSecret
                    )
                }
                onSendLiveBlock(packet, block)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Inner 2-Tab Velvet Pill Bar
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = ActVelvetTrack,
            border = BorderStroke(1.dp, ActBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("🌹 Date Match", "💌 Secret Whispers").forEachIndexed { idx, label ->
                    val selected = activeTab == idx
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) ActRoseGlow else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                AestheticHaptics.softTick(context, haptic)
                                activeTab = idx
                            }
                            .testTag("date_whispers_tab_$idx")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) Color.White else ActTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        if (activeTab == 0) {
            // === DATE MATCH ===
            if (room.isPassAndPlay) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = ActVelvetSurface,
                        border = BorderStroke(1.dp, ActBorder),
                        modifier = Modifier.clickable {
                            AestheticHaptics.softTick(context, haptic)
                            passAndPlayPartnerTurn = !passAndPlayPartnerTurn
                        }
                    ) {
                        Text(
                            text = if (passAndPlayPartnerTurn) {
                                "Voting as: ${room.partnerName.ifBlank { "Partner" }} · Tap to switch"
                            } else {
                                "Voting as: ${room.myName.ifBlank { "Me" }} · Tap to switch"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFF9A8D4),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            val remaining = if (room.isPassAndPlay && passAndPlayPartnerTurn) {
                orderedIdeas.filter { it.id !in state.partnerLikedIds && it.id !in partnerSkippedIds }
            } else {
                orderedIdeas.filter { it.id !in state.myLikedIds && it.id !in state.mySkippedIds }
            }
            val currentIdea = remaining.firstOrNull()

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = ActVelvetTop),
                border = BorderStroke(1.2.dp, ActBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .cute3DCard(maxTilt = 3.5f, cornerRadius = 28.dp)
                    .testTag("date_match_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(ActVelvetTop, ActVelvetBottom)))
                        .padding(24.dp)
                ) {
                    if (currentIdea == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("✨", fontSize = 34.sp)
                            Text(
                                text = "You've voted on every date idea!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = ActTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = {
                                    AestheticHaptics.softTick(context, haptic)
                                    if (room.isPassAndPlay && passAndPlayPartnerTurn) {
                                        partnerSkippedIds = emptySet()
                                    } else {
                                        val reset = state.copy(mySkippedIds = emptySet())
                                        state = reset
                                        onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(reset), false)
                                    }
                                },
                                border = BorderStroke(1.dp, ActBorder),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Review Skipped Ideas", color = ActTextPrimary)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "${currentIdea.spiceLevel.emoji} ${currentIdea.spiceLevel.title} · ${remaining.size} left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ActTextMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Text(currentIdea.emoji, fontSize = 44.sp)

                            Text(
                                text = currentIdea.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = ActTextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = currentIdea.vibeNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ActTextMuted,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AestheticHaptics.softTick(context, haptic)
                                        if (room.isPassAndPlay && passAndPlayPartnerTurn) {
                                            partnerSkippedIds = partnerSkippedIds + currentIdea.id
                                        } else {
                                            val next = state.copy(mySkippedIds = state.mySkippedIds + currentIdea.id)
                                            state = next
                                            onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(next), false)
                                        }
                                    },
                                    border = BorderStroke(1.dp, ActBorder),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("date_skip_btn")
                                ) {
                                    Text("Pass", color = ActTextMuted, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        AestheticHaptics.warmConfirm(context, haptic)
                                        val next = if (room.isPassAndPlay && passAndPlayPartnerTurn) {
                                            state.copy(partnerLikedIds = state.partnerLikedIds + currentIdea.id)
                                        } else {
                                            state.copy(myLikedIds = state.myLikedIds + currentIdea.id)
                                        }
                                        val isNewMatch = currentIdea.id in next.matchedIds &&
                                            currentIdea.id !in state.matchedIds
                                        if (isNewMatch && soundEnabled) {
                                            SoundHelper.playCuteChime(context)
                                        }
                                        state = next
                                        onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(next), isNewMatch)
                                        if (!room.isPassAndPlay) {
                                            sendWhisperBlock(likedDateId = currentIdea.id)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ActRoseGlow,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(50.dp)
                                        .testTag("date_love_btn")
                                ) {
                                    Text("Love It ♥", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Matched Dates List
            val matchedIdeas = CoupleActivitiesDeck.dateNightIdeas.filter { it.id in state.matchedIds }
            if (matchedIdeas.isNotEmpty()) {
                Text(
                    text = "💞 Matched Date Nights (${matchedIdeas.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
                matchedIdeas.forEach { idea ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ActVelvetSurface,
                        border = BorderStroke(1.dp, ActRoseGlow.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .cute3DCard(maxTilt = 2f, cornerRadius = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(idea.emoji, fontSize = 26.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = idea.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ActTextPrimary
                                )
                                Text(
                                    text = idea.vibeNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ActTextMuted
                                )
                            }
                            Text("♥", color = ActRoseGlow, fontSize = 18.sp)
                        }
                    }
                }
            }
        } else {
            // === SECRET WHISPERS ===
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = ActVelvetTop),
                border = BorderStroke(1.dp, ActBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .cute3DCard(maxTilt = 2.5f, cornerRadius = 26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(ActVelvetTop, ActVelvetBottom)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Send an Encrypted Velvet Whisper",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = ActTextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoupleActivitiesDeck.quickWhisperStarters.forEach { starter ->
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(0.8.dp, ActBorder),
                                modifier = Modifier.clickable {
                                    AestheticHaptics.softTick(context, haptic)
                                    whisperInput = starter
                                }
                            ) {
                                Text(
                                    text = starter,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF9A8D4),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = whisperInput,
                        onValueChange = { if (it.length <= 240) whisperInput = it },
                        label = { Text("Secret note for ${room.partnerName.ifBlank { "your partner" }}") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ActTextPrimary,
                            unfocusedTextColor = ActTextPrimary,
                            focusedBorderColor = Color(0xFFF472B6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            focusedLabelColor = Color(0xFFF9A8D4),
                            unfocusedLabelColor = ActTextMuted,
                            cursorColor = ActRoseGlow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("whisper_message_input")
                    )

                    Button(
                        onClick = {
                            val clean = whisperInput.trim()
                            if (clean.isBlank()) return@Button
                            AestheticHaptics.warmConfirm(context, haptic)
                            val note = SecretWhisperNote(
                                id = "w_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
                                senderName = room.myName.ifBlank { "Me" },
                                text = clean,
                                timestamp = System.currentTimeMillis(),
                                isRevealed = !room.isPassAndPlay
                            )
                            val next = state.copy(whispers = (state.whispers + note).takeLast(24))
                            state = next
                            whisperInput = ""
                            onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(next), false)
                            sendWhisperBlock(note = note)
                        },
                        enabled = whisperInput.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActRoseGlow,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("whisper_send_btn")
                    ) {
                        Text(
                            text = if (isLiveConnected) "Seal & Send Live 💌" else "Seal Whisper 💌",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Whisper Envelope Vault
            val whispers = state.whispers.reversed()
            if (whispers.isEmpty()) {
                Text(
                    text = "No sealed whispers yet — send the first velvet note above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                whispers.forEach { note ->
                    val isMine = note.senderName.equals(room.myName.trim(), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ActVelvetSurface,
                        border = BorderStroke(
                            1.dp,
                            if (note.isRevealed) ActBorder else ActRoseGlow.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .cute3DCard(maxTilt = 2.2f, cornerRadius = 20.dp)
                            .cuteBouncyClick(
                                onClick = {
                                    if (!note.isRevealed) {
                                        AestheticHaptics.warmConfirm(context, haptic)
                                        val updated = state.copy(
                                            whispers = state.whispers.map {
                                                if (it.id == note.id) it.copy(isRevealed = true) else it
                                            }
                                        )
                                        state = updated
                                        onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(updated), false)
                                    }
                                }
                            )
                            .testTag("whisper_envelope_${note.id}")
                    ) {
                        AnimatedContent(
                            targetState = note.isRevealed,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "whisper_reveal"
                        ) { revealed ->
                            if (!revealed) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(ActRoseGlow.copy(alpha = 0.22f), ActVioletGlow.copy(alpha = 0.16f))
                                            )
                                        )
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ActRoseGlow.copy(alpha = 0.24f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Sealed whisper",
                                                tint = Color(0xFFF9A8D4),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sealed Whisper from ${note.senderName}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ActTextPrimary
                                        )
                                        Text(
                                            text = "Tap to break the velvet seal & reveal 💌",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFF9A8D4)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isMine) "You whispered" else "${note.senderName} whispered",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFFF9A8D4)
                                        )
                                        Icon(
                                            Icons.Default.LockOpen,
                                            contentDescription = "Revealed whisper",
                                            tint = ActTextMuted.copy(alpha = 0.65f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = note.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            lineHeight = 22.sp
                                        ),
                                        color = ActTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
