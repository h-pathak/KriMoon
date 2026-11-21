package com.example.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

private enum class MemoryGridSize(val label: String, val rows: Int, val cols: Int) {
    COZY("4×3", 3, 4),
    CLASSIC("4×4", 4, 4),
    COSMIC("5×4", 5, 4)
}

private data class MemoryCardTile(
    val id: Int,
    val symbol: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false
)

private val MEMORY_SYMBOLS = listOf(
    "🌙", "🌸", "⭐", "💎", "🦋", "🍓", "🔮", "🪐", "💌", "🫧"
)

@Composable
fun StarlightMemoryGame(
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

    var gridSize by remember { mutableStateOf(MemoryGridSize.CLASSIC) }

    fun createDeck(size: MemoryGridSize): List<MemoryCardTile> {
        val pairCount = (size.rows * size.cols) / 2
        val chosen = MEMORY_SYMBOLS.shuffled().take(pairCount)
        return (chosen + chosen).shuffled().mapIndexed { idx, sym ->
            MemoryCardTile(id = idx, symbol = sym)
        }
    }

    var cards by remember { mutableStateOf(createDeck(gridSize)) }
    var firstIdx by remember { mutableStateOf<Int?>(null) }
    var secondIdx by remember { mutableStateOf<Int?>(null) }
    var isCheckingMismatch by remember { mutableStateOf(false) }
    var moves by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var maxStreak by remember { mutableIntStateOf(0) }
    var isWon by remember { mutableStateOf(false) }

    fun restart(newSize: MemoryGridSize = gridSize) {
        gridSize = newSize
        cards = createDeck(newSize)
        firstIdx = null
        secondIdx = null
        isCheckingMismatch = false
        moves = 0
        score = 0
        streak = 0
        maxStreak = 0
        isWon = false
    }

    LaunchedEffect(isCheckingMismatch) {
        if (!isCheckingMismatch) return@LaunchedEffect
        delay(650)
        val a = firstIdx
        val b = secondIdx
        if (a != null && b != null) {
            cards = cards.mapIndexed { idx, tile ->
                if (idx == a || idx == b) tile.copy(isFaceUp = false) else tile
            }
        }
        firstIdx = null
        secondIdx = null
        isCheckingMismatch = false
    }

    fun onCardTap(index: Int) {
        if (isCheckingMismatch || isWon) return
        val tile = cards.getOrNull(index) ?: return
        if (tile.isFaceUp || tile.isMatched) return

        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.55f)

        val flipped = cards.mapIndexed { idx, c -> if (idx == index) c.copy(isFaceUp = true) else c }
        cards = flipped

        val prev = firstIdx
        if (prev == null) {
            firstIdx = index
        } else {
            secondIdx = index
            moves++
            val firstCard = flipped[prev]
            val secondCard = flipped[index]
            if (firstCard.symbol == secondCard.symbol) {
                streak++
                if (streak > maxStreak) maxStreak = streak
                val gain = 100 + (streak * 35)
                score += gain
                val matchedDeck = flipped.mapIndexed { idx, c ->
                    if (idx == prev || idx == index) c.copy(isMatched = true) else c
                }
                cards = matchedDeck
                firstIdx = null
                secondIdx = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (latestSoundEnabled) SoundHelper.playCuteChime(context)
                particleController.spawn(500f, 620f, listOf(firstCard.symbol, "✨", "⭐"), 10)

                if (matchedDeck.all { it.isMatched }) {
                    val efficiencyBonus = ((gridSize.rows * gridSize.cols * 18) - moves * 10).coerceAtLeast(50)
                    score += efficiencyBonus
                    isWon = true
                    latestOnScoreSaved(score, maxStreak)
                }
            } else {
                streak = 0
                isCheckingMismatch = true
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
        // Difficulty switcher
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                MemoryGridSize.entries.forEach { preset ->
                    val selected = gridSize == preset
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) PhasesPlum else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { restart(preset) }
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // HUD Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesPlum.copy(alpha = 0.14f)) {
                Text(
                    text = "Score: $score  ·  🔥 ×$streak",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                text = "Moves: $moves  ·  Best: ${maxOf(score, bestScore)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PhasesRose
            )
        }

        // Card Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("starlight_memory_grid"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until gridSize.rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until gridSize.cols) {
                            val idx = r * gridSize.cols + c
                            val tile = cards.getOrNull(idx)
                            if (tile != null) {
                                MemoryFlipCard(
                                    tile = tile,
                                    onClick = { onCardTap(idx) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.92f)
                                        .testTag("memory_card_$idx")
                                )
                            }
                        }
                    }
                }
            }

            if (isWon) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, PhasesLavender)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "✨ Constellation Cleared!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                "Score: $score · $moves moves · Max ×$maxStreak",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = PhasesPlum
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { restart(gridSize) },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Play Again 🃏", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryFlipCard(
    tile: MemoryCardTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (tile.isFaceUp || tile.isMatched) 180f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "memory_flip"
    )
    val showFront = rotation >= 90f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            tile.isMatched -> PhasesSage.copy(alpha = 0.2f)
            showFront -> MaterialTheme.colorScheme.surface
            else -> Color(0xFF27143A)
        },
        border = BorderStroke(
            width = 1.4.dp,
            color = when {
                tile.isMatched -> PhasesSage
                showFront -> PhasesRose.copy(alpha = 0.7f)
                else -> PhasesLavender.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !tile.isFaceUp && !tile.isMatched, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (!showFront) {
                        Brush.verticalGradient(listOf(Color(0xFF2D1645), Color(0xFF1A0D2B)))
                    } else {
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showFront) {
                Text(
                    text = tile.symbol,
                    fontSize = 28.sp,
                    modifier = Modifier.graphicsLayer { rotationY = 180f }
                )
            } else {
                Text(
                    text = "☽",
                    fontSize = 20.sp,
                    color = Color(0xFFF9A8D4).copy(alpha = 0.7f)
                )
            }
        }
    }
}
