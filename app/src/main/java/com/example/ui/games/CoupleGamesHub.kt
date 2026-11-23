package com.example.ui.games

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.cloud.InviteJoinUiState
import com.example.cloud.attemptAutoSignIn
import com.example.cloud.onGoogleSignInClicked
import com.example.cloud.signOutGoogle
import com.example.data.CoupleRoomRecord
import com.example.model.CoupleActivitiesDeck
import com.example.model.CoupleTurnCodec
import com.example.model.SpiceLevel
import kotlinx.coroutines.delay
import com.example.ui.components.AestheticHaptics
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// Midnight Rose Velvet palette — clean, hot & minimal
private val VelvetBgTop = Color(0xFF1A0E1B)
private val VelvetBgBottom = Color(0xFF2D1225)
private val VelvetSurface = Color(0xFF231120)
private val VelvetTrack = Color(0xFF140A14)
private val VelvetRoseGlow = Color(0xFFE11D68)
private val VelvetBorder = Color(0x52F472B6)
private val VelvetTextPrimary = Color(0xFFFFF5F8)
private val VelvetTextMuted = Color(0xFFD8B8C8)
private val LiveEmerald = Color(0xFF34D399)
private val WaitingAmber = Color(0xFFFBBF24)

// Gate silent CredentialManager auto-sign-in to at most once per app process launch
private var hasAttemptedSessionAutoSignIn = false

@Immutable
data class CoupleLoungeUiState(
    val coupleRooms: List<CoupleRoomRecord> = emptyList(),
    val activeRoom: CoupleRoomRecord? = null,
    val cloudAuthUid: String? = null,
    val cloudSyncStatus: String = "SIGNED_OUT",
    val isCloudBusy: Boolean = false,
    val pendingTurnCount: Int = 0,
    val inviteJoinState: InviteJoinUiState = InviteJoinUiState.Idle
)

@Immutable
data class CoupleLoungeActions(
    val onCloudUserChanged: (uid: String?, displayName: String?) -> Unit = { _, _ -> },
    val onDetachCloudListeners: () -> Unit = {},
    val onRetryCloudSync: () -> Unit = {},
    val onCreateUniqueRoom: (name: String) -> Unit = {},
    val onRequestJoinRoom: (name: String, code: String) -> Unit = { _, _ -> },
    val onClearInviteJoinState: () -> Unit = {},
    val onApprovePendingPartner: (code: String) -> Unit = {},
    val onDeclinePendingPartner: (code: String) -> Unit = {},
    val onSelectRoom: (code: String) -> Unit = {},
    val onDeleteRoom: (code: String) -> Unit = {},
    val onUpdateSpiceLevel: (SpiceLevel) -> Unit = {},
    val onTogglePassAndPlay: (Boolean) -> Unit = {},
    val onShowFeedback: (String) -> Unit = {}
)

enum class CoupleGameType(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val accentColor: Color
) {
    DOODLE("Co-op Doodle", "🎨", "Draw together live on one canvas", PhasesRose),
    QUIZ("Couple Quiz", "💋", "Lock your answers, reveal together", PhasesPlum),
    WYR("Would You Rather", "🔥", "Playful & steamy dilemmas", Color(0xFFE11D68)),
    TIC_TAC_TOE("Tic-Tac-Toe+", "⚡", "Classic & 3-piece Bolt showdown", PhasesSage),
    TRUTH_OR_DARE("Truth or Dare", "🎲", "Velvet prompts matched to your vibe", Color(0xFFEC4899)),
    WAVELENGTH("Heart Wavelength", "🌊", "Tune the dial to read each other's mind", Color(0xFFA855F7)),
    HEART_TAP("Thinking of You", "🫂", "Tap to send a warm hug or thinking-of-you nudge ✨", Color(0xFFF43F5E))
}

