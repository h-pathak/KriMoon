package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloud.authStateFlow
import com.example.data.UserProfile
import com.example.model.CycleStatus
import com.example.notifications.NotificationHelper
import com.example.ui.components.LocalParticleController
import com.example.ui.components.ParticleBurstOverlay
import com.example.ui.components.ParticleController
import com.example.ui.components.PhasesBottomNav
import com.example.ui.components.PhasesTopBar
import com.example.ui.games.CoupleLoungeActions
import com.example.ui.games.CoupleLoungeUiState
import com.example.ui.screens.CareHubScreen
import com.example.ui.screens.CycleScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PeriodsScreen
import com.example.ui.screens.PeriodsViewTab
import com.example.ui.screens.PlayGameSaveActions
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
        // Privacy & anti-breach hardening: block screenshots, screen recording, Recents thumbnails, and tapjacking overlays
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        window.decorView.filterTouchesWhenObscured = true

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
    // Only collect shell-level flows here; each tab collects its own flows to isolate recompositions and shrink JIT lambdas
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val cycleStatus by viewModel.cycleStatus.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val showSettingsSheet by viewModel.showSettingsSheet.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val particleController = remember { ParticleController() }

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
                                NavScreen.PERIODS -> "Cycle Calendar"
                                NavScreen.CARE -> "Self-Care"
                                NavScreen.NOTES -> "Journal"
                                NavScreen.PLAY -> "Play & Lounge"
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
                            NavScreen.CYCLE -> CycleTabHost(viewModel, userProfile, cycleStatus)
                            NavScreen.PERIODS -> PeriodsTabHost(viewModel, userProfile, cycleStatus)
                            NavScreen.CARE -> CareTabHost(viewModel, userProfile, cycleStatus)
                            NavScreen.NOTES -> NotesTabHost(viewModel)
                            NavScreen.PLAY -> PlayTabHost(viewModel, userProfile)
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
                            onSendTestNotification = { type -> viewModel.sendTestNotification(type) },
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
private fun CycleTabHost(
    viewModel: PhasesViewModel,
    userProfile: UserProfile?,
    cycleStatus: CycleStatus
) {
    val periodLogs by viewModel.periodLogs.collectAsStateWithLifecycle()
    val medicineReminders by viewModel.medicineReminders.collectAsStateWithLifecycle()
    val todayCycleLog by viewModel.todayCycleLog.collectAsStateWithLifecycle()
    val todayWater by viewModel.todayWater.collectAsStateWithLifecycle()

    CycleScreen(
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
        onSaveDailyCycleLog = { log -> viewModel.saveDailyCycleLog(log) },
        onOpenSettings = { viewModel.setShowSettings(true) }
    )
}

