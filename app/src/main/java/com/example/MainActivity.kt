package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloud.authStateFlow
import com.example.notifications.NotificationHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.components.ParticleBurstOverlay
import com.example.ui.components.ParticleController
import com.example.ui.components.PhasesBottomNav
import com.example.ui.components.PhasesTopBar
import com.example.ui.screens.CareHubScreen
import com.example.ui.screens.CycleScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PeriodsScreen
import com.example.ui.screens.PlayScreen
import com.example.ui.screens.SettingsSheet
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CareTab
import com.example.viewmodel.NavScreen
import com.example.viewmodel.PhasesViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    private val viewModel: PhasesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannels(this)
        handleIntentDestination(intent)

        setContent {
            MyApplicationTheme {
                PhasesApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentDestination(intent)
    }

    private fun handleIntentDestination(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationHelper.EXTRA_TARGET_SCREEN)
        if (!com.example.security.BackendSecurityFirewall.isAllowedNavigationTarget(target)) return
        when (target) {
            NotificationHelper.TARGET_CYCLE -> viewModel.setScreen(NavScreen.CYCLE)
            NotificationHelper.TARGET_PERIODS -> viewModel.setScreen(NavScreen.PERIODS)
            NotificationHelper.TARGET_SHOPPING -> {
                viewModel.setCareTab(CareTab.CHECKLIST)
                viewModel.setScreen(NavScreen.CARE)
            }
            NotificationHelper.TARGET_MEDICATIONS -> {
                viewModel.setCareTab(CareTab.MEDS)
                viewModel.setScreen(NavScreen.CARE)
            }
            NotificationHelper.TARGET_WATER -> {
                viewModel.setCareTab(CareTab.WATER)
                viewModel.setScreen(NavScreen.CARE)
            }
            NotificationHelper.TARGET_NOTES -> viewModel.setScreen(NavScreen.NOTES)
        }
    }
}

