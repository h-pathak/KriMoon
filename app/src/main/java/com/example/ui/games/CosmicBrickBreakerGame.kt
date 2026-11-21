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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import kotlinx.coroutines.isActive
import kotlin.math.abs

private data class PrismBrick(
    val id: Int,
    val row: Int,
    val col: Int,
    var hp: Int,
    val color: Color
)

@Composable
fun CosmicBrickBreakerGame(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (score: Int, bestCombo: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)
    val latestOnScoreSaved by rememberUpdatedState(onScoreSaved)

    val cols = 6
    val rows = 5
    val rowColors = listOf(
        Color(0xFFE11D68),
        PhasesRose,
        Color(0xFFA855F7),
        Color(0xFF38BDF8),
        PhasesSage
    )

    fun buildWave(waveNum: Int): List<PrismBrick> {
        val list = mutableListOf<PrismBrick>()
        var id = 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val hp = if (r == 0 || (waveNum > 1 && r == 1)) 2 else 1
                list.add(PrismBrick(id++, r, c, hp, rowColors[r % rowColors.size]))
            }
        }
        return list
    }

    var wave by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var isStarted by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    var paddleXFrac by remember { mutableFloatStateOf(0.5f) }
    var ballX by remember { mutableFloatStateOf(0.5f) }
    var ballY by remember { mutableFloatStateOf(0.76f) }
    var ballVx by remember { mutableFloatStateOf(0.42f) }
    var ballVy by remember { mutableFloatStateOf(-0.62f) }
    var bricks by remember { mutableStateOf(buildWave(1)) }
    var frameTick by remember { mutableIntStateOf(0) }

    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }

    fun resetBallOnPaddle() {
        ballX = paddleXFrac
        ballY = 0.80f
        ballVx = if (paddleXFrac >= 0.5f) -0.42f else 0.42f
        ballVy = -(0.60f + (wave * 0.04f)).coerceAtMost(0.85f)
    }

    fun restart() {
        wave = 1
        score = 0
        combo = 0
        maxCombo = 0
        lives = 3
        paddleXFrac = 0.5f
        bricks = buildWave(1)
        isGameOver = false
        isStarted = true
        resetBallOnPaddle()
    }

    LaunchedEffect(isStarted, isGameOver, widthPx, heightPx) {
        if (!isStarted || isGameOver || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
        var lastNano = 0L
        val paddleHalfW = 0.135f
        val paddleY = 0.86f
        val ballRadiusFrac = 0.022f

        while (isActive && isStarted && !isGameOver) {
            withFrameNanos { now ->
                if (lastNano == 0L) {
                    lastNano = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.002f, 0.032f)
                lastNano = now

                var nx = ballX + ballVx * dt
                var ny = ballY + ballVy * dt
                var nvx = ballVx
                var nvy = ballVy

                // Wall bounces
                if (nx - ballRadiusFrac < 0.02f) {
                    nx = 0.02f + ballRadiusFrac
                    nvx = abs(nvx)
                } else if (nx + ballRadiusFrac > 0.98f) {
                    nx = 0.98f - ballRadiusFrac
                    nvx = -abs(nvx)
                }
                if (ny - ballRadiusFrac < 0.02f) {
                    ny = 0.02f + ballRadiusFrac
                    nvy = abs(nvy)
                }

                // Paddle bounce
                if (nvy > 0f && ny + ballRadiusFrac >= paddleY && ny <= paddleY + 0.045f &&
                    abs(nx - paddleXFrac) <= (paddleHalfW + ballRadiusFrac)
                ) {
                    ny = paddleY - ballRadiusFrac
                    val offset = ((nx - paddleXFrac) / paddleHalfW).coerceIn(-1f, 1f)
                    val speedBoost = (0.64f + wave * 0.04f).coerceAtMost(0.92f)
                    nvx = (offset * 0.68f).coerceIn(-0.78f, 0.78f)
                    nvy = -speedBoost
                    combo = 0
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.65f)
                }

                // Brick collisions
                val gridLeft = 0.04f
                val gridRight = 0.96f
                val gridTop = 0.08f
                val brickW = (gridRight - gridLeft) / cols
                val brickH = 0.056f
                val updatedBricks = bricks.map { it.copy() }.toMutableList()
                var hitBrick: PrismBrick? = null

                for (brick in updatedBricks) {
                    val bx0 = gridLeft + brick.col * brickW + 0.006f
                    val bx1 = gridLeft + (brick.col + 1) * brickW - 0.006f
                    val by0 = gridTop + brick.row * (brickH + 0.012f)
                    val by1 = by0 + brickH

                    if (nx + ballRadiusFrac >= bx0 && nx - ballRadiusFrac <= bx1 &&
                        ny + ballRadiusFrac >= by0 && ny - ballRadiusFrac <= by1
                    ) {
                        hitBrick = brick
                        val overlapX = minOf(nx + ballRadiusFrac - bx0, bx1 - (nx - ballRadiusFrac))
                        val overlapY = minOf(ny + ballRadiusFrac - by0, by1 - (ny - ballRadiusFrac))
                        if (overlapX < overlapY) nvx = -nvx else nvy = -nvy
                        break
                    }
                }

                if (hitBrick != null) {
                    hitBrick.hp -= 1
                    combo++
                    if (combo > maxCombo) maxCombo = combo
                    score += (25 + combo * 10)
                    if (hitBrick.hp <= 0) {
                        updatedBricks.removeAll { it.id == hitBrick.id }
                        if (latestSoundEnabled) SoundHelper.playCuteChime(context)
                        particleController.spawn(
                            centerX = nx * widthPx,
                            centerY = ny * heightPx,
                            emojis = listOf("✨", "☄️", "💖"),
                            count = 6
                        )
                    } else if (latestSoundEnabled) {
                        SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.6f)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    if (updatedBricks.isEmpty()) {
                        wave++
                        score += 200
                        bricks = buildWave(wave)
                        resetBallOnPaddle()
                    } else {
                        bricks = updatedBricks
                        ballX = nx
                        ballY = ny
                        ballVx = nvx
                        ballVy = nvy
                    }
                } else if (ny > 0.99f) {
                    // Missed ball
                    lives--
                    combo = 0
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (lives <= 0) {
                        isGameOver = true
                        latestOnScoreSaved(score, maxCombo)
                    } else {
                        resetBallOnPaddle()
                    }
                } else {
                    ballX = nx
                    ballY = ny
                    ballVx = nvx
                    ballVy = nvy
                }

                frameTick++
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesPlum.copy(alpha = 0.14f)) {
                Text(
                    text = "☄️ $score  ·  Wave $wave",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                text = "❤️".repeat(lives.coerceAtLeast(0)) + "  Best: ${maxOf(score, bestScore)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PhasesRose
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF140B24), Color(0xFF24113A), Color(0xFF341443))
                    )
                )
                .border(2.dp, PhasesLavender.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .pointerInput(isGameOver) {
                    detectTapGestures { offset ->
                        if (!isGameOver && size.width > 0) {
                            paddleXFrac = (offset.x / size.width.toFloat()).coerceIn(0.14f, 0.86f)
                            if (!isStarted) isStarted = true
                        }
                    }
                }
                .pointerInput(isGameOver) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (!isGameOver && size.width > 0) {
                            if (!isStarted) isStarted = true
                            paddleXFrac = (change.position.x / size.width.toFloat()).coerceIn(0.14f, 0.86f)
                        }
                    }
                }
                .testTag("cosmic_breaker_canvas")
        ) {
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            LaunchedEffect(maxW, maxH) {
                widthPx = maxW
                heightPx = maxH
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tick = frameTick
                if (tick < 0) return@Canvas

                val gridLeft = size.width * 0.04f
                val gridRight = size.width * 0.96f
                val gridTop = size.height * 0.08f
                val brickW = (gridRight - gridLeft) / cols
                val brickH = size.height * 0.056f
                val padX = size.width * 0.006f

                // Bricks
                bricks.forEach { brick ->
                    val x = gridLeft + brick.col * brickW + padX
                    val y = gridTop + brick.row * (brickH + size.height * 0.012f)
                    val w = brickW - padX * 2f
                    drawRoundRect(
                        color = brick.color.copy(alpha = if (brick.hp > 1) 1f else 0.78f),
                        topLeft = Offset(x, y),
                        size = Size(w, brickH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    if (brick.hp > 1) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.45f),
                            topLeft = Offset(x + 4.dp.toPx(), y + 3.dp.toPx()),
                            size = Size(w - 8.dp.toPx(), 3.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }

                // Glowing Paddle
                val paddleW = size.width * 0.27f
                val paddleH = 14.dp.toPx()
                val px = (paddleXFrac * size.width - paddleW / 2f).coerceIn(8f, size.width - paddleW - 8f)
                val py = size.height * 0.86f
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(PhasesRose, Color(0xFFF472B6), PhasesPlum)),
                    topLeft = Offset(px, py),
                    size = Size(paddleW, paddleH),
                    cornerRadius = CornerRadius(100f, 100f)
                )

                // Cosmic Orb Ball
                val bx = ballX * size.width
                val by = ballY * size.height
                val r = size.width * 0.022f
                drawCircle(Color(0xFFFDE047).copy(alpha = 0.25f), radius = r * 1.9f, center = Offset(bx, by))
                drawCircle(Color(0xFFFFF7ED), radius = r, center = Offset(bx, by))
            }

            if (!isStarted && !isGameOver) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, PhasesLavender)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "☄️ Cosmic Breaker",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                "Drag anywhere to steer the neon paddle.\nShatter prism waves and chain combos!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap or drag to launch ✨",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PhasesPlum
                            )
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "☄️ Wave $wave Complete!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                "Score: $score · Best Combo ×$maxCombo",
                                color = PhasesPlum,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { restart() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Play Again ☄️", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
