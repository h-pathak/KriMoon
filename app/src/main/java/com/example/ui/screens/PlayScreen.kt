package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.data.GameScoreRecord
import com.example.data.UserProfile
import com.example.model.CoupleBlock
import com.example.model.SpiceLevel
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import com.example.ui.games.BlockAnd2048Game
import com.example.ui.games.CoupleDoodleGame
import com.example.ui.games.CoupleGameType
import com.example.ui.games.CoupleGamesHub
import com.example.ui.games.CoupleQuizGame
import com.example.ui.games.CoupleTicTacToeGame
import com.example.ui.games.CoupleWyrGame
import com.example.ui.games.MoonHopperGame
import com.example.ui.games.MoonMergeGame
import com.example.ui.games.NeonBeatReflexGame
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage

private enum class PlaySection(val title: String, val emoji: String) {
    OFFLINE("Offline Arcade", "🕹️"),
    COUPLE("Couple Lounge", "💌")
}

private enum class OfflineGameType(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val accentColor: Color
) {
    MOON_MERGE("Moon & Fruit Merge", "🍉", "Suika-style cozy physics drop", PhasesRose),
    NEON_BEAT("Neon Beat & Reflex", "🎵", "4-lane rhythm + ms reflex sprint", PhasesPlum),
    BLOCK_PUZZLE("Block Blast & 2048", "🧱", "8×8 block clear + 2048 glow", PhasesSage),
    MOON_HOPPER("Moon Hopper", "🌙", "Endless double-jump star runner", Color(0xFF8B5CF6))
}

private sealed class ActivePlayDestination {
    data class Offline(val type: OfflineGameType) : ActivePlayDestination()
    data class Couple(val type: CoupleGameType) : ActivePlayDestination()
}