@Composable
fun PhasesApp(viewModel: PhasesViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val periodLogs by viewModel.periodLogs.collectAsStateWithLifecycle()
    val shopItems by viewModel.shopItems.collectAsStateWithLifecycle()
    val taskCategories by viewModel.taskCategories.collectAsStateWithLifecycle()
    val shopHideCompleted by viewModel.shopHideCompleted.collectAsStateWithLifecycle()
    val shopViewMode by viewModel.shopViewMode.collectAsStateWithLifecycle()
    val shopSortMode by viewModel.shopSortMode.collectAsStateWithLifecycle()
    val userNotes by viewModel.userNotes.collectAsStateWithLifecycle()
    val todayWater by viewModel.todayWater.collectAsStateWithLifecycle()
    val medicineReminders by viewModel.medicineReminders.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val cycleStatus by viewModel.cycleStatus.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val dailyCycleLogs by viewModel.dailyCycleLogs.collectAsStateWithLifecycle()
    val todayCycleLog by viewModel.todayCycleLog.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val activeCareTab by viewModel.activeCareTab.collectAsStateWithLifecycle()
    val periodsTabName by viewModel.periodsTabName.collectAsStateWithLifecycle()
    val showSettingsSheet by viewModel.showSettingsSheet.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val gameScores by viewModel.gameScores.collectAsStateWithLifecycle()
    val coupleRooms by viewModel.coupleRooms.collectAsStateWithLifecycle()
    val activeCoupleRoom by viewModel.activeCoupleRoom.collectAsStateWithLifecycle()
    val cloudAuthUid by viewModel.cloudAuthUid.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val isCloudBusy by viewModel.isCloudBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val particleController = remember { ParticleController() }

    // Keep Firebase Auth state synced at app root so Couple Lounge listeners stay live across tabs & games
    val authFlow = remember {
        runCatching { Firebase.auth.authStateFlow() }.getOrElse { flowOf(null) }
    }
    val initialAuthUser = remember {
        runCatching { Firebase.auth.currentUser }.getOrNull()
    }
    val currentAuthUser by authFlow.collectAsState(initial = initialAuthUser)

    LaunchedEffect(currentAuthUser?.uid, currentAuthUser?.displayName) {
        viewModel.onCloudUserChanged(currentAuthUser?.uid, currentAuthUser?.displayName)
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearFeedback()
        }
    }

    // Check if onboarding is needed
    val isOnboarded = userProfile?.isOnboarded == true

    CompositionLocalProvider(LocalParticleController provides particleController) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isOnboarded) {
        OnboardingScreen(
            onComplete = { name, lastPeriodMillis, cycleLength, periodLength ->
                viewModel.completeOnboarding(
                    name = name,
                    lastPeriodStartMillis = lastPeriodMillis,
                    cycleLength = cycleLength,
                    periodLength = periodLength
                )
            }
        )
    } else {
        Scaffold(
            topBar = {
                PhasesTopBar(
                    title = when (currentScreen) {
                        NavScreen.CYCLE -> "KriMoon"
                        NavScreen.PERIODS -> "Periods"
                        NavScreen.CARE -> "Daily Care"
                        NavScreen.NOTES -> "Notes"
                        NavScreen.PLAY -> "Play Arcade"
                    },
                    subtitle = "${cycleStatus.currentPhase.emoji} ${cycleStatus.currentPhase.shortName}",
                    onOpenSettings = { viewModel.setShowSettings(true) },
                    modifier = Modifier.testTag("app_top_bar")
                )
            },
            bottomBar = {
                PhasesBottomNav(
                    currentScreen = currentScreen,
                    onScreenSelected = { viewModel.setScreen(it) },
                    modifier = Modifier.testTag("bottom_nav_bar")
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { screen ->
                when (screen) {
                    NavScreen.CYCLE -> CycleScreen(
                        userProfile = userProfile,
                        cycleStatus = cycleStatus,
                        recentPeriods = periodLogs,
                        medicines = medicineReminders,
                        todayCycleLog = todayCycleLog,
                        todayWaterCount = todayWater?.glassesCount ?: 0,
                        waterGoal = userProfile?.waterGoal ?: 8,
                        onLogPeriodToday = { viewModel.logPeriodToday() },
                        onEndPeriodToday = { viewModel.endPeriodToday() },
                        onMarkMedicineTaken = { id -> viewModel.markMedicineTakenToday(id) },
                        onAddMedicine = { name, dosage, hour, min, emoji -> viewModel.addMedicineReminder(name, dosage, hour, min, emoji) },
                        onUpdateMedicine = { med -> viewModel.updateMedicineReminder(med) },
                        onDeleteMedicine = { id -> viewModel.deleteMedicineReminder(id) },
                        onToggleMedicine = { id, enabled -> viewModel.toggleMedicineReminder(id, enabled) },
                        onNavigateToCalendar = { 
                            viewModel.setPeriodsTab("CALENDAR")
                            viewModel.setScreen(NavScreen.PERIODS) 
                        },
                        onNavigateToTrends = {
                            viewModel.setPeriodsTab("TRENDS")
                            viewModel.setScreen(NavScreen.PERIODS)
                        },
                        onNavigateToPeriods = {
                            viewModel.setPeriodsTab("CALENDAR")
                            viewModel.setScreen(NavScreen.PERIODS)
                        },
                        onNavigateToCare = { careTab ->
                            viewModel.setCareTab(careTab)
                            viewModel.setScreen(NavScreen.CARE)
                        },
                        onCancelActivePeriod = { viewModel.cancelActivePeriod() },
                        onUpdatePeriod = { periodId, startMillis, endMillis, note ->
                            viewModel.updatePeriodDateRange(periodId, startMillis, endMillis, note)
                        },
                        onOpenSettings = { viewModel.setShowSettings(true) }
                    )
                    NavScreen.PERIODS -> PeriodsScreen(
                        userProfile = userProfile,
                        cycleStatus = cycleStatus,
                        historyItems = historyItems,
                        periodLogs = periodLogs,
                        dailyCycleLogs = dailyCycleLogs,
                        initialTab = when (periodsTabName) {
                            "TRENDS" -> com.example.ui.screens.PeriodsViewTab.TRENDS
                            "HISTORY" -> com.example.ui.screens.PeriodsViewTab.HISTORY
                            else -> com.example.ui.screens.PeriodsViewTab.CALENDAR
                        },
                        onAddPeriodDate = { dateMillis, note, durationDays, endDateMillis ->
                            viewModel.logPeriodDate(dateMillis, note, durationDays, endDateMillis)
                        },
                        onUpdatePeriod = { periodId, startMillis, endMillis, note ->
                            viewModel.updatePeriodDateRange(periodId, startMillis, endMillis, note)
                        },
                        onEndPeriodToday = { viewModel.endPeriodToday() },
                        onEndPeriodOnDate = { id, endDateMillis -> viewModel.endPeriodOnDate(id, endDateMillis) },
                        onDeletePeriod = { id -> viewModel.deletePeriod(id) },
                        onSaveDailyCycleLog = { log -> viewModel.saveDailyCycleLog(log) },
                        onDeleteDailyCycleLog = { id -> viewModel.deleteDailyCycleLog(id) },
                        onOpenSettings = { viewModel.setShowSettings(true) }
                    )
                    NavScreen.CARE -> CareHubScreen(
                        activeTab = activeCareTab,
                        onSelectTab = { tab -> viewModel.setCareTab(tab) },
                        userProfile = userProfile,
                        cycleStatus = cycleStatus,
                        waterLog = todayWater,
                        medications = medications,
                        shopItems = shopItems,
                        taskCategories = taskCategories,
                        shopHideCompleted = shopHideCompleted,
                        shopViewMode = shopViewMode,
                        shopSortMode = shopSortMode,
                        onIncrementWater = { viewModel.incrementWater() },
                        onDecrementWater = { viewModel.decrementWater() },
                        onSetWaterCount = { count -> viewModel.setWaterCount(count) },
                        onResetWater = { viewModel.resetWaterToday() },
                        onUpdateWaterGoal = { goal -> viewModel.updateWaterGoal(goal) },
                        onAddMedication = { name, dosage, hour, min, emoji, days, sound, msg, instr, color ->
                            viewModel.addMedication(name, dosage, hour, min, emoji, days, sound, msg, instr, color)
                        },
                        onUpdateMedication = { med -> viewModel.updateMedication(med) },
                        onDeleteMedication = { id -> viewModel.deleteMedication(id) },
                        onToggleMedication = { id, enabled -> viewModel.toggleMedication(id, enabled) },
                        onMarkMedicationTaken = { id -> viewModel.markMedicationTakenToday(id) },
                        onToggleShopItem = { item -> viewModel.toggleShopItem(item) },
                        onAddShopItem = { text, cat, notes, pri -> viewModel.addShopItem(text, cat, notes, pri) },
                        onUpdateShopItem = { item -> viewModel.updateShopItem(item) },
                        onDeleteShopItem = { id -> viewModel.deleteShopItem(id) },
                        onMoveShopItem = { item, dir -> viewModel.moveShopItem(item, shopItems, dir) },
                        onClearCompletedShop = { viewModel.clearCompletedShopItems() },
                        onResetShopDefaults = { viewModel.resetShopItemsToDefaults() },
                        onAddCategory = { name, emoji, color -> viewModel.addCategory(name, emoji, color) },
                        onUpdateCategory = { cat -> viewModel.updateCategory(cat) },
                        onDeleteCategory = { id -> viewModel.deleteCategory(id) },
                        onToggleHideCompleted = { hide -> viewModel.setShopHideCompleted(hide) },
                        onSetViewMode = { mode -> viewModel.setShopViewMode(mode) },
                        onSetSortMode = { mode -> viewModel.setShopSortMode(mode) }
                    )
                    NavScreen.NOTES -> NotesScreen(
                        notes = userNotes,
                        onAddNote = { content, tag, reminderMillis -> viewModel.addNote(content, tag, reminderMillis) },
                        onUpdateReminder = { id, reminderMillis -> viewModel.updateNoteReminder(id, reminderMillis) },
                        onDeleteNote = { id -> viewModel.deleteNote(id) },
                        onSendTestNotification = { note -> viewModel.sendTestNoteNotification(note) }
                    )
                    NavScreen.PLAY -> PlayScreen(
                        userProfile = userProfile,
                        gameScores = gameScores,
                        coupleRooms = coupleRooms,
                        activeCoupleRoom = activeCoupleRoom,
                        cloudAuthUid = cloudAuthUid,
                        cloudSyncStatus = cloudSyncStatus,
                        isCloudBusy = isCloudBusy,
                        onCloudUserChanged = { uid, displayName ->
                            viewModel.onCloudUserChanged(uid, displayName)
                        },
                        onDetachCloudListeners = { viewModel.detachCloudListeners() },
                        onRetryCloudSync = { viewModel.retryCloudSync() },
                        onRecordGameScore = { gameId, score, streak, lowerIsBetter ->
                            viewModel.recordGameScore(gameId, score, streak, lowerIsBetter)
                        },
                        onCreateOrJoinCoupleRoom = { name, code ->
                            viewModel.createOrJoinCoupleRoom(name, code)
                        },
                        onSelectCoupleRoom = { code -> viewModel.selectCoupleRoom(code) },
                        onDeleteCoupleRoom = { code -> viewModel.deleteCoupleRoom(code) },
                        onUpdateCoupleSpiceLevel = { spice -> viewModel.updateCoupleSpiceLevel(spice) },
                        onToggleCouplePassAndPlay = { enabled -> viewModel.toggleCouplePassAndPlay(enabled) },
                        onSaveCoupleDoodle = { strokesJson, prompt ->
                            viewModel.saveCoupleDoodle(strokesJson, prompt)
                        },
                        onSaveCoupleQuizState = { stateJson, bumpMatch ->
                            viewModel.saveCoupleQuizState(stateJson, bumpMatch)
                        },
                        onSaveCoupleWyrState = { stateJson, bumpMatch ->
                            viewModel.saveCoupleWyrState(stateJson, bumpMatch)
                        },
                        onSaveCoupleTicTacToeState = { stateJson, bumpWin ->
                            viewModel.saveCoupleTicTacToeState(stateJson, bumpWin)
                        },
                        onSendLiveCoupleBlock = { packet, block ->
                            viewModel.sendLiveEncryptedTurn(packet, block)
                        },
                        onShowFeedback = { msg -> viewModel.showFeedback(msg) }
                    )
                }
            }

            if (showSettingsSheet) {
                SettingsSheet(
                    userProfile = userProfile,
                    onSaveSettings = { name, cycleLen, periodLen, waterGoal, waterReminders, cycleReminders, fertileReminders, checkinReminders, shopReminders, reminderHour ->
                        viewModel.updateSettings(
                            name = name,
                            cycleLength = cycleLen,
                            periodLength = periodLen,
                            waterGoal = waterGoal,
                            waterReminders = waterReminders,
                            cycleReminders = cycleReminders,
                            fertileReminders = fertileReminders,
                            checkinReminders = checkinReminders,
                            shopReminders = shopReminders,
                            reminderHour = reminderHour
                        )
                    },
                    onSendTestNotification = { type ->
                        viewModel.sendTestNotification(type)
                    },
                    onClearAllData = { viewModel.clearAllData() },
                    onDismiss = { viewModel.setShowSettings(false) }
                )
            }
        }
            }

            ParticleBurstOverlay(controller = particleController)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Text(
            text = "Welcome to KriMoon ✿, $name!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
