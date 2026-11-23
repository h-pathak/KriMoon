package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.data.GameScoreRecord
import com.example.data.UserProfile
import com.example.model.CoupleBlock
import com.example.ui.components.cute3DCard
import com.example.ui.components.cuteBouncyClick
import com.example.ui.games.BlockAnd2048Game
import com.example.ui.games.ColorPopChainGame
import com.example.ui.games.CosmicBrickBreakerGame
import com.example.ui.games.CoupleActivityRowItem
import com.example.ui.games.CoupleDoodleGame
import com.example.ui.games.CoupleGameType
import com.example.ui.games.CoupleGamesHub
import com.example.ui.games.CoupleLovePulseScreen
import com.example.ui.games.CoupleLoungeActions
import com.example.ui.games.CoupleLoungeUiState
import com.example.ui.games.CoupleQuizGame
import com.example.ui.games.CoupleTicTacToeGame
import com.example.ui.games.CoupleTruthOrDareGame
import com.example.ui.games.CoupleWavelengthGame
import com.example.ui.games.CoupleWyrGame
import com.example.ui.games.MoonHopperGame
import com.example.ui.games.MoonMergeGame
import com.example.ui.games.MoonlightSnakeGame
import com.example.ui.games.NeonBeatReflexGame
import com.example.ui.games.StarlightMemoryGame
import com.example.ui.theme.MaleBorder
import com.example.ui.theme.MaleCobalt
import com.example.ui.theme.MaleInk
import com.example.ui.theme.MaleInkMuted
import com.example.ui.theme.MaleSky
import com.example.ui.theme.MaleSurface
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage

@Immutable
data class PlayGameSaveActions(
    val onRecordGameScore: (gameId: String, score: Int, streak: Int, lowerIsBetter: Boolean) -> Unit,
    val onSaveCoupleDoodle: (strokesJson: String, prompt: String) -> Unit,
    val onSaveCoupleQuizState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    val onSaveCoupleWyrState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    val onSaveCoupleTicTacToeState: (stateJson: String, bumpWin: Boolean) -> Unit,
    val onSaveCoupleDareState: (stateJson: String, bumpStreak: Boolean) -> Unit,
    val onSaveCoupleWavelengthState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    val onSaveCoupleWhisperState: (stateJson: String, bumpMatch: Boolean) -> Unit,
    val onSendLiveCoupleBlock: (packet: String, block: CoupleBlock) -> Unit
)

enum class PlaySection(val title: String, val emoji: String) {
    ARCADE("Arcade", "🕹️"),
    COUPLE("Couple Lounge", "💌")
}

private enum class ArcadeGameType(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val accentColor: Color
) {
    MOON_MERGE("Moon & Fruit Merge", "🍉", "Suika-style cozy physics drop", PhasesRose),
    NEON_BEAT("Neon Beat & Reflex", "🎵", "4-lane rhythm + ms reflex sprint", PhasesPlum),
    BLOCK_PUZZLE("Block Blast & 2048", "🧱", "8×8 block clear + 2048 glow", PhasesSage),
    MOON_HOPPER("Moon Hopper", "🌙", "Endless double-jump star runner", Color(0xFF8B5CF6)),
    STARLIGHT_MEMORY("Starlight Memory", "🃏", "3D constellation flip & match", Color(0xFFEC4899)),
    COSMIC_BREAKER("Cosmic Breaker", "☄️", "Neon prism paddle brick smash", Color(0xFFE11D68)),
    MOONLIGHT_SNAKE("Moonlight Snake", "🐍", "Glowing grid snake + star orbs", Color(0xFF10B981)),
    COLOR_POP_CHAIN("Orb Pop Chain", "🫧", "Cluster-pop combos & gravity clear", Color(0xFF38BDF8))
}

private sealed class ActivePlayDestination {
    data class Arcade(val type: ArcadeGameType) : ActivePlayDestination()
    data class Couple(val type: CoupleGameType) : ActivePlayDestination()
}