@Composable
fun CoupleGamesHub(
    defaultUserName: String,
    loungeState: CoupleLoungeUiState,
    actions: CoupleLoungeActions,
    onLaunchCoupleGame: (CoupleGameType) -> Unit,
    modifier: Modifier = Modifier,
    showActivityGrid: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Lazily instantiate CredentialManager only when an auth call is actually invoked
    val credentialManagerHolder = remember(context) { arrayOfNulls<CredentialManager>(1) }
    fun getCredentialManager(): CredentialManager {
        return credentialManagerHolder[0] ?: CredentialManager.create(context).also {
            credentialManagerHolder[0] = it
        }
    }

    val activeRoom = loungeState.activeRoom
    val isSignedIn = !loungeState.cloudAuthUid.isNullOrBlank()
    val firebaseDisplayName = remember(loungeState.cloudAuthUid) {
        if (loungeState.cloudAuthUid.isNullOrBlank()) null
        else runCatching { Firebase.auth.currentUser?.displayName }.getOrNull()
    }

    var isSigningIn by remember { mutableStateOf(false) }
    var bypassAuthForLocalPlay by remember { mutableStateOf(false) }
    var showRemovePartnerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            bypassAuthForLocalPlay = false
        }
    }

    val isAuthGated = !isSignedIn && !bypassAuthForLocalPlay && activeRoom?.isPassAndPlay != true

    // Attempt silent auto sign-in at most once per app launch to avoid repeated CredMan IPC
    LaunchedEffect(Unit) {
        if (!isSignedIn && !hasAttemptedSessionAutoSignIn) {
            hasAttemptedSessionAutoSignIn = true
            attemptAutoSignIn(
                context = context,
                credentialManager = getCredentialManager(),
                onAuthSuccess = { user ->
                    actions.onCloudUserChanged(user?.uid, user?.displayName)
                },
                onUnauthenticated = { },
                scope = scope
            )
        }
    }

    // Sleek Velvet Auth Gate if not signed in and not using local Pass & Play
    if (isAuthGated) {
        CoupleAuthGateCard(
            isSigningIn = isSigningIn,
            hasLocalRoom = activeRoom != null,
            onSignInClick = {
                isSigningIn = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = getCredentialManager(),
                    onAuthSuccess = { user ->
                        isSigningIn = false
                        actions.onCloudUserChanged(user?.uid, user?.displayName)
                        actions.onShowFeedback("Signed in — ready to connect live ♥")
                    },
                    onAuthError = { err ->
                        isSigningIn = false
                        actions.onShowFeedback(err)
                    },
                    onAuthCancelled = {
                        isSigningIn = false
                    },
                    scope = scope
                )
            },
            onPlayOfflineSinglePhone = {
                bypassAuthForLocalPlay = true
                if (activeRoom != null) {
                    actions.onTogglePassAndPlay(true)
                }
            },
            modifier = modifier
        )
        return
    }

    if (activeRoom == null) {
        CouplePairingCard(
            defaultUserName = firebaseDisplayName?.substringBefore(" ")?.takeIf { it.isNotBlank() }
                ?: defaultUserName,
            existingRooms = loungeState.coupleRooms,
            isSignedIn = isSignedIn,
            isBusy = loungeState.isCloudBusy,
            inviteJoinState = loungeState.inviteJoinState,
            onCreateUniqueRoom = actions.onCreateUniqueRoom,
            onRequestJoinRoom = actions.onRequestJoinRoom,
            onClearInviteJoinState = actions.onClearInviteJoinState,
            onSelectRoom = actions.onSelectRoom,
            modifier = modifier
        )
        return
    }

    // Secondary Confirmation Gate: Partner 2 waits here until the Room Host explicitly taps Allow
    if (!activeRoom.isPassAndPlay && activeRoom.approvalStatus == "PENDING_HOST_APPROVAL") {
        WaitingForHostApprovalCard(
            room = activeRoom,
            isBusy = loungeState.isCloudBusy,
            onCancelRequest = {
                AestheticHaptics.softTick(context, haptic)
                actions.onDeclinePendingPartner(activeRoom.coupleCode)
            },
            modifier = modifier
        )
        return
    }

    val hasPendingHostApproval = !activeRoom.isPassAndPlay &&
        activeRoom.approvalStatus == "AWAITING_MY_APPROVAL" &&
        activeRoom.pendingPartnerName.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CoupleActiveRoomHeaderCard(
            activeRoom = activeRoom,
            isSignedIn = isSignedIn,
            cloudSyncStatus = loungeState.cloudSyncStatus,
            hasPendingHostApproval = hasPendingHostApproval,
            pendingTurnCount = loungeState.pendingTurnCount,
            onRequireSignIn = { bypassAuthForLocalPlay = false },
            onRetryCloudSync = actions.onRetryCloudSync,
            onTogglePassAndPlay = actions.onTogglePassAndPlay,
            onSignOut = {
                signOutGoogle(
                    credentialManager = getCredentialManager(),
                    scope = scope,
                    onBeforeSignOut = actions.onDetachCloudListeners,
                    onSignOutComplete = {
                        actions.onCloudUserChanged(null, null)
                        actions.onShowFeedback("Signed out")
                    }
                )
            },
            onRequestEndRoom = { showRemovePartnerDialog = true },
            onUpdateSpiceLevel = actions.onUpdateSpiceLevel,
            onShowFeedback = actions.onShowFeedback
        )

        if (hasPendingHostApproval) {
            HostApprovalBannerCard(
                coupleCode = activeRoom.coupleCode,
                requesterName = activeRoom.pendingPartnerName,
                isBusy = loungeState.isCloudBusy,
                onAllow = {
                    AestheticHaptics.warmConfirm(context, haptic)
                    actions.onApprovePendingPartner(activeRoom.coupleCode)
                },
                onDecline = {
                    AestheticHaptics.softTick(context, haptic)
                    actions.onDeclinePendingPartner(activeRoom.coupleCode)
                }
            )
        }

        val latestPartnerPulse = remember(activeRoom.whisperStateJson, activeRoom.myName) {
            val myTrimmed = activeRoom.myName.trim()
            CoupleActivitiesDeck.dateWhisperStateFromJson(activeRoom.whisperStateJson)
                .whispers
                .lastOrNull { !it.senderName.equals(myTrimmed, ignoreCase = true) }
        }
        if (latestPartnerPulse != null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = VelvetSurface,
                border = BorderStroke(1.2.dp, Color(0xFFF472B6).copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        AestheticHaptics.softTick(context, haptic)
                        onLaunchCoupleGame(CoupleGameType.HEART_TAP)
                    }
                    .testTag("couple_hub_partner_pulse_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    VelvetRoseGlow.copy(alpha = 0.24f),
                                    Color(0xFFA855F7).copy(alpha = 0.18f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "🫂", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${latestPartnerPulse.senderName} sent you a pulse 🩷",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFF9A8D4)
                        )
                        Text(
                            text = latestPartnerPulse.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = VelvetTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = VelvetRoseGlow.copy(alpha = 0.28f)
                    ) {
                        Text(
                            text = "Open ✨",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        if (showActivityGrid) {
            CoupleActivityGrid(onLaunchCoupleGame = onLaunchCoupleGame)
        }
    }

    if (showRemovePartnerDialog) {
        EndCoupleRoomDialog(
            coupleCode = activeRoom.coupleCode,
            onDismiss = { showRemovePartnerDialog = false },
            onConfirmEnd = {
                showRemovePartnerDialog = false
                actions.onDeleteRoom(activeRoom.coupleCode)
            }
        )
    }
}

@Composable
private fun CoupleActiveRoomHeaderCard(
    activeRoom: CoupleRoomRecord,
    isSignedIn: Boolean,
    cloudSyncStatus: String,
    hasPendingHostApproval: Boolean,
    pendingTurnCount: Int = 0,
    onRequireSignIn: () -> Unit,
    onRetryCloudSync: () -> Unit,
    onTogglePassAndPlay: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onRequestEndRoom: () -> Unit,
    onUpdateSpiceLevel: (SpiceLevel) -> Unit,
    onShowFeedback: (String) -> Unit
) {
    val currentSpice = remember(activeRoom.spiceLevel) {
        runCatching { SpiceLevel.valueOf(activeRoom.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
    }
    val isLive = !activeRoom.isPassAndPlay && isSignedIn && cloudSyncStatus == "LIVE"
    val isOffline = !activeRoom.isPassAndPlay && isSignedIn && cloudSyncStatus == "OFFLINE"
    val isConnecting = !activeRoom.isPassAndPlay && isSignedIn && cloudSyncStatus == "CONNECTING"
    val partnerDisplay = activeRoom.partnerName.takeIf { it.isNotBlank() }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetBgTop),
        border = BorderStroke(1.dp, VelvetBorder),
        modifier = Modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2.5f, cornerRadius = 28.dp)
            .testTag("couple_room_header")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VelvetBgTop, VelvetBgBottom)))
                .background(
                    Brush.radialGradient(
                        colors = listOf(VelvetRoseGlow.copy(alpha = 0.22f), Color.Transparent),
                        radius = 520f
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CoupleRoomPresenceAndMenuRow(
                    isPassAndPlay = activeRoom.isPassAndPlay,
                    isSignedIn = isSignedIn,
                    isLive = isLive,
                    isOffline = isOffline,
                    isConnecting = isConnecting,
                    hasPendingHostApproval = hasPendingHostApproval,
                    pendingTurnCount = pendingTurnCount,
                    onRequireSignIn = onRequireSignIn,
                    onRetryCloudSync = onRetryCloudSync,
                    onTogglePassAndPlay = { onTogglePassAndPlay(!activeRoom.isPassAndPlay) },
                    onSignOut = onSignOut,
                    onRequestEndRoom = onRequestEndRoom
                )

                CoupleHeroNamesBlock(
                    myName = activeRoom.myName.ifBlank { "Me" },
                    partnerDisplay = partnerDisplay,
                    streakCount = activeRoom.streakCount,
                    matchCount = activeRoom.matchCount
                )

                CoupleRoomCodeBar(
                    coupleCode = activeRoom.coupleCode,
                    onShowFeedback = onShowFeedback
                )

                CoupleSpiceSelectorBar(
                    currentSpice = currentSpice,
                    onSelectSpice = onUpdateSpiceLevel
                )
            }
        }
    }
}

