package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

private data class RunnerObstacle(
    val id: Int,
    var x: Float,
    val yOffsetFromGround: Float,
    val width: Float,
    val height: Float,
    val isStarShard: Boolean
)

@Composable
fun MoonHopperGame(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (score: Int, starsCollected: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var score by remember { mutableIntStateOf(0) }
    var starsCollected by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }
    var isStarted by remember { mutableStateOf(false) }

    // Runner vertical physics (0f = standing on ground, positive = above ground)
    var playerY by remember { mutableFloatStateOf(0f) }
    var playerVy by remember { mutableFloatStateOf(0f) }
    var jumpsLeft by remember { mutableIntStateOf(2) }
    var obstacles by remember { mutableStateOf<List<RunnerObstacle>>(emptyList()) }
    var nextId by remember { mutableIntStateOf(1) }
    var spawnTimer by remember { mutableFloatStateOf(0f) }
    var distanceFloat by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)
    val latestOnScoreSaved by rememberUpdatedState(onScoreSaved)

    fun jumpOrStart() {
        if (isGameOver) return
        if (!isStarted) {
            isStarted = true
        }
        if (jumpsLeft > 0) {
            playerVy = with(density) { 540.dp.toPx() }
            jumpsLeft--
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.7f)
        }
    }

    fun restart() {
        score = 0
        starsCollected = 0
        playerY = 0f
        playerVy = 0f
        jumpsLeft = 2
        obstacles = emptyList()
        spawnTimer = 0.6f
        distanceFloat = 0f
        isGameOver = false
        isStarted = true
    }

    LaunchedEffect(isStarted, isGameOver, widthPx, heightPx) {
        if (!isStarted || isGameOver || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
        var lastNano = 0L
        val gravity = with(density) { 1550.dp.toPx() }
        val playerX = widthPx * 0.2f
        val playerSize = with(density) { 36.dp.toPx() }

        while (isActive && isStarted && !isGameOver) {
            withFrameNanos { now ->
                if (lastNano == 0L) {
                    lastNano = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.002f, 0.032f)
                lastNano = now

                // Player vertical physics
                playerY += playerVy * dt
                playerVy -= gravity * dt
                if (playerY <= 0f) {
                    playerY = 0f
                    playerVy = 0f
                    jumpsLeft = 2
                }

                val speed = with(density) { (250f + (score / 12f)).coerceAtMost(540f).dp.toPx() }
                distanceFloat += dt * 18f
                score = distanceFloat.toInt() + (starsCollected * 25)

                spawnTimer -= dt
                val updated = mutableListOf<RunnerObstacle>()
                var hitHazard = false
                var collectedStar = false

                for (ob in obstacles) {
                    val nx = ob.x - speed * dt
                    if (nx + ob.width > -40f) {
                        val dx = abs((playerX + playerSize * 0.45f) - (nx + ob.width * 0.5f))
                        val playerCenterY = playerY + playerSize * 0.45f
                        val obCenterY = ob.yOffsetFromGround + ob.height * 0.5f
                        val dy = abs(playerCenterY - obCenterY)
                        if (dx < (playerSize * 0.35f + ob.width * 0.35f) && dy < (playerSize * 0.35f + ob.height * 0.35f)) {
                            if (ob.isStarShard) {
                                starsCollected++
                                collectedStar = true
                                continue
                            } else {
                                hitHazard = true
                            }
                        }
                        updated.add(ob.copy(x = nx))
                    }
                }

                if (collectedStar) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (latestSoundEnabled) SoundHelper.playCuteChime(context)
                    particleController.spawn(playerX, heightPx * 0.55f, listOf("⭐", "✨", "🌙"), 8)
                }

                if (spawnTimer <= 0f) {
                    spawnTimer = Random.nextFloat() * 0.85f + 0.72f
                    val spawnStar = Random.nextFloat() < 0.38f
                    updated.add(
                        RunnerObstacle(
                            id = nextId++,
                            x = widthPx + 30f,
                            yOffsetFromGround = if (spawnStar) with(density) { Random.nextInt(40, 120).dp.toPx() } else 0f,
                            width = with(density) { if (spawnStar) 28.dp.toPx() else Random.nextInt(26, 38).dp.toPx() },
                            height = with(density) { if (spawnStar) 28.dp.toPx() else Random.nextInt(34, 58).dp.toPx() },
                            isStarShard = spawnStar
                        )
                    )
                }

                obstacles = updated
                frameTick++

                if (hitHazard) {
                    isGameOver = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestOnScoreSaved(score, starsCollected)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesPlum.copy(alpha = 0.14f)) {
                Text(
                    "🌙 Score: $score  ·  ⭐ $starsCollected",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Text("Best: ${maxOf(score, bestScore)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
        }

        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF150E26), Color(0xFF2B1B4D), Color(0xFF43235E))
                    )
                )
                .border(2.dp, PhasesLavender.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { jumpOrStart() })
                }
                .testTag("moon_hopper_canvas")
        ) {
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            LaunchedEffect(maxW, maxH) {
                widthPx = maxW
                heightPx = maxH
            }
            val groundY = maxH * 0.80f
            val moonMeasured = remember(textMeasurer) {
                textMeasurer.measure("🌙", style = TextStyle(fontSize = 32.sp))
            }
            val starMeasured = remember(textMeasurer) {
                textMeasurer.measure("⭐", style = TextStyle(fontSize = 24.sp))
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tick = frameTick
                if (tick < 0) return@Canvas

                // Twinkling stars
                for (i in 0..18) {
                    val sx = ((i * 97f - (score * 2f)) % size.width + size.width) % size.width
                    val sy = (i * 53f) % (groundY * 0.85f)
                    drawCircle(Color.White.copy(alpha = 0.45f), radius = 2.dp.toPx(), center = Offset(sx, sy))
                }

                // Ground glow ribbon
                drawRect(
                    brush = Brush.verticalGradient(listOf(PhasesPlum.copy(alpha = 0.85f), Color(0xFF1F1233))),
                    topLeft = Offset(0f, groundY),
                    size = Size(size.width, size.height - groundY)
                )

                // Player Crescent Moon
                val playerX = size.width * 0.2f
                val pSize = 36.dp.toPx()
                drawText(
                    moonMeasured,
                    topLeft = Offset(playerX, groundY - pSize - playerY)
                )

                // Obstacles & Star Shards
                obstacles.forEach { ob ->
                    if (ob.isStarShard) {
                        drawText(starMeasured, topLeft = Offset(ob.x, groundY - ob.height - ob.yOffsetFromGround))
                    } else {
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(PhasesRose, Color(0xFF831843))),
                            topLeft = Offset(ob.x, groundY - ob.height),
                            size = Size(ob.width, ob.height),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }
                }
            }

            if (!isStarted && !isGameOver) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, PhasesLavender)
                    ) {
                        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌙 Moon Hopper", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tap anywhere to jump & double-jump over meteors!\nCollect ⭐ stars for +25 bonus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Tap screen to launch 🚀", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PhasesPlum)
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💫 Meteor Bonk!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Distance Score: $score · Best: ${maxOf(score, bestScore)}", color = PhasesPlum, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { restart() }, colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)) {
                                Text("Hop Again 🌙")
                            }
                        }
                    }
                }
            }
        }
    }
}