@Composable
fun PlayScreen(
    userProfile: UserProfile?,
    gameScores: Map<String, GameScoreRecord>,
    loungeState: CoupleLoungeUiState,
    loungeActions: CoupleLoungeActions,
    gameSaveActions: PlayGameSaveActions,
    lockedSection: PlaySection? = null,
    isMasculine: Boolean = false,
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf(lockedSection ?: PlaySection.ARCADE) }
    var activeDestination by remember { mutableStateOf<ActivePlayDestination?>(null) }
    var soundEnabled by remember { mutableStateOf(false) }
    val effectiveSection = lockedSection ?: activeSection

    BackHandler(enabled = activeDestination != null) {
        activeDestination = null
    }

    val currentDest = activeDestination
    if (currentDest != null) {
        ActivePlayStage(
            destination = currentDest,
            gameScores = gameScores,
            loungeState = loungeState,
            soundEnabled = soundEnabled,
            onToggleSound = { soundEnabled = !soundEnabled },
            onBack = { activeDestination = null },
            saveActions = gameSaveActions,
            modifier = modifier
        )
        return
    }

    val arcadeRows = remember { ArcadeGameType.entries.chunked(2) }
    val coupleRows = remember { CoupleGameType.entries.chunked(2) }
    val activeCoupleRoom = loungeState.activeRoom
    val isCloudSignedIn = !loungeState.cloudAuthUid.isNullOrBlank()
    val showCoupleActivityGrid = activeCoupleRoom != null && (
        activeCoupleRoom.isPassAndPlay ||
            (isCloudSignedIn && activeCoupleRoom.approvalStatus != "PENDING_HOST_APPROVAL")
        )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (lockedSection == null) {
            item(key = "play_hub_switcher") {
                PlayHubSwitcherBar(
                    activeSection = effectiveSection,
                    soundEnabled = soundEnabled,
                    onSelectSection = { activeSection = it },
                    onToggleSound = { soundEnabled = !soundEnabled },
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        }

        if (effectiveSection == PlaySection.ARCADE) {
            item(key = "arcade_header") {
                ArcadeHeaderBanner(
                    gameScores = gameScores,
                    isMasculine = isMasculine,
                    showSoundToggle = lockedSection != null,
                    soundEnabled = soundEnabled,
                    onToggleSound = { soundEnabled = !soundEnabled },
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
            itemsIndexed(
                items = arcadeRows,
                key = { idx, _ -> "arcade_row_$idx" }
            ) { _, pair ->
                ArcadeGameRowItem(
                    pair = pair,
                    gameScores = gameScores,
                    onLaunchArcade = { activeDestination = ActivePlayDestination.Arcade(it) },
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        } else {
            item(key = "couple_hub_header") {
                CoupleGamesHub(
                    defaultUserName = userProfile?.name.orEmpty(),
                    loungeState = loungeState,
                    actions = loungeActions,
                    onLaunchCoupleGame = { activeDestination = ActivePlayDestination.Couple(it) },
                    showActivityGrid = false,
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
            if (showCoupleActivityGrid) {
                itemsIndexed(
                    items = coupleRows,
                    key = { idx, _ -> "couple_row_$idx" }
                ) { _, pair ->
                    CoupleActivityRowItem(
                        pair = pair,
                        onLaunchCoupleGame = { activeDestination = ActivePlayDestination.Couple(it) },
                        modifier = Modifier.widthIn(max = 620.dp)
                    )
                }
            }
        }

        item(key = "play_bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun ActivePlayStage(
    destination: ActivePlayDestination,
    gameScores: Map<String, GameScoreRecord>,
    loungeState: CoupleLoungeUiState,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onBack: () -> Unit,
    saveActions: PlayGameSaveActions,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val headerTitle = when (destination) {
        is ActivePlayDestination.Arcade -> "${destination.type.emoji} ${destination.type.title}"
        is ActivePlayDestination.Couple -> "${destination.type.emoji} ${destination.type.title}"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("active_game_stage")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleSound()
                },
                modifier = Modifier.testTag("game_sound_toggle")
            ) {
                Icon(
                    imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (soundEnabled) "Mute game sounds" else "Enable game sounds",
                    tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxSize()) {
                when (destination) {
                    is ActivePlayDestination.Arcade -> ActiveArcadeGameStage(
                        type = destination.type,
                        gameScores = gameScores,
                        soundEnabled = soundEnabled,
                        onRecordGameScore = saveActions.onRecordGameScore
                    )
                    is ActivePlayDestination.Couple -> ActiveCoupleGameStage(
                        type = destination.type,
                        room = loungeState.activeRoom,
                        cloudAuthUid = loungeState.cloudAuthUid,
                        cloudSyncStatus = loungeState.cloudSyncStatus,
                        soundEnabled = soundEnabled,
                        saveActions = saveActions
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveArcadeGameStage(
    type: ArcadeGameType,
    gameScores: Map<String, GameScoreRecord>,
    soundEnabled: Boolean,
    onRecordGameScore: (gameId: String, score: Int, streak: Int, lowerIsBetter: Boolean) -> Unit
) {
    when (type) {
        ArcadeGameType.MOON_MERGE -> MoonMergeGame(
            bestScore = gameScores["moon_merge"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, streak -> onRecordGameScore("moon_merge", score, streak, false) }
        )
        ArcadeGameType.NEON_BEAT -> NeonBeatReflexGame(
            bestBeatScore = gameScores["neon_beat"]?.highScore ?: 0,
            bestReflexMs = gameScores["reaction_reflex"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onBeatScoreSaved = { score, streak -> onRecordGameScore("neon_beat", score, streak, false) },
            onReflexSaved = { avgMs -> onRecordGameScore("reaction_reflex", avgMs, 1, true) }
        )
        ArcadeGameType.BLOCK_PUZZLE -> BlockAnd2048Game(
            bestBlockScore = gameScores["block_blast"]?.highScore ?: 0,
            best2048Score = gameScores["glow_2048"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onBlockScoreSaved = { score, streak -> onRecordGameScore("block_blast", score, streak, false) },
            on2048ScoreSaved = { score, streak -> onRecordGameScore("glow_2048", score, streak, false) }
        )
        ArcadeGameType.MOON_HOPPER -> MoonHopperGame(
            bestScore = gameScores["moon_hopper"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, stars -> onRecordGameScore("moon_hopper", score, stars, false) }
        )
        ArcadeGameType.STARLIGHT_MEMORY -> StarlightMemoryGame(
            bestScore = gameScores["starlight_memory"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, combo -> onRecordGameScore("starlight_memory", score, combo, false) }
        )
        ArcadeGameType.COSMIC_BREAKER -> CosmicBrickBreakerGame(
            bestScore = gameScores["cosmic_breaker"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, combo -> onRecordGameScore("cosmic_breaker", score, combo, false) }
        )
        ArcadeGameType.MOONLIGHT_SNAKE -> MoonlightSnakeGame(
            bestScore = gameScores["moonlight_snake"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, length -> onRecordGameScore("moonlight_snake", score, length, false) }
        )
        ArcadeGameType.COLOR_POP_CHAIN -> ColorPopChainGame(
            bestScore = gameScores["color_pop_chain"]?.highScore ?: 0,
            soundEnabled = soundEnabled,
            onScoreSaved = { score, bestPop -> onRecordGameScore("color_pop_chain", score, bestPop, false) }
        )
    }
}

@Composable
private fun ActiveCoupleGameStage(
    type: CoupleGameType,
    room: CoupleRoomRecord?,
    cloudAuthUid: String?,
    cloudSyncStatus: String,
    soundEnabled: Boolean,
    saveActions: PlayGameSaveActions
) {
    if (room == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please join a Couple Room first.")
        }
        return
    }
    val isLive = !room.isPassAndPlay && !cloudAuthUid.isNullOrBlank() && cloudSyncStatus == "LIVE"
    when (type) {
        CoupleGameType.DOODLE -> CoupleDoodleGame(
            room = room,
            isLiveConnected = isLive,
            onSaveDoodle = saveActions.onSaveCoupleDoodle,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.QUIZ -> CoupleQuizGame(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleQuizState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.WYR -> CoupleWyrGame(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleWyrState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.TIC_TAC_TOE -> CoupleTicTacToeGame(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleTicTacToeState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.TRUTH_OR_DARE -> CoupleTruthOrDareGame(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleDareState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.WAVELENGTH -> CoupleWavelengthGame(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleWavelengthState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
        CoupleGameType.HEART_TAP -> CoupleLovePulseScreen(
            room = room,
            soundEnabled = soundEnabled,
            isLiveConnected = isLive,
            onSaveState = saveActions.onSaveCoupleWhisperState,
            onSendLiveBlock = saveActions.onSendLiveCoupleBlock
        )
    }
}

@Composable
private fun PlayHubSwitcherBar(
    activeSection: PlaySection,
    soundEnabled: Boolean,
    onSelectSection: (PlaySection) -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
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
                            .cuteBouncyClick { onSelectSection(section) }
                            .testTag("play_section_${section.name.lowercase()}")
                    ) {
                        Text(
                            text = "${section.emoji} ${section.title}",
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
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
                onToggleSound()
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
}

@Composable
private fun ArcadeHeaderBanner(
    gameScores: Map<String, GameScoreRecord>,
    isMasculine: Boolean = false,
    showSoundToggle: Boolean = false,
    soundEnabled: Boolean = false,
    onToggleSound: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalPlays = remember(gameScores) { gameScores.values.sumOf { it.gamesPlayed } }
    val haptic = LocalHapticFeedback.current
    if (isMasculine) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaleSurface),
            border = BorderStroke(1.dp, MaleBorder),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaleCobalt.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline Arcade",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaleInk
                    )
                    Text(
                        text = "8 instant offline games · $totalPlays runs logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaleInkMuted
                    )
                }
                if (showSoundToggle) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSound()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hub_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (soundEnabled) "Sound on" else "Sound muted",
                            tint = if (soundEnabled) MaleSky else MaleInkMuted
                        )
                    }
                }
            }
        }
        return
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, PhasesLavender.copy(alpha = 0.55f)),
        modifier = modifier
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
                    text = "🕹️ Cozy Arcade ✨",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
                Text(
                    text = "8 tactile mini-games · instant cozy play anytime 🌸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(100.dp), color = PhasesRose.copy(alpha = 0.15f)) {
                Text(
                    text = "✨ $totalPlays runs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PhasesPlum,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ArcadeGameRowItem(
    pair: List<ArcadeGameType>,
    gameScores: Map<String, GameScoreRecord>,
    onLaunchArcade: (ArcadeGameType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pair.forEach { game ->
            val bestLabel = when (game) {
                ArcadeGameType.MOON_MERGE -> "Best: ${gameScores["moon_merge"]?.highScore ?: 0}"
                ArcadeGameType.NEON_BEAT -> {
                    val beat = gameScores["neon_beat"]?.highScore ?: 0
                    val ms = gameScores["reaction_reflex"]?.highScore ?: 0
                    if (ms > 0) "Beat: $beat · ${ms}ms" else "Best: $beat"
                }
                ArcadeGameType.BLOCK_PUZZLE -> {
                    val b = gameScores["block_blast"]?.highScore ?: 0
                    val g = gameScores["glow_2048"]?.highScore ?: 0
                    "Block: $b · 2048: $g"
                }
                ArcadeGameType.MOON_HOPPER -> "Best: ${gameScores["moon_hopper"]?.highScore ?: 0}"
                ArcadeGameType.STARLIGHT_MEMORY -> "Best: ${gameScores["starlight_memory"]?.highScore ?: 0}"
                ArcadeGameType.COSMIC_BREAKER -> "Best: ${gameScores["cosmic_breaker"]?.highScore ?: 0}"
                ArcadeGameType.MOONLIGHT_SNAKE -> "Best: ${gameScores["moonlight_snake"]?.highScore ?: 0}"
                ArcadeGameType.COLOR_POP_CHAIN -> "Best: ${gameScores["color_pop_chain"]?.highScore ?: 0}"
            }

            ArcadeGameCard(
                game = game,
                bestLabel = bestLabel,
                onClick = { onLaunchArcade(game) },
                modifier = Modifier
                    .weight(1f)
                    .height(172.dp)
            )
        }
    }
}

@Composable
private fun ArcadeGameCard(
    game: ArcadeGameType,
    bestLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, game.accentColor.copy(alpha = 0.4f)),
        modifier = modifier
            .cute3DCard(
                maxTilt = 3.5f,
                cornerRadius = 22.dp,
                accentColor = game.accentColor,
                onClick = onClick
            )
            .testTag("arcade_game_${game.name.lowercase()}")
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = game.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = game.accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = bestLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = game.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
