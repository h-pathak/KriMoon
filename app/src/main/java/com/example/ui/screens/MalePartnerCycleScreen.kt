package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CoupleRoomRecord
import com.example.model.CyclePhase
import com.example.model.PartnerCareGuideProvider
import com.example.model.PartnerCycleSyncSnapshot
import com.example.ui.theme.MaleAmber
import com.example.ui.theme.MaleBorder
import com.example.ui.theme.MaleCobalt
import com.example.ui.theme.MaleCrimson
import com.example.ui.theme.MaleEmerald
import com.example.ui.theme.MaleIndigo
import com.example.ui.theme.MaleInk
import com.example.ui.theme.MaleInkMuted
import com.example.ui.theme.MaleObsidian
import com.example.ui.theme.MaleSky
import com.example.ui.theme.MaleSurface
import com.example.ui.theme.MaleSurfaceElevated

private fun masculinePhaseAccent(phase: CyclePhase): Color = when (phase) {
    CyclePhase.MENSTRUAL -> MaleCrimson
    CyclePhase.FOLLICULAR -> MaleEmerald
    CyclePhase.FERTILE -> MaleSky
    CyclePhase.OVULATION -> MaleCobalt
    CyclePhase.LUTEAL -> MaleIndigo
    CyclePhase.LATE_LUTEAL -> MaleAmber
}

