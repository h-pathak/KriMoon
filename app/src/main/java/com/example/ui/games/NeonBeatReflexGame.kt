package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class BeatReflexMode(val title: String) {
    BEAT("Neon Beat 🎵"),
    REFLEX("Reaction Reflex ⚡")
}

private data class BeatTile(
    val id: Int,
    val lane: Int, // 0..3
    var yFrac: Float // 0f top -> 1f bottom
)

private enum class ReflexStage {
    READY, WAITING, GO, TOO_SOON, RESULT
}

@Composable
fun NeonBeatReflexGame(
    bestBeatScore: Int,
    bestReflexMs: Int,
    soundEnabled: Boolean,
    onBeatScoreSaved: (score: Int, streak: Int) -> Unit,
    onReflexSaved: (avgMs: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(BeatReflexMode.BEAT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sub-mode switcher
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                BeatReflexMode.values().forEach { item ->
                    val selected = mode == item
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) PhasesPlum else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { mode = item }
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (mode) {
            BeatReflexMode.BEAT -> NeonBeatStage(
                bestScore = bestBeatScore,
                soundEnabled = soundEnabled,
                onScoreSaved = onBeatScoreSaved,
                modifier = Modifier.weight(1f)
            )
            BeatReflexMode.REFLEX -> ReactionReflexStage(
                bestMs = bestReflexMs,
                soundEnabled = soundEnabled,
                onReflexSaved = onReflexSaved,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NeonBeatStage(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current

    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(5) }
    var judgement by remember { mutableStateOf("Tap lanes as glow notes hit the zone! 🎵") }
    var isGameOver by remember { mutableStateOf(false) }
    var tiles by remember { mutableStateOf<List<BeatTile>>(emptyList()) }
    var nextId by remember { mutableIntStateOf(1) }
    var spawnAccum by remember { mutableFloatStateOf(0f) }

    val laneColors = listOf(PhasesRose, PhasesPlum, PhasesLavender, PhasesSage)
    val laneEmojis = listOf("🌸", "✨", "🌙", "💖")
    val hitZoneY = 0.84f

    fun restart() {
        score = 0
        combo = 0
        maxCombo = 0
        lives = 5
        judgement = "Feel the beat! 🎶"
        tiles = emptyList()
        spawnAccum = 0f
        isGameOver = false
    }

    LaunchedEffect(isGameOver) {
        if (isGameOver) return@LaunchedEffect
        var lastNano = 0L
        while (isActive && !isGameOver) {
            withFrameNanos { now ->
                if (lastNano == 0L) {
                    lastNano = now
                    return@withFrameNanos
                }
                val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.002f, 0.04f)
                lastNano = now

                val speed = (0.36f + (score / 4000f)).coerceAtMost(0.78f)
                val spawnInterval = (0.62f - (score / 12000f)).coerceAtLeast(0.28f)
                spawnAccum += dt

                val moved = mutableListOf<BeatTile>()
                var missed = 0
                for (tile in tiles) {
                    val ny = tile.yFrac + speed * dt
                    if (ny > 0.98f) {
                        missed++
                    } else {
                        moved.add(tile.copy(yFrac = ny))
                    }
                }
                if (missed > 0) {
                    combo = 0
                    lives = (lives - missed).coerceAtLeast(0)
                    judgement = "Miss! 💔"
                    if (lives <= 0) {
                        isGameOver = true
                        onScoreSaved(score, maxCombo)
                    }
                }

                if (spawnAccum >= spawnInterval && !isGameOver) {
                    spawnAccum = 0f
                    moved.add(BeatTile(id = nextId++, lane = Random.nextInt(4), yFrac = -0.05f))
                }
                tiles = moved
            }
        }
    }

    fun tapLane(lane: Int) {
        if (isGameOver) return
        val hit = tiles.filter { it.lane == lane && abs(it.yFrac - hitZoneY) <= 0.14f }
            .minByOrNull { abs(it.yFrac - hitZoneY) }
        if (hit != null) {
            val dist = abs(hit.yFrac - hitZoneY)
            val isPerfect = dist <= 0.065f
            combo++
            if (combo > maxCombo) maxCombo = combo
            val pts = (if (isPerfect) 100 else 50) * (1 + (combo / 8))
            score += pts
            judgement = if (isPerfect) "PERFECT! +$pts 🔥" else "GOOD! +$pts ✨"
            tiles = tiles.filterNot { it.id == hit.id }
            haptic.performHapticFeedback(if (isPerfect) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            if (soundEnabled) {
                SoundHelper.playSound(
                    context,
                    if (isPerfect) SoundHelper.SOUND_SPARKLE_BELL else SoundHelper.SOUND_SWEET_BUBBLE,
                    0.8f
                )
            }
            if (combo > 0 && combo % 10 == 0) {
                particleController.spawn(500f, 650f, listOf("🔥", "✨", "🎵", "💖"), 12)
            }
        } else {
            combo = 0
            judgement = "Early/Empty tap! 👀"
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Score: $score (Best: ${maxOf(score, bestScore)})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PhasesPlum)
            Text("🔥 ${combo}x   " + "❤️".repeat(lives.coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(judgement, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171126))
                .border(2.dp, PhasesPlum.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val laneW = size.width / 4f
                for (i in 1..3) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.10f),
                        start = Offset(i * laneW, 0f),
                        end = Offset(i * laneW, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Hit Target Bar
                val hitCenterY = size.height * hitZoneY
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(8f, hitCenterY - 24.dp.toPx()),
                    size = Size(size.width - 16f, 48.dp.toPx()),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Notes
                tiles.forEach { tile ->
                    val cx = tile.lane * laneW + laneW / 2f
                    val cy = tile.yFrac * size.height
                    val c = laneColors[tile.lane]
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), Color.White.copy(alpha = 0.7f))),
                        topLeft = Offset(cx - laneW * 0.34f, cy - 16.dp.toPx()),
                        size = Size(laneW * 0.68f, 32.dp.toPx()),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }
            }

            // 4 Tap Pads pinned at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (lane in 0..3) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = laneColors[lane].copy(alpha = 0.25f),
                        border = BorderStroke(1.5.dp, laneColors[lane]),
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { tapLane(lane) }
                            .testTag("beat_lane_$lane")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(laneEmojis[lane], fontSize = 24.sp)
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎵 Track Complete!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Score: $score · Max Combo: ${maxCombo}x", color = PhasesPlum, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { restart() }, colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)) {
                                Text("Replay Beat 🎶")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionReflexStage(
    bestMs: Int,
    soundEnabled: Boolean,
    onReflexSaved: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current

    var stage by remember { mutableStateOf(ReflexStage.READY) }
    var roundIndex by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Int>>(emptyList()) }
    var goTimestamp by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableIntStateOf(0) }

    LaunchedEffect(stage, roundIndex) {
        if (stage == ReflexStage.WAITING) {
            delay(Random.nextLong(1100L, 3200L))
            goTimestamp = System.currentTimeMillis()
            stage = ReflexStage.GO
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleTap() {
        when (stage) {
            ReflexStage.READY -> {
                results = emptyList()
                roundIndex = 1
                stage = ReflexStage.WAITING
            }
            ReflexStage.WAITING -> {
                stage = ReflexStage.TOO_SOON
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            ReflexStage.GO -> {
                val reaction = (System.currentTimeMillis() - goTimestamp).toInt().coerceAtLeast(1)
                lastMs = reaction
                val updated = results + reaction
                results = updated
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (soundEnabled) SoundHelper.playCuteChime(context)
                if (updated.size >= 5) {
                    val avg = updated.average().toInt()
                    onReflexSaved(avg)
                    stage = ReflexStage.RESULT
                    particleController.spawn(500f, 650f, listOf("⚡", "✨", "🏆", "🌸"), 14)
                } else {
                    roundIndex++
                    stage = ReflexStage.WAITING
                }
            }
            ReflexStage.TOO_SOON -> {
                stage = ReflexStage.WAITING
            }
            ReflexStage.RESULT -> {
                results = emptyList()
                roundIndex = 1
                stage = ReflexStage.WAITING
            }
        }
    }

    val bgColor = when (stage) {
        ReflexStage.READY -> PhasesPlum.copy(alpha = 0.16f)
        ReflexStage.WAITING -> Color(0xFF4A1D40)
        ReflexStage.GO -> PhasesSage
        ReflexStage.TOO_SOON -> PhasesRose
        ReflexStage.RESULT -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
            .clickable { handleTap() }
            .testTag("reaction_reflex_pad"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            if (bestMs > 0) {
                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                    Text(
                        "🏆 Best Avg: ${bestMs}ms",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = PhasesPlum,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            when (stage) {
                ReflexStage.READY -> {
                    Text("⚡", fontSize = 62.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("5-Round Reflex Sprint", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap anywhere to start! Wait for green before tapping.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReflexStage.WAITING -> {
                    Text("👀", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Round $roundIndex / 5 · Wait for Green...", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    if (lastMs > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Last: ${lastMs}ms", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                ReflexStage.GO -> {
                    Text("⚡ TAP NOW!", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                }
                ReflexStage.TOO_SOON -> {
                    Text("😅 Too Soon!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap to retry round $roundIndex", color = Color.White.copy(alpha = 0.9f))
                }
                ReflexStage.RESULT -> {
                    val avg = if (results.isNotEmpty()) results.average().toInt() else 0
                    val rank = when {
                        avg < 210 -> "GenZ Lightning God ⚡"
                        avg < 270 -> "Cracked Reflexes 🔥"
                        avg < 340 -> "Snappy Vibes ✨"
                        else -> "Cozy Chill Mode 🌙"
                    }
                    Text("🏁 ${avg}ms avg", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif), color = PhasesPlum)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(rank, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rounds: ${results.joinToString(" · ") { "${it}ms" }}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tap anywhere to run again!", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PhasesPlum)
                }
            }
        }
    }
}
