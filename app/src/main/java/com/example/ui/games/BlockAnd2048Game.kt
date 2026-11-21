package com.example.ui.games

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.random.Random

private enum class PuzzleSubMode(val title: String) {
    BLOCK_BLAST("Block Blast 🧱"),
    GLOW_2048("2048 Glow ✨")
}

private data class BlockShape(
    val cells: List<Pair<Int, Int>>, // (rowOffset, colOffset)
    val color: Color
)

private val BLOCK_CATALOG = listOf(
    BlockShape(listOf(0 to 0), PhasesRose),
    BlockShape(listOf(0 to 0, 0 to 1), PhasesPlum),
    BlockShape(listOf(0 to 0, 1 to 0), PhasesSage),
    BlockShape(listOf(0 to 0, 0 to 1, 0 to 2), Color(0xFFEC4899)),
    BlockShape(listOf(0 to 0, 1 to 0, 2 to 0), Color(0xFF8B5CF6)),
    BlockShape(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), Color(0xFFF59E0B)),
    BlockShape(listOf(0 to 0, 1 to 0, 1 to 1), Color(0xFF10B981)),
    BlockShape(listOf(0 to 0, 0 to 1, 1 to 1), Color(0xFF6366F1)),
    BlockShape(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1), PhasesRose),
    BlockShape(listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1), PhasesPlum)
)