private fun formatRelativeSyncTime(syncedAtMillis: Long): String {
    if (syncedAtMillis <= 0L) return "Not synced yet"
    val diffSec = ((System.currentTimeMillis() - syncedAtMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Live · just now"
        diffSec < 3600L -> "Synced ${diffSec / 60L}m ago"
        diffSec < 86_400L -> "Synced ${diffSec / 3600L}h ago"
        else -> "Synced ${diffSec / 86_400L}d ago"
    }
}

@Composable
fun MalePartnerCycleScreen(
    activeRoom: CoupleRoomRecord?,
    cloudSyncStatus: String,
    isCloudBusy: Boolean,
    onSyncNow: () -> Unit,
    onOpenCoupleLounge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLivePaired = activeRoom != null &&
        !activeRoom.isPassAndPlay &&
        activeRoom.approvalStatus == "CONNECTED"

    val rawSnapshot = remember(activeRoom?.partnerCycleSyncJson) {
        PartnerCycleSyncSnapshot.fromJsonString(activeRoom?.partnerCycleSyncJson)
    }
    val liveSnapshot = remember(rawSnapshot) {
        rawSnapshot?.projectToTime()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaleObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Connection Status Header
            item(key = "sync_status_bar") {
                PartnerSyncHeaderCard(
                    activeRoom = activeRoom,
                    isLivePaired = isLivePaired,
                    cloudSyncStatus = cloudSyncStatus,
                    syncedAtMillis = rawSnapshot?.syncedAtMillis ?: 0L,
                    isBusy = isCloudBusy,
                    onSyncNow = onSyncNow
                )
            }

            // 2. Unpaired / Waiting States (Never display mock/fake partner cycle data)
            if (!isLivePaired) {
                item(key = "unpaired_connect_card") {
                    UnpairedConnectCard(
                        hasPendingRoom = activeRoom != null,
                        pendingStatus = activeRoom?.approvalStatus.orEmpty(),
                        roomCode = activeRoom?.coupleCode.orEmpty(),
                        onOpenCoupleLounge = onOpenCoupleLounge
                    )
                }
            } else if (liveSnapshot == null) {
                item(key = "awaiting_sync_card") {
                    AwaitingPartnerCycleSyncCard(
                        partnerName = activeRoom?.partnerName.orEmpty().ifBlank { "Your partner" },
                        onSyncNow = onSyncNow,
                        onOpenCoupleLounge = onOpenCoupleLounge
                    )
                }
            } else {
                val currentPhase = liveSnapshot.currentPhase
                val nextPhase = liveSnapshot.nextPhase
                val currentGuide = PartnerCareGuideProvider.guideFor(currentPhase)
                val nextGuide = PartnerCareGuideProvider.guideFor(nextPhase)
                val partnerLabel = liveSnapshot.partnerName
                    .ifBlank { activeRoom?.partnerName.orEmpty() }
                    .ifBlank { "Partner" }

                // 3. Current Phase Hero Card
                item(key = "current_phase_hero") {
                    CurrentPhaseBriefCard(
                        partnerName = partnerLabel,
                        snapshot = liveSnapshot,
                        energyLabel = currentGuide.energyLabel,
                        statusHeadline = currentGuide.statusHeadline,
                        moodSummary = currentGuide.moodSummary
                    )
                }

                // 4. Current Phase Care Playbook
                item(key = "current_care_guide") {
                    CarePlaybookCard(
                        title = "How to Support Her Right Now",
                        subtitle = currentGuide.displayTitle,
                        accent = masculinePhaseAccent(currentPhase),
                        actions = currentGuide.careRightNow,
                        avoidList = currentGuide.avoidList
                    )
                }

                // 5. Next Phase Forecast & Prep Playbook
                item(key = "next_phase_prep") {
                    NextPhasePrepCard(
                        nextPhase = nextPhase,
                        daysUntilNextPhase = liveSnapshot.daysUntilNextPhase,
                        prepHeadline = currentGuide.prepForNextHeadline,
                        prepChecklist = currentGuide.prepForNextActions,
                        nextPhaseGuide = nextGuide
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PartnerSyncHeaderCard(
    activeRoom: CoupleRoomRecord?,
    isLivePaired: Boolean,
    cloudSyncStatus: String,
    syncedAtMillis: Long,
    isBusy: Boolean,
    onSyncNow: () -> Unit
) {
    val isLiveOnline = isLivePaired && cloudSyncStatus == "LIVE"
    val statusColor = when {
        isLiveOnline -> MaleEmerald
        isLivePaired -> MaleAmber
        else -> MaleInkMuted
    }
    val statusLabel = when {
        isLiveOnline -> "LIVE E2EE SYNC"
        isLivePaired && cloudSyncStatus == "OFFLINE" -> "OFFLINE · CACHED"
        isLivePaired -> "SYNCING..."
        activeRoom?.approvalStatus == "PENDING_HOST_APPROVAL" -> "AWAITING APPROVAL"
        activeRoom?.approvalStatus == "AWAITING_MY_APPROVAL" -> "APPROVAL NEEDED"
        else -> "NOT PAIRED"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.dp, MaleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_sync_header_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = statusColor
                    )
                    if (isLivePaired && activeRoom?.ecdhSharedSecretB64?.isNotBlank() == true) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "End-to-end encrypted",
                            tint = MaleSky,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = if (isLivePaired) {
                        val name = activeRoom?.partnerName.orEmpty().ifBlank { "Partner" }
                        "$name · ${formatRelativeSyncTime(syncedAtMillis)}"
                    } else {
                        "Connect in Couple Lounge for live partner sync"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaleInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLivePaired) {
                IconButton(
                    onClick = onSyncNow,
                    enabled = !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("male_sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh live partner sync",
                        tint = if (isBusy) MaleInkMuted else MaleSky
                    )
                }
            }
        }
    }
}

@Composable
private fun UnpairedConnectCard(
    hasPendingRoom: Boolean,
    pendingStatus: String,
    roomCode: String,
    onOpenCoupleLounge: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.dp, MaleCobalt.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_unpaired_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaleCobalt.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaleCobalt.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Encrypted partner link",
                    tint = MaleSky,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = when {
                    hasPendingRoom && pendingStatus == "PENDING_HOST_APPROVAL" ->
                        "Waiting for Room #$roomCode Approval"
                    hasPendingRoom && pendingStatus == "AWAITING_MY_APPROVAL" ->
                        "Approve Your Partner in Room #$roomCode"
                    hasPendingRoom ->
                        "Finish Pairing Room #$roomCode"
                    else ->
                        "Pair With Your Partner"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaleInk
            )

            Text(
                text = "Link phones in Couple Lounge using a 6-digit private code. Once paired, her current cycle phase, next upcoming phase, and practical care guide sync here automatically over end-to-end encryption.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaleInkMuted
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BriefFeatureBullet("Live current-phase briefing & cycle day countdown")
                BriefFeatureBullet("Clear, practical care guide for today and the next phase")
                BriefFeatureBullet("Zero-knowledge ECDH-P256 + AES-256-GCM encryption")
            }

            Button(
                onClick = onOpenCoupleLounge,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaleCobalt,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("male_go_to_lounge_button")
            ) {
                Text(
                    text = if (hasPendingRoom) "Open Couple Lounge" else "Pair in Couple Lounge",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AwaitingPartnerCycleSyncCard(
    partnerName: String,
    onSyncNow: () -> Unit,
    onOpenCoupleLounge: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.dp, MaleEmerald.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_waiting_sync_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Awaiting partner sync",
                    tint = MaleEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Paired with $partnerName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaleInk
                )
            }

            Text(
                text = "Your encrypted link is active. As soon as $partnerName opens KriMoon on her phone, her current cycle phase and care guide will appear here automatically in real time.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaleInkMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncNow,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaleCobalt),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("male_check_sync_button")
                ) {
                    Text("Check Sync", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenCoupleLounge,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaleBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Couple Lounge", color = MaleInk)
                }
            }
        }
    }
}

@Composable
private fun CurrentPhaseBriefCard(
    partnerName: String,
    snapshot: PartnerCycleSyncSnapshot,
    energyLabel: String,
    statusHeadline: String,
    moodSummary: String
) {
    val accent = masculinePhaseAccent(snapshot.currentPhase)
    val progress = (snapshot.dayInCycle.toFloat() / snapshot.totalCycleDays.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_current_phase_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "CURRENT PHASE · ${partnerName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        ),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaleSurfaceElevated
                ) {
                    Text(
                        text = if (snapshot.isLate) {
                            "Day ${snapshot.dayInCycle} · +${snapshot.daysLate}d"
                        } else {
                            "Day ${snapshot.dayInCycle} / ${snapshot.totalCycleDays}"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaleInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = PartnerCareGuideProvider.guideFor(snapshot.currentPhase).displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaleInk
                )
                Text(
                    text = statusHeadline,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accent
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                trackColor = MaleSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = if (snapshot.isPeriodCurrentlyActive) "Period Status" else "Next Period",
                    value = when {
                        snapshot.isPeriodCurrentlyActive -> "Active · Day ${snapshot.dayInCycle}"
                        snapshot.isLate -> "${snapshot.daysLate}d late"
                        snapshot.daysUntilNextPeriod <= 0 -> "Expected today"
                        snapshot.daysUntilNextPeriod == 1 -> "In 1 day"
                        else -> "In ${snapshot.daysUntilNextPeriod} days"
                    },
                    accent = if (snapshot.isPeriodCurrentlyActive) MaleCrimson else MaleSky,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Next Shift",
                    value = "${snapshot.nextPhase.shortName} · ${snapshot.daysUntilNextPhase}d",
                    accent = masculinePhaseAccent(snapshot.nextPhase),
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaleSurfaceElevated,
                border = BorderStroke(1.dp, MaleBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Energy & Vibe: $energyLabel",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaleInk
                        )
                    }
                    Text(
                        text = moodSummary,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        color = MaleInkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CarePlaybookCard(
    title: String,
    subtitle: String,
    accent: Color,
    actions: List<com.example.model.PartnerCareAction>,
    avoidList: List<String>
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.dp, MaleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_care_playbook_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaleInk
                )
                Text(
                    text = "Practical care playbook · $subtitle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaleInkMuted
                )
            }

            actions.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaleSurfaceElevated,
                    border = BorderStroke(1.dp, MaleBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaleInk
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = item.category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = accent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                color = MaleInkMuted
                            )
                        }
                    }
                }
            }

            if (avoidList.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaleAmber.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaleAmber.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaleAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "WHAT TO AVOID RIGHT NOW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = MaleAmber
                            )
                        }
                        avoidList.forEach { warn ->
                            Text(
                                text = "• $warn",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaleInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextPhasePrepCard(
    nextPhase: CyclePhase,
    daysUntilNextPhase: Int,
    prepHeadline: String,
    prepChecklist: List<String>,
    nextPhaseGuide: com.example.model.PartnerPhaseCareGuide
) {
    val nextAccent = masculinePhaseAccent(nextPhase)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaleSurface),
        border = BorderStroke(1.dp, nextAccent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("male_next_phase_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = nextAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "UP NEXT · PLAN AHEAD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        ),
                        color = nextAccent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = nextAccent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (daysUntilNextPhase <= 1) "Starts tomorrow" else "In ~$daysUntilNextPhase days",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = nextAccent,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = nextPhaseGuide.displayTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaleInk
                )
                Text(
                    text = prepHeadline,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = nextAccent
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prepChecklist.forEach { step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = nextAccent,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaleInk
                        )
                    }
                }
            }

            HorizontalDivider(color = MaleBorder)

            Text(
                text = "CARE FOCUS FOR ${nextPhaseGuide.displayTitle.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = MaleInkMuted
            )

            nextPhaseGuide.careRightNow.take(3).forEach { nextAction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(nextAccent)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = nextAction.title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaleInk
                        )
                        Text(
                            text = nextAction.detail,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaleInkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaleSurfaceElevated,
        border = BorderStroke(1.dp, MaleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp
                ),
                color = MaleInkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BriefFeatureBullet(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaleEmerald,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaleInk
        )
    }
}