@Composable
private fun CoupleRoomPresenceAndMenuRow(
    isPassAndPlay: Boolean,
    isSignedIn: Boolean,
    isLive: Boolean,
    isOffline: Boolean,
    isConnecting: Boolean,
    hasPendingHostApproval: Boolean,
    pendingTurnCount: Int = 0,
    onRequireSignIn: () -> Unit,
    onRetryCloudSync: () -> Unit,
    onTogglePassAndPlay: () -> Unit,
    onSignOut: () -> Unit,
    onRequestEndRoom: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    val dotColor = when {
        isPassAndPlay -> Color(0xFFF472B6)
        isLive -> LiveEmerald
        isOffline -> Color(0xFFFB7185)
        else -> WaitingAmber
    }
    val statusLabel = when {
        isPassAndPlay -> "1 Phone"
        isLive -> "Live"
        hasPendingHostApproval -> "Request pending"
        !isSignedIn -> "Sign in to sync"
        isOffline -> "Offline · Retry"
        isConnecting -> "Connecting…"
        else -> "Waiting"
    }
    val pillActionable = (!isSignedIn && !isPassAndPlay) || isOffline

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, dotColor.copy(alpha = 0.45f)),
                modifier = Modifier.clickable(enabled = pillActionable) {
                    if (!isSignedIn) onRequireSignIn()
                    else if (isOffline) onRetryCloudSync()
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        ),
                        color = VelvetTextPrimary
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, VelvetBorder),
                modifier = Modifier
                    .size(26.dp)
                    .testTag("silent_lock_badge")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Always-on private protection",
                        tint = Color(0xFFF9A8D4),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (!isPassAndPlay && pendingTurnCount > 0) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = WaitingAmber.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, WaitingAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("pending_sync_badge")
                ) {
                    Text(
                        text = "Queued $pendingTurnCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        color = WaitingAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { showOverflowMenu = true },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("couple_room_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Room options",
                    tint = VelvetTextMuted
                )
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isPassAndPlay) "Switch to 2-Phone Live"
                            else "Switch to Pass & Play (1 Phone)"
                        )
                    },
                    onClick = {
                        showOverflowMenu = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePassAndPlay()
                    },
                    modifier = Modifier.testTag("pass_and_play_switch")
                )
                if (isSignedIn) {
                    DropdownMenuItem(
                        text = { Text("Sign Out Google") },
                        onClick = {
                            showOverflowMenu = false
                            onSignOut()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Sign In for 2-Phone Live") },
                        onClick = {
                            showOverflowMenu = false
                            onRequireSignIn()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("End Couple Room", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showOverflowMenu = false
                        onRequestEndRoom()
                    },
                    modifier = Modifier.testTag("leave_room_button")
                )
            }
        }
    }
}

