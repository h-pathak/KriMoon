package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
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
    val tactile = remember(context, haptic) { TactileStressHelper(context, haptic) }

    // Guided Zen Breathing toggle & sensory preferences (quiet haptics by default)
    var isBreathingMode by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(false) }
    var stressReliefCount by remember { mutableIntStateOf(0) }

    var cardCenterInWindow by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var interactiveTiltX by remember { mutableFloatStateOf(0f) }
    var interactiveTiltY by remember { mutableFloatStateOf(0f) }
    var isCardTouched by remember { mutableStateOf(false) }

    val smoothTouchTiltXState = animateFloatAsState(
        targetValue = interactiveTiltX,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "hero_touch_tilt_x"
    )
    val smoothTouchTiltYState = animateFloatAsState(
        targetValue = interactiveTiltY,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "hero_touch_tilt_y"
    )

    // Zen Breathing state machine
    var currentBreathPhase by remember { mutableStateOf(BreathPhase.INHALE) }
    val breathScale = remember { Animatable(0.95f) }
    val breathProgress = remember { Animatable(0f) }

    LaunchedEffect(isBreathingMode) {
        if (isBreathingMode) {
            while (isActive) {
                currentBreathPhase = BreathPhase.INHALE
                tactile.onBreathPulse()
                launch {
                    breathProgress.snapTo(0f)
                    breathProgress.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                }
                breathScale.animateTo(1.35f, tween(4000, easing = FastOutSlowInEasing))

                currentBreathPhase = BreathPhase.HOLD
                tactile.onBreathPulse()
                delay(4000)

                currentBreathPhase = BreathPhase.EXHALE
                tactile.onBreathPulse()
                launch {
                    breathProgress.animateTo(0f, tween(4000, easing = FastOutSlowInEasing))
                }
                breathScale.animateTo(0.95f, tween(4000, easing = FastOutSlowInEasing))

                currentBreathPhase = BreathPhase.REST
                tactile.onBreathPulse()
                delay(4000)
            }
        } else {
            breathScale.snapTo(1f)
            breathProgress.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { cardSize = it }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                cardCenterInWindow = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            }
            // Non-consuming PointerEventPass.Initial 3D tilt: 100% smooth LazyColumn scrolling
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var pressOrigin = Offset.Zero
                    var scrolling = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        val pos = change.position
                        val w = cardSize.width.coerceAtLeast(1).toFloat()
                        val h = cardSize.height.coerceAtLeast(1).toFloat()
                        val hasPressed = event.changes.any { it.pressed }
                        when {
                            event.type == PointerEventType.Press && hasPressed -> {
                                pressOrigin = pos
                                scrolling = false
                                isCardTouched = true
                                val nx = ((pos.x / w) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                val ny = ((pos.y / h) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                interactiveTiltX = -ny * 3.6f
                                interactiveTiltY = nx * 3.6f
                            }
                            event.type == PointerEventType.Move && hasPressed -> {
                                val dy = abs(pos.y - pressOrigin.y)
                                val dx = abs(pos.x - pressOrigin.x)
                                if (dy > 18f && dy > dx * 1.15f) {
                                    scrolling = true
                                    isCardTouched = false
                                    interactiveTiltX = 0f
                                    interactiveTiltY = 0f
                                } else if (!scrolling) {
                                    val nx = ((pos.x / w) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                    val ny = ((pos.y / h) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                    interactiveTiltX = -ny * 3.6f
                                    interactiveTiltY = nx * 3.6f
                                }
                            }
                            !hasPressed || event.type == PointerEventType.Release || event.type == PointerEventType.Exit -> {
                                isCardTouched = false
                                interactiveTiltX = 0f
                                interactiveTiltY = 0f
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                rotationX = smoothTouchTiltXState.value.coerceIn(-4.2f, 4.2f)
                rotationY = smoothTouchTiltYState.value.coerceIn(-4.2f, 4.2f)
                cameraDistance = 20f * density
            }
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCardTouched) 6.dp else 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                phase.accentColor.copy(alpha = if (isCardTouched) 0.65f else 0.45f),
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
                                phase.accentColor.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surface
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

                // Specular Dynamic Glare — shifts on touch tilt, zero redraws when idle
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = if (isCardTouched) 0.62f else 0.38f }
                ) {
                    val tiltX = smoothTouchTiltXState.value.coerceIn(-4.2f, 4.2f)
                    val tiltY = smoothTouchTiltYState.value.coerceIn(-4.2f, 4.2f)
                    val glareCenterX = size.width * (0.5f + (tiltY / 10f) * 0.35f)
                    val glareCenterY = size.height * (0.35f - (tiltX / 10f) * 0.30f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.42f),
                                Color.Transparent
                            ),
                            center = Offset(glareCenterX, glareCenterY),
                            radius = size.maxDimension * 0.56f
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PhaseCardTopHeader(
                        phase = phase,
                        cycleCount = status.cycleCount,
                        soundEnabled = soundEnabled,
                        onToggleSound = {
                            soundEnabled = !soundEnabled
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PhaseCardBreathingToggle(
                        isBreathingMode = isBreathingMode,
                        onToggle = {
                            isBreathingMode = !isBreathingMode
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PhaseCardInteractiveOrb(
                        phase = phase,
                        isMoonPhase = isMoonPhase,
                        isBreathingMode = isBreathingMode,
                        soundEnabled = soundEnabled,
                        tactile = tactile,
                        cardCenterInWindow = cardCenterInWindow,
                        touchTiltXState = smoothTouchTiltXState,
                        touchTiltYState = smoothTouchTiltYState,
                        breathScale = breathScale,
                        breathProgress = breathProgress,
                        onStressReliefIncrement = { stressReliefCount++ }
                    )

                    PhaseCardBottomSummary(
                        status = status,
                        phase = phase,
                        isMoonPhase = isMoonPhase,
                        isBreathingMode = isBreathingMode,
                        currentBreathPhase = currentBreathPhase,
                        stressReliefCount = stressReliefCount
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseCardTopHeader(
    phase: CyclePhase,
    cycleCount: Int,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit
) {
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
            IconButton(
                onClick = onToggleSound,
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
                    text = if (cycleCount > 1) "📊 $cycleCount cycles" else "📊 Cycle 1",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PhaseCardBreathingToggle(
    isBreathingMode: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (isBreathingMode) PhasesPlum else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (isBreathingMode) PhasesPlum else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onToggle)
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
}

@Composable
private fun PhaseCardInteractiveOrb(
    phase: CyclePhase,
    isMoonPhase: Boolean,
    isBreathingMode: Boolean,
    soundEnabled: Boolean,
    tactile: TactileStressHelper,
    cardCenterInWindow: Offset,
    touchTiltXState: State<Float>,
    touchTiltYState: State<Float>,
    breathScale: Animatable<Float, *>,
    breathProgress: Animatable<Float, *>,
    onStressReliefIncrement: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current

    var localReliefCount by remember { mutableIntStateOf(0) }
    val squishScaleX = remember { Animatable(1f) }
    val squishScaleY = remember { Animatable(1f) }
    val stretchOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val flowerWobbleZ = remember { Animatable(0f) }

    var lastDragTime by remember { mutableLongStateOf(0L) }
    val moonSpinAngleState = remember { mutableFloatStateOf(0f) }
    var moonSpinVelocity by remember { mutableFloatStateOf(0f) }
    var lastTickAngle by remember { mutableFloatStateOf(0f) }
    var lastDragAngle by remember { mutableFloatStateOf(0f) }
    var isMoonBeingDragged by remember { mutableStateOf(false) }
    var isMoonSettling by remember { mutableStateOf(false) }
    var isMoonSpinning by remember { mutableStateOf(false) }
    var moonSettleJob by remember { mutableStateOf<Job?>(null) }

    fun settleMoonToUpright(initialVelocity: Float = 0f) {
        moonSettleJob?.cancel()
        isMoonSpinning = false
        moonSettleJob = coroutineScope.launch {
            try {
                isMoonSettling = true
                moonSpinVelocity = 0f

                val current = moonSpinAngleState.floatValue
                val turns = current / 360f
                val target = when {
                    initialVelocity > 60f -> {
                        var t = ceil(turns) * 360f
                        if (t - current < 15f && initialVelocity > 140f) t += 360f
                        t
                    }
                    initialVelocity < -60f -> {
                        var t = floor(turns) * 360f
                        if (current - t < 15f && initialVelocity < -140f) t -= 360f
                        t
                    }
                    else -> round(turns) * 360f
                }

                val anim = Animatable(current)
                anim.animateTo(
                    targetValue = target,
                    initialVelocity = initialVelocity.coerceIn(-500f, 500f),
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ) {
                    moonSpinAngleState.floatValue = value
                }

                moonSpinAngleState.floatValue = 0f
                moonSpinVelocity = 0f
                lastTickAngle = 0f
            } finally {
                isMoonSettling = false
            }
        }
    }

    LaunchedEffect(isMoonPhase) {
        if (!isMoonPhase) {
            moonSettleJob?.cancel()
            moonSpinAngleState.floatValue = 0f
            moonSpinVelocity = 0f
            lastTickAngle = 0f
            isMoonBeingDragged = false
            isMoonSettling = false
            isMoonSpinning = false
        }
    }

    // Physics loop ONLY runs while the moon is actively coasting — zero idle frame callbacks!
    LaunchedEffect(isMoonPhase, isMoonSpinning) {
        if (!isMoonPhase || !isMoonSpinning) return@LaunchedEffect
        var lastNano = System.nanoTime()
        while (isActive && isMoonSpinning) {
            withFrameNanos { now ->
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNano = now

                if (!isMoonBeingDragged && !isMoonSettling && abs(moonSpinVelocity) > 0f) {
                    moonSpinAngleState.floatValue += moonSpinVelocity * dt
                    moonSpinVelocity *= (1f - (0.55f * dt))

                    if (abs(moonSpinAngleState.floatValue - lastTickAngle) >= 30f) {
                        lastTickAngle = moonSpinAngleState.floatValue
                        tactile.onSpinTick()
                    }

                    if (abs(moonSpinVelocity) <= 120f) {
                        val finalVel = moonSpinVelocity
                        moonSpinVelocity = 0f
                        settleMoonToUpright(initialVelocity = finalVel)
                    }
                } else {
                    isMoonSpinning = false
                }
            }
        }
    }

    fun triggerSquishPop(spawnOffset: Offset? = null) {
        localReliefCount++
        onStressReliefIncrement()
        tactile.onSquishPop()
        if (soundEnabled) {
            SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE)
        }

        coroutineScope.launch {
            squishScaleX.snapTo(1.35f)
            squishScaleY.snapTo(0.68f)
            squishScaleX.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)
            )
        }
        coroutineScope.launch {
            squishScaleY.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)
            )
        }

        val spawnX = spawnOffset?.x ?: cardCenterInWindow.x
        val spawnY = spawnOffset?.y ?: (cardCenterInWindow.y - 40f)
        particleController.spawn(
            centerX = spawnX,
            centerY = spawnY,
            emojis = listOf("🌸", "✨", "💖", "💫", "🫧", "🌷", "💕", "⭐", "🍃"),
            count = 14
        )

        if (localReliefCount % 3 == 0) {
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

    fun triggerMoonSpin(velocity: Float = 1080f) {
        if (!isMoonPhase) return
        moonSettleJob?.cancel()
        isMoonSettling = false
        tactile.onTouchGrab()
        val boost = if (moonSpinVelocity >= 0f) velocity else -velocity
        moonSpinVelocity = (moonSpinVelocity + boost).coerceIn(-3600f, 3600f)
        if (moonSpinVelocity == 0f) moonSpinVelocity = velocity
        isMoonSpinning = true
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(150.dp)
            .offset {
                IntOffset(
                    x = ((touchTiltYState.value * 1.5f) + stretchOffset.value.x).roundToInt(),
                    y = ((-touchTiltXState.value * 1.5f) + stretchOffset.value.y).roundToInt()
                )
            }
    ) {
        // Cast shadow beneath orb — offset lambda defers state reads to Layout phase
        Box(
            modifier = Modifier
                .size(70.dp, 22.dp)
                .align(Alignment.BottomCenter)
                .offset {
                    IntOffset(
                        x = (-touchTiltYState.value * 0.9f).dp.roundToPx(),
                        y = (touchTiltXState.value * 0.9f - 4f).dp.roundToPx()
                    )
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        if (isBreathingMode) {
            Canvas(modifier = Modifier.size(140.dp)) {
                drawCircle(
                    color = phase.accentColor.copy(alpha = 0.12f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(phase.accentColor, PhasesPlum, phase.accentColor)),
                    startAngle = -90f,
                    sweepAngle = breathProgress.value * 360f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(136.dp)
                .graphicsLayer {
                    val moonAngle = moonSpinAngleState.floatValue
                    if (isBreathingMode) {
                        scaleX = breathScale.value
                        scaleY = breathScale.value
                        rotationZ = if (isMoonPhase) moonAngle * 0.1f else 0f
                    } else {
                        scaleX = squishScaleX.value
                        scaleY = squishScaleY.value
                        rotationZ = if (isMoonPhase) moonAngle + flowerWobbleZ.value else flowerWobbleZ.value
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
                                isMoonSpinning = false
                                isMoonBeingDragged = true
                                tactile.onTouchGrab()
                                moonSpinVelocity = 0f
                                val center = Offset(68f * density, 68f * density)
                                lastDragAngle = Math.toDegrees(
                                    atan2(offset.y - center.y, offset.x - center.x).toDouble()
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
                                    atan2(currentPos.y - center.y, currentPos.x - center.x).toDouble()
                                ).toFloat()
                                var deltaAngle = currentAngle - lastDragAngle
                                if (deltaAngle > 180f) deltaAngle -= 360f
                                if (deltaAngle < -180f) deltaAngle += 360f
                                lastDragAngle = currentAngle

                                val radius = hypot(currentPos.x - center.x, currentPos.y - center.y).coerceAtLeast(20f)
                                val tangent = (dragAmount.x * -(currentPos.y - center.y) + dragAmount.y * (currentPos.x - center.x)) / radius
                                val effDelta = if (abs(deltaAngle) > 0.3f) deltaAngle else (tangent * 0.7f)

                                moonSpinAngleState.floatValue += effDelta
                                moonSpinVelocity = (effDelta / dt) * 1000f

                                if (abs(moonSpinAngleState.floatValue - lastTickAngle) >= 30f) {
                                    lastTickAngle = moonSpinAngleState.floatValue
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
                                    isMoonSpinning = true
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
}

@Composable
private fun PhaseCardBottomSummary(
    status: CycleStatus,
    phase: CyclePhase,
    isMoonPhase: Boolean,
    isBreathingMode: Boolean,
    currentBreathPhase: BreathPhase,
    stressReliefCount: Int
) {
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

    Text(
        text = phase.title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(2.dp))

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

    Text(
        text = phase.description,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

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

@Composable
private fun FrostedStatPill(
    icon: String,
    value: String,
    label: String,
    valueColor: Color = PhasesPlum,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
                        AestheticHaptics.softTick(context, haptic)
                        coroutineScope.launch {
                            scale.animateTo(0.94f, tween(90))
                            tryAwaitRelease()
                            scale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium))
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