@Composable
fun BlockAnd2048Game(
    bestBlockScore: Int,
    best2048Score: Int,
    soundEnabled: Boolean,
    onBlockScoreSaved: (Int, Int) -> Unit,
    on2048ScoreSaved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(PuzzleSubMode.BLOCK_BLAST) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                PuzzleSubMode.values().forEach { item ->
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
            PuzzleSubMode.BLOCK_BLAST -> CozyBlockBlastStage(
                bestScore = bestBlockScore,
                soundEnabled = soundEnabled,
                onScoreSaved = onBlockScoreSaved,
                modifier = Modifier.weight(1f)
            )
            PuzzleSubMode.GLOW_2048 -> Glow2048Stage(
                bestScore = best2048Score,
                soundEnabled = soundEnabled,
                onScoreSaved = on2048ScoreSaved,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CozyBlockBlastStage(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current

    var board by remember { mutableStateOf(List(8) { List<Color?>(8) { null } }) }
    var tray by remember { mutableStateOf(List(3) { BLOCK_CATALOG.random() as BlockShape? }) }
    var selectedTrayIdx by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }

    fun canPlace(grid: List<List<Color?>>, shape: BlockShape, r: Int, c: Int): Boolean {
        return shape.cells.all { (dr, dc) ->
            val nr = r + dr
            val nc = c + dc
            nr in 0..7 && nc in 0..7 && grid[nr][nc] == null
        }
    }

    fun hasAnyMove(grid: List<List<Color?>>, pieces: List<BlockShape?>): Boolean {
        return pieces.filterNotNull().any { shape ->
            (0..7).any { r -> (0..7).any { c -> canPlace(grid, shape, r, c) } }
        }
    }

    fun restart() {
        board = List(8) { List<Color?>(8) { null } }
        tray = List(3) { BLOCK_CATALOG.random() }
        selectedTrayIdx = 0
        score = 0
        bestCombo = 0
        isGameOver = false
    }

    fun placeAt(r: Int, c: Int) {
        if (isGameOver) return
        val shape = tray.getOrNull(selectedTrayIdx) ?: return
        if (!canPlace(board, shape, r, c)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }

        val mutable = board.map { it.toMutableList() }.toMutableList()
        shape.cells.forEach { (dr, dc) -> mutable[r + dr][c + dc] = shape.color }

        val fullRows = (0..7).filter { row -> mutable[row].all { it != null } }
        val fullCols = (0..7).filter { col -> (0..7).all { row -> mutable[row][col] != null } }
        fullRows.forEach { row -> for (col in 0..7) mutable[row][col] = null }
        fullCols.forEach { col -> for (row in 0..7) mutable[row][col] = null }

        val linesCleared = fullRows.size + fullCols.size
        val gain = shape.cells.size * 10 + (linesCleared * linesCleared * 90)
        score += gain
        if (linesCleared > bestCombo) bestCombo = linesCleared

        board = mutable.map { it.toList() }

        val updatedTray = tray.toMutableList()
        updatedTray[selectedTrayIdx] = null
        val finalTray = if (updatedTray.all { it == null }) {
            List(3) { BLOCK_CATALOG.random() }
        } else {
            updatedTray
        }
        tray = finalTray
        selectedTrayIdx = finalTray.indexOfFirst { it != null }.coerceAtLeast(0)

        haptic.performHapticFeedback(if (linesCleared > 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        if (soundEnabled) {
            SoundHelper.playSound(context, if (linesCleared > 0) SoundHelper.SOUND_CUTE_CHIME else SoundHelper.SOUND_SWEET_BUBBLE, 0.8f)
        }
        if (linesCleared > 0) {
            particleController.spawn(500f, 620f, listOf("✨", "🧱", "💖", "🌸"), 12)
        }

        if (!hasAnyMove(board, finalTray)) {
            isGameOver = true
            onScoreSaved(score, bestCombo)
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Score: $score", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = PhasesPlum)
            Text("Best: ${maxOf(score, bestScore)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(2.dp, PhasesPlum.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                .padding(8.dp)
                .testTag("block_blast_grid")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (r in 0..7) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (c in 0..7) {
                            val cellColor = board[r][c]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cellColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .border(
                                        1.dp,
                                        if (cellColor != null) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { placeAt(r, c) }
                            )
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🧱 No More Fits!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Score: $score", color = PhasesPlum, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { restart() }, colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)) {
                                Text("New Board ✨")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("1. Tap a piece below   2. Tap its top-left spot on the grid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        // 3-Piece Tray
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tray.forEachIndexed { idx, piece ->
                val isSelected = selectedTrayIdx == idx && piece != null
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) PhasesPlum.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) PhasesPlum else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = piece != null) {
                            selectedTrayIdx = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (piece != null) {
                            MiniPiecePreview(piece)
                        } else {
                            Text("✓", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPiecePreview(shape: BlockShape) {
    val maxR = shape.cells.maxOf { it.first }
    val maxC = shape.cells.maxOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0..maxR) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0..maxC) {
                    val filled = (r to c) in shape.cells
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (filled) shape.color else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun Glow2048Stage(
    bestScore: Int,
    soundEnabled: Boolean,
    onScoreSaved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val latestSoundEnabled by rememberUpdatedState(soundEnabled)

    fun spawnTile(grid: List<List<Int>>): List<List<Int>> {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0..3) for (c in 0..3) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return grid
        val (rr, cc) = empty.random()
        val copy = grid.map { it.toMutableList() }.toMutableList()
        copy[rr][cc] = if (Random.nextFloat() < 0.85f) 2 else 4
        return copy.map { it.toList() }
    }

    var grid by remember { mutableStateOf(spawnTile(spawnTile(List(4) { List(4) { 0 } }))) }
    var score by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }

    fun canMove(g: List<List<Int>>): Boolean {
        for (r in 0..3) for (c in 0..3) {
            val v = g[r][c]
            if (v == 0) return true
            if (r < 3 && g[r + 1][c] == v) return true
            if (c < 3 && g[r][c + 1] == v) return true
        }
        return false
    }

    fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
        val nonZero = line.filter { it != 0 }.toMutableList()
        val out = mutableListOf<Int>()
        var gained = 0
        var i = 0
        while (i < nonZero.size) {
            if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                val merged = nonZero[i] * 2
                out.add(merged)
                gained += merged
                i += 2
            } else {
                out.add(nonZero[i])
                i++
            }
        }
        while (out.size < 4) out.add(0)
        return out to gained
    }

    fun move(dir: Int) { // 0=left, 1=right, 2=up, 3=down
        if (isGameOver) return
        val next = MutableList(4) { MutableList(4) { 0 } }
        var totalGain = 0
        when (dir) {
            0 -> for (r in 0..3) {
                val (line, g) = slideLine(grid[r])
                next[r] = line.toMutableList()
                totalGain += g
            }
            1 -> for (r in 0..3) {
                val (line, g) = slideLine(grid[r].reversed())
                next[r] = line.reversed().toMutableList()
                totalGain += g
            }
            2 -> for (c in 0..3) {
                val col = (0..3).map { grid[it][c] }
                val (line, g) = slideLine(col)
                for (r in 0..3) next[r][c] = line[r]
                totalGain += g
            }
            else -> for (c in 0..3) {
                val col = (0..3).map { grid[it][c] }.reversed()
                val (line, g) = slideLine(col)
                val rev = line.reversed()
                for (r in 0..3) next[r][c] = rev[r]
                totalGain += g
            }
        }
        val candidate = next.map { it.toList() }
        if (candidate != grid) {
            score += totalGain
            val spawned = spawnTile(candidate)
            grid = spawned
            haptic.performHapticFeedback(if (totalGain > 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            if (totalGain > 0 && latestSoundEnabled) SoundHelper.playSound(context, SoundHelper.SOUND_SWEET_BUBBLE, 0.7f)
            if (totalGain >= 64) particleController.spawn(500f, 650f, listOf("✨", "🌙", "💎"), 10)
            if (!canMove(spawned)) {
                isGameOver = true
                val maxTile = spawned.flatten().maxOrNull() ?: 2
                onScoreSaved(score, maxTile)
            }
        }
    }

    fun tileColor(v: Int): Color = when (v) {
        0 -> Color.Transparent
        2 -> Color(0xFFFCE7F3)
        4 -> Color(0xFFFBCFE8)
        8 -> Color(0xFFF9A8D4)
        16 -> Color(0xFFF472B6)
        32 -> Color(0xFFE879F9)
        64 -> Color(0xFFC084FC)
        128 -> Color(0xFFA78BFA)
        256 -> Color(0xFF818CF8)
        512 -> Color(0xFF38BDF8)
        1024 -> Color(0xFF34D399)
        else -> Color(0xFFFACC15)
    }

    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Score: $score", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = PhasesPlum)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Best: ${maxOf(score, bestScore)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = PhasesRose)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = PhasesPlum.copy(alpha = 0.14f),
                    modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable {
                        grid = spawnTile(spawnTile(List(4) { List(4) { 0 } }))
                        score = 0
                        isGameOver = false
                    }
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PhasesPlum, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(PhasesPlum.copy(alpha = 0.14f))
                .border(2.dp, PhasesLavender.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(10.dp)
                .pointerInput(isGameOver) {
                    detectDragGestures(
                        onDragStart = { dragAccumX = 0f; dragAccumY = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            dragAccumX += drag.x
                            dragAccumY += drag.y
                        },
                        onDragEnd = {
                            if (abs(dragAccumX) > abs(dragAccumY) && abs(dragAccumX) > 28f) {
                                move(if (dragAccumX > 0f) 1 else 0)
                            } else if (abs(dragAccumY) > 28f) {
                                move(if (dragAccumY > 0f) 3 else 2)
                            }
                        }
                    )
                }
                .testTag("glow_2048_grid")
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0..3) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0..3) {
                            val v = grid[r][c]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (v == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else tileColor(v)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (v > 0) {
                                    Text(
                                        text = "$v",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = if (v >= 1000) 18.sp else 24.sp
                                        ),
                                        color = if (v <= 8) Color(0xFF3E1D78) else Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isGameOver) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✨ No Moves Left!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Final Score: $score", color = PhasesPlum, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    grid = spawnTile(spawnTile(List(4) { List(4) { 0 } }))
                                    score = 0
                                    isGameOver = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)
                            ) {
                                Text("Play Again ✨")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        // Swipe or tap quick directional controls for effortless one-hand play
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("⬅️" to 0, "⬆️" to 2, "⬇️" to 3, "➡️" to 1).forEach { (arrow, dir) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { move(dir) }
                ) {
                    Text(arrow, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}
