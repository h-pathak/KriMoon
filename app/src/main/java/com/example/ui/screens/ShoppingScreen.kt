package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShopItem
import com.example.data.TaskCategory
import com.example.model.CyclePhase
import com.example.model.CycleStatus
import com.example.notifications.SoundHelper
import com.example.ui.components.CuteAddButton
import com.example.ui.components.CuteEmptyStateCard
import com.example.ui.components.FloatingPetalsBackground
import com.example.ui.components.LocalParticleController
import com.example.ui.components.cute3DCard
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPeach
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesSky

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen(
    cycleStatus: CycleStatus,
    shopItems: List<ShopItem>,
    taskCategories: List<TaskCategory> = emptyList(),
    hideCompleted: Boolean = false,
    viewMode: String = "COZY",
    sortMode: String = "CUSTOM",
    onToggleItem: (ShopItem) -> Unit,
    onAddItem: (String, String, String, String) -> Unit,
    onUpdateItem: (ShopItem) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onMoveItem: (ShopItem, Int) -> Unit,
    onClearCompleted: () -> Unit,
    onResetDefaults: () -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    onUpdateCategory: (TaskCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onToggleHideCompleted: (Boolean) -> Unit,
    onSetViewMode: (String) -> Unit,
    onSetSortMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val particleController = LocalParticleController.current
    val haptic = LocalHapticFeedback.current

    // Local UI states
    var newItemText by remember { mutableStateOf("") }
    var newItemNotes by remember { mutableStateOf("") }
    var newItemPriority by remember { mutableStateOf("NORMAL") }
    var selectedNewCategory by remember { mutableStateOf("general") }
    var selectedFilterCategory by remember { mutableStateOf("all") } // "all", or categoryId
    var isReorderMode by remember { mutableStateOf(false) }

    // Dialog states
    var editingItem by remember { mutableStateOf<ShopItem?>(null) }
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var showCustomizationMenu by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Phase identification
    val currentPhaseKey = remember(cycleStatus.currentPhase) {
        when (cycleStatus.currentPhase) {
            CyclePhase.MENSTRUAL -> "menstrual"
            CyclePhase.FOLLICULAR -> "follicular"
            CyclePhase.FERTILE, CyclePhase.OVULATION -> "ovulation"
            CyclePhase.LUTEAL, CyclePhase.LATE_LUTEAL -> "luteal"
        }
    }

    // Filter & Sort Items
    val displayedItems by remember(shopItems, selectedFilterCategory, hideCompleted, sortMode) {
        derivedStateOf {
            var list = when (selectedFilterCategory) {
                "all" -> shopItems
                "current" -> shopItems.filter { it.phaseCategory == currentPhaseKey || it.phaseCategory == "general" }
                else -> shopItems.filter { it.phaseCategory == selectedFilterCategory }
            }

            if (hideCompleted) {
                list = list.filter { !it.isDone }
            }

            when (sortMode) {
                "PRIORITY" -> list.sortedWith(
                    compareBy<ShopItem> {
                        when (it.priority) {
                            "HIGH" -> 0
                            "NORMAL" -> 1
                            else -> 2
                        }
                    }.thenBy { it.orderIndex }
                )
                "STATUS" -> list.sortedWith(compareBy<ShopItem> { it.isDone }.thenBy { it.orderIndex })
                "ALPHA" -> list.sortedBy { it.text.lowercase() }
                else -> list.sortedBy { it.orderIndex } // CUSTOM
            }
        }
    }

    val doneCount = shopItems.count { it.isDone }
    val totalCount = shopItems.size

    // Fallback categories if db not yet seeded
    val effectiveCategories = if (taskCategories.isNotEmpty()) taskCategories else listOf(
        TaskCategory("menstrual", "Crimson Care", "🩸", "#D4629A", 0, false),
        TaskCategory("follicular", "Rising Energy", "🌱", "#67A572", 1, false),
        TaskCategory("ovulation", "Glow & Social", "✨", "#DC9B29", 2, false),
        TaskCategory("luteal", "PMS & Rest", "🌙", "#7C3FA6", 3, false),
        TaskCategory("general", "Everyday", "🛍️", "#9370DB", 4, false)
    )

    Box(modifier = modifier.fillMaxSize()) {
        FloatingPetalsBackground()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title + Customization button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wishlist & Care Essentials",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Curated cycle essentials, cozy comforts & custom checklists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Preferences & View Customizer Button
                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCustomizationMenu = true
                            },
                            modifier = Modifier.testTag("shopping_customization_button")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Customize Interface",
                                tint = PhasesRose
                            )
                        }

                        DropdownMenu(
                            expanded = showCustomizationMenu,
                            onDismissRequest = { showCustomizationMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (viewMode == "COZY") "Switch to Compact View 📋" else "Switch to Cozy Cards 🌸") },
                                onClick = {
                                    onSetViewMode(if (viewMode == "COZY") "COMPACT" else "COZY")
                                    showCustomizationMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (viewMode == "COZY") Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                                        contentDescription = null
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(if (hideCompleted) "Show Completed Items ✅" else "Hide Completed Items 🙈") },
                                onClick = {
                                    onToggleHideCompleted(!hideCompleted)
                                    showCustomizationMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Sort: Custom Order 🔀") },
                                onClick = {
                                    onSetSortMode("CUSTOM")
                                    showCustomizationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: By Priority 🔥") },
                                onClick = {
                                    onSetSortMode("PRIORITY")
                                    showCustomizationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Pending First ⏳") },
                                onClick = {
                                    onSetSortMode("STATUS")
                                    showCustomizationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Alphabetical 🔤") },
                                onClick = {
                                    onSetSortMode("ALPHA")
                                    showCustomizationMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Phase Banner with Quick Stats & Category Manager Shortcut
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cycleStatus.currentPhase.containerColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            cycleStatus.currentPhase.accentColor.copy(alpha = 0.35f),
                            RoundedCornerShape(22.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = cycleStatus.currentPhase.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Current: ${cycleStatus.currentPhase.title}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cycleStatus.currentPhase.accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$doneCount of $totalCount items packed/ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Reorder Mode Pill Toggle
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isReorderMode = !isReorderMode
                            },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReorderMode) PhasesRose else MaterialTheme.colorScheme.surface,
                                contentColor = if (isReorderMode) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isReorderMode) PhasesRose else MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).testTag("toggle_reorder_mode")
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isReorderMode) "Done Reordering" else "Reorder ↕", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Editable Categories Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Categories:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCategoryManagerDialog = true
                            },
                            modifier = Modifier.testTag("manage_categories_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = PhasesRose)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Categories 🏷️", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PhasesRose)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // All Filter Chip
                        FilterChip(
                            selected = selectedFilterCategory == "all",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilterCategory = "all"
                            },
                            label = { Text("All (${shopItems.size})", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        // Current Phase Chip
                        FilterChip(
                            selected = selectedFilterCategory == "current",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilterCategory = "current"
                            },
                            label = { Text("Current (${cycleStatus.currentPhase.emoji})", style = MaterialTheme.typography.labelSmall) }
                        )

                        // Dynamic task categories from DB
                        effectiveCategories.forEach { cat ->
                            val isSelected = selectedFilterCategory == cat.id
                            val catItemCount = shopItems.count { it.phaseCategory == cat.id }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilterCategory = cat.id
                                },
                                label = {
                                    Text("${cat.emoji} ${cat.displayName} ($catItemCount)", style = MaterialTheme.typography.labelSmall)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhasesPlum.copy(alpha = 0.2f),
                                    selectedLabelColor = PhasesPlum
                                )
                            )
                        }
                    }
                }
            }

            // Add New Item Card (Supports Text, Category, Priority, and Notes) with 3D Depth
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cute3DCard(maxTilt = 3f, cornerRadius = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add Care Item or Routine",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesRose.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "✨ Essentials",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PhasesRose,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                placeholder = { Text("e.g. Organic pads, Chamomile tea, Fuzzy socks...") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("shop_item_input")
                            )

                            CuteAddButton(
                                text = "Add",
                                icon = "🛍️",
                                sparkleEmoji = "✨",
                                enabled = newItemText.isNotBlank(),
                                onClick = {
                                    if (newItemText.isNotBlank()) {
                                        onAddItem(newItemText.trim(), selectedNewCategory, newItemNotes.trim(), newItemPriority)
                                        particleController.showToast(
                                            coroutineScope = coroutineScope,
                                            message = "Added '$newItemText' to care list! 🌸",
                                            icon = "🛍️"
                                        )
                                        newItemText = ""
                                        newItemNotes = ""
                                    }
                                },
                                testTag = "add_shop_item_button"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category selector chips for new item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            effectiveCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedNewCategory == cat.id,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNewCategory = cat.id
                                    },
                                    label = { Text("${cat.emoji} ${cat.displayName}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Optional Notes & Priority row with Haptics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newItemNotes,
                                onValueChange = { newItemNotes = it },
                                placeholder = { Text("Notes / brand preferences (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            // Priority selector with Haptic feedback
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                listOf("NORMAL" to "🌸", "HIGH" to "🔥").forEach { (priKey, priEmoji) ->
                                    val isPri = newItemPriority == priKey
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPri) PhasesRose else Color.Transparent,
                                        modifier = Modifier.clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            newItemPriority = priKey
                                        }
                                    ) {
                                        Text(
                                            text = priEmoji,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions: Clear Completed, Restore defaults
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allCompletedCount = shopItems.count { it.isDone }
                    if (allCompletedCount > 0) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClearCompleted()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("clear_completed_button")
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Completed ($allCompletedCount)", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showResetConfirmDialog = true
                        },
                        modifier = Modifier.testTag("reset_defaults_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Essentials 🌸", fontSize = 12.sp)
                    }
                }
            }

            // Checklist Items / Friendly Empty State
            if (displayedItems.isEmpty()) {
                item {
                    CuteEmptyStateCard(
                        emoji = if (hideCompleted) "🎉" else "🛍️",
                        title = if (hideCompleted) "All Tasks Checked Off!" else "Your Care List is Empty",
                        description = if (hideCompleted) {
                            "You're all caught up on your cycle essentials and self-care routines! 🌸"
                        } else {
                            "Add comfort pads, raspberry leaf tea, magnesium, or cozy self-care essentials above."
                        },
                        buttonText = "+ Add Care Item ✨",
                        onButtonClick = {
                            particleController.showToast(coroutineScope, "Enter your comfort item in the box above 🌸", "🛍️")
                        },
                        testTag = "empty_shop_card"
                    )
                }
            } else {
                itemsIndexed(displayedItems, key = { _, item -> item.id }) { index, item ->
                    val categoryObj = effectiveCategories.firstOrNull { it.id == item.phaseCategory }

                    if (viewMode == "COMPACT") {
                        CompactShopItemRow(
                            item = item,
                            category = categoryObj,
                            isReorderMode = isReorderMode,
                            canMoveUp = index > 0,
                            canMoveDown = index < displayedItems.size - 1,
                            onToggle = { onToggleItem(item) },
                            onEdit = { editingItem = item },
                            onDelete = { onDeleteItem(item.id) },
                            onMoveUp = { onMoveItem(item, -1) },
                            onMoveDown = { onMoveItem(item, 1) }
                        )
                    } else {
                        CozyShopItemCard(
                            item = item,
                            category = categoryObj,
                            isReorderMode = isReorderMode,
                            canMoveUp = index > 0,
                            canMoveDown = index < displayedItems.size - 1,
                            onToggle = { onToggleItem(item) },
                            onEdit = { editingItem = item },
                            onDelete = { onDeleteItem(item.id) },
                            onMoveUp = { onMoveItem(item, -1) },
                            onMoveDown = { onMoveItem(item, 1) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Edit Item Dialog
    editingItem?.let { itemToEdit ->
        EditShopItemDialog(
            item = itemToEdit,
            categories = effectiveCategories,
            onDismiss = { editingItem = null },
            onSave = { updatedText, updatedCategory, updatedNotes, updatedPriority ->
                onUpdateItem(
                    itemToEdit.copy(
                        text = updatedText,
                        phaseCategory = updatedCategory,
                        notes = updatedNotes,
                        priority = updatedPriority
                    )
                )
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                editingItem = null
            }
        )
    }

    // Category Manager Dialog (Rename, add, customize categories)
    if (showCategoryManagerDialog) {
        CategoryManagerDialog(
            categories = effectiveCategories,
            onDismiss = { showCategoryManagerDialog = false },
            onAddCategory = onAddCategory,
            onUpdateCategory = onUpdateCategory,
            onDeleteCategory = onDeleteCategory
        )
    }

    // Restore Essentials Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Restore Curated Essentials? 🌸", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will restore KriMoon's recommended cycle essentials (comfort pads, teas, heating pads, magnesium) and category presets.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaults()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CozyShopItemCard(
    item: ShopItem,
    category: TaskCategory?,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val particleController = LocalParticleController.current
    val haptic = LocalHapticFeedback.current
    var rowCenter by remember { mutableStateOf(Offset.Zero) }

    fun handleToggle() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (!item.isDone) {
            particleController.spawn(
                centerX = if (rowCenter != Offset.Zero) rowCenter.x else 300f,
                centerY = if (rowCenter != Offset.Zero) rowCenter.y else 400f,
                emojis = listOf("✨", "🌸", "💖", "🎀", "⭐", "🌷"),
                count = 10
            )
        }
        onToggle()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (item.isDone) MaterialTheme.colorScheme.outlineVariant else PhasesRose.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                rowCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            }
            .clickable { handleToggle() }
            .testTag("shop_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder controls if in reorder mode
                if (isReorderMode) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveUp()
                            },
                            enabled = canMoveUp,
                            modifier = Modifier.size(28.dp).testTag("move_up_${item.id}")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveDown()
                            },
                            enabled = canMoveDown,
                            modifier = Modifier.size(28.dp).testTag("move_down_${item.id}")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = { handleToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PhasesPlum,
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.testTag("shop_checkbox_${item.id}")
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (item.isDone) FontWeight.Normal else FontWeight.Medium,
                                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (item.isDone) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (item.priority == "HIGH") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = PhasesRose.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "🔥 High",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PhasesRose,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (item.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "💡 ${item.notes}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val catName = category?.displayName ?: item.phaseCategory.replaceFirstChar { it.uppercase() }
                    val catEmoji = category?.emoji ?: "🛍️"

                    Text(
                        text = "$catEmoji $catName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Edit action
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEdit()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("edit_shop_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete action
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_shop_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompactShopItemRow(
    item: ShopItem,
    category: TaskCategory?,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isReorderMode) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(16.dp))
            }
        }

        Checkbox(
            checked = item.isDone,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
            ),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = category?.emoji ?: "🛍️",
            fontSize = 14.sp
        )

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditShopItemDialog(
    item: ShopItem,
    categories: List<TaskCategory>,
    onDismiss: () -> Unit,
    onSave: (text: String, category: String, notes: String, priority: String) -> Unit
) {
    var text by remember { mutableStateOf(item.text) }
    var category by remember { mutableStateOf(item.phaseCategory) }
    var notes by remember { mutableStateOf(item.notes) }
    var priority by remember { mutableStateOf(item.priority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Task / Item ✏️🌸",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_item_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Preferences") },
                    placeholder = { Text("e.g. 100% organic cotton, 85% cocoa...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Category:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat.id,
                            onClick = { category = cat.id },
                            label = { Text("${cat.emoji} ${cat.displayName}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    text = "Priority:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW" to "🍃 Low", "NORMAL" to "🌸 Normal", "HIGH" to "🔥 High").forEach { (priKey, priLabel) ->
                        FilterChip(
                            selected = priority == priKey,
                            onClick = { priority = priKey },
                            label = { Text(priLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text.trim(), category, notes.trim(), priority)
                    }
                },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum),
                modifier = Modifier.testTag("save_edit_item_button")
            ) {
                Text("Save Changes ✨", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryManagerDialog(
    categories: List<TaskCategory>,
    onDismiss: () -> Unit,
    onAddCategory: (String, String, String) -> Unit,
    onUpdateCategory: (TaskCategory) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isAddingNew by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    var newCatEmoji by remember { mutableStateOf("🌸") }
    var editingCategory by remember { mutableStateOf<TaskCategory?>(null) }

    val emojis = listOf("🌸", "🩸", "🌱", "🌿", "🌙", "🛍️", "🧴", "☕", "🥑", "🧘‍♀️", "✨", "💊")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Manage & Rename Categories 🏷️", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Customize category names and icons to suit your lifestyle:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Existing categories list
                itemsIndexed(categories, key = { _, cat -> cat.id }) { _, cat ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = cat.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = cat.displayName, fontWeight = FontWeight.Bold)
                                    Text(text = "Key: ${cat.id}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { editingCategory = cat },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                }

                                if (cat.isCustom) {
                                    IconButton(
                                        onClick = { onDeleteCategory(cat.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Category Section
                item {
                    if (isAddingNew) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, PhasesRose),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("New Custom Category", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = newCatName,
                                    onValueChange = { newCatName = it },
                                    label = { Text("Category Name") },
                                    placeholder = { Text("e.g. Cozy Snacks, Self-Care") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    emojis.forEach { em ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (newCatEmoji == em) PhasesRose.copy(alpha = 0.2f) else Color.Transparent,
                                            border = BorderStroke(1.dp, if (newCatEmoji == em) PhasesRose else MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.clickable { newCatEmoji = em }
                                        ) {
                                            Text(text = em, fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (newCatName.isNotBlank()) {
                                                onAddCategory(newCatName.trim(), newCatEmoji, "#D4629A")
                                                newCatName = ""
                                                isAddingNew = false
                                            }
                                        },
                                        enabled = newCatName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = PhasesRose)
                                    ) {
                                        Text("Save Category")
                                    }
                                    TextButton(onClick = { isAddingNew = false }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isAddingNew = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Custom Category")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    // Edit single category dialog
    editingCategory?.let { catToEdit ->
        var renameText by remember { mutableStateOf(catToEdit.displayName) }
        var renameEmoji by remember { mutableStateOf(catToEdit.emoji) }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        emojis.forEach { em ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (renameEmoji == em) PhasesRose.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (renameEmoji == em) PhasesRose else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable { renameEmoji = em }
                            ) {
                                Text(text = em, fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onUpdateCategory(catToEdit.copy(displayName = renameText.trim(), emoji = renameEmoji))
                            editingCategory = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhasesPlum)
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
