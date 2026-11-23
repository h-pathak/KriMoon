package com.example.ui.games

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.model.SecretWhisperNote
import com.example.notifications.NotificationHelper
import com.example.notifications.SoundHelper
import com.example.ui.components.AestheticHaptics
import com.example.ui.components.LocalParticleController
import com.example.ui.components.cute3DCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val PulseBgTop = Color(0xFF1A0E1B)
private val PulseBgBottom = Color(0xFF2D1225)
private val PulseSurface = Color(0xFF241121)
private val PulseRoseGlow = Color(0xFFE11D68)
private val PulseBlushGlow = Color(0xFFF472B6)
private val PulseLilacGlow = Color(0xFFC084FC)
private val PulseBorder = Color(0x52F472B6)
private val PulseTextPrimary = Color(0xFFFFF5F8)
private val PulseTextMuted = Color(0xFFD8B8C8)
private val PulseEmerald = Color(0xFF34D399)

enum class LovePulsePreset(
    val id: String,
    val emoji: String,
    val label: String,
    val partnerViewText: String,
    val burstEmojis: List<String>
) {
    HUG(
        id = "hug",
        emoji = "🫂",
        label = "Warm Hug",
        partnerViewText = "wants to give you the biggest warm hug 🫂🩷",
        burstEmojis = listOf("🫂", "🩷", "🌸", "✨", "💕")
    ),
    THINKING(
        id = "thinking",
        emoji = "💭",
        label = "Thinking of You",
        partnerViewText = "is thinking about you right now 💭✨",
        burstEmojis = listOf("💭", "✨", "🩷", "🌙", "🌸")
    ),
    MISS_YOU(
        id = "miss",
        emoji = "🌸",
        label = "Miss You",
        partnerViewText = "misses you so much right now 🌸💕",
        burstEmojis = listOf("🌸", "💕", "🌷", "✨", "🩷")
    ),
    KISS(
        id = "kiss",
        emoji = "💋",
        label = "Forehead Kiss",
        partnerViewText = "sent you a gentle forehead kiss ✨💋",
        burstEmojis = listOf("💋", "✨", "🩷", "🌹", "💫")
    ),
    COZY(
        id = "cozy",
        emoji = "☕",
        label = "Cozy Check-In",
        partnerViewText = "is checking in to make you smile ☕🌷",
        burstEmojis = listOf("☕", "🌷", "🫧", "✨", "💛")
    )
}

