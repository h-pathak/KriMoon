package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

private enum class SnakeDir(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun isOpposite(other: SnakeDir): Boolean = dx + other.dx == 0 && dy + other.dy == 0
}

@Composable
fun MoonlightSnakeGame(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (score: Int, length: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)
    val latestOnScoreSaved by rememberUpdatedState(onScoreSaved)

    val gridSize = 14

    fun spawnFood(occupied: List<Pair<Int, Int>>, avoid: Pair<Int, Int>? = null): Pair<Int, Int> {
        val taken = occupied.toSet()
        val free = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                val p = x to y
                if (p !in taken && p != avoid) free.add(p)
            }
        }
        return if (free.isNotEmpty()) free.random() else (0 to 0)
    }

    var wrapWalls by remember { mutableStateOf(true) }
    var snake by remember { mutableStateOf(listOf(7 to 7, 6 to 7, 5 to 7)) }
    var direction by remember { mutableStateOf(SnakeDir.RIGHT) }
    var pendingDirection by remember { mutableStateOf(SnakeDir.RIGHT) }
    var food by remember { mutableStateOf(spawnFood(listOf(7 to 7, 6 to 7, 5 to 7))) }
    var bonusOrb by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var bonusTicksLeft by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }

    fun setDir(next: SnakeDir) {
        if (!next.isOpposite(direction)) {
            pendingDirection = next
        }
    }

    fun restart() {
        val initial = listOf(7 to 7, 6 to 7, 5 to 7)
        snake = initial
        direction = SnakeDir.RIGHT
        pendingDirection = SnakeDir.RIGHT
        food = spawnFood(initial)
        bonusOrb = null
        bonusTicksLeft = 0
        score = 0
        isGameOver = false
    }

    LaunchedEffect(isGameOver, wrapWalls) {
        if (isGameOver) return@LaunchedEffect
        while (isActive && !isGameOver) {
            val tickMs = (195L - (score / 12L)).coerceAtLeast(88L)
            delay(tickMs)

            direction = pendingDirection
            val head = snake.first()
            var nx = head.first + direction.dx
            var ny = head.second + direction.dy

            if (wrapWalls) {
                nx = (nx + gridSize) % gridSize
                ny = (ny + gridSize) % gridSize
            } else if (nx !in 0 until gridSize || ny !in 0 until gridSize) {
                isGameOver = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                latestOnScoreSaved(score, snake.size)
                break
            }

            val nextHead = nx to ny
            val ateFood = nextHead == food
            val ateBonus = bonusOrb != null && nextHead == bonusOrb
            val bodyToCheck = if (ateFood || ateBonus) snake else snake.dropLast(1)

            if (nextHead in bodyToCheck) {
                isGameOver = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                latestOnScoreSaved(score, snake.size)
                break
            }

            val nextSnake = if (ateFood || ateBonus) {
                listOf(nextHead) + snake
            } else {
                listOf(nextHead) + snake.dropLast(1)
            }
            snake = nextSnake

            if (ateFood) {
                score += 10
                food = spawnFood(nextSnake, bonusOrb)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.7f)
                if (bonusOrb == null && Random.nextFloat() < 0.28f) {
                    bonusOrb = spawnFood(nextSnake, food)
                    bonusTicksLeft = 28
                }
            } else if (ateBonus) {
                score += 35
                bonusOrb = null
                bonusTicksLeft = 0
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (latestSoundEnabled) SoundHelper.playCuteChime(context)
                particleController.spawn(500f, 520f, listOf("⭐", "🌙", "✨"), 10)
            } else if (bonusOrb != null) {
                bonusTicksLeft--
                if (bonusTicksLeft <= 0) bonusOrb = null
            }
        }
    }

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // HUD + Wall mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesSage.copy(alpha = 0.16f)) {
                Text(
                    text = "🐍 $score  ·  Best: ${maxOf(score, bestScore)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = PhasesSage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable {
                        wrapWalls = !wrapWalls
                        restart()
                    }
            ) {
                Text(
                    text = if (wrapWalls) "Portal Walls 🌀" else "Classic Walls 🧱",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Square Neon Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF140D22))
                .border(2.dp, PhasesLavender.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .pointerInput(isGameOver) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            dragX += drag.x
                            dragY += drag.y
                        },
                        onDragEnd = {
                            if (abs(dragX) > abs(dragY) && abs(dragX) > 22f) {
                                setDir(if (dragX > 0f) SnakeDir.RIGHT else SnakeDir.LEFT)
                            } else if (abs(dragY) > 22f) {
                                setDir(if (dragY > 0f) SnakeDir.DOWN else SnakeDir.UP)
                            }
                        }
                    )
                }
                .testTag("moonlight_snake_board")
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val cell = size.width / gridSize

                // Subtle grid dots
                for (x in 0 until gridSize) {
                    for (y in 0 until gridSize) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = 1.5.dp.toPx(),
                            center = Offset(x * cell + cell / 2f, y * cell + cell / 2f)
                        )
                    }
                }

                // Rose Starlight Food
                val fx = food.first * cell + cell / 2f
                val fy = food.second * cell + cell / 2f
                drawCircle(PhasesRose.copy(alpha = 0.28f), radius = cell * 0.56f, center = Offset(fx, fy))
                drawCircle(Color(0xFFF43F5E), radius = cell * 0.34f, center = Offset(fx, fy))

                // Golden Timed Bonus Orb
                bonusOrb?.let { b ->
                    val bx = b.first * cell + cell / 2f
                    val by = b.second * cell + cell / 2f
                    drawCircle(Color(0xFFFDE047).copy(alpha = 0.32f), radius = cell * 0.6f, center = Offset(bx, by))
                    drawCircle(Color(0xFFFACC15), radius = cell * 0.36f, center = Offset(bx, by))
                }

                // Glowing Snake Body
                snake.forEachIndexed { idx, (sx, sy) ->
                    val ratio = 1f - (idx.toFloat() / (snake.size + 2f)).coerceIn(0f, 0.7f)
                    val color = if (idx == 0) Color(0xFF34D399) else PhasesSage.copy(alpha = 0.45f + ratio * 0.5f)
                    val inset = cell * 0.10f
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(sx * cell + inset, sy * cell + inset),
                        size = Size(cell - inset * 2f, cell - inset * 2f),
                        cornerRadius = CornerRadius(cell * 0.32f, cell * 0.32f)
                    )
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
                                "🐍 Cozy Tangle!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                "Score: $score · Length: ${snake.size}",
                                color = PhasesPlum,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { restart() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Slither Again 🌙", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Tactile D-pad for effortless one-hand play
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "⬅️" to SnakeDir.LEFT,
                "⬆️" to SnakeDir.UP,
                "⬇️" to SnakeDir.DOWN,
                "➡️" to SnakeDir.RIGHT
            ).forEach { (arrow, dir) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { setDir(dir) }
                ) {
                    Text(arrow, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}