@Composable
fun PlayScreen(
    userProfile: UserProfile?,
    gameScores: Map<String, GameScoreRecord>,
    coupleRooms: List<CoupleRoomRecord>,
    activeCoupleRoom: CoupleRoomRecord?,
    cloudAuthUid: String? = null,
    cloudSyncStatus: String = "SIGNED_OUT",
    isCloudBusy: Boolean = false,
    onCloudUserChanged: (uid: String?, displayName: String?) -> Unit = { _, _ -> },
    onDetachCloudListeners: () -> Unit = {},
    onRetryCloudSync: () -> Unit = {},
    onRecordGameScore: (gameId: String, score: Int, streak: Int, lowerIsBetter: Boolean) -> Unit,
    onCreateOrJoinCoupleRoom: (name: String, code: String) -> Unit,
    onSelectCoupleRoom: (code: String) -> Unit,
    onDeleteCoupleRoom: (code: String) -> Unit,
    onUpdateCoupleSpiceLevel: (SpiceLevel) -> Unit,
    onToggleCouplePassAndPlay: (Boolean) -> Unit,
    onSaveCoupleDoodle: (strokesJson: String, prompt: String) -> Unit,
    onSaveCoupleQuizState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSaveCoupleWyrState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    onSaveCoupleTicTacToeState: (stateJson: String, bumpWin: Boolean) -> Unit,
    onSendLiveCoupleBlock: (packet: String, block: CoupleBlock) -> Unit = { _, _ -> },
    onShowFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var activeSection by remember { mutableStateOf(PlaySection.OFFLINE) }
    var activeDestination by remember { mutableStateOf<ActivePlayDestination?>(null) }
    var soundEnabled by remember { mutableStateOf(true) }

    // System back button returns from any active mini-game to the Play Hub
    BackHandler(enabled = activeDestination != null) {
        activeDestination = null
    }

    val currentDest = activeDestination
    if (currentDest != null) {
        val headerTitle = when (currentDest) {
            is ActivePlayDestination.Offline -> "${currentDest.type.emoji} ${currentDest.type.title}"
            is ActivePlayDestination.Couple -> "${currentDest.type.emoji} ${currentDest.type.title}"
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("active_game_stage")
        ) {
            // Game Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeDestination = null
                        },
                        modifier = Modifier.testTag("game_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Arcade"
                        )
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        soundEnabled = !soundEnabled
                    },
                    modifier = Modifier.testTag("game_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (soundEnabled) "Mute game sounds" else "Enable game sounds",
                        tint = if (soundEnabled) PhasesPlum else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Active Game Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
                    when (currentDest) {
                        is ActivePlayDestination.Offline -> when (currentDest.type) {
                            OfflineGameType.MOON_MERGE -> MoonMergeGame(
                                bestScore = gameScores["moon_merge"]?.highScore ?: 0,
                                soundEnabled = soundEnabled,
                                onScoreSaved = { score, streak ->
                                    onRecordGameScore("moon_merge", score, streak, false)
                                }
                            )
                            OfflineGameType.NEON_BEAT -> NeonBeatReflexGame(
                                bestBeatScore = gameScores["neon_beat"]?.highScore ?: 0,
                                bestReflexMs = gameScores["reaction_reflex"]?.highScore ?: 0,
                                soundEnabled = soundEnabled,
                                onBeatScoreSaved = { score, streak ->
                                    onRecordGameScore("neon_beat", score, streak, false)
                                },
                                onReflexSaved = { avgMs ->
                                    onRecordGameScore("reaction_reflex", avgMs, 1, true)
                                }
                            )
                            OfflineGameType.BLOCK_PUZZLE -> BlockAnd2048Game(
                                bestBlockScore = gameScores["block_blast"]?.highScore ?: 0,
                                best2048Score = gameScores["glow_2048"]?.highScore ?: 0,
                                soundEnabled = soundEnabled,
                                onBlockScoreSaved = { score, streak ->
                                    onRecordGameScore("block_blast", score, streak, false)
                                },
                                on2048ScoreSaved = { score, streak ->
                                    onRecordGameScore("glow_2048", score, streak, false)
                                }
                            )
                            OfflineGameType.MOON_HOPPER -> MoonHopperGame(
                                bestScore = gameScores["moon_hopper"]?.highScore ?: 0,
                                soundEnabled = soundEnabled,
                                onScoreSaved = { score, stars ->
                                    onRecordGameScore("moon_hopper", score, stars, false)
                                }
                            )
                        }

                        is ActivePlayDestination.Couple -> {
                            val room = activeCoupleRoom
                            if (room == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Please join a Couple Room first.")
                                }
                            } else {
                                val isLive = !room.isPassAndPlay && !cloudAuthUid.isNullOrBlank() && cloudSyncStatus == "LIVE"
                                when (currentDest.type) {
                                    CoupleGameType.DOODLE -> CoupleDoodleGame(
                                        room = room,
                                        isLiveConnected = isLive,
                                        onSaveDoodle = onSaveCoupleDoodle,
                                        onSendLiveBlock = onSendLiveCoupleBlock
                                    )
                                    CoupleGameType.QUIZ -> CoupleQuizGame(
                                        room = room,
                                        soundEnabled = soundEnabled,
                                        isLiveConnected = isLive,
                                        onSaveState = onSaveCoupleQuizState,
                                        onSendLiveBlock = onSendLiveCoupleBlock
                                    )
                                    CoupleGameType.WYR -> CoupleWyrGame(
                                        room = room,
                                        soundEnabled = soundEnabled,
                                        isLiveConnected = isLive,
                                        onSaveState = onSaveCoupleWyrState,
                                        onSendLiveBlock = onSendLiveCoupleBlock
                                    )
                                    CoupleGameType.TIC_TAC_TOE -> CoupleTicTacToeGame(
                                        room = room,
                                        soundEnabled = soundEnabled,
                                        isLiveConnected = isLive,
                                        onSaveState = onSaveCoupleTicTacToeState,
                                        onSendLiveBlock = onSendLiveCoupleBlock
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Play Hub Screen
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Switcher + Sound Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        PlaySection.entries.forEach { section ->
                            val selected = activeSection == section
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (selected) PhasesPlum else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .cuteBouncyClick { activeSection = section }
                                    .testTag("play_section_${section.name.lowercase()}")
                            ) {
                                Text(
                                    text = "${section.emoji} ${section.title}",
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        soundEnabled = !soundEnabled
                    },
                    modifier = Modifier.testTag("hub_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (soundEnabled) "Sound on" else "Sound muted",
                        tint = if (soundEnabled) PhasesPlum else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (activeSection == PlaySection.OFFLINE) {
                // Offline Arcade Banner
                val totalPlays = gameScores.values.sumOf { it.gamesPlayed }
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, PhasesLavender.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cute3DCard(maxTilt = 3f, cornerRadius = 22.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PhasesPlum.copy(alpha = 0.14f), PhasesRose.copy(alpha = 0.12f))
                                )
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🕹️ Cozy Offline Arcade",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                            Text(
                                text = "Zero Wi-Fi needed · Tactile 3D time-killers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = PhasesPlum.copy(alpha = 0.14f)) {
                            Text(
                                text = "$totalPlays runs",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PhasesPlum,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 2x2 Equal-Height Offline Game Cards
                val rows = OfflineGameType.entries.chunked(2)
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { game ->
                            val bestLabel = when (game) {
                                OfflineGameType.MOON_MERGE -> "Best: ${gameScores["moon_merge"]?.highScore ?: 0}"
                                OfflineGameType.NEON_BEAT -> {
                                    val beat = gameScores["neon_beat"]?.highScore ?: 0
                                    val ms = gameScores["reaction_reflex"]?.highScore ?: 0
                                    if (ms > 0) "Beat: $beat · ${ms}ms" else "Best: $beat"
                                }
                                OfflineGameType.BLOCK_PUZZLE -> {
                                    val b = gameScores["block_blast"]?.highScore ?: 0
                                    val g = gameScores["glow_2048"]?.highScore ?: 0
                                    "Block: $b · 2048: $g"
                                }
                                OfflineGameType.MOON_HOPPER -> "Best: ${gameScores["moon_hopper"]?.highScore ?: 0}"
                            }

                            OfflineGameCard(
                                game = game,
                                bestLabel = bestLabel,
                                onClick = { activeDestination = ActivePlayDestination.Offline(game) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            } else {
                // Couple Lounge
                CoupleGamesHub(
                    defaultUserName = userProfile?.name.orEmpty(),
                    coupleRooms = coupleRooms,
                    activeRoom = activeCoupleRoom,
                    cloudAuthUid = cloudAuthUid,
                    cloudSyncStatus = cloudSyncStatus,
                    isCloudBusy = isCloudBusy,
                    onCloudUserChanged = onCloudUserChanged,
                    onDetachCloudListeners = onDetachCloudListeners,
                    onRetryCloudSync = onRetryCloudSync,
                    onCreateOrJoinRoom = onCreateOrJoinCoupleRoom,
                    onSelectRoom = onSelectCoupleRoom,
                    onDeleteRoom = onDeleteCoupleRoom,
                    onUpdateSpiceLevel = onUpdateCoupleSpiceLevel,
                    onTogglePassAndPlay = onToggleCouplePassAndPlay,
                    onShowFeedback = onShowFeedback,
                    onLaunchCoupleGame = { gameType ->
                        activeDestination = ActivePlayDestination.Couple(gameType)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OfflineGameCard(
    game: OfflineGameType,
    bestLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, game.accentColor.copy(alpha = 0.4f)),
        modifier = modifier
            .cute3DCard(maxTilt = 4f, cornerRadius = 22.dp)
            .cuteBouncyClick(onClick = onClick)
            .testTag("offline_game_${game.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(game.accentColor.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(game.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(game.emoji, fontSize = 24.sp)
            }

            Text(
                text = game.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                minLines = 2
            )

            Text(
                text = game.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2
            )

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = game.accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = bestLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = game.accentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