@Composable
fun CoupleLovePulseScreen(
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
    val particleController = LocalParticleController.current

    var state by remember(room.coupleCode, room.whisperStateJson) {
        mutableStateOf(CoupleActivitiesDeck.dateWhisperStateFromJson(room.whisperStateJson))
    }
    var selectedPreset by remember { mutableStateOf(LovePulsePreset.HUG) }
    var lastSentConfirmation by remember { mutableStateOf<String?>(null) }
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
            if (granted) {
                particleController.showToast(
                    coroutineScope = scope,
                    message = "Partner pulse notifications enabled!",
                    icon = "🔔"
                )
            }
        }
    )

    val myName = room.myName.ifBlank { "Me" }
    val partnerName = room.partnerName.ifBlank { "Your partner" }

    val latestPartnerPulse = remember(state.whispers, myName) {
        state.whispers.lastOrNull { !it.senderName.equals(myName.trim(), ignoreCase = true) }
    }

    fun sendEncryptedLovePulse(preset: LovePulsePreset) {
        AestheticHaptics.warmConfirm(context, haptic)
        if (soundEnabled) {
            SoundHelper.playCuteChime(context)
        }
        particleController.spawn(
            centerX = 480f,
            centerY = 640f,
            emojis = preset.burstEmojis,
            count = 16
        )

        val pulseNote = SecretWhisperNote(
            id = "pulse_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
            senderName = myName,
            text = "${preset.emoji} ${preset.partnerViewText}",
            timestamp = System.currentTimeMillis(),
            isRevealed = true
        )
        val next = state.copy(whispers = (state.whispers + pulseNote).takeLast(24))
        state = next
        onSaveState(CoupleActivitiesDeck.dateWhisperStateToJson(next), true)

        if (!room.isPassAndPlay) {
            val sharedSecret = room.ecdhSharedSecretB64
            scope.launch {
                val (packet, block) = withContext(Dispatchers.Default) {
                    CoupleTurnCodec.encodeWhisperTurnWithBlock(
                        coupleCode = room.coupleCode,
                        senderName = myName,
                        likedDateId = "",
                        whisperNote = pulseNote,
                        previousHash = room.lastBlockHash.ifBlank {
                            CoupleBlockchain.createDeterministicGenesisBlock(room.coupleCode).blockHash
                        },
                        blockIndex = room.blockHeight.coerceAtLeast(1),
                        ecdhSharedSecretB64 = sharedSecret
                    )
                }
                onSendLiveBlock(packet, block)
            }
        }

        val statusToast = when {
            room.isPassAndPlay -> "${preset.label} saved in your room 🩷"
            isLiveConnected -> "Encrypted ${preset.label} sent to $partnerName's phone!"
            else -> "Encrypted ${preset.label} queued — delivers to $partnerName once online!"
        }
        lastSentConfirmation = when {
            room.isPassAndPlay -> "Saved locally · Switch to Live Sync to send straight to $partnerName's phone"
            isLiveConnected -> "Encrypted & sent straight to $partnerName's phone ✨"
            else -> "Encrypted & queued · Will deliver automatically when back online 🌙"
        }
        particleController.showToast(
            coroutineScope = scope,
            message = statusToast,
            icon = preset.emoji
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Partner's Glowing Arrival Card (what she sees when opening KriMoon)
        if (latestPartnerPulse != null) {
            PartnerLovePulseCard(
                pulse = latestPartnerPulse,
                onHugBack = { sendEncryptedLovePulse(LovePulsePreset.HUG) }
            )
        }

        // 2. Aesthetic Vibe Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LovePulsePreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (isSelected) PulseRoseGlow else PulseSurface,
                    border = BorderStroke(
                        width = if (isSelected) 1.2.dp else 0.9.dp,
                        color = if (isSelected) PulseBlushGlow else PulseBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            AestheticHaptics.softTick(context, haptic)
                            selectedPreset = preset
                        }
                        .testTag("love_pulse_preset_${preset.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(text = preset.emoji, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else PulseTextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Centerpiece Aesthetic Velvet Pulse Card & Breathing Orb Button
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = PulseBgTop),
            border = BorderStroke(1.2.dp, PulseBorder),
            modifier = Modifier
                .fillMaxWidth()
                .cute3DCard(maxTilt = 3f, cornerRadius = 30.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(PulseBgTop, PulseBgBottom)))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // End-to-End Encryption & Live Delivery Badge
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        0.8.dp,
                        if (isLiveConnected) PulseEmerald.copy(alpha = 0.45f) else PulseBorder
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "End-to-end encrypted",
                            tint = if (isLiveConnected) PulseEmerald else Color(0xFFF9A8D4),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                room.isPassAndPlay -> "1-Phone Mode · Pair Live to Ping $partnerName"
                                isLiveConnected -> "E2EE Live · Direct to $partnerName's Phone ✨"
                                else -> "E2EE Protected · Queues Until Connected 🌙"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isLiveConnected) PulseEmerald else Color(0xFFF9A8D4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Breathing Aesthetic Tap Orb
                AestheticLovePulseOrb(
                    preset = selectedPreset,
                    partnerName = partnerName,
                    onTap = { sendEncryptedLovePulse(selectedPreset) }
                )

                Text(
                    text = "“$myName ${selectedPreset.partnerViewText}”",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        lineHeight = 22.sp
                    ),
                    color = PulseTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Delivery Confirmation Pill (purely in-app encrypted delivery, no external message)
                AnimatedVisibility(
                    visible = lastSentConfirmation != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PulseEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, PulseEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("love_pulse_delivery_status")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PulseEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lastSentConfirmation.orEmpty(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PulseTextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Android 13+ Notification Permission prompt so partner pulses alert on this phone
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(0.9.dp, PulseBlushGlow.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                AestheticHaptics.softTick(context, haptic)
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            .testTag("love_pulse_enable_notifications_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Enable notifications",
                                tint = Color(0xFFF9A8D4),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable KriMoon alerts 🔔",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PulseTextPrimary
                                )
                                Text(
                                    text = "Tap so you receive $partnerName's hugs & thinking-of-you pings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PulseTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Recent Sweet Moments Timeline
        val recentPulses = state.whispers.reversed().take(6)
        if (recentPulses.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Recent Love Pulses ✨",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                recentPulses.forEach { pulse ->
                    val isMine = pulse.senderName.equals(myName.trim(), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = PulseSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isMine) PulseBorder else PulseBlushGlow.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isMine) "You sent 🔐" else "${pulse.senderName} sent 🩷",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isMine) PulseTextMuted else Color(0xFFF9A8D4)
                                )
                                Text(
                                    text = pulse.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                    color = PulseTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = formatPulseTime(pulse.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PulseTextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun AestheticLovePulseOrb(
    preset: LovePulsePreset,
    partnerName: String,
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_press_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orb_breathing")
    val haloScaleState = infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_halo_scale"
    )
    val haloAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_halo_alpha"
    )

    val haloBrush = remember {
        Brush.radialGradient(
            colors = listOf(PulseBlushGlow, PulseLilacGlow.copy(alpha = 0.4f), Color.Transparent)
        )
    }
    val orbBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFF43F5E),
                PulseRoseGlow,
                Color(0xFF9333EA)
            )
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(224.dp)
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
    ) {
        // Outer dreamy breathing halo (strictly GPU layer-only transform)
        Box(
            modifier = Modifier
                .size(208.dp)
                .graphicsLayer {
                    val s = haloScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = haloAlphaState.value
                }
                .clip(CircleShape)
                .background(haloBrush)
        )

        // Inner soft rose ring
        Box(
            modifier = Modifier
                .size(184.dp)
                .clip(CircleShape)
                .border(1.4.dp, PulseBlushGlow.copy(alpha = 0.45f), CircleShape)
        )

        // Main Tactile Aesthetic Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(166.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = PulseRoseGlow
                )
                .clip(CircleShape)
                .background(orbBrush)
                .border(2.dp, Color.White.copy(alpha = 0.38f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap
                )
                .testTag("love_pulse_tap_orb_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Text(text = preset.emoji, fontSize = 46.sp)
                Text(
                    text = "Tap to Send",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = Color.White
                )
                Text(
                    text = "to $partnerName ✨",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PartnerLovePulseCard(
    pulse: SecretWhisperNote,
    onHugBack: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PulseSurface),
        border = BorderStroke(1.3.dp, PulseBlushGlow.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2.4f, cornerRadius = 24.dp)
            .testTag("partner_love_pulse_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PulseRoseGlow.copy(alpha = 0.25f),
                            PulseLilacGlow.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = PulseEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "From ${pulse.senderName} 🩷",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF9A8D4)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = formatPulseTime(pulse.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PulseTextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "${pulse.senderName} ${pulse.text.replaceFirst(Regex("^\\S+\\s+"), "")}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 24.sp
                ),
                color = PulseTextPrimary
            )

            OutlinedButton(
                onClick = onHugBack,
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Color(0xFFF9A8D4).copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("love_pulse_hug_back_btn")
            ) {
                Text(
                    text = "Send a Warm Hug Back 🫂🩷",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

private fun formatPulseTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Just now"
    val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        mins < 24 * 60L -> "${mins / 60L}h ago"
        else -> SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
