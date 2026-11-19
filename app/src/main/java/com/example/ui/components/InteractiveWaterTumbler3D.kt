package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesWaterBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * An interactive, physics-driven 3D Water Tumbler with living sinusoidal liquid waves,
 * floating effervescent bubbles, dynamic slosh reaction, specular glass reflections,
 * and subtle tactile haptic responses.
 */
@Composable
fun InteractiveWaterTumbler3D(
    count: Int,
    goal: Int,
    onLogSip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val tactile = remember(context, haptic) { TactileHydrationHelper(context, haptic) }

    val progress = (count.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 130f),
        label = "water_fill_level"
    )

    // Dynamic slosh physics state
    val sloshTiltZ = remember { Animatable(0f) }
    val sloshTiltY = remember { Animatable(0f) }
    val sloshWaveSurge = remember { Animatable(0f) }
    val scaleRecoil = remember { Animatable(1f) }

    var tumblerCenter by remember { mutableStateOf(Offset.Zero) }
    var lastSloshTick by remember { mutableFloatStateOf(0f) }

    // Wave animation loops
    val infiniteTransition = rememberInfiniteTransition(label = "water_waves")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = (2 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    val bubbleFloatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbles"
    )

    val isGoalReached = count >= goal

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_water_tumbler")
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                tumblerCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PhasesWaterBlue.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            // Header status chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (isGoalReached) PhasesPlum.copy(alpha = 0.14f) else PhasesWaterBlue.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (isGoalReached) PhasesPlum.copy(alpha = 0.35f) else PhasesWaterBlue.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isGoalReached) "🎉 Goal Achieved!" else "💧 Today's Hydration",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isGoalReached) PhasesPlum else PhasesWaterBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The 3D Glass Vessel with interactive fluid simulation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 175.dp, height = 230.dp)
                    .graphicsLayer {
                        rotationZ = sloshTiltZ.value
                        rotationY = sloshTiltY.value
                        scaleX = scaleRecoil.value
                        scaleY = scaleRecoil.value
                        cameraDistance = 16f * density
                    }
                    .pointerInput(count, goal) {
                        detectTapGestures(
                            onTap = {
                                val next = count + 1
                                onLogSip()
                                SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE)
                                tactile.onDropSip(next >= goal && count < goal)

                                // Trigger slosh ripple physics
                                coroutineScope.launch {
                                    scaleRecoil.snapTo(1f)
                                    sloshWaveSurge.snapTo(18f)
                                    launch { scaleRecoil.animateTo(1.08f, tween(80)) }
                                    launch { sloshTiltZ.animateTo(9f, tween(100, easing = FastOutSlowInEasing)) }
                                    delay(90)
                                    launch { sloshTiltZ.animateTo(-7f, tween(120)) }
                                    launch { scaleRecoil.animateTo(1f, spring(dampingRatio = 0.50f, stiffness = 220f)) }
                                    delay(110)
                                    launch { sloshTiltZ.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
                                    sloshWaveSurge.animateTo(0f, spring(dampingRatio = 0.60f, stiffness = 100f))
                                }

                                particleController.spawn(
                                    centerX = tumblerCenter.x,
                                    centerY = tumblerCenter.y - 40f,
                                    emojis = listOf("💧", "🫧", "✨", "🌊", "💙"),
                                    count = 10
                                )
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val currentZ = (sloshTiltZ.value + dragAmount * 0.16f).coerceIn(-18f, 18f)
                                coroutineScope.launch {
                                    sloshTiltZ.snapTo(currentZ)
                                    sloshWaveSurge.snapTo(abs(currentZ) * 0.8f)
                                }

                                if (abs(currentZ - lastSloshTick) >= 4.5f) {
                                    lastSloshTick = currentZ
                                    tactile.onWaveSlosh()
                                }
                            },
                            onDragEnd = {
                                tactile.onWaveSlosh()
                                coroutineScope.launch {
                                    launch {
                                        sloshTiltZ.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.44f, stiffness = Spring.StiffnessLow)
                                        )
                                    }
                                    sloshWaveSurge.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
                                    )
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    sloshTiltZ.animateTo(0f, spring(dampingRatio = 0.44f, stiffness = Spring.StiffnessLow))
                                    sloshWaveSurge.animateTo(0f)
                                }
                            }
                        )
                    }
            ) {
                // Glass Tumbler Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 32.dp, bottomEnd = 32.dp))
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Frosted Back glass fill
                    drawRoundRect(
                        color = Color(0xFFF1F5F9).copy(alpha = 0.35f),
                        size = size,
                        cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx())
                    )

                    // 2. Liquid Waves Simulation
                    if (animatedProgress > 0.01f) {
                        val baseWaterLevelY = h * (1f - animatedProgress)
                        val surge = sloshWaveSurge.value.dp.toPx()
                        val amp1 = (6.dp.toPx() + surge).coerceIn(4f, 26f)
                        val amp2 = (4.dp.toPx() + surge * 0.7f).coerceIn(3f, 20f)
                        val wavelength = w * 0.9f

                        // Layer 1: Back Wave (darker azure)
                        val backWavePath = Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, (baseWaterLevelY - 4.dp.toPx()).coerceAtLeast(0f))
                            var x = 0f
                            while (x <= w) {
                                val y = (baseWaterLevelY - 4.dp.toPx()) + amp2 * sin((x / wavelength * 2 * PI + wavePhase2).toDouble()).toFloat()
                                lineTo(x, y.coerceAtLeast(0f))
                                x += 4f
                            }
                            lineTo(w, h)
                            close()
                        }
                        drawPath(
                            path = backWavePath,
                            color = Color(0xFF2563EB).copy(alpha = 0.45f)
                        )

                        // Layer 2: Front Wave (vibrant gradient water)
                        val frontWavePath = Path().apply {
                            moveTo(0f, h)
                            lineTo(0f, baseWaterLevelY.coerceAtLeast(0f))
                            var x = 0f
                            while (x <= w) {
                                val y = baseWaterLevelY + amp1 * sin((x / wavelength * 2 * PI + wavePhase1).toDouble()).toFloat()
                                lineTo(x, y.coerceAtLeast(0f))
                                x += 4f
                            }
                            lineTo(w, h)
                            close()
                        }
                        drawPath(
                            path = frontWavePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF60A5FA),
                                    Color(0xFF3B82F6),
                                    Color(0xFF1D4ED8)
                                ),
                                startY = baseWaterLevelY,
                                endY = h
                            )
                        )

                        // 3. Effervescent Rising Bubbles
                        val bubbleCount = 7
                        for (i in 0 until bubbleCount) {
                            val bubbleX = w * (0.20f + (i * 0.10f))
                            val speedFactor = 1f + (i % 3) * 0.3f
                            val rawY = (bubbleFloatOffset * speedFactor + (i * 0.15f)) % 1f
                            val bubbleY = h - (rawY * (h - baseWaterLevelY))
                            if (bubbleY >= baseWaterLevelY && bubbleY <= h - 10f) {
                                val radius = (3.dp.toPx() + (i % 3).dp.toPx())
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.50f),
                                    radius = radius,
                                    center = Offset(bubbleX, bubbleY)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    radius = radius * 0.4f,
                                    center = Offset(bubbleX - radius * 0.3f, bubbleY - radius * 0.3f)
                                )
                            }
                        }
                    }

                    // 4. Specular Curved Gloss Highlight on Glass Wall (Left Side Reflection)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startX = 8.dp.toPx(),
                            endX = 36.dp.toPx()
                        ),
                        topLeft = Offset(10.dp.toPx(), 8.dp.toPx()),
                        size = Size(20.dp.toPx(), h - 24.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )

                    // 5. Outer Glass Outline & Rim Lip
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.70f),
                        style = Stroke(width = 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx())
                    )
                }

                // Centered Information Overlay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = if (isGoalReached) "👑" else "💧",
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = if (animatedProgress > 0.45f) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of $goal glasses",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (animatedProgress > 0.45f) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (animatedProgress > 0.45f) Color.Black.copy(alpha = 0.25f) else PhasesWaterBlue.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${count * 250} ml",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (animatedProgress > 0.45f) Color.White else PhasesWaterBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Guidance hint
            Text(
                text = when {
                    count == 0 -> "Tap the glass to log your first sip 💧"
                    count < goal / 2 -> "Sipping steadily! Tap or drag glass to slosh 🌊"
                    count < goal -> "Almost there! Feeling refreshed & glowing ✨"
                    else -> "Goal achieved! Fully hydrated queen 💧👑"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