@Composable
private fun CoupleHeroNamesBlock(
    myName: String,
    partnerDisplay: String?,
    streakCount: Int,
    matchCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = myName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = VelvetTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = "♥", fontSize = 18.sp, color = VelvetRoseGlow)
            Text(
                text = partnerDisplay ?: "Waiting…",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = if (partnerDisplay != null) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Serif
                ),
                color = if (partnerDisplay != null) VelvetTextPrimary else VelvetTextMuted.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Text(
            text = "🔥 $streakCount streak   ·   💞 $matchCount matches",
            style = MaterialTheme.typography.labelMedium,
            color = VelvetTextMuted
        )
    }
}

@Composable
private fun CoupleRoomCodeBar(
    coupleCode: String,
    onShowFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    fun copySensitiveRoomCode() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Couple Room Code", coupleCode).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        cm?.setPrimaryClip(clip)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ROOM CODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = VelvetTextMuted.copy(alpha = 0.75f)
                )
                Text(
                    text = "#$coupleCode",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    ),
                    color = VelvetTextPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copySensitiveRoomCode()
                        onShowFeedback("Copied private code #$coupleCode")
                    },
                    modifier = Modifier.testTag("copy_couple_code_btn")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Room Code",
                        tint = VelvetTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val inviteText = "Join me in KriMoon Couple Lounge ♥\n" +
                            "Open Play → Couple Lounge and enter code: #$coupleCode"
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, inviteText)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(sendIntent, "Invite Partner"))
                        }.onFailure {
                            onShowFeedback("Couldn't open share sheet — code copied instead")
                            copySensitiveRoomCode()
                        }
                    },
                    modifier = Modifier.testTag("share_couple_invite_btn")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share Room Code",
                        tint = Color(0xFFF472B6),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoupleSpiceSelectorBar(
    currentSpice: SpiceLevel,
    onSelectSpice: (SpiceLevel) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = VelvetTrack,
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpiceLevel.entries.forEach { level ->
                val selected = currentSpice == level
                val activeBg = when (level) {
                    SpiceLevel.SWEET -> Color(0xFF9D4EDD)
                    SpiceLevel.FLIRTY -> VelvetRoseGlow
                    SpiceLevel.BOLD -> Color(0xFFBE123C)
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (selected) activeBg else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSpice(level)
                        }
                        .testTag("spice_${level.name.lowercase()}")
                ) {
                    Text(
                        text = "${level.emoji} ${level.title}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        color = if (selected) Color.White else VelvetTextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CoupleActivityRowItem(
    pair: List<CoupleGameType>,
    onLaunchCoupleGame: (CoupleGameType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pair.size == 1) {
        val game = pair.first()
        CoupleGameLauncherCard(
            game = game,
            onClick = { onLaunchCoupleGame(game) },
            modifier = modifier
                .fillMaxWidth()
                .height(136.dp)
        )
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pair.forEach { game ->
                CoupleGameLauncherCard(
                    game = game,
                    onClick = { onLaunchCoupleGame(game) },
                    modifier = Modifier
                        .weight(1f)
                        .height(148.dp)
                )
            }
        }
    }
}

@Composable
private fun CoupleActivityGrid(
    onLaunchCoupleGame: (CoupleGameType) -> Unit
) {
    val rows = remember { CoupleGameType.entries.chunked(2) }
    rows.forEach { pair ->
        CoupleActivityRowItem(
            pair = pair,
            onLaunchCoupleGame = onLaunchCoupleGame
        )
    }
}

@Composable
private fun EndCoupleRoomDialog(
    coupleCode: String,
    onDismiss: () -> Unit,
    onConfirmEnd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "End Couple Room?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
        },
        text = {
            Text(
                "This disconnects Room #$coupleCode for both phones. You can create a new room code anytime.",
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmEnd,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_remove_partner_btn")
            ) {
                Text("End Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Connected ♥")
            }
        }
    )
}

