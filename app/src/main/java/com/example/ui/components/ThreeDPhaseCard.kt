package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class BreathPhase(val prompt: String, val emoji: String) {
    INHALE("Breathe in deeply...", "🌸"),
    HOLD("Hold gently...", "💫"),
    EXHALE("Slowly release tension...", "🍃"),
    REST("Rest in still calm...", "☁️")
}

@Composable
fun ThreeDPhaseCard(
    status: CycleStatus,
    onTiltChange: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val phase = status.currentPhase
    val isMoonPhase = phase == CyclePhase.LUTEAL
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val tactile = remember(context, haptic) { TactileStressHelper(context, haptic) }

    // Guided Zen Breathing toggle & sensory preferences
    var isBreathingMode by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var stressReliefCount by remember { mutableIntStateOf(0) }

    // Subtle ambient 3D card parallax & depth state
    val infiniteTransition = rememberInfiniteTransition(label = "emoji_float")
    val ambientTiltX by infiniteTransition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_tilt_x"
    )
    val ambientTiltY by infiniteTransition.animateFloat(
        initialValue = 2.5f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_tilt_y"
    )
    var cardCenterInWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(ambientTiltX, ambientTiltY) {
        onTiltChange?.invoke(Offset(ambientTiltY, ambientTiltX))
    }

    // Idle floating bounce for centerpiece
    val floatBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Aura pulse
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    // Mochi Squish & Jelly Stretch physics state
    val squishScaleX = remember { Animatable(1f) }
    val squishScaleY = remember { Animatable(1f) }
    val stretchOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val flowerWobbleZ = remember { Animatable(0f) }

    // Haptic feedback & velocity tracking for moon spin
    var lastDragTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    // Smooth moon-only spinning state (active only when centerpiece is the Moon 🌙)
    var moonSpinAngle by remember { mutableFloatStateOf(0f) }
    var moonSpinVelocity by remember { mutableFloatStateOf(0f) }
    var lastTickAngle by remember { mutableFloatStateOf(0f) }
    var lastDragAngle by remember { mutableFloatStateOf(0f) }
    var isMoonBeingDragged by remember { mutableStateOf(false) }
    var isMoonSettling by remember { mutableStateOf(false) }
    var moonSettleJob by remember { mutableStateOf<Job?>(null) }

    // Smooth settling animation for the moon orb to land upright at 0°
    fun settleMoonToUpright(initialVelocity: Float = 0f) {
        moonSettleJob?.cancel()
        moonSettleJob = coroutineScope.launch {
            try {
                isMoonSettling = true
                moonSpinVelocity = 0f

                val current = moonSpinAngle
                val turns = current / 360f
                val target = when {
                    initialVelocity > 60f -> {
                        var t = kotlin.math.ceil(turns) * 360f
                        if (t - current < 15f && initialVelocity > 140f) t += 360f
                        t
                    }
                    initialVelocity < -60f -> {
                        var t = kotlin.math.floor(turns) * 360f
                        if (current - t < 15f && initialVelocity < -140f) t -= 360f
                        t
                    }
                    else -> {
                        kotlin.math.round(turns) * 360f
                    }
                }

                val anim = Animatable(current)
                anim.animateTo(
                    targetValue = target,
                    initialVelocity = initialVelocity.coerceIn(-500f, 500f),
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ) {
                    moonSpinAngle = value
                }

                moonSpinAngle = 0f
                moonSpinVelocity = 0f
                lastTickAngle = 0f
            } finally {
                isMoonSettling = false
            }
        }
    }

    // Reset moon angle if phase leaves Luteal
    LaunchedEffect(isMoonPhase) {
        if (!isMoonPhase) {
            moonSettleJob?.cancel()
            moonSpinAngle = 0f
            moonSpinVelocity = 0f
            lastTickAngle = 0f
            isMoonBeingDragged = false
            isMoonSettling = false
        }
    }

    // Continuous smooth moon-spin physics loop with upright auto-landing
    LaunchedEffect(isMoonPhase) {
        if (!isMoonPhase) return@LaunchedEffect
        var lastNano = System.nanoTime()
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNano = now

                if (!isMoonBeingDragged && !isMoonSettling && abs(moonSpinVelocity) > 0f) {
                    moonSpinAngle += moonSpinVelocity * dt
                    moonSpinVelocity *= (1f - (0.55f * dt))

                    if (abs(moonSpinAngle - lastTickAngle) >= 30f) {
                        lastTickAngle = moonSpinAngle
                        tactile.onSpinTick()
                    }

                    if (abs(moonSpinVelocity) <= 120f) {
                        val finalVel = moonSpinVelocity
                        moonSpinVelocity = 0f
                        settleMoonToUpright(initialVelocity = finalVel)
                    }
                } else if (!isMoonBeingDragged && !isMoonSettling && abs(moonSpinVelocity) == 0f) {
                    val normMoon = ((moonSpinAngle % 360f) + 360f) % 360f
                    val moonDiff = if (normMoon > 180f) 360f - normMoon else normMoon
                    if (moonDiff > 0.5f) {
                        settleMoonToUpright(initialVelocity = 0f)
                    }
                }
            }
        }
    }

    // Zen Breathing state machine
    var currentBreathPhase by remember { mutableStateOf(BreathPhase.INHALE) }
    val breathScale = remember { Animatable(0.95f) }
    val breathProgress = remember { Animatable(0f) }

    // Run continuous 4-4-4-4 box breathing loop when Zen Breathe mode is active
    LaunchedEffect(isBreathingMode) {
        if (isBreathingMode) {
            while (isActive) {
                // 1. INHALE (4 seconds)
                currentBreathPhase = BreathPhase.INHALE
                tactile.onBreathPulse()
                launch {
                    breathProgress.snapTo(0f)
                    breathProgress.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                }
                breathScale.animateTo(1.35f, tween(4000, easing = FastOutSlowInEasing))

                // 2. HOLD (4 seconds)
                currentBreathPhase = BreathPhase.HOLD
                tactile.onBreathPulse()
                delay(4000)

                // 3. EXHALE (4 seconds)
                currentBreathPhase = BreathPhase.EXHALE
                tactile.onBreathPulse()
                launch {
                    breathProgress.animateTo(0f, tween(4000, easing = FastOutSlowInEasing))
                }
                breathScale.animateTo(0.95f, tween(4000, easing = FastOutSlowInEasing))

                // 4. REST (4 seconds)
                currentBreathPhase = BreathPhase.REST
                tactile.onBreathPulse()
                delay(4000)
            }
        } else {
            breathScale.snapTo(1f)
            breathProgress.snapTo(0f)
        }
    }

    // Stress relief trigger: Squish pop celebration
    fun triggerSquishPop(spawnOffset: Offset? = null) {
        stressReliefCount++
        tactile.onSquishPop()
        if (soundEnabled) {
            SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE)
        }

        // Bouncy spring squash-and-stretch with natural jelly damping
        coroutineScope.launch {
            squishScaleX.snapTo(1.35f)
            squishScaleY.snapTo(0.68f)
            squishScaleX.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.38f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        coroutineScope.launch {
            squishScaleY.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.38f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        // Spawn particles
        val spawnX = spawnOffset?.x ?: cardCenterInWindow.x
        val spawnY = spawnOffset?.y ?: (cardCenterInWindow.y - 40f)
        particleController.spawn(
            centerX = spawnX,
            centerY = spawnY,
            emojis = listOf("🌸", "✨", "💖", "💫", "🫧", "🌷", "💕", "⭐", "🍃"),
            count = 14
        )

        // Show reassuring affirmations periodically
        if (stressReliefCount % 3 == 0) {
            val sweetNotes = listOf(
                "Take a slow, deep breath 🌸✨",
                "Release any tension in your shoulders 🌿",
                "Your body is wise and resilient 💖",
                "Softness is a superpower too ☁️",
                "Honor where you are today 🌷",
                "Sending gentle warmth and peace 💕",
                "Exhale completely and relax ✨"
            )
            val note = sweetNotes[Random.nextInt(sweetNotes.size)]
            particleController.showToast(coroutineScope, note, "🌸")
        }
    }

    // Gentle rotational impulse with subtle haptic ticks exclusively for the Moon orb
    fun triggerMoonSpin(velocity: Float = 1080f) {
        if (!isMoonPhase) return
        moonSettleJob?.cancel()
        isMoonSettling = false
        tactile.onTouchGrab()
        val boost = if (moonSpinVelocity >= 0f) velocity else -velocity
        moonSpinVelocity = (moonSpinVelocity + boost).coerceIn(-3600f, 3600f)
        if (moonSpinVelocity == 0f) moonSpinVelocity = velocity
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                cardCenterInWindow = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            }
    ) {
        // 1. Glowing Parallax Aura in background (shifts subtly with 3D tilt)
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .offset(
                    x = (30 + (ambientTiltY * 0.8f)).dp,
                    y = (-25 - (ambientTiltX * 0.8f)).dp
                )
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = 0.55f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            phase.accentColor.copy(alpha = 0.32f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.BottomStart)
                .offset(
                    x = (-20 - (ambientTiltY * 0.6f)).dp,
                    y = (20 + (ambientTiltX * 0.6f)).dp
                )
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = 0.42f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PhasesPlum.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 2. Main 3D Card with Subtle Ambient Tilt, Depth, & Dynamic Specular Lighting (upright & easy to read)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationX = ambientTiltX
                    rotationY = ambientTiltY
                    cameraDistance = 16f * density
                    shadowElevation = 18.dp.toPx()
                }
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                phase.accentColor.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .testTag("three_d_phase_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                phase.accentColor.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                // Top Color Accent Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(phase.accentColor, PhasesPlum, phase.accentColor)
                            )
                        )
                )

                // Specular Dynamic Glare that shifts gently with 3D ambient tilt
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.45f }
                ) {
                    val glareCenterX = size.width * (0.5f + (ambientTiltY / 16f) * 0.3f)
                    val glareCenterY = size.height * (0.35f - (ambientTiltX / 16f) * 0.25f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.42f),
                                Color.Transparent
                            ),
                            center = Offset(glareCenterX, glareCenterY),
                            radius = size.maxDimension * 0.55f
                        )
                    )
                }

                // Content inside 3D Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Header: Phase Pill + Realign/Spin Controls + Sound/Mute Toggle + Cycle Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = phase.accentColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, phase.accentColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = phase.emoji, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${phase.shortName.uppercase()} PHASE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = phase.accentColor
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Sound/Mute Toggle for stress relief bubbles
                            IconButton(
                                onClick = {
                                    soundEnabled = !soundEnabled
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.testTag("stress_sound_toggle")
                            ) {
                                Icon(
                                    imageVector = if (soundEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                    contentDescription = if (soundEnabled) "Sound on" else "Sound muted",
                                    tint = if (soundEnabled) phase.accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesPlum.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, PhasesPlum.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = if (status.cycleCount > 1) "📊 ${status.cycleCount} cycles" else "📊 Cycle 1",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PhasesPlum,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Guided Zen Breathing Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isBreathingMode) PhasesPlum else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            1.dp,
                            if (isBreathingMode) PhasesPlum else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                isBreathingMode = !isBreathingMode
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .testTag("stress_relief_mode_bar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelfImprovement,
                                contentDescription = null,
                                tint = if (isBreathingMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBreathingMode) "Breathing Active · Tap to Stop" else "🌬️ Guided Zen Breathe",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isBreathingMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 🌸 Interactive 3D Centerpiece Orb (Moon spins gently; all phases squish & pop!)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            // 3D Parallax floating extrusion: orb hovers in front of card
                            .offset {
                                IntOffset(
                                    x = ((ambientTiltY * 1.5f) + stretchOffset.value.x).roundToInt(),
                                    y = ((-ambientTiltX * 1.5f) + floatBounce + stretchOffset.value.y).roundToInt()
                                )
                            }
                    ) {
                        // Cast shadow beneath the orb that shifts inversely with tilt angle
                        Box(
                            modifier = Modifier
                                .size(70.dp, 22.dp)
                                .align(Alignment.BottomCenter)
                                .offset(
                                    x = (-ambientTiltY * 0.9f).dp,
                                    y = (ambientTiltX * 0.9f - 4).dp
                                )
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.28f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // Zen Breathing Halo Rings (active in Breathe mode)
                        if (isBreathingMode) {
                            Canvas(modifier = Modifier.size(140.dp)) {
                                // Background calm track
                                drawCircle(
                                    color = phase.accentColor.copy(alpha = 0.12f),
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Active breathing progress arc
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(phase.accentColor, PhasesPlum, phase.accentColor)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = breathProgress.value * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // ✨ Interactive Centerpiece Orb (Only Moon 🌙 spins; other phases softly squish & bounce)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(136.dp)
                                .graphicsLayer {
                                    if (isBreathingMode) {
                                        scaleX = breathScale.value
                                        scaleY = breathScale.value
                                        rotationZ = if (isMoonPhase) moonSpinAngle * 0.1f else 0f
                                    } else {
                                        scaleX = squishScaleX.value
                                        scaleY = squishScaleY.value
                                        rotationZ = if (isMoonPhase) moonSpinAngle + flowerWobbleZ.value else flowerWobbleZ.value
                                    }
                                    cameraDistance = 14f * density
                                }
                                .pointerInput(isBreathingMode, isMoonPhase) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!isBreathingMode && isMoonPhase) {
                                                triggerMoonSpin(1080f)
                                            }
                                            triggerSquishPop(cardCenterInWindow)
                                            coroutineScope.launch {
                                                squishScaleX.snapTo(0.82f)
                                                squishScaleY.snapTo(1.18f)
                                                squishScaleX.animateTo(1f, spring(0.35f, Spring.StiffnessMediumLow))
                                                squishScaleY.animateTo(1f, spring(0.35f, Spring.StiffnessMediumLow))
                                            }
                                        }
                                    )
                                }
                                .pointerInput(isBreathingMode, isMoonPhase) {
                                    if (!isBreathingMode && isMoonPhase) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                moonSettleJob?.cancel()
                                                isMoonSettling = false
                                                isMoonBeingDragged = true
                                                tactile.onTouchGrab()
                                                moonSpinVelocity = 0f
                                                val center = Offset(68f * density, 68f * density)
                                                lastDragAngle = Math.toDegrees(
                                                    kotlin.math.atan2(offset.y - center.y, offset.x - center.x).toDouble()
                                                ).toFloat()
                                                lastDragTime = System.currentTimeMillis()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val now = System.currentTimeMillis()
                                                val dt = (now - lastDragTime).coerceAtLeast(1L)
                                                lastDragTime = now
                                                val center = Offset(68f * density, 68f * density)
                                                val currentPos = change.position
                                                val currentAngle = Math.toDegrees(
                                                    kotlin.math.atan2(currentPos.y - center.y, currentPos.x - center.x).toDouble()
                                                ).toFloat()
                                                var deltaAngle = currentAngle - lastDragAngle
                                                if (deltaAngle > 180f) deltaAngle -= 360f
                                                if (deltaAngle < -180f) deltaAngle += 360f
                                                lastDragAngle = currentAngle

                                                val radius = kotlin.math.hypot(currentPos.x - center.x, currentPos.y - center.y).coerceAtLeast(20f)
                                                val tangent = (dragAmount.x * -(currentPos.y - center.y) + dragAmount.y * (currentPos.x - center.x)) / radius
                                                val effDelta = if (abs(deltaAngle) > 0.3f) deltaAngle else (tangent * 0.7f)

                                                moonSpinAngle += effDelta
                                                moonSpinVelocity = (effDelta / dt) * 1000f

                                                if (abs(moonSpinAngle - lastTickAngle) >= 30f) {
                                                    lastTickAngle = moonSpinAngle
                                                    tactile.onSpinTick()
                                                }
                                            },
                                            onDragEnd = {
                                                isMoonBeingDragged = false
                                                if (abs(moonSpinVelocity) >= 120f) {
                                                    if (abs(moonSpinVelocity) < 360f) {
                                                        moonSpinVelocity = if (moonSpinVelocity >= 0f) 650f else -650f
                                                    }
                                                    moonSpinVelocity = moonSpinVelocity.coerceIn(-3600f, 3600f)
                                                } else {
                                                    settleMoonToUpright(initialVelocity = moonSpinVelocity)
                                                }
                                            },
                                            onDragCancel = {
                                                isMoonBeingDragged = false
                                                settleMoonToUpright(initialVelocity = 0f)
                                            }
                                        )
                                    }
                                }
                                .testTag("three_d_interactive_flower")
                        ) {
                            // Gentle celestial radiant aura ring
                            Canvas(modifier = Modifier.size(126.dp)) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            phase.accentColor.copy(alpha = 0.28f),
                                            phase.accentColor.copy(alpha = 0.08f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.minDimension / 2f
                                )
                                drawCircle(
                                    color = phase.accentColor.copy(alpha = 0.35f),
                                    radius = 50.dp.toPx(),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            Text(
                                text = phase.emoji,
                                fontSize = 56.sp,
                                modifier = Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = phase.accentColor.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }

                    // Dynamic Guidance Text
                    if (isBreathingMode) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = PhasesPlum.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PhasesPlum.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "${currentBreathPhase.emoji} ${currentBreathPhase.prompt}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = PhasesPlum,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = when {
                                    isMoonPhase && stressReliefCount > 0 ->
                                        "🌙 Destressed ($stressReliefCount) ✨ · Tap or swirl the moon gently"
                                    isMoonPhase ->
                                        "🌙 Tap or swirl the moon for a calm spin & pop"
                                    stressReliefCount > 0 ->
                                        "✨ Destressed ($stressReliefCount) 🌸 · Tap orb for a calming squish"
                                    else ->
                                        "🌸 Tap orb for a soothing squish & affirmation"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = phase.accentColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Phase Title
                    Text(
                        text = phase.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Cycle day label
                    Text(
                        text = if (status.isLate) {
                            "Day ${status.dayInCycle} · ${status.daysLate} days past expected"
                        } else {
                            "Day ${status.dayInCycle} of your cycle"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (status.isLate) PhasesRose else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Poetic Description
                    Text(
                        text = phase.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Frosted Glass 3-Stat Summary Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FrostedStatPill(
                            icon = "🗓",
                            value = "Day ${status.dayInCycle}",
                            label = "Cycle Day",
                            modifier = Modifier.weight(1f)
                        )

                        FrostedStatPill(
                            icon = "🩸",
                            value = when {
                                status.isLate -> "+${status.daysLate}d"
                                status.daysUntilNextPeriod == 0 -> "Today"
                                else -> "${status.daysUntilNextPeriod}d"
                            },
                            label = "Until Next",
                            valueColor = if (status.isLate) PhasesRose else PhasesPlum,
                            modifier = Modifier.weight(1f)
                        )

                        FrostedStatPill(
                            icon = status.nextPhase.emoji,
                            value = "${status.daysUntilNextPhase}d",
                            label = status.nextPhase.shortName,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedStatPill(
    icon: String,
    value: String,
    label: String,
    valueColor: Color = PhasesPlum,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 2.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        coroutineScope.launch {
                            scale.animateTo(0.94f, tween(100))
                            tryAwaitRelease()
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = valueColor
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