@Composable
private fun PeriodsTabHost(
    viewModel: PhasesViewModel,
    userProfile: UserProfile?,
    cycleStatus: CycleStatus
) {
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val periodLogs by viewModel.periodLogs.collectAsStateWithLifecycle()
    val dailyCycleLogs by viewModel.dailyCycleLogs.collectAsStateWithLifecycle()
    val periodsTabName by viewModel.periodsTabName.collectAsStateWithLifecycle()

    PeriodsScreen(
        userProfile = userProfile,
        cycleStatus = cycleStatus,
        historyItems = historyItems,
        periodLogs = periodLogs,
        dailyCycleLogs = dailyCycleLogs,
        initialTab = when (periodsTabName) {
            "TRENDS" -> PeriodsViewTab.TRENDS
            "HISTORY" -> PeriodsViewTab.HISTORY
            else -> PeriodsViewTab.CALENDAR
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
}

@Composable
private fun CareTabHost(
    viewModel: PhasesViewModel,
    userProfile: UserProfile?,
    cycleStatus: CycleStatus
) {
    val activeCareTab by viewModel.activeCareTab.collectAsStateWithLifecycle()
    val todayWater by viewModel.todayWater.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val shopItems by viewModel.shopItems.collectAsStateWithLifecycle()
    val taskCategories by viewModel.taskCategories.collectAsStateWithLifecycle()
    val shopHideCompleted by viewModel.shopHideCompleted.collectAsStateWithLifecycle()
    val shopViewMode by viewModel.shopViewMode.collectAsStateWithLifecycle()
    val shopSortMode by viewModel.shopSortMode.collectAsStateWithLifecycle()

    CareHubScreen(
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
}

@Composable
private fun NotesTabHost(viewModel: PhasesViewModel) {
    val userNotes by viewModel.userNotes.collectAsStateWithLifecycle()

    NotesScreen(
        notes = userNotes,
        onAddNote = { content, tag, reminderMillis -> viewModel.addNote(content, tag, reminderMillis) },
        onUpdateReminder = { id, reminderMillis -> viewModel.updateNoteReminder(id, reminderMillis) },
        onDeleteNote = { id -> viewModel.deleteNote(id) },
        onSendTestNotification = { note -> viewModel.sendTestNoteNotification(note) }
    )
}

@Composable
private fun PlayTabHost(
    viewModel: PhasesViewModel,
    userProfile: UserProfile?
) {
    val gameScores by viewModel.gameScores.collectAsStateWithLifecycle()
    val coupleRooms by viewModel.coupleRooms.collectAsStateWithLifecycle()
    val activeCoupleRoom by viewModel.activeCoupleRoom.collectAsStateWithLifecycle()
    val cloudAuthUid by viewModel.cloudAuthUid.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val isCloudBusy by viewModel.isCloudBusy.collectAsStateWithLifecycle()

    val loungeState = remember(coupleRooms, activeCoupleRoom, cloudAuthUid, cloudSyncStatus, isCloudBusy) {
        CoupleLoungeUiState(
            coupleRooms = coupleRooms,
            activeRoom = activeCoupleRoom,
            cloudAuthUid = cloudAuthUid,
            cloudSyncStatus = cloudSyncStatus,
            isCloudBusy = isCloudBusy
        )
    }
    val loungeActions = remember(viewModel) {
        CoupleLoungeActions(
            onCloudUserChanged = { uid, name -> viewModel.onCloudUserChanged(uid, name) },
            onDetachCloudListeners = { viewModel.detachCloudListeners() },
            onRetryCloudSync = { viewModel.retryCloudSync() },
            onCreateUniqueRoom = { name -> viewModel.createUniqueCoupleRoom(name) },
            onRequestJoinRoom = { name, code -> viewModel.requestJoinCoupleRoom(name, code) },
            onApprovePendingPartner = { code -> viewModel.approvePendingPartner(code) },
            onDeclinePendingPartner = { code -> viewModel.declinePendingPartner(code) },
            onSelectRoom = { code -> viewModel.selectCoupleRoom(code) },
            onDeleteRoom = { code -> viewModel.deleteCoupleRoom(code) },
            onUpdateSpiceLevel = { spice -> viewModel.updateCoupleSpiceLevel(spice) },
            onTogglePassAndPlay = { enabled -> viewModel.toggleCouplePassAndPlay(enabled) },
            onShowFeedback = { msg -> viewModel.showFeedback(msg) }
        )
    }
    val saveActions = remember(viewModel) {
        PlayGameSaveActions(
            onRecordGameScore = { id, score, streak, lower -> viewModel.recordGameScore(id, score, streak, lower) },
            onSaveCoupleDoodle = { json, prompt -> viewModel.saveCoupleDoodle(json, prompt) },
            onSaveCoupleQuizState = { json, bump -> viewModel.saveCoupleQuizState(json, bump) },
            onSaveCoupleWyrState = { json, bump -> viewModel.saveCoupleWyrState(json, bump) },
            onSaveCoupleTicTacToeState = { json, bump -> viewModel.saveCoupleTicTacToeState(json, bump) },
            onSaveCoupleDareState = { json, bump -> viewModel.saveCoupleDareState(json, bump) },
            onSaveCoupleWavelengthState = { json, bump -> viewModel.saveCoupleWavelengthState(json, bump) },
            onSaveCoupleWhisperState = { json, bump -> viewModel.saveCoupleWhisperState(json, bump) },
            onSendLiveCoupleBlock = { pkt, blk -> viewModel.sendLiveEncryptedTurn(pkt, blk) }
        )
    }

    PlayScreen(
        userProfile = userProfile,
        gameScores = gameScores,
        loungeState = loungeState,
        loungeActions = loungeActions,
        gameSaveActions = saveActions
    )
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