@Composable
private fun CoupleAuthGateCard(
    isSigningIn: Boolean,
    hasLocalRoom: Boolean,
    onSignInClick: () -> Unit,
    onPlayOfflineSinglePhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetBgTop),
        border = BorderStroke(1.dp, VelvetBorder),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 3f, cornerRadius = 28.dp)
            .testTag("couple_auth_gate_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VelvetBgTop, VelvetBgBottom)))
                .background(
                    Brush.radialGradient(
                        colors = listOf(VelvetRoseGlow.copy(alpha = 0.24f), Color.Transparent),
                        radius = 480f
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = VelvetRoseGlow.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VelvetBorder),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private Couple Lounge",
                            tint = Color(0xFFF9A8D4),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = "Private Couple Lounge",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = VelvetTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sign in to connect two phones live over the internet. Everything stays private between you two.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VelvetTextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onSignInClick,
                    enabled = !isSigningIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelvetRoseGlow,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_sign_in_btn")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                TextButton(
                    onClick = onPlayOfflineSinglePhone,
                    modifier = Modifier.testTag("pass_and_play_offline_btn")
                ) {
                    Text(
                        text = if (hasLocalRoom) "Use existing room on 1 phone" else "Or play on 1 phone (Pass & Play)",
                        style = MaterialTheme.typography.labelMedium,
                        color = VelvetTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun HostApprovalBannerCard(
    coupleCode: String,
    requesterName: String,
    isBusy: Boolean,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetBgTop),
        border = BorderStroke(1.5.dp, VelvetRoseGlow.copy(alpha = 0.85f)),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2.5f, cornerRadius = 24.dp)
            .testTag("host_approval_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(VelvetRoseGlow.copy(alpha = 0.22f), VelvetBgBottom)
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = VelvetRoseGlow.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, VelvetBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Host confirmation required",
                            tint = Color(0xFFF9A8D4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm Partner Entry",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = VelvetTextPrimary
                    )
                    Text(
                        text = "$requesterName wants to join Room #$coupleCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = VelvetTextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, VelvetBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("decline_partner_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Decline",
                        tint = VelvetTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decline", color = VelvetTextMuted, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onAllow,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelvetRoseGlow,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("allow_partner_btn")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Allow",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Allow ♥", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingForHostApprovalCard(
    room: CoupleRoomRecord,
    isBusy: Boolean,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetBgTop),
        border = BorderStroke(1.2.dp, WaitingAmber.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 2.5f, cornerRadius = 28.dp)
            .testTag("waiting_host_approval_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VelvetBgTop, VelvetBgBottom)))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = WaitingAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, WaitingAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Waiting for Host approval",
                            tint = WaitingAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = "Awaiting Host Confirmation",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = VelvetTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "You requested access to Room #${room.coupleCode}. ${room.partnerName.ifBlank { "The room creator" }} must tap Allow on their phone to unlock the lounge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VelvetTextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFF472B6),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Listening live for approval…",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFF9A8D4)
                    )
                }

                OutlinedButton(
                    onClick = onCancelRequest,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, VelvetBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_join_request_btn")
                ) {
                    Text("Cancel Request", color = VelvetTextMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CouplePairingCard(
    defaultUserName: String,
    existingRooms: List<CoupleRoomRecord>,
    isSignedIn: Boolean,
    isBusy: Boolean,
    inviteJoinState: InviteJoinUiState,
    onCreateUniqueRoom: (name: String) -> Unit,
    onRequestJoinRoom: (name: String, code: String) -> Unit,
    onClearInviteJoinState: () -> Unit,
    onSelectRoom: (code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember(defaultUserName) { mutableStateOf(defaultUserName.ifBlank { "" }) }
    var codeInput by remember { mutableStateOf("") }
    var isJoinMode by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetBgTop),
        border = BorderStroke(1.dp, VelvetBorder),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 3f, cornerRadius = 28.dp)
            .testTag("couple_pairing_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(VelvetBgTop, VelvetBgBottom)))
                .background(
                    Brush.radialGradient(
                        colors = listOf(VelvetRoseGlow.copy(alpha = 0.2f), Color.Transparent),
                        radius = 480f
                    )
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PairingHeaderRow(isSignedIn = isSignedIn)

                Text(
                    text = "Two Phones, One Private Space",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = VelvetTextPrimary,
                    textAlign = TextAlign.Center
                )

                PairingModeToggleBar(
                    isJoinMode = isJoinMode,
                    onModeChange = {
                        isJoinMode = it
                        onClearInviteJoinState()
                    }
                )

                PairingFormFields(
                    nameInput = nameInput,
                    onNameChange = { nameInput = it },
                    codeInput = codeInput,
                    onCodeChange = {
                        codeInput = CoupleTurnCodec.normalizeCode(it)
                        if (inviteJoinState is InviteJoinUiState.Error) {
                            onClearInviteJoinState()
                        }
                    },
                    isJoinMode = isJoinMode,
                    isBusy = isBusy,
                    inviteJoinState = inviteJoinState,
                    onCreateUniqueRoom = { onCreateUniqueRoom(nameInput) },
                    onRequestJoinRoom = { onRequestJoinRoom(nameInput, codeInput) },
                    onDismissInviteState = onClearInviteJoinState
                )

                if (existingRooms.isNotEmpty()) {
                    PairingRecentRoomsRow(
                        existingRooms = existingRooms,
                        onSelectRoom = onSelectRoom
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingHeaderRow(isSignedIn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isSignedIn) "● Ready to connect live" else "● Pass & Play",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSignedIn) LiveEmerald else Color(0xFFF472B6)
        )
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(0.8.dp, VelvetBorder),
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Always-on private protection",
                    tint = Color(0xFFF9A8D4),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun PairingModeToggleBar(
    isJoinMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = VelvetTrack,
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Create Room" to false, "Join Partner" to true).forEach { (label, joinFlag) ->
                val selected = isJoinMode == joinFlag
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (selected) VelvetRoseGlow else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            AestheticHaptics.softTick(context, haptic)
                            onModeChange(joinFlag)
                        }
                        .testTag(if (joinFlag) "pairing_mode_join" else "pairing_mode_create")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) Color.White else VelvetTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingFormFields(
    nameInput: String,
    onNameChange: (String) -> Unit,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    isJoinMode: Boolean,
    isBusy: Boolean,
    inviteJoinState: InviteJoinUiState,
    onCreateUniqueRoom: () -> Unit,
    onRequestJoinRoom: () -> Unit,
    onDismissInviteState: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = VelvetTextPrimary,
        unfocusedTextColor = VelvetTextPrimary,
        focusedBorderColor = Color(0xFFF472B6),
        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
        focusedLabelColor = Color(0xFFF9A8D4),
        unfocusedLabelColor = VelvetTextMuted,
        cursorColor = VelvetRoseGlow
    )

    // Live 1-second cooldown countdown when server-side rate limiting is active
    var rateLimitSecondsLeft by remember(inviteJoinState) {
        val initial = if (inviteJoinState is InviteJoinUiState.RateLimited) {
            ((inviteJoinState.retryAfterEpochMs - System.currentTimeMillis() + 999L) / 1000L)
                .toInt()
                .coerceAtLeast(0)
        } else 0
        mutableStateOf(initial)
    }
    LaunchedEffect(inviteJoinState) {
        if (inviteJoinState is InviteJoinUiState.RateLimited) {
            while (true) {
                val remaining = ((inviteJoinState.retryAfterEpochMs - System.currentTimeMillis() + 999L) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                rateLimitSecondsLeft = remaining
                if (remaining <= 0) break
                delay(1000L)
            }
        } else {
            rateLimitSecondsLeft = 0
        }
    }

    OutlinedTextField(
        value = nameInput,
        onValueChange = onNameChange,
        label = { Text("Your Name") },
        placeholder = { Text("e.g. Hariom", color = VelvetTextMuted.copy(alpha = 0.5f)) },
        singleLine = true,
        colors = fieldColors,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("couple_name_input")
    )

    if (isJoinMode) {
        val isLookingUp = isBusy || inviteJoinState is InviteJoinUiState.Loading
        val isRateLimitCoolingDown = inviteJoinState is InviteJoinUiState.RateLimited && rateLimitSecondsLeft > 0

        OutlinedTextField(
            value = codeInput,
            onValueChange = onCodeChange,
            label = { Text("Partner's 6-Digit Code") },
            placeholder = { Text("482913", color = VelvetTextMuted.copy(alpha = 0.5f)) },
            singleLine = true,
            enabled = !isLookingUp,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("couple_code_input")
        )

        when (inviteJoinState) {
            is InviteJoinUiState.Loading -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, VelvetBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_lookup_loading_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFF472B6),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Verifying Room #${inviteJoinState.coupleCode} securely…",
                            style = MaterialTheme.typography.labelMedium,
                            color = VelvetTextPrimary
                        )
                    }
                }
            }
            is InviteJoinUiState.RateLimited -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WaitingAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, WaitingAmber.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_rate_limit_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Rate limit active",
                                tint = WaitingAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (rateLimitSecondsLeft > 0) {
                                    "Rate limit active · Try again in ${rateLimitSecondsLeft}s"
                                } else {
                                    "Cooldown ended · Ready to retry"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = WaitingAmber
                            )
                        }
                        Text(
                            text = inviteJoinState.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = VelvetTextPrimary
                        )
                    }
                }
            }
            is InviteJoinUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE11D48).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFFFB7185).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_error_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Invite lookup error",
                                tint = Color(0xFFFDA4AF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = inviteJoinState.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = VelvetTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (inviteJoinState.canRetry) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AestheticHaptics.crispClick(context, haptic)
                                        onRequestJoinRoom()
                                    },
                                    enabled = !isLookingUp,
                                    border = BorderStroke(1.dp, Color(0xFFFDA4AF)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("invite_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry verification",
                                        tint = VelvetTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", color = VelvetTextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(
                                    onClick = onDismissInviteState,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("invite_dismiss_error_button")
                                ) {
                                    Text("Dismiss", color = VelvetTextMuted)
                                }
                            }
                        }
                    }
                }
            }
            InviteJoinUiState.Idle -> Unit
        }

        Text(
            text = "The room creator must tap Allow on their phone before you enter.",
            style = MaterialTheme.typography.labelSmall,
            color = VelvetTextMuted,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = {
                AestheticHaptics.crispClick(context, haptic)
                onRequestJoinRoom()
            },
            enabled = !isLookingUp && !isRateLimitCoolingDown,
            colors = ButtonDefaults.buttonColors(
                containerColor = VelvetRoseGlow,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("couple_join_button")
        ) {
            if (isLookingUp) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isRateLimitCoolingDown) {
                Text(
                    text = "Wait ${rateLimitSecondsLeft}s…",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                Text(
                    text = if (inviteJoinState is InviteJoinUiState.RateLimited) {
                        "Retry Join ♥"
                    } else {
                        "Request to Join ♥"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    } else {
        Text(
            text = "Generates a permanently unique 6-digit code exclusively for you two — no other couple can ever claim it.",
            style = MaterialTheme.typography.labelSmall,
            color = VelvetTextMuted,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = {
                AestheticHaptics.warmConfirm(context, haptic)
                onCreateUniqueRoom()
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = VelvetRoseGlow,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_couple_code_btn")
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Generate Unique Room ♥",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun PairingRecentRoomsRow(
    existingRooms: List<CoupleRoomRecord>,
    onSelectRoom: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        existingRooms.take(3).forEach { room ->
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, VelvetBorder),
                modifier = Modifier.clickable {
                    AestheticHaptics.softTick(context, haptic)
                    onSelectRoom(room.coupleCode)
                }
            ) {
                Text(
                    text = "#${room.coupleCode}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = VelvetTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CoupleGameLauncherCard(
    game: CoupleGameType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VelvetSurface),
        border = BorderStroke(1.dp, game.accentColor.copy(alpha = 0.38f)),
        modifier = modifier
            .cute3DCard(
                maxTilt = 3.2f,
                cornerRadius = 24.dp,
                accentColor = game.accentColor,
                onClick = onClick
            )
            .testTag("couple_game_${game.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(game.accentColor.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(game.accentColor.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Text(game.emoji, fontSize = 22.sp)
            }
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = VelvetTextPrimary
            )
            Text(
                text = game.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = VelvetTextMuted,
                minLines = 2
            )
        }
    }
}
