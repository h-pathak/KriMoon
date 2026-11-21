package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.random.Random

private val POP_ORB_COLORS = listOf(
    Color(0xFFE11D68), // Rose
    Color(0xFF8B5CF6), // Violet
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
    Color(0xFF38BDF8)  // Sky
)

@Composable
fun ColorPopChainGame(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (score: Int, bestPop: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)
    val latestOnScoreSaved by rememberUpdatedState(onScoreSaved)

    val rows = 8
    val cols = 8

    fun randomBoard(): List<List<Int?>> = List(rows) { List(cols) { Random.nextInt(POP_ORB_COLORS.size) } }

    var board by remember { mutableStateOf(randomBoard()) }
    var previousBoard by remember { mutableStateOf<List<List<Int?>>?>(null) }
    var previousScore by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var bestPop by remember { mutableIntStateOf(0) }
    var lastPopLabel by remember { mutableStateOf("Tap any 2+ matching orbs to pop! 🫧") }
    var isRoundOver by remember { mutableStateOf(false) }

    fun findCluster(grid: List<List<Int?>>, startR: Int, startC: Int): Set<Pair<Int, Int>> {
        val target = grid[startR][startC] ?: return emptySet()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startR to startC)
        visited.add(startR to startC)
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            for ((dr, dc) in dirs) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && (nr to nc) !in visited && grid[nr][nc] == target) {
                    visited.add(nr to nc)
                    queue.add(nr to nc)
                }
            }
        }
        return visited
    }

    fun hasAnyPair(grid: List<List<Int?>>): Boolean {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = grid[r][c] ?: continue
                if (r + 1 < rows && grid[r + 1][c] == v) return true
                if (c + 1 < cols && grid[r][c + 1] == v) return true
            }
        }
        return false
    }

    fun applyGravityAndShift(grid: List<List<Int?>>): List<List<Int?>> {
        // Gravity per column
        val collapsedCols = mutableListOf<List<Int?>>()
        for (c in 0 until cols) {
            val nonNulls = (0 until rows).mapNotNull { r -> grid[r][c] }
            if (nonNulls.isNotEmpty()) {
                val padded = List(rows - nonNulls.size) { null } + nonNulls
                collapsedCols.add(padded)
            }
        }
        // Pad empty columns on the right
        while (collapsedCols.size < cols) {
            collapsedCols.add(List(rows) { null })
        }
        return List(rows) { r -> List(cols) { c -> collapsedCols[c][r] } }
    }

    fun restart() {
        board = randomBoard()
        previousBoard = null
        previousScore = 0
        score = 0
        bestPop = 0
        lastPopLabel = "Fresh board! Chain bigger clusters for huge points ✨"
        isRoundOver = false
    }

    fun onCellTap(r: Int, c: Int) {
        if (isRoundOver) return
        val cluster = findCluster(board, r, c)
        if (cluster.size < 2) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }

        previousBoard = board
        previousScore = score

        val cleared = board.mapIndexed { rr, row ->
            row.mapIndexed { cc, value -> if ((rr to cc) in cluster) null else value }
        }
        val settled = applyGravityAndShift(cleared)
        val gain = cluster.size * (cluster.size - 1) * 5
        score += gain
        if (cluster.size > bestPop) bestPop = cluster.size
        board = settled
        lastPopLabel = "Popped ${cluster.size} orbs · +$gain pts!"

        haptic.performHapticFeedback(if (cluster.size >= 5) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        if (latestSoundEnabled) {
            if (cluster.size >= 5) SoundHelper.playCuteChime(context)
            else SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.7f)
        }
        if (cluster.size >= 5) {
            particleController.spawn(500f, 620f, listOf("🫧", "✨", "💖", "⭐"), 12)
        }

        if (!hasAnyPair(settled)) {
            val remaining = settled.flatten().count { it != null }
            val clearBonus = (500 - remaining * 35).coerceAtLeast(0)
            score += clearBonus
            lastPopLabel = if (clearBonus > 0) "Clear Bonus +$clearBonus pts! 🏆" else "No more clusters left!"
            isRoundOver = true
            latestOnScoreSaved(score, bestPop)
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
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesRose.copy(alpha = 0.14f)) {
                Text(
                    text = "🫧 $score  ·  Best: ${maxOf(score, bestScore)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = PhasesRose,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                text = "Max Chain: $bestPop",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PhasesPlum
            )
        }

        Text(
            text = lastPopLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 8x8 Orb Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1D102B), Color(0xFF2B1438)))
                )
                .border(2.dp, PhasesLavender.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(8.dp)
                .testTag("color_pop_board")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (c in 0 until cols) {
                            val colorIdx = board[r][c]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .clickable(enabled = colorIdx != null && !isRoundOver) { onCellTap(r, c) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorIdx != null) {
                                    val baseColor = POP_ORB_COLORS[colorIdx]
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.92f)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(Color.White.copy(alpha = 0.45f), baseColor)
                                                )
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isRoundOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
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
                                "🫧 Board Cleared!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                "Score: $score · Best Chain: $bestPop",
                                color = PhasesPlum,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { restart() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("New Board 🫧", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Undo + Reset controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = {
                    val prev = previousBoard ?: return@OutlinedButton
                    board = prev
                    score = previousScore
                    previousBoard = null
                    isRoundOver = false
                    lastPopLabel = "Undid last pop ↩️"
                },
                enabled = previousBoard != null,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("↩️ Undo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { restart() },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("🔄 New Board", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
