package com.example.ui.games

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleTurnCodec
import com.example.model.TicTacToeSyncState
import com.example.notifications.SoundHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WIN_LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6)
)

private fun findWinner(board: List<String>): String? {
    for (line in WIN_LINES) {
        val a = board[line[0]]
        if (a.isNotEmpty() && a == board[line[1]] && a == board[line[2]]) return a
    }
    return null
}

@Composable
fun CoupleTicTacToeGame(
    room: CoupleRoomRecord,
    soundEnabled: Boolean,
    isLiveConnected: Boolean = false,
    onSaveState: (stateJson: String, bumpWin: Boolean) -> Unit,
    onSendLiveBlock: (packet: String, block: CoupleBlock) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val scope = rememberCoroutineScope()

    val state = remember(room.ticTacToeStateJson) {
        CoupleTurnCodec.ticTacToeFromJsonString(room.ticTacToeStateJson)
    }

    val winner = remember(state.board) { findWinner(state.board) }
    val isDraw = winner == null && !state.isBoltTwist && state.board.none { it.isEmpty() }

    val fadingCell: Int? = if (state.isBoltTwist && winner == null) {
        if (state.nextTurn == "X" && state.xQueue.size >= 3) state.xQueue.first()
        else if (state.nextTurn == "O" && state.oQueue.size >= 3) state.oQueue.first()
        else null
    } else null

    val canTap = winner == null && !isDraw && (room.isPassAndPlay || state.nextTurn == state.mySymbol)

    fun transmitLiveMove(updatedState: TicTacToeSyncState) {
        if (room.isPassAndPlay) return
        val code = room.coupleCode
        val sender = room.myName
        val prevHash = room.lastBlockHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(code).blockHash }
        val nextIndex = room.blockHeight
        val sharedSecret = room.ecdhSharedSecretB64
        scope.launch {
            val (packet, block) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeTicTacToeTurnWithBlock(
                    coupleCode = code,
                    senderName = sender,
                    state = updatedState,
                    previousHash = prevHash,
                    blockIndex = nextIndex,
                    ecdhSharedSecretB64 = sharedSecret
                )
            }
            onSendLiveBlock(packet, block)
        }
    }

    fun onCellTap(index: Int) {
        if (!canTap || state.board[index].isNotEmpty()) return
        val newBoard = state.board.toMutableList()
        var newXQueue = state.xQueue
        var newOQueue = state.oQueue

        if (state.nextTurn == "X") {
            if (state.isBoltTwist && newXQueue.size >= 3) {
                val oldest = newXQueue.first()
                newBoard[oldest] = ""
                newXQueue = newXQueue.drop(1)
            }
            newBoard[index] = "X"
            newXQueue = newXQueue + index
        } else {
            if (state.isBoltTwist && newOQueue.size >= 3) {
                val oldest = newOQueue.first()
                newBoard[oldest] = ""
                newOQueue = newOQueue.drop(1)
            }
            newBoard[index] = "O"
            newOQueue = newOQueue + index
        }

        val newWinner = findWinner(newBoard)
        val updated = state.copy(
            board = newBoard,
            xQueue = newXQueue,
            oQueue = newOQueue,
            nextTurn = if (state.nextTurn == "X") "O" else "X"
        )

        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (newWinner != null) {
            if (soundEnabled) SoundHelper.playCuteChime(context)
            particleController.spawn(500f, 650f, listOf("⚡", "💕", "🏆", "✨"), 16)
        }
        onSaveState(CoupleTurnCodec.ticTacToeToJsonString(updated), newWinner != null)
        transmitLiveMove(updated)
    }

    val partnerDisplay = room.partnerName.ifBlank { "Partner" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Variant toggle (Bolt 3-piece vs Classic)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.isBoltTwist) "⚡ Bolt 3-Piece Twist" else "⭕ Classic 3×3",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
            OutlinedButton(
                onClick = {
                    val reset = TicTacToeSyncState(
                        isBoltTwist = !state.isBoltTwist,
                        mySymbol = state.mySymbol,
                        roundCount = state.roundCount
                    )
                    onSaveState(CoupleTurnCodec.ticTacToeToJsonString(reset), false)
                    transmitLiveMove(reset)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (state.isBoltTwist) "Switch Classic" else "Switch Bolt ⚡", fontSize = 11.sp)
            }
        }

        Text(
            text = when {
                winner != null -> "🏆 ${if (winner == "X") "💖" else "🌙"} Wins Round ${state.roundCount}!"
                isDraw -> "🤝 Cozy Draw!"
                room.isPassAndPlay -> "Turn: ${if (state.nextTurn == "X") "💖" else "🌙"} (Pass & Play)"
                state.nextTurn == state.mySymbol -> "Your Turn (${if (state.mySymbol == "X") "💖" else "🌙"}) — Tap a square!"
                else -> "Waiting for $partnerDisplay's move…"
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (winner != null) PhasesSage else PhasesPlum
        )

        if (state.isBoltTwist && winner == null) {
            Text(
                text = "Max 3 marks each — faded mark vanishes on the 4th move!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3x3 Board
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (r in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (c in 0..2) {
                        val idx = r * 3 + c
                        val mark = state.board[idx]
                        val isFading = idx == fadingCell

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.5.dp,
                                when {
                                    isFading -> Color.Gray.copy(alpha = 0.4f)
                                    mark == "X" -> PhasesRose.copy(alpha = 0.6f)
                                    mark == "O" -> PhasesPlum.copy(alpha = 0.6f)
                                    else -> PhasesLavender.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(enabled = canTap && mark.isEmpty()) { onCellTap(idx) }
                                .testTag("ttt_cell_$idx")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (mark) {
                                        "X" -> "💖"
                                        "O" -> "🌙"
                                        else -> ""
                                    },
                                    fontSize = 38.sp,
                                    modifier = Modifier.alpha(if (isFading) 0.35f else 1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clean Rematch + Live Sync Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val rematch = TicTacToeSyncState(
                        isBoltTwist = state.isBoltTwist,
                        mySymbol = state.mySymbol,
                        roundCount = state.roundCount + 1
                    )
                    onSaveState(CoupleTurnCodec.ticTacToeToJsonString(rematch), false)
                    transmitLiveMove(rematch)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("ttt_rematch_btn")
            ) {
                Text("🔄 Rematch", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            if (!room.isPassAndPlay) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isLiveConnected) PhasesSage else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLiveConnected) "Live sync" else "Waiting",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private encrypted board",
                            tint = PhasesRose.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
