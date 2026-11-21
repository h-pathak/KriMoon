package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

private data class MergeTier(
    val level: Int,
    val emoji: String,
    val name: String,
    val radiusDp: Float,
    val color: Color,
    val points: Int
)

private val MERGE_TIERS = listOf(
    MergeTier(0, "🍒", "Cherry", 16f, Color(0xFFFF4D6D), 10),
    MergeTier(1, "🍓", "Berry", 21f, Color(0xFFFF758F), 25),
    MergeTier(2, "🍊", "Tangerine", 26f, Color(0xFFFF9F1C), 50),
    MergeTier(3, "🍑", "Peach", 31f, Color(0xFFFFB5A7), 90),
    MergeTier(4, "🌟", "Starfruit", 37f, Color(0xFFFFD166), 150),
    MergeTier(5, "🌙", "Crescent", 44f, Color(0xFFA78BFA), 240),
    MergeTier(6, "🌕", "Full Moon", 52f, Color(0xFFFDE047), 400),
    MergeTier(7, "🌸", "KriMoon", 61f, Color(0xFFE879F9), 750)
)

private data class MergeOrb(
    val id: Int,
    val tier: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    var ageSec: Float = 0f
)

@Composable
fun MoonMergeGame(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (score: Int, streak: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var score by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var mergeStreak by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }
    var currentTier by remember { mutableIntStateOf(Random.nextInt(0, 4)) }
    var nextTier by remember { mutableIntStateOf(Random.nextInt(0, 4)) }
    var aimXFraction by remember { mutableFloatStateOf(0.5f) }
    var dropCooldown by remember { mutableFloatStateOf(0f) }
    var orbs by remember { mutableStateOf<List<MergeOrb>>(emptyList()) }
    var nextOrbId by remember { mutableIntStateOf(1) }
    var frameTick by remember { mutableIntStateOf(0) }

    var boxWidthPx by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)
    val latestOnScoreSaved by rememberUpdatedState(onScoreSaved)

    fun resetGame() {
        orbs = emptyList()
        score = 0
        mergeStreak = 0
        maxCombo = 0
        isGameOver = false
        currentTier = Random.nextInt(0, 4)
        nextTier = Random.nextInt(0, 4)
        aimXFraction = 0.5f
        dropCooldown = 0f
    }

    fun dropCurrentOrb() {
        if (isGameOver || dropCooldown > 0f || boxWidthPx <= 0f) return
        val tierMeta = MERGE_TIERS[currentTier]
        val radiusPx = with(density) { tierMeta.radiusDp.dp.toPx() }
        val clampedX = (aimXFraction * boxWidthPx).coerceIn(radiusPx + 8f, boxWidthPx - radiusPx - 8f)
        val spawnY = with(density) { 46.dp.toPx() }
        val newOrb = MergeOrb(
            id = nextOrbId++,
            tier = currentTier,
            x = clampedX,
            y = spawnY,
            vx = Random.nextFloat() * 14f - 7f,
            vy = 60f,
            radius = radiusPx
        )
        orbs = orbs + newOrb
        currentTier = nextTier
        nextTier = Random.nextInt(0, 4)
        dropCooldown = 0.34f
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.7f)
    }

    // 60fps Suika Physics Loop
    LaunchedEffect(isGameOver, boxWidthPx, boxHeightPx) {
        if (isGameOver || boxWidthPx <= 0f || boxHeightPx <= 0f) return@LaunchedEffect
        var lastNano = 0L
        val gravity = with(density) { 1250.dp.toPx() }
        val dangerY = with(density) { 74.dp.toPx() }

        while (isActive && !isGameOver) {
            withFrameNanos { now ->
                if (lastNano == 0L) {
                    lastNano = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.002f, 0.032f)
                lastNano = now
                if (dropCooldown > 0f) dropCooldown = max(0f, dropCooldown - dt)

                val list = orbs.map { it.copy() }.toMutableList()
                val toRemove = mutableSetOf<Int>()
                val toAdd = mutableListOf<MergeOrb>()
                var mergedAny = false

                for (orb in list) {
                    orb.ageSec += dt
                    orb.vy += gravity * dt
                    orb.x += orb.vx * dt
                    orb.y += orb.vy * dt
                    orb.vx *= (1f - 1.1f * dt)

                    if (orb.x - orb.radius < 4f) {
                        orb.x = 4f + orb.radius
                        orb.vx = kotlin.math.abs(orb.vx) * 0.45f
                    } else if (orb.x + orb.radius > boxWidthPx - 4f) {
                        orb.x = boxWidthPx - 4f - orb.radius
                        orb.vx = -kotlin.math.abs(orb.vx) * 0.45f
                    }
                    if (orb.y + orb.radius > boxHeightPx - 6f) {
                        orb.y = boxHeightPx - 6f - orb.radius
                        orb.vy = -orb.vy * 0.28f
                        if (kotlin.math.abs(orb.vy) < 18f) orb.vy = 0f
                        orb.vx *= 0.88f
                    }
                }

                // Pairwise collision & merging (2 relaxation passes for stability)
                for (pass in 0..1) {
                    for (i in 0 until list.size) {
                        val a = list[i]
                        if (a.id in toRemove) continue
                        for (j in i + 1 until list.size) {
                            val b = list[j]
                            if (b.id in toRemove) continue
                            val dx = b.x - a.x
                            val dy = b.y - a.y
                            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
                            val minDist = a.radius + b.radius
                            if (dist < minDist) {
                                if (pass == 0 && a.tier == b.tier && a.tier < MERGE_TIERS.lastIndex) {
                                    toRemove.add(a.id)
                                    toRemove.add(b.id)
                                    val newTierIdx = a.tier + 1
                                    val newTier = MERGE_TIERS[newTierIdx]
                                    val newRadiusPx = with(density) { newTier.radiusDp.dp.toPx() }
                                    toAdd.add(
                                        MergeOrb(
                                            id = nextOrbId++,
                                            tier = newTierIdx,
                                            x = ((a.x + b.x) / 2f).coerceIn(newRadiusPx + 6f, boxWidthPx - newRadiusPx - 6f),
                                            y = ((a.y + b.y) / 2f).coerceAtMost(boxHeightPx - newRadiusPx - 6f),
                                            vx = (a.vx + b.vx) * 0.3f,
                                            vy = -120f,
                                            radius = newRadiusPx,
                                            ageSec = 0.5f
                                        )
                                    )
                                    score += newTier.points
                                    mergeStreak++
                                    if (mergeStreak > maxCombo) maxCombo = mergeStreak
                                    mergedAny = true
                                } else {
                                    val overlap = (minDist - dist) * 0.52f
                                    val nx = dx / dist
                                    val ny = dy / dist
                                    a.x -= nx * overlap
                                    a.y -= ny * overlap
                                    b.x += nx * overlap
                                    b.y += ny * overlap
                                    val rvx = b.vx - a.vx
                                    val rvy = b.vy - a.vy
                                    val velAlongNormal = rvx * nx + rvy * ny
                                    if (velAlongNormal < 0f) {
                                        val impulse = -1.25f * velAlongNormal * 0.5f
                                        a.vx -= impulse * nx
                                        a.vy -= impulse * ny
                                        b.vx += impulse * nx
                                        b.vy += impulse * ny
                                    }
                                }
                            }
                        }
                    }
                }

                if (mergedAny) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (latestSoundEnabled) SoundHelper.playCuteChime(context)
                    particleController.spawn(
                        centerX = boxWidthPx * aimXFraction,
                        centerY = boxHeightPx * 0.45f,
                        emojis = listOf("🌙", "✨", "🌸", "⭐", "💖"),
                        count = 10
                    )
                }

                val updated = list.filterNot { it.id in toRemove } + toAdd
                orbs = updated
                frameTick++

                // Overflow check: any settled orb sticking above danger line triggers Game Over
                val overflowed = updated.any { it.ageSec > 1.8f && (it.y - it.radius) < dangerY && kotlin.math.abs(it.vy) < 40f }
                if (overflowed) {
                    isGameOver = true
                    latestOnScoreSaved(score, maxCombo)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score & Next Orb Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$score",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif),
                            color = PhasesPlum
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${maxOf(score, bestScore)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PhasesRose
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NEXT ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MERGE_TIERS[nextTier].emoji, fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Evolution Chain Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MERGE_TIERS.forEach { tier ->
                Text(tier.emoji, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Physics Play Jar
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            PhasesPlum.copy(alpha = 0.14f)
                        )
                    )
                )
                .border(2.dp, PhasesPlum.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                .pointerInput(isGameOver) {
                    detectTapGestures { offset ->
                        if (!isGameOver && size.width > 0) {
                            aimXFraction = (offset.x / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                            dropCurrentOrb()
                        }
                    }
                }
                .pointerInput(isGameOver) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            if (!isGameOver && size.width > 0) {
                                aimXFraction = (change.position.x / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                            }
                        },
                        onDragEnd = { dropCurrentOrb() }
                    )
                }
                .testTag("moon_merge_canvas")
        ) {
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            LaunchedEffect(maxW, maxH) {
                boxWidthPx = maxW
                boxHeightPx = maxH
            }
            val currentMeta = MERGE_TIERS[currentTier]
            val currentRadiusPx = with(density) { currentMeta.radiusDp.dp.toPx() }
            val dangerY = with(density) { 74.dp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Force redraw per tick
                val tick = frameTick
                if (tick < 0) return@Canvas

                // Danger line
                drawLine(
                    color = PhasesRose.copy(alpha = 0.55f),
                    start = Offset(12f, dangerY),
                    end = Offset(size.width - 12f, dangerY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                )

                // Aim drop guide + pending orb
                if (!isGameOver) {
                    val aimX = (aimXFraction * size.width).coerceIn(currentRadiusPx + 8f, size.width - currentRadiusPx - 8f)
                    drawLine(
                        color = currentMeta.color.copy(alpha = 0.3f),
                        start = Offset(aimX, 46.dp.toPx()),
                        end = Offset(aimX, size.height - 16f),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                    drawCircle(
                        color = currentMeta.color.copy(alpha = 0.25f),
                        radius = currentRadiusPx,
                        center = Offset(aimX, 34.dp.toPx())
                    )
                    drawCircle(
                        color = currentMeta.color,
                        radius = currentRadiusPx,
                        center = Offset(aimX, 34.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    val measured = textMeasurer.measure(
                        currentMeta.emoji,
                        style = TextStyle(fontSize = (currentMeta.radiusDp * 0.9f).sp)
                    )
                    drawText(
                        measured,
                        topLeft = Offset(
                            aimX - measured.size.width / 2f,
                            34.dp.toPx() - measured.size.height / 2f
                        )
                    )
                }

                // Active falling & merged orbs
                for (orb in orbs) {
                    val tier = MERGE_TIERS[orb.tier]
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.45f), tier.color.copy(alpha = 0.85f)),
                            center = Offset(orb.x - orb.radius * 0.25f, orb.y - orb.radius * 0.25f),
                            radius = orb.radius * 1.1f
                        ),
                        radius = orb.radius,
                        center = Offset(orb.x, orb.y)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = orb.radius,
                        center = Offset(orb.x, orb.y),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    val measured = textMeasurer.measure(
                        tier.emoji,
                        style = TextStyle(fontSize = (tier.radiusDp * 0.92f).sp)
                    )
                    drawText(
                        measured,
                        topLeft = Offset(
                            orb.x - measured.size.width / 2f,
                            orb.y - measured.size.height / 2f
                        )
                    )
                }
            }

            if (isGameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🌕 Overflow!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Score: $score · Best: ${maxOf(score, bestScore)}", style = MaterialTheme.typography.bodyLarge, color = PhasesPlum)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                modifier = Modifier.testTag("moon_merge_restart")
                            ) {
                                Text("Play Again 🌙")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap or slide anywhere in the jar to drop & merge up to 🌸 KriMoon!",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
