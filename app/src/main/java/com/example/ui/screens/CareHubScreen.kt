package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Medication
import com.example.data.ShopItem
import com.example.data.TaskCategory
import com.example.data.UserProfile
import com.example.data.WaterLog
import com.example.model.CycleStatus
import com.example.ui.components.cuteBouncyClick
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesWaterBlue
import com.example.viewmodel.CareTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CareHubScreen(
    activeTab: CareTab,
    onSelectTab: (CareTab) -> Unit,
    userProfile: UserProfile?,
    cycleStatus: CycleStatus,
    waterLog: WaterLog?,
    medications: List<Medication>,
    shopItems: List<ShopItem>,
    taskCategories: List<TaskCategory>,
    shopHideCompleted: Boolean,
    shopViewMode: String,
    shopSortMode: String,
    onIncrementWater: () -> Unit,
    onDecrementWater: () -> Unit,
    onSetWaterCount: (Int) -> Unit,
    onResetWater: () -> Unit,
    onUpdateWaterGoal: (Int) -> Unit,
    onAddMedication: (String, String, Int, Int, String, String, String, String, String, String) -> Unit,
    onUpdateMedication: (Medication) -> Unit,
    onDeleteMedication: (Long) -> Unit,
    onToggleMedication: (Long, Boolean) -> Unit,
    onMarkMedicationTaken: (Long) -> Unit,
    onToggleShopItem: (ShopItem) -> Unit,
    onAddShopItem: (String, String, String, String) -> Unit,
    onUpdateShopItem: (ShopItem) -> Unit,
    onDeleteShopItem: (Long) -> Unit,
    onMoveShopItem: (ShopItem, Int) -> Unit,
    onClearCompletedShop: () -> Unit,
    onResetShopDefaults: () -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    onUpdateCategory: (TaskCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onToggleHideCompleted: (Boolean) -> Unit,
    onSetViewMode: (String) -> Unit,
    onSetSortMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val waterCount = waterLog?.glassesCount ?: 0
    val waterGoal = userProfile?.waterGoal ?: 8
    val activeMeds = medications.count { it.isEnabled }
    val takenMeds = medications.count { it.lastTakenDate == todayDateStr }
    val pendingItems = shopItems.count { !it.isDone }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Unified 3-column Equal-Height Interactive Switcher & Live Summary Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CareMetricCard(
                        emoji = "💧",
                        label = "Hydration",
                        value = "$waterCount / $waterGoal",
                        accentColor = PhasesWaterBlue,
                        selected = activeTab == CareTab.WATER,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(CareTab.WATER)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        testTag = "care_tab_water"
                    )
                    CareMetricCard(
                        emoji = "💊",
                        label = "Meds",
                        value = if (activeMeds == 0) "0 set" else "$takenMeds / $activeMeds",
                        accentColor = PhasesRose,
                        selected = activeTab == CareTab.MEDS,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(CareTab.MEDS)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        testTag = "care_tab_meds"
                    )
                    CareMetricCard(
                        emoji = "🛍️",
                        label = "Wishlist",
                        value = "$pendingItems left",
                        accentColor = PhasesSage,
                        selected = activeTab == CareTab.CHECKLIST,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(CareTab.CHECKLIST)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        testTag = "care_tab_checklist"
                    )
                }
            }

            // Active Care Sub-screen
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "care_tab_transition",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { tab ->
                when (tab) {
                    CareTab.WATER -> WaterScreen(
                        userProfile = userProfile,
                        cycleStatus = cycleStatus,
                        waterLog = waterLog,
                        onIncrementWater = onIncrementWater,
                        onDecrementWater = onDecrementWater,
                        onSetWaterCount = onSetWaterCount,
                        onResetWater = onResetWater,
                        onUpdateWaterGoal = onUpdateWaterGoal
                    )
                    CareTab.MEDS -> MedicationsScreen(
                        userProfile = userProfile,
                        medications = medications,
                        onAddMedication = onAddMedication,
                        onUpdateMedication = onUpdateMedication,
                        onDeleteMedication = onDeleteMedication,
                        onToggleMedication = onToggleMedication,
                        onMarkTaken = onMarkMedicationTaken
                    )
                    CareTab.CHECKLIST -> ShoppingScreen(
                        cycleStatus = cycleStatus,
                        shopItems = shopItems,
                        taskCategories = taskCategories,
                        hideCompleted = shopHideCompleted,
                        viewMode = shopViewMode,
                        sortMode = shopSortMode,
                        onToggleItem = onToggleShopItem,
                        onAddItem = onAddShopItem,
                        onUpdateItem = onUpdateShopItem,
                        onDeleteItem = onDeleteShopItem,
                        onMoveItem = onMoveShopItem,
                        onClearCompleted = onClearCompletedShop,
                        onResetDefaults = onResetShopDefaults,
                        onAddCategory = onAddCategory,
                        onUpdateCategory = onUpdateCategory,
                        onDeleteCategory = onDeleteCategory,
                        onToggleHideCompleted = onToggleHideCompleted,
                        onSetViewMode = onSetViewMode,
                        onSetSortMode = onSetSortMode
                    )
                }
            }
        }
    }
}

@Composable
private fun CareMetricCard(
    emoji: String,
    label: String,
    value: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .cuteBouncyClick(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = if (selected) 0.14f else 0.06f), Color.Transparent)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
