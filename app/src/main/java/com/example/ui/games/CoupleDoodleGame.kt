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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleGameDecks
import com.example.model.CoupleTurnCodec
import com.example.model.DoodleStrokeData
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DOODLE_COLORS = listOf(
    0xFFD4629A, // Rose
    0xFF7C3AED, // Plum
    0xFFEC4899, // Hot Pink
    0xFF10B981, // Sage
    0xFFF59E0B, // Gold
    0xFF38BDF8, // Sky
    0xFF1F2937  // Ink
)

@Composable
fun CoupleDoodleGame(
    room: CoupleRoomRecord,
    isLiveConnected: Boolean = false,
    onSaveDoodle: (strokesJson: String, prompt: String) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var strokes by remember(room.coupleCode) {
        mutableStateOf(CoupleTurnCodec.strokesFromJsonString(room.doodleStrokesJson))
    }
    var prompt by remember(room.coupleCode) {
        mutableStateOf(room.doodlePrompt.ifBlank { CoupleGameDecks.doodlePrompts.first() })
    }
    LaunchedEffect(room.doodleStrokesJson, room.doodlePrompt) {
        strokes = CoupleTurnCodec.strokesFromJsonString(room.doodleStrokesJson)
        if (room.doodlePrompt.isNotBlank()) prompt = room.doodlePrompt
    }

    var selectedColorHex by remember { mutableStateOf(DOODLE_COLORS.first()) }
    var brushWidth by remember { mutableFloatStateOf(9f) }
    var activeAuthorIsPartner by remember { mutableStateOf(false) }
    var activePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    val currentAuthor = if (room.isPassAndPlay && activeAuthorIsPartner) {
        room.partnerName.ifBlank { "Partner" }
    } else {
        room.myName.ifBlank { "Me" }
    }

    val latestRoom by rememberUpdatedState(room)
    val latestStrokes by rememberUpdatedState(strokes)
    val latestPrompt by rememberUpdatedState(prompt)
    val latestAuthor by rememberUpdatedState(currentAuthor)
    val latestColorHex by rememberUpdatedState(selectedColorHex)
    val latestBrushWidth by rememberUpdatedState(brushWidth)
    val latestOnSaveDoodle by rememberUpdatedState(onSaveDoodle)
    val latestOnSendLiveBlock by rememberUpdatedState(onSendLiveBlock)

    fun transmitLiveStrokes(strokesToSend: List<DoodleStrokeData>, currentPrompt: String) {
        val currentRoom = latestRoom
        if (currentRoom.isPassAndPlay) return
        val code = currentRoom.coupleCode
        val sender = currentRoom.myName
        val prevHash = currentRoom.lastBlockHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(code).blockHash }
        val nextIndex = currentRoom.blockHeight
        val sharedSecret = currentRoom.ecdhSharedSecretB64
        scope.launch {
            val (packet, block) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeDoodleTurnWithBlock(
                    coupleCode = code,
                    senderName = sender,
                    prompt = currentPrompt,
                    strokes = strokesToSend,
                    previousHash = prevHash,
                    blockIndex = nextIndex,
                    ecdhSharedSecretB64 = sharedSecret
                )
            }
            latestOnSendLiveBlock(packet, block)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Prompt Pill + Next Prompt
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = PhasesRose.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🎨 Co-op Prompt", style = MaterialTheme.typography.labelSmall, color = PhasesRose)
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val idx = (CoupleGameDecks.doodlePrompts.indexOf(prompt) + 1) % CoupleGameDecks.doodlePrompts.size
                        val nextPrompt = CoupleGameDecks.doodlePrompts[idx]
                        prompt = nextPrompt
                        onSaveDoodle(CoupleTurnCodec.strokesToJsonString(strokes), nextPrompt)
                        transmitLiveStrokes(strokes.takeLast(12), nextPrompt)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🎲 Next", fontSize = 12.sp)
                }
            }
        }

        // Pass-and-Play drawer toggle if on 1 phone
        if (room.isPassAndPlay) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Drawing as:", style = MaterialTheme.typography.labelMedium)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!activeAuthorIsPartner) PhasesRose else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { activeAuthorIsPartner = false }
                ) {
                    Text(
                        room.myName.ifBlank { "Player 1" },
                        color = if (!activeAuthorIsPartner) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (activeAuthorIsPartner) PhasesPlum else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { activeAuthorIsPartner = true }
                ) {
                    Text(
                        room.partnerName.ifBlank { "Partner" },
                        color = if (activeAuthorIsPartner) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Canvas Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFFBF7))
                .border(1.5.dp, PhasesLavender.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .pointerInput(room.coupleCode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            activePoints = listOf((offset.x / w).coerceIn(0f, 1f) to (offset.y / h).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val nx = (change.position.x / w).coerceIn(0f, 1f)
                            val ny = (change.position.y / h).coerceIn(0f, 1f)
                            activePoints = activePoints + (nx to ny)
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                val newStroke = DoodleStrokeData(
                                    author = latestAuthor,
                                    colorHex = latestColorHex,
                                    strokeWidth = latestBrushWidth,
                                    points = activePoints
                                )
                                val updated = (latestStrokes + newStroke).takeLast(120)
                                strokes = updated
                                activePoints = emptyList()
                                latestOnSaveDoodle(CoupleTurnCodec.strokesToJsonString(updated), latestPrompt)
                                transmitLiveStrokes(listOf(newStroke), latestPrompt)
                            }
                        },
                        onDragCancel = {
                            activePoints = emptyList()
                        }
                    )
                }
                .testTag("couple_doodle_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun drawStroke(points: List<Pair<Float, Float>>, colorHex: Long, width: Float) {
                    if (points.isEmpty()) return
                    val color = Color(colorHex)
                    if (points.size == 1) {
                        drawCircle(
                            color = color,
                            radius = width / 2f,
                            center = Offset(points.first().first * size.width, points.first().second * size.height)
                        )
                    } else {
                        val path = Path().apply {
                            moveTo(points.first().first * size.width, points.first().second * size.height)
                            for (i in 1 until points.size) {
                                lineTo(points[i].first * size.width, points[i].second * size.height)
                            }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                strokes.forEach { s -> drawStroke(s.points, s.colorHex, s.strokeWidth) }
                if (activePoints.isNotEmpty()) {
                    drawStroke(activePoints, selectedColorHex, brushWidth)
                }
            }

            if (strokes.isEmpty() && activePoints.isEmpty()) {
                Text(
                    text = "Draw together live ✨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8A9C9),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Color & Tool Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DOODLE_COLORS.forEach { hex ->
                    val isSelected = selectedColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 30.dp else 24.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(if (isSelected) 2.5.dp else 1.dp, Color.White, CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedColorHex = hex
                            }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            val updated = strokes.dropLast(1)
                            strokes = updated
                            onSaveDoodle(CoupleTurnCodec.strokesToJsonString(updated), prompt)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("↩️", fontSize = 14.sp) }

                OutlinedButton(
                    onClick = {
                        strokes = emptyList()
                        onSaveDoodle("[]", prompt)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("🗑️", fontSize = 14.sp) }
            }
        }

        // Subtle live status footer (zero manual sync buttons)
        if (!room.isPassAndPlay) {
            val partnerLabel = room.partnerName.ifBlank { "partner" }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLiveConnected) PhasesSage else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLiveConnected) {
                                "Drawing live with $partnerLabel"
                            } else {
                                "Waiting for $partnerLabel to join #${room.coupleCode}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private encrypted canvas",
                        tint = PhasesRose.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
