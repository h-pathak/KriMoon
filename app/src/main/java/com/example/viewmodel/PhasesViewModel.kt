package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CoupleRoomRecord
import com.example.data.DailyCycleLog
import com.example.data.GameScoreRecord
import com.example.data.Medication
import com.example.data.MedicineReminder
import com.example.data.PeriodLog
import com.example.data.PhasesRepository
import com.example.data.ShopItem
import com.example.data.TaskCategory
import com.example.data.UserNote
import com.example.data.UserProfile
import com.example.cloud.BlockPublishOutcome
import com.example.cloud.CoupleCloudRepository
import com.example.cloud.CoupleCloudRoom
import com.example.model.CoupleActivitiesDeck
import com.example.model.CoupleActivityIntent
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleEcdhEngine
import com.example.model.CoupleEcdhKeyPair
import com.example.model.CoupleTurnCodec
import com.example.model.CycleCalculator
import com.example.model.CycleHistoryItem
import com.example.model.CycleStatus
import com.example.model.DecodedTurnPacket
import com.example.model.PeriodAnomalyAnalysis
import com.example.model.PeriodAnomalyDetector
import com.example.model.RoundSyncState
import com.example.model.SpiceLevel
import com.example.model.TicTacToeSyncState
import com.example.notifications.NotificationHelper
import com.example.security.LocalVaultCrypto
import com.example.security.LocalVaultUnavailableException
import com.example.security.VaultState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale

class PhasesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhasesRepository(AppDatabase.getInstance(application))

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val periodLogs: StateFlow<List<PeriodLog>> = repository.periodLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shopItems: StateFlow<List<ShopItem>> = repository.shopItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskCategories: StateFlow<List<TaskCategory>> = repository.taskCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userNotes: StateFlow<List<UserNote>> = repository.userNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val medications: StateFlow<List<Medication>> = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val medicineReminders: StateFlow<List<MedicineReminder>> = medications

    val dailyCycleLogs: StateFlow<List<DailyCycleLog>> = repository.dailyCycleLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val todayCycleLog: StateFlow<DailyCycleLog?> = repository.getDailyCycleLog(todayString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayWater = repository.getWaterLogForDate(todayString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val gameScores: StateFlow<Map<String, GameScoreRecord>> = repository.gameScores
        .map { list -> list.associateBy { it.gameId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val coupleRooms: StateFlow<List<CoupleRoomRecord>> = repository.coupleRooms
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCoupleCode = MutableStateFlow<String?>(null)
    val activeCoupleCode: StateFlow<String?> = _activeCoupleCode.asStateFlow()

    val activeCoupleRoom: StateFlow<CoupleRoomRecord?> = combine(
        coupleRooms,
        _activeCoupleCode,
        userProfile
    ) { rooms, selectedCode, profile ->
        val targetCode = selectedCode?.takeIf { it.isNotBlank() }
            ?: profile?.activeCoupleCode?.takeIf { it.isNotBlank() }
        when {
            targetCode != null -> rooms.firstOrNull { it.coupleCode == targetCode } ?: rooms.firstOrNull { it.isBonded } ?: rooms.firstOrNull()
            else -> rooms.firstOrNull { it.isBonded } ?: rooms.firstOrNull()
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Fail-closed local vault state, per-record decryption issues & offline queue visibility
    val vaultState: StateFlow<VaultState> = LocalVaultCrypto.vaultState
    val vaultDecryptionIssues: StateFlow<List<com.example.data.VaultDecryptionIssue>> = repository.decryptionIssues

    val activePendingTurnCount: StateFlow<Int> = repository.activePendingTurnCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Live 2-Phone Encrypted Cloud Sync State
    private val _cloudAuthUid = MutableStateFlow<String?>(null)
    val cloudAuthUid: StateFlow<String?> = _cloudAuthUid.asStateFlow()

    private val _cloudUserName = MutableStateFlow<String?>(null)
    val cloudUserName: StateFlow<String?> = _cloudUserName.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("SIGNED_OUT") // SIGNED_OUT, CONNECTING, WAITING, LIVE, OFFLINE
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _isCloudBusy = MutableStateFlow(false)
    val isCloudBusy: StateFlow<Boolean> = _isCloudBusy.asStateFlow()

    private var cachedCloudRepo: CoupleCloudRepository? = null
    private var userRoomsJob: Job? = null
    private var activeRoomSyncJob: Job? = null
    private var activeBlocksSyncJob: Job? = null
    private var roomWatcherCoordinatorJob: Job? = null
    private val coupleRoomMutex = Mutex()
    private val pendingDrainMutex = Mutex()
    private val sentBlockHashes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val processedBlockHashes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val rebroadcastedCodes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private fun getCloudRepoOrNull(): CoupleCloudRepository? {
        cachedCloudRepo?.let { return it }
        return runCatching { CoupleCloudRepository(getApplication()) }
            .getOrNull()
            ?.also { cachedCloudRepo = it }
    }

    // Derived Cycle Status
    val cycleStatus: StateFlow<CycleStatus> = combine(
        periodLogs,
        userProfile
    ) { periods, profile ->
        val cycleLen = profile?.cycleLength ?: 28
        val periodLen = profile?.periodLength ?: 5
        CycleCalculator.compute(
            periods = periods,
            defaultCycleLength = cycleLen,
            defaultPeriodLength = periodLen
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CycleCalculator.compute(emptyList())
    )

    // Derived Period History Items with Anomaly Detection
    val historyItems: StateFlow<List<CycleHistoryItem>> = periodLogs
        .combine(userProfile) { periods, profile ->
            val cycleLen = profile?.cycleLength ?: 28
            val periodLen = profile?.periodLength ?: 5
            CycleCalculator.buildHistoryItems(periods, cycleLen, periodLen)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Statistical Anomaly Detection & Prediction Analysis Flow
    val periodAnomalyAnalysis: StateFlow<PeriodAnomalyAnalysis> = periodLogs
        .combine(userProfile) { periods, profile ->
            val cycleLen = profile?.cycleLength ?: 28
            val periodLen = profile?.periodLength ?: 5
            PeriodAnomalyDetector.analyze(
                periods = periods,
                defaultCycleLength = cycleLen,
                defaultPeriodLength = periodLen
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PeriodAnomalyDetector.analyze(emptyList())
        )

    private val _currentScreen = MutableStateFlow(NavScreen.CYCLE)
    val currentScreen = _currentScreen.asStateFlow()

    private val _activeCareTab = MutableStateFlow(CareTab.WATER)
    val activeCareTab = _activeCareTab.asStateFlow()

    private val _showSettingsSheet = MutableStateFlow(false)
    val showSettingsSheet = _showSettingsSheet.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage = _feedbackMessage.asStateFlow()

    // Shopping interface custom preferences
    private val _shopHideCompleted = MutableStateFlow(false)
    val shopHideCompleted = _shopHideCompleted.asStateFlow()

    private val _shopViewMode = MutableStateFlow("COZY") // "COZY" or "COMPACT"
    val shopViewMode = _shopViewMode.asStateFlow()

    private val _shopSortMode = MutableStateFlow("CUSTOM") // "CUSTOM", "PRIORITY", "STATUS", "ALPHA"
    val shopSortMode = _shopSortMode.asStateFlow()

    private val _periodsTabName = MutableStateFlow("CALENDAR")
    val periodsTabName = _periodsTabName.asStateFlow()

    init {
        viewModelScope.launch {
            if (LocalVaultCrypto.canSafelyWrite()) {
                runCatching {
                    repository.checkAndSeedUserProfileIfEmpty()
                    repository.checkAndSeedCategoriesIfEmpty()
                    repository.checkAndSeedShopItemsIfEmpty()
                    repository.checkAndSeedMedicineRemindersIfEmpty()
                    repository.migratePlaintextRecordsToEncryptedVault()
                }
            }
        }
    }

    fun setScreen(screen: NavScreen) {
        _currentScreen.value = screen
    }

    fun setCareTab(tab: CareTab) {
        _activeCareTab.value = tab
    }

    fun setShowSettings(show: Boolean) {
        _showSettingsSheet.value = show
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun showFeedback(msg: String) {
        _feedbackMessage.value = msg
    }

    fun setShopHideCompleted(hide: Boolean) {
        _shopHideCompleted.value = hide
    }

    fun setShopViewMode(mode: String) {
        _shopViewMode.value = mode
    }

    fun setShopSortMode(mode: String) {
        _shopSortMode.value = mode
    }

    fun setPeriodsTab(tab: String) {
        _periodsTabName.value = tab
    }

    fun completeOnboarding(name: String, lastPeriodStartMillis: Long, cycleLength: Int, periodLength: Int) {
        viewModelScope.launch {
            val profile = UserProfile(
                id = 1,
                name = name.trim(),
                cycleLength = cycleLength,
                periodLength = periodLength,
                waterRemindersEnabled = true,
                cycleRemindersEnabled = true,
                fertileRemindersEnabled = true,
                checkinRemindersEnabled = true,
                shopRemindersEnabled = true,
                isOnboarded = true
            )
            repository.saveProfile(profile)
            repository.logPeriod(startDateMillis = lastPeriodStartMillis, notes = "Initial period")
            
            // Schedule notifications
            com.example.notifications.NotificationHelper.createNotificationChannels(getApplication())
            com.example.notifications.NotificationHelper.scheduleAllReminders(
                getApplication(),
                profile,
                cycleStatus.value,
                medications.value
            )

            showFeedback("Welcome to KriMoon, ${name.ifBlank { "dear" }}! 🌸")
        }
    }

    fun updateSettings(
        name: String,
        cycleLength: Int,
        periodLength: Int,
        waterGoal: Int,
        waterReminders: Boolean,
        cycleReminders: Boolean,
        fertileReminders: Boolean,
        checkinReminders: Boolean,
        shopReminders: Boolean,
        reminderHour: Int = 20
    ) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfile(id = 1)
            val updated = current.copy(
                name = name.trim(),
                cycleLength = cycleLength.coerceIn(18, 60),
                periodLength = periodLength.coerceIn(2, 14),
                waterGoal = waterGoal.coerceIn(4, 20),
                waterRemindersEnabled = waterReminders,
                cycleRemindersEnabled = cycleReminders,
                fertileRemindersEnabled = fertileReminders,
                checkinRemindersEnabled = checkinReminders,
                shopRemindersEnabled = shopReminders,
                reminderHour = reminderHour,
                isOnboarded = true
            )
            repository.saveProfile(updated)

            // Re-schedule with new preferences
            com.example.notifications.NotificationHelper.createNotificationChannels(getApplication())
            com.example.notifications.NotificationHelper.scheduleAllReminders(
                getApplication(),
                updated,
                cycleStatus.value,
                medications.value
            )

            showFeedback("Settings and reminders updated ✿")
            _showSettingsSheet.value = false
        }
    }

    fun sendTestNotification(type: String) {
        com.example.notifications.NotificationHelper.sendTestNotification(getApplication(), type)
        showFeedback("Sent test notification for $type 🔔")
    }

    fun logPeriodToday() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.logPeriod(startDateMillis = now, notes = "Logged from home")
            userProfile.value?.let { profile ->
                com.example.notifications.NotificationHelper.scheduleAllReminders(
                    getApplication(),
                    profile,
                    cycleStatus.value,
                    medications.value
                )
            }
            showFeedback("Period start recorded! 🩸")
        }
    }

    fun logPeriodDate(
        dateMillis: Long,
        notes: String = "",
        durationDays: Int? = null,
        endDateMillis: Long? = null
    ) {
        viewModelScope.launch {
            repository.logPeriod(
                startDateMillis = dateMillis,
                notes = notes,
                durationDays = durationDays,
                endDateMillis = endDateMillis
            )
            showFeedback("Period added! 🌸")
        }
    }

    fun endPeriodToday() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val activeId = cycleStatus.value.activePeriodId
            if (activeId != null) {
                repository.endPeriod(activeId, now)
                showFeedback("Period ended recorded! Flow complete 🌸")
            } else {
                val latest = periodLogs.value.firstOrNull()
                if (latest != null) {
                    repository.endPeriod(latest.id, now)
                    showFeedback("Period ended recorded! Flow complete 🌸")
                }
            }
        }
    }

    fun endPeriodOnDate(periodId: Long, endDateMillis: Long) {
        viewModelScope.launch {
            repository.endPeriod(periodId, endDateMillis)
            showFeedback("Period end date recorded 🌸")
        }
    }

    fun cancelActivePeriod() {
        viewModelScope.launch {
            val activeId = cycleStatus.value.activePeriodId ?: periodLogs.value.firstOrNull { it.endDateMillis == null }?.id
            if (activeId != null) {
                repository.deletePeriod(activeId)
                showFeedback("Active period cancelled & removed 🌸")
            } else {
                val latest = periodLogs.value.firstOrNull()
                if (latest != null) {
                    repository.deletePeriod(latest.id)
                    showFeedback("Period entry cancelled & removed 🌸")
                }
            }
        }
    }

    fun updatePeriodDateRange(
        periodId: Long,
        startDateMillis: Long,
        endDateMillis: Long?,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val duration = if (endDateMillis != null && endDateMillis >= startDateMillis) {
                val days = kotlin.math.round((endDateMillis - startDateMillis).toDouble() / (24L * 60 * 60 * 1000)).toInt() + 1
                days.coerceIn(1, 15)
            } else null
            val updated = PeriodLog(
                id = periodId,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                durationDays = duration,
                notes = notes
            )
            repository.updatePeriod(updated)
            showFeedback("Period updated in calendar! ✨")
        }
    }

    fun deletePeriod(id: Long) {
        viewModelScope.launch {
            repository.deletePeriod(id)
            showFeedback("Period removed")
        }
    }

    // Categories
    fun addCategory(displayName: String, emoji: String, colorHex: String = "#D4629A") {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            repository.addCategory(displayName, emoji, colorHex)
            showFeedback("Category '$displayName' created! 🌸")
        }
    }

    fun updateCategory(category: TaskCategory) {
        viewModelScope.launch {
            repository.updateCategory(category)
            showFeedback("Category updated ✨")
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            showFeedback("Category removed")
        }
    }

    // Shopping & Task items
    fun toggleShopItem(item: ShopItem) {
        viewModelScope.launch {
            repository.toggleShopItem(item)
        }
    }

    fun updateShopItem(item: ShopItem) {
        viewModelScope.launch {
            repository.updateShopItem(item)
            showFeedback("Task updated ✨")
        }
    }

    fun addShopItem(
        text: String,
        category: String = "general",
        notes: String = "",
        priority: String = "NORMAL"
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addShopItem(
                text = text.trim(),
                category = category,
                isCustom = true,
                notes = notes,
                priority = priority
            )
            showFeedback("Added to list 🛍️")
        }
    }

    fun deleteShopItem(id: Long) {
        viewModelScope.launch {
            repository.deleteShopItem(id)
            showFeedback("Item removed")
        }
    }

    fun moveShopItem(item: ShopItem, allCategoryItems: List<ShopItem>, direction: Int) {
        viewModelScope.launch {
            repository.moveShopItem(item, allCategoryItems, direction)
        }
    }

    fun clearCompletedShopItems() {
        viewModelScope.launch {
            repository.clearCompletedShopItems(shopItems.value)
            showFeedback("Completed items cleared ✨")
        }
    }

    fun resetShopItemsToDefaults() {
        viewModelScope.launch {
            repository.resetShopItemsToDefaults()
            showFeedback("Essential checklist restored 🌸")
        }
    }

    // Medications
    fun addMedication(
        name: String,
        dosage: String = "1 pill",
        hour: Int = 9,
        minute: Int = 0,
        categoryEmoji: String = "💊",
        daysOfWeek: String = "ALL",
        customSound: String = "cute_chime",
        customReminderMessage: String = "",
        instructions: String = "Take with water",
        colorHex: String = "#D4629A"
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addMedication(
                name = name.trim(),
                dosage = dosage.trim().ifBlank { "1 dose" },
                hour = hour,
                minute = minute,
                categoryEmoji = categoryEmoji,
                daysOfWeek = daysOfWeek,
                customSound = customSound,
                customReminderMessage = customReminderMessage,
                instructions = instructions,
                colorHex = colorHex
            )
            rescheduleReminders()
            showFeedback("Scheduled reminder for $name 💊🌸")
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            repository.updateMedication(medication)
            rescheduleReminders()
            showFeedback("Medication updated ✨")
        }
    }

    fun deleteMedication(id: Long) {
        viewModelScope.launch {
            repository.deleteMedication(id)
            rescheduleReminders()
            showFeedback("Medication removed")
        }
    }

    fun toggleMedication(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMedication(id, isEnabled)
            rescheduleReminders()
            showFeedback(if (isEnabled) "Reminder activated ⏰" else "Reminder paused ⏸️")
        }
    }

    fun markMedicationTakenToday(id: Long) {
        viewModelScope.launch {
            val nowTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            repository.markMedicationTakenToday(id, todayString, nowTimeStr)
            showFeedback("Marked as taken! Stay glowing! ✨💊")
        }
    }

    fun sendTestMedicationNotification(medication: Medication) {
        com.example.notifications.NotificationHelper.sendTestNotification(
            context = getApplication(),
            type = "medicine",
            customSound = medication.customSound,
            customMessage = medication.customReminderMessage,
            medicineName = medication.name,
            medicineDosage = medication.dosage
        )
        showFeedback("Sent test alert with '${medication.customSound}' 🔔")
    }

    // Compatibility methods for MedicineReminder
    fun markMedicineTakenToday(id: Long) = markMedicationTakenToday(id)

    fun addMedicineReminder(
        name: String,
        dosage: String = "1 pill",
        hour: Int = 9,
        minute: Int = 0,
        categoryEmoji: String = "💊"
    ) = addMedication(
        name = name,
        dosage = dosage,
        hour = hour,
        minute = minute,
        categoryEmoji = categoryEmoji
    )

    fun updateMedicineReminder(reminder: MedicineReminder) = updateMedication(reminder)
    fun deleteMedicineReminder(id: Long) = deleteMedication(id)
    fun toggleMedicineReminder(id: Long, isEnabled: Boolean) = toggleMedication(id, isEnabled)

    private fun rescheduleReminders() {
        val profile = userProfile.value ?: return
        com.example.notifications.NotificationHelper.createNotificationChannels(getApplication())
        com.example.notifications.NotificationHelper.scheduleAllReminders(
            getApplication(),
            profile,
            cycleStatus.value,
            medications.value
        )
    }

    fun incrementWater() {
        viewModelScope.launch {
            val currentCount = todayWater.value?.glassesCount ?: 0
            val goal = userProfile.value?.waterGoal ?: 8
            val newCount = currentCount + 1
            repository.setWaterCount(todayString, newCount)
            if (newCount == goal) {
                showFeedback("🎉 Daily water goal achieved! Stay glowing! 💧")
            }
        }
    }

    fun decrementWater() {
        viewModelScope.launch {
            val currentCount = todayWater.value?.glassesCount ?: 0
            if (currentCount > 0) {
                repository.setWaterCount(todayString, currentCount - 1)
            }
        }
    }

    fun setWaterCount(count: Int) {
        viewModelScope.launch {
            repository.setWaterCount(todayString, count)
        }
    }

    fun resetWaterToday() {
        viewModelScope.launch {
            repository.setWaterCount(todayString, 0)
            showFeedback("Water count reset for today")
        }
    }

    fun updateWaterGoal(goal: Int) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfile(id = 1)
            val clamped = goal.coerceIn(4, 20)
            val updated = current.copy(waterGoal = clamped)
            repository.saveProfile(updated)
            showFeedback("Daily water goal set to $clamped glasses 💧")
        }
    }

    fun addNote(content: String, tag: String, reminderTimeMillis: Long? = null) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val newId = repository.addNote(content = content.trim(), tag = tag, reminderTimeMillis = reminderTimeMillis)
            if (reminderTimeMillis != null && reminderTimeMillis > System.currentTimeMillis()) {
                val createdNote = repository.getNoteById(newId)
                if (createdNote != null) {
                    NotificationHelper.scheduleNoteReminder(getApplication(), createdNote)
                    showFeedback("Note saved with reminder notification! ⏰🌸")
                    return@launch
                }
            }
            showFeedback("Note saved privately 🌸")
        }
    }

    fun updateNoteReminder(id: Long, reminderTimeMillis: Long?) {
        viewModelScope.launch {
            repository.updateNoteReminder(id, reminderTimeMillis)
            if (reminderTimeMillis != null && reminderTimeMillis > System.currentTimeMillis()) {
                val note = repository.getNoteById(id)
                if (note != null) {
                    NotificationHelper.scheduleNoteReminder(getApplication(), note)
                    showFeedback("Reminder notification scheduled! ⏰✨")
                    return@launch
                }
            }
            NotificationHelper.cancelNoteReminder(getApplication(), id)
            showFeedback("Reminder cleared")
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            NotificationHelper.cancelNoteReminder(getApplication(), id)
            repository.deleteNote(id)
            showFeedback("Note deleted")
        }
    }

    fun sendTestNoteNotification(note: UserNote? = null) {
        viewModelScope.launch {
            val targetNote = note ?: userNotes.value.firstOrNull() ?: UserNote(
                id = 9999L,
                content = "Remember to take 5 deep calming breaths, drink water, and be kind to yourself! 🌸",
                tag = "thought"
            )
            NotificationHelper.showNoteReminderNotification(getApplication(), targetNote)
            showFeedback("Notification sent! Check your notification shade 🔔🌸")
        }
    }

    fun saveDailyCycleLog(log: DailyCycleLog) {
        viewModelScope.launch {
            repository.saveDailyCycleLog(log)
            showFeedback("Private symptom & cycle data saved locally 🔒🌸")
        }
    }

    fun deleteDailyCycleLog(id: Long) {
        viewModelScope.launch {
            repository.deleteDailyCycleLog(id)
            showFeedback("Symptom record removed")
        }
    }

    fun clearAllDailyCycleLogs() {
        viewModelScope.launch {
            repository.clearDailyCycleLogs()
            showFeedback("All saved symptom & flow history cleared")
        }
    }

    fun getDailyCycleLogForDate(dateString: String): kotlinx.coroutines.flow.Flow<DailyCycleLog?> {
        return repository.getDailyCycleLog(dateString)
    }

    fun logQuickMood(moodEmoji: String, moodLabel: String) {
        viewModelScope.launch {
            val noteContent = "Reflection: $moodEmoji $moodLabel"
            repository.addNote(content = noteContent, tag = "thought")
            showFeedback("$moodEmoji Note saved!")
        }
    }

    fun retryVaultUnlock() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                LocalVaultCrypto.retryUnlock()
            }
            if (state == VaultState.HEALTHY || state == VaultState.TEST_EPHEMERAL) {
                runCatching {
                    repository.checkAndSeedUserProfileIfEmpty()
                    repository.checkAndSeedCategoriesIfEmpty()
                    repository.checkAndSeedShopItemsIfEmpty()
                    repository.checkAndSeedMedicineRemindersIfEmpty()
                    repository.migratePlaintextRecordsToEncryptedVault()
                }
                showFeedback("Private vault unlocked 🔒")
            } else {
                showFeedback("Encryption key is still unavailable. Unlock your device or reset the local vault.")
            }
        }
    }

    fun confirmResetCorruptedVault() {
        viewModelScope.launch {
            detachCloudListeners()
            cancelAllScheduledRemindersAndNotifications()
            repository.resetCorruptedVaultAndClearAllData()
            sentBlockHashes.clear()
            processedBlockHashes.clear()
            rebroadcastedCodes.clear()
            _activeCoupleCode.value = null
            showFeedback("Local vault reset with a fresh encryption key 🔒")
        }
    }

    private suspend fun cancelAllScheduledRemindersAndNotifications() {
        val meds = runCatching { repository.getMedicationsOnce() }.getOrDefault(medications.value)
        val notes = runCatching { repository.getAllNotesOnce() }.getOrDefault(userNotes.value)
        NotificationHelper.cancelAllReminders(
            context = getApplication(),
            medicines = meds,
            notes = notes,
            cancelPostedNotifications = true
        )
    }

    fun clearAllData() {
        viewModelScope.launch {
            detachCloudListeners()
            cancelAllScheduledRemindersAndNotifications()
            repository.clearAllData()
            sentBlockHashes.clear()
            processedBlockHashes.clear()
            rebroadcastedCodes.clear()
            _activeCoupleCode.value = null
            showFeedback("All local data & reminders erased 🔒")
            _showSettingsSheet.value = false
        }
    }

    /**
     * Full account & data deletion:
     * 1. Tombstones all user Couple Lounge rooms in Firestore (scrubs personal names & public keys
     *    while keeping non-identifying room-code reservation so retired codes cannot be reused).
     * 2. Cancels all scheduled alarms and posted notifications.
     * 3. Wipes all local Room tables and performs cryptographic DEK/KEK erasure.
     * 4. Deletes the Firebase Auth account (or informs user if recent Google re-authentication is required).
     */
    fun deleteAccountAndEraseData() {
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val signedInUid = _cloudAuthUid.value ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                var tombstonedCount = 0
                if (!signedInUid.isNullOrBlank()) {
                    val cloudRepo = getCloudRepoOrNull()
                    if (cloudRepo != null) {
                        val tombstoneResult = cloudRepo.tombstoneAllUserRoomsForAccountDeletion()
                        if (tombstoneResult.isFailure) {
                            showFeedback("Cloud room scrub failed (check connection) — account deletion paused so no cloud data is stranded.")
                            return@launch
                        }
                        tombstonedCount = tombstoneResult.getOrDefault(0)
                    }
                }

                detachCloudListeners()
                cancelAllScheduledRemindersAndNotifications()
                repository.clearAllData()
                sentBlockHashes.clear()
                processedBlockHashes.clear()
                rebroadcastedCodes.clear()
                _activeCoupleCode.value = null

                var authDeletionStatus = "LOCAL_ONLY"
                val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                if (currentUser != null) {
                    try {
                        currentUser.delete().await()
                        authDeletionStatus = "DELETED"
                    } catch (_: FirebaseAuthRecentLoginRequiredException) {
                        authDeletionStatus = "REAUTH_REQUIRED"
                        runCatching { Firebase.auth.signOut() }
                    } catch (_: Exception) {
                        authDeletionStatus = "SIGNED_OUT_ONLY"
                        runCatching { Firebase.auth.signOut() }
                    }
                }

                _cloudAuthUid.value = null
                _cloudUserName.value = null
                _cloudSyncStatus.value = "SIGNED_OUT"
                _showSettingsSheet.value = false

                val roomNote = if (tombstonedCount > 0) " & $tombstonedCount cloud room(s) scrubbed" else ""
                when (authDeletionStatus) {
                    "DELETED" -> showFeedback("Account deleted, local vault erased$roomNote 🔒")
                    "REAUTH_REQUIRED" -> showFeedback("Local data erased$roomNote. Sign in again briefly to finish deleting your Firebase account.")
                    "SIGNED_OUT_ONLY" -> showFeedback("Local data erased$roomNote & signed out.")
                    else -> showFeedback("All local data & reminders erased 🔒")
                }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    // --- Offline Arcade & Long-Distance Couple Lounge ---
    fun recordGameScore(gameId: String, score: Int, streak: Int = 0, lowerIsBetter: Boolean = false) {
        viewModelScope.launch {
            repository.recordGameScore(gameId, score, streak, lowerIsBetter)
        }
    }

    // --- Live 2-Phone Encrypted Cloud Sync Lifecycle ---
    fun detachCloudListeners() {
        userRoomsJob?.cancel()
        userRoomsJob = null
        activeRoomSyncJob?.cancel()
        activeRoomSyncJob = null
        activeBlocksSyncJob?.cancel()
        activeBlocksSyncJob = null
        roomWatcherCoordinatorJob?.cancel()
        roomWatcherCoordinatorJob = null
    }

    fun retryCloudSync() {
        val uid = _cloudAuthUid.value ?: return
        val name = _cloudUserName.value
        val activeCode = activeCoupleRoom.value?.takeIf { !it.isPassAndPlay && it.approvalStatus == "CONNECTED" }?.coupleCode
        detachCloudListeners()
        _cloudAuthUid.value = null
        onCloudUserChanged(uid, name)
        if (!activeCode.isNullOrBlank()) {
            viewModelScope.launch {
                drainPendingTurnsForRoom(activeCode)
            }
        }
    }

    fun onCloudUserChanged(uid: String?, displayName: String?) {
        val normalizedUid = uid?.takeIf { it.isNotBlank() }
        _cloudUserName.value = displayName?.takeIf { it.isNotBlank() }

        if (normalizedUid == _cloudAuthUid.value && userRoomsJob?.isActive == true) {
            return
        }

        detachCloudListeners()
        _cloudAuthUid.value = normalizedUid

        if (normalizedUid == null) {
            _cloudSyncStatus.value = "SIGNED_OUT"
            return
        }

        val cloudRepo = getCloudRepoOrNull()
        if (cloudRepo == null) {
            _cloudSyncStatus.value = "OFFLINE"
            return
        }

        _cloudSyncStatus.value = "CONNECTING"

        // 1. Discover any bonded cloud rooms for this signed-in user (off Main thread)
        userRoomsJob = viewModelScope.launch(Dispatchers.Default) {
            var attempt = 0
            while (attempt < 4 && _cloudAuthUid.value == normalizedUid) {
                var failed = false
                cloudRepo.observeUserRooms(normalizedUid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        failed = true
                        _cloudSyncStatus.value = "OFFLINE"
                    }
                    .collect { cloudRooms ->
                        attempt = 0
                        for (cloudRoom in cloudRooms) {
                            mergeCloudRoomIntoLocal(cloudRoom, normalizedUid)
                        }
                        if (_activeCoupleCode.value.isNullOrBlank() &&
                            userProfile.value?.activeCoupleCode.isNullOrBlank() &&
                            cloudRooms.isNotEmpty()
                        ) {
                            val newest = cloudRooms.maxByOrNull { it.updatedAt?.seconds ?: 0L } ?: cloudRooms.first()
                            _activeCoupleCode.value = newest.coupleCode
                        }
                    }
                if (!failed) break
                attempt++
                delay(2000L * attempt)
            }
        }

        // 2. Coordinate real-time room & encrypted block listeners for whichever room is active
        roomWatcherCoordinatorJob = viewModelScope.launch {
            activeCoupleRoom
                .map { room ->
                    if (room == null || room.isPassAndPlay) null
                    else Pair(room.coupleCode, room.approvalStatus == "PENDING_HOST_APPROVAL")
                }
                .distinctUntilChanged()
                .collectLatest { state ->
                    activeRoomSyncJob?.cancel()
                    activeBlocksSyncJob?.cancel()
                    val code = state?.first
                    val isPendingApproval = state?.second ?: false
                    if (code.isNullOrBlank()) {
                        _cloudSyncStatus.value = "WAITING"
                        return@collectLatest
                    }
                    attachActiveRoomListeners(cloudRepo, code, normalizedUid, isPendingApproval)
                }
        }
    }

    private fun attachActiveRoomListeners(
        cloudRepo: CoupleCloudRepository,
        coupleCode: String,
        uid: String,
        isPendingHostApproval: Boolean
    ) {
        activeRoomSyncJob = viewModelScope.launch(Dispatchers.Default) {
            var attemptedAutoRegister = false
            var attempt = 0
            while (attempt < 4 && _cloudAuthUid.value == uid) {
                var failed = false
                var declinedByPermission = false
                cloudRepo.observeRoom(coupleCode, uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val localPending = repository.getCoupleRoom(coupleCode)
                        if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                            localPending != null &&
                            !localPending.isHost &&
                            localPending.approvalStatus == "PENDING_HOST_APPROVAL"
                        ) {
                            declinedByPermission = true
                            coupleRoomMutex.withLock {
                                repository.deleteCoupleRoom(coupleCode)
                            }
                            if (_activeCoupleCode.value == coupleCode) {
                                _activeCoupleCode.value = null
                            }
                            userProfile.value?.let { profile ->
                                if (profile.activeCoupleCode == coupleCode) {
                                    repository.saveProfile(profile.copy(activeCoupleCode = ""))
                                }
                            }
                            _cloudSyncStatus.value = "WAITING"
                            showFeedback("Host declined your join request")
                        } else {
                            failed = true
                            _cloudSyncStatus.value = "OFFLINE"
                        }
                    }
                    .collect { cloudRoom ->
                        attempt = 0
                        if (cloudRoom == null) {
                            // Legacy local-only room created before cloud sync: register it once if we are host
                            if (!attemptedAutoRegister) {
                                attemptedAutoRegister = true
                                val local = repository.getCoupleRoom(coupleCode)
                                if (local != null && local.isBonded && local.isHost && local.approvalStatus != "PENDING_HOST_APPROVAL") {
                                    val spice = runCatching { SpiceLevel.valueOf(local.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
                                    val pubKey = if (local.ecdhPublicKeyB64.isNotBlank()) {
                                        local.ecdhPublicKeyB64
                                    } else {
                                        val generated = withContext(Dispatchers.Default) { CoupleEcdhEngine.generateKeyPair() }
                                        coupleRoomMutex.withLock {
                                            repository.getCoupleRoom(coupleCode)?.let { cur ->
                                                repository.saveCoupleRoom(
                                                    cur.copy(
                                                        ecdhPublicKeyB64 = generated.publicKeyB64,
                                                        ecdhPrivateKeyB64 = generated.privateKeyB64
                                                    )
                                                )
                                            }
                                        }
                                        generated.publicKeyB64
                                    }
                                    cloudRepo.createUniqueCloudRoom(
                                        myName = local.myName,
                                        spiceLevel = spice,
                                        preferredCode = coupleCode,
                                        creatorPublicKey = pubKey
                                    )
                                }
                            }
                            _cloudSyncStatus.value = "WAITING"
                            return@collect
                        }

                        if (cloudRoom.status == "UNPAIRED") {
                            coupleRoomMutex.withLock {
                                repository.deleteCoupleRoom(coupleCode)
                            }
                            if (_activeCoupleCode.value == coupleCode) {
                                _activeCoupleCode.value = null
                            }
                            userProfile.value?.let { profile ->
                                if (profile.activeCoupleCode == coupleCode) {
                                    repository.saveProfile(profile.copy(activeCoupleCode = ""))
                                }
                            }
                            _cloudSyncStatus.value = "WAITING"
                            showFeedback("Partner ended the couple room")
                            return@collect
                        }

                        val localBefore = repository.getCoupleRoom(coupleCode)
                        val wasPendingRequester = localBefore != null &&
                            !localBefore.isHost &&
                            localBefore.approvalStatus == "PENDING_HOST_APPROVAL"

                        // Detect Host declining Partner 2's pending request
                        if (wasPendingRequester &&
                            cloudRoom.status == "WAITING" &&
                            cloudRoom.pendingPartnerUid != uid
                        ) {
                            coupleRoomMutex.withLock {
                                repository.deleteCoupleRoom(coupleCode)
                            }
                            if (_activeCoupleCode.value == coupleCode) {
                                _activeCoupleCode.value = null
                            }
                            userProfile.value?.let { profile ->
                                if (profile.activeCoupleCode == coupleCode) {
                                    repository.saveProfile(profile.copy(activeCoupleCode = ""))
                                }
                            }
                            _cloudSyncStatus.value = "WAITING"
                            showFeedback("Host declined your join request")
                            return@collect
                        }

                        val justConnected = mergeCloudRoomIntoLocal(cloudRoom, uid)
                        _cloudSyncStatus.value = when (cloudRoom.status) {
                            "CONNECTED" -> "LIVE"
                            "PENDING_APPROVAL" -> "PENDING_APPROVAL"
                            else -> "WAITING"
                        }

                        // Partner 2 was just allowed in by Host -> enqueue encrypted BondHandshake intent
                        if (wasPendingRequester && cloudRoom.status == "CONNECTED") {
                            val freshLocal = repository.getCoupleRoom(coupleCode)
                            if (freshLocal != null) {
                                val spice = runCatching { SpiceLevel.valueOf(freshLocal.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
                                val (packet, _) = withContext(Dispatchers.Default) {
                                    CoupleTurnCodec.encodeBondHandshakeWithBlock(
                                        coupleCode = coupleCode,
                                        senderName = freshLocal.myName,
                                        spiceLevel = spice,
                                        previousHash = freshLocal.lastBlockHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(coupleCode).blockHash },
                                        blockIndex = freshLocal.blockHeight.coerceAtLeast(1),
                                        ecdhSharedSecretB64 = freshLocal.ecdhSharedSecretB64
                                    )
                                }
                                CoupleTurnCodec.extractActivityIntent(
                                    rawPacket = packet,
                                    coupleCode = coupleCode,
                                    ecdhSharedSecretB64 = freshLocal.ecdhSharedSecretB64
                                ).onSuccess { intent ->
                                    enqueueAndDrainTurnIntent(coupleCode, intent, triggerCloudDrain = false)
                                }
                                showFeedback("Host allowed you in! Connected live ♥")
                            }
                        }

                        // If partner was just approved by Host, enqueue any pre-approval local Host moves
                        val isHostLocal = localBefore?.isHost ?: (cloudRoom.creatorUid == uid)
                        if (cloudRoom.status == "CONNECTED" &&
                            isHostLocal &&
                            cloudRoom.creatorUid == uid &&
                            (justConnected || coupleCode !in rebroadcastedCodes)
                        ) {
                            rebroadcastedCodes.add(coupleCode)
                            rebroadcastPendingLocalMoves(cloudRepo, coupleCode)
                        }

                        // Drain any offline/queued encrypted turns whenever room is CONNECTED
                        if (cloudRoom.status == "CONNECTED") {
                            drainPendingTurnsForRoom(coupleCode)
                        }
                    }
                if (!failed || declinedByPermission) break
                attempt++
                delay(2000L * attempt)
            }
        }

        // Only subscribe to encrypted blocks once admitted (pending requesters are not yet in memberUids)
        if (!isPendingHostApproval) {
            activeBlocksSyncJob = viewModelScope.launch(Dispatchers.Default) {
                var attempt = 0
                while (attempt < 4 && _cloudAuthUid.value == uid) {
                    var failed = false
                    cloudRepo.observeEncryptedBlocks(coupleCode, uid)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            failed = true
                        }
                        .collect { blocks ->
                            attempt = 0
                            for (blk in blocks) {
                                val hash = blk.blockHash
                                if (hash.isBlank() || hash in processedBlockHashes || hash in sentBlockHashes) continue
                                val localRoom = repository.getCoupleRoom(coupleCode) ?: continue
                                val existingLedger = CoupleBlockchain.ledgerFromJson(localRoom.blockchainLedgerJson)
                                if (existingLedger.any { it.blockHash == hash }) {
                                    processedBlockHashes.add(hash)
                                    continue
                                }
                                // Ignore blocks originating from this exact authenticated UID
                                // (V2 rules strictly prohibit self-join with duplicate UID, so senderUid == uid is authoritative even if display name changes)
                                val isOwnBlock = blk.senderUid == uid
                                if (isOwnBlock) {
                                    processedBlockHashes.add(hash)
                                    continue
                                }
                                processedBlockHashes.add(hash)
                                applyTurnPacketToRoom(
                                    room = localRoom,
                                    rawPacket = blk.encryptedPacket,
                                    isAutoSync = true,
                                    fromCloudLive = true
                                )
                            }
                        }
                    if (!failed) break
                    attempt++
                    delay(2000L * attempt)
                }
            }
        }
    }

    /**
     * When Phone B is approved into a room where Phone A already made moves while waiting,
     * converts any unqueued pre-approval local moves into durable encrypted intents and drains
     * them atomically with the 2-member ECDH v3 session key.
     */
    private suspend fun rebroadcastPendingLocalMoves(
        cloudRepo: CoupleCloudRepository,
        coupleCode: String
    ) {
        if (!LocalVaultCrypto.canSafelyWrite()) return
        val local = repository.getCoupleRoom(coupleCode) ?: return
        if (local.isPassAndPlay) return
        val spice = runCatching { SpiceLevel.valueOf(local.spiceLevel) }.getOrDefault(SpiceLevel.FLIRTY)
        val sharedSecret = local.ecdhSharedSecretB64
        val prevHash = local.lastBlockHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(coupleCode).blockHash }
        val height = local.blockHeight.coerceAtLeast(1)

        // If durable pending turns were already queued while WAITING, simply drain them!
        val alreadyQueued = repository.getPendingTurnsForRoomOnce(coupleCode)
        if (alreadyQueued.isNotEmpty()) {
            drainPendingTurnsForRoom(coupleCode)
            return
        }

        suspend fun enqueueFromPacket(packet: String) {
            CoupleTurnCodec.extractActivityIntent(packet, coupleCode, sharedSecret).onSuccess { intent ->
                enqueueAndDrainTurnIntent(coupleCode, intent, triggerCloudDrain = false)
            }
        }

        val quizState = CoupleTurnCodec.roundStateFromJson(local.quizStateJson)
        if (quizState.myChoice != null && quizState.partnerChoice == null) {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeQuizTurnWithBlock(
                    coupleCode, local.myName, quizState.roundIndex, quizState.myChoice, spice, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val wyrState = CoupleTurnCodec.roundStateFromJson(local.wyrStateJson)
        if (wyrState.myChoice != null && wyrState.partnerChoice == null) {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeWyrTurnWithBlock(
                    coupleCode, local.myName, wyrState.roundIndex, wyrState.myChoice, spice, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val strokes = CoupleTurnCodec.strokesFromJsonString(local.doodleStrokesJson)
        if (strokes.isNotEmpty()) {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeDoodleTurnWithBlock(
                    coupleCode, local.myName, local.doodlePrompt, strokes.takeLast(30), prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val ttt = CoupleTurnCodec.ticTacToeFromJsonString(local.ticTacToeStateJson)
        if (ttt.board.any { it.isNotEmpty() }) {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeTicTacToeTurnWithBlock(
                    coupleCode, local.myName, ttt, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val dareState = CoupleActivitiesDeck.dareStateFromJson(local.dareStateJson)
        if (dareState.promptText.isNotBlank() && dareState.status == "PENDING") {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeDareTurnWithBlock(
                    coupleCode, local.myName, dareState, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val waveState = CoupleActivitiesDeck.wavelengthStateFromJson(local.wavelengthStateJson)
        if (waveState.clueWord.isNotBlank() && waveState.phase == "GUESS") {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeWavelengthTurnWithBlock(
                    coupleCode, local.myName, waveState, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        val whisperState = CoupleActivitiesDeck.dateWhisperStateFromJson(local.whisperStateJson)
        val latestMyWhisper = whisperState.whispers.lastOrNull {
            it.senderName.equals(local.myName.trim(), ignoreCase = true)
        }
        val latestMyDateLike = whisperState.myLikedIds.lastOrNull().orEmpty()
        if (latestMyWhisper != null || latestMyDateLike.isNotBlank()) {
            val (pkt, _) = withContext(Dispatchers.Default) {
                CoupleTurnCodec.encodeWhisperTurnWithBlock(
                    coupleCode, local.myName, latestMyDateLike, latestMyWhisper, prevHash, height, sharedSecret
                )
            }
            enqueueFromPacket(pkt)
        }

        drainPendingTurnsForRoom(coupleCode)
    }

    private suspend fun mergeCloudRoomIntoLocal(cloudRoom: CoupleCloudRoom, uid: String): Boolean = coupleRoomMutex.withLock {
        if (!LocalVaultCrypto.canSafelyWrite()) return@withLock false
        val existing = repository.getCoupleRoom(cloudRoom.coupleCode)
        val isHostLocal = when {
            existing != null -> existing.isHost
            cloudRoom.creatorUid == uid -> true
            else -> false
        }
        val defaultMyName = _cloudUserName.value
            ?: userProfile.value?.name?.takeIf { it.isNotBlank() }
            ?: if (isHostLocal) cloudRoom.creatorName else cloudRoom.partnerName.ifBlank { cloudRoom.pendingPartnerName.ifBlank { "Me" } }

        val resolvedMyName = existing?.myName?.takeIf { it.isNotBlank() } ?: defaultMyName
        val resolvedPartnerName = when {
            isHostLocal && cloudRoom.partnerName.isNotBlank() &&
                !cloudRoom.partnerName.equals(resolvedMyName, ignoreCase = true) -> cloudRoom.partnerName
            isHostLocal && cloudRoom.creatorName.isNotBlank() &&
                !cloudRoom.creatorName.equals(resolvedMyName, ignoreCase = true) -> cloudRoom.creatorName
            !isHostLocal -> cloudRoom.creatorName
            else -> existing?.partnerName.orEmpty()
        }
        val isLiveConnected = cloudRoom.status == "CONNECTED"
        val resolvedApprovalStatus = when (cloudRoom.status) {
            "CONNECTED" -> "CONNECTED"
            "PENDING_APPROVAL" -> if (isHostLocal) "AWAITING_MY_APPROVAL" else "PENDING_HOST_APPROVAL"
            else -> "WAITING"
        }

        val peerPubKey = if (isHostLocal) cloudRoom.partnerPublicKey else cloudRoom.creatorPublicKey
        val cloudSchema = cloudRoom.schemaVersion.coerceAtLeast(1)

        if (existing == null) {
            val genesis = CoupleBlockchain.createDeterministicGenesisBlock(cloudRoom.coupleCode)
            val initialTtt = TicTacToeSyncState(mySymbol = if (isHostLocal) "X" else "O")
            val created = CoupleRoomRecord(
                coupleCode = cloudRoom.coupleCode,
                myName = resolvedMyName,
                partnerName = resolvedPartnerName,
                spiceLevel = cloudRoom.spiceLevel.ifBlank { SpiceLevel.FLIRTY.name },
                isBonded = true,
                partnerLocked = isLiveConnected,
                isHost = isHostLocal,
                approvalStatus = resolvedApprovalStatus,
                pendingPartnerName = cloudRoom.pendingPartnerName,
                peerPublicKeyB64 = peerPubKey,
                ticTacToeStateJson = CoupleTurnCodec.ticTacToeToJsonString(initialTtt),
                blockchainLedgerJson = CoupleBlockchain.ledgerToJson(listOf(genesis)),
                lastBlockHash = cloudRoom.latestBlockHash.ifBlank { genesis.blockHash },
                blockHeight = cloudRoom.blockCount.toInt().coerceAtLeast(1),
                chainSchemaVersion = cloudSchema,
                minProtocolVersion = CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2,
                migrationAnchorHash = cloudRoom.migrationAnchorHash,
                migrationAnchorIndex = cloudRoom.migrationAnchorIndex.toInt(),
                firewallPassedCount = 1,
                firewallBlockedCount = 0,
                lastFirewallEvent = "Protected live bond ready"
            )
            repository.saveCoupleRoom(created)
            return@withLock isLiveConnected
        } else {
            val resolvedPeerPub = peerPubKey.ifBlank { existing.peerPublicKeyB64 }
            val resolvedSharedSecret = if (
                resolvedPeerPub.isNotBlank() &&
                existing.ecdhPrivateKeyB64.isNotBlank() &&
                (existing.peerPublicKeyB64 != resolvedPeerPub || existing.ecdhSharedSecretB64.isBlank())
            ) {
                withContext(Dispatchers.Default) {
                    CoupleEcdhEngine.deriveSharedSecretB64(
                        myPrivateKeyB64 = existing.ecdhPrivateKeyB64,
                        peerPublicKeyB64 = resolvedPeerPub,
                        coupleCode = cloudRoom.coupleCode,
                        myPublicKeyB64 = existing.ecdhPublicKeyB64
                    ).orEmpty()
                }
            } else {
                existing.ecdhSharedSecretB64
            }

            // Protocol version is independent of DB schemaVersion and NEVER downgrades once ECDH v3 is established
            val resolvedMinProtocol = maxOf(
                existing.minProtocolVersion,
                if (resolvedSharedSecret.isNotBlank()) CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3 else CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2
            )
            val resolvedSchema = maxOf(existing.chainSchemaVersion, cloudSchema)
            val resolvedAnchorHash = cloudRoom.migrationAnchorHash.ifBlank { existing.migrationAnchorHash }
            val resolvedAnchorIndex = if (cloudRoom.migrationAnchorIndex > 0) {
                cloudRoom.migrationAnchorIndex.toInt()
            } else {
                existing.migrationAnchorIndex
            }

            val partnerChanged = resolvedPartnerName.isNotBlank() && resolvedPartnerName != existing.partnerName
            val lockChanged = isLiveConnected && !existing.partnerLocked
            val spiceChanged = cloudRoom.spiceLevel.isNotBlank() && cloudRoom.spiceLevel != existing.spiceLevel
            val statusChanged = resolvedApprovalStatus != existing.approvalStatus
            val pendingChanged = cloudRoom.pendingPartnerName != existing.pendingPartnerName
            val ecdhChanged = resolvedPeerPub != existing.peerPublicKeyB64 ||
                resolvedSharedSecret != existing.ecdhSharedSecretB64 ||
                resolvedMinProtocol != existing.minProtocolVersion
            val metaChanged = resolvedSchema != existing.chainSchemaVersion ||
                resolvedAnchorHash != existing.migrationAnchorHash ||
                resolvedAnchorIndex != existing.migrationAnchorIndex

            if (partnerChanged || lockChanged || spiceChanged || statusChanged || pendingChanged || ecdhChanged || metaChanged) {
                repository.saveCoupleRoom(
                    existing.copy(
                        partnerName = resolvedPartnerName.ifBlank { existing.partnerName },
                        partnerLocked = existing.partnerLocked || isLiveConnected,
                        spiceLevel = cloudRoom.spiceLevel.ifBlank { existing.spiceLevel },
                        approvalStatus = resolvedApprovalStatus,
                        pendingPartnerName = cloudRoom.pendingPartnerName,
                        peerPublicKeyB64 = resolvedPeerPub,
                        ecdhSharedSecretB64 = resolvedSharedSecret,
                        chainSchemaVersion = resolvedSchema,
                        minProtocolVersion = resolvedMinProtocol,
                        migrationAnchorHash = resolvedAnchorHash,
                        migrationAnchorIndex = resolvedAnchorIndex
                    )
                )
                if (isHostLocal &&
                    resolvedApprovalStatus == "AWAITING_MY_APPROVAL" &&
                    existing.approvalStatus != "AWAITING_MY_APPROVAL"
                ) {
                    val reqName = cloudRoom.pendingPartnerName.ifBlank { "Your partner" }
                    showFeedback("$reqName wants to enter Room #${cloudRoom.coupleCode} — tap Allow ♥")
                } else if (lockChanged && resolvedPartnerName.isNotBlank() && existing.approvalStatus != "PENDING_HOST_APPROVAL") {
                    showFeedback("Connected live with $resolvedPartnerName ♥")
                }
            }
            return@withLock lockChanged
        }
    }

    /**
     * Generates and atomically reserves a globally unique, single-use 6-digit couple room code.
     * Once generated, no other couple can ever claim or reuse this code.
     */
    fun createUniqueCoupleRoom(myName: String) {
        val cleanName = myName.trim()
        if (cleanName.isBlank()) {
            showFeedback("Please enter your name first 💕")
            return
        }
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val uid = _cloudAuthUid.value
                val cloudRepo = if (uid != null) getCloudRepoOrNull() else null
                val isOfflinePassAndPlay = uid == null

                val keyPair = withContext(Dispatchers.Default) {
                    CoupleEcdhEngine.generateKeyPair()
                }

                val reservedCode: String = if (cloudRepo != null && uid != null) {
                    val result = cloudRepo.createUniqueCloudRoom(
                        myName = cleanName,
                        spiceLevel = SpiceLevel.FLIRTY,
                        creatorPublicKey = keyPair.publicKeyB64
                    )
                    if (result.isFailure) {
                        val msg = result.exceptionOrNull()?.message
                            ?: "Could not reserve a unique room code. Check your internet and try again."
                        showFeedback(msg)
                        return@launch
                    }
                    result.getOrThrow().room.coupleCode
                } else {
                    CoupleTurnCodec.generateUniqueCode(coupleRooms.value.map { it.coupleCode }.toSet())
                }

                val genesis = withContext(Dispatchers.Default) {
                    CoupleBlockchain.createDeterministicGenesisBlock(reservedCode)
                }

                coupleRoomMutex.withLock {
                    val ledger = listOf(genesis)
                    val initialTtt = TicTacToeSyncState(mySymbol = "X")
                    val created = CoupleRoomRecord(
                        coupleCode = reservedCode,
                        myName = cleanName,
                        partnerName = "",
                        spiceLevel = SpiceLevel.FLIRTY.name,
                        isPassAndPlay = isOfflinePassAndPlay,
                        isBonded = true,
                        partnerLocked = false,
                        isHost = true,
                        approvalStatus = if (isOfflinePassAndPlay) "CONNECTED" else "WAITING",
                        pendingPartnerName = "",
                        ecdhPublicKeyB64 = keyPair.publicKeyB64,
                        ecdhPrivateKeyB64 = keyPair.privateKeyB64,
                        ticTacToeStateJson = CoupleTurnCodec.ticTacToeToJsonString(initialTtt),
                        blockchainLedgerJson = CoupleBlockchain.ledgerToJson(ledger),
                        lastBlockHash = genesis.blockHash,
                        blockHeight = 1,
                        chainSchemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
                        minProtocolVersion = CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2,
                        firewallPassedCount = 1,
                        firewallBlockedCount = 0,
                        lastFirewallEvent = "Unique zero-knowledge E2EE room reserved"
                    )
                    repository.saveCoupleRoom(created)
                }

                val currentProfile = userProfile.value ?: UserProfile(id = 1, name = cleanName, isOnboarded = true)
                repository.saveProfile(
                    currentProfile.copy(
                        name = currentProfile.name.ifBlank { cleanName },
                        activeCoupleCode = reservedCode
                    )
                )
                _activeCoupleCode.value = reservedCode

                if (isOfflinePassAndPlay) {
                    showFeedback("Pass & Play room #$reservedCode ready ♥")
                } else {
                    showFeedback("Unique Room #$reservedCode created — share it & tap Allow when your partner joins 🔒")
                }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    /**
     * Requests entry into a Host's existing 6-digit room code.
     * Enters PENDING_HOST_APPROVAL until the room creator explicitly taps Allow.
     */
    fun requestJoinCoupleRoom(myName: String, rawCode: String) {
        val cleanName = myName.trim()
        val cleanCode = CoupleTurnCodec.extractCoupleCodeFromText(rawCode)
        if (cleanName.isBlank()) {
            showFeedback("Please enter your name first 💕")
            return
        }
        if (cleanCode.length != 6) {
            showFeedback("Room code must be 6 digits")
            return
        }
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val uid = _cloudAuthUid.value
                val cloudRepo = if (uid != null) getCloudRepoOrNull() else null
                val isOfflinePassAndPlay = uid == null

                var partnerName = ""
                var spice = SpiceLevel.FLIRTY.name
                var isHostFlag = isOfflinePassAndPlay
                var approvalStatus = if (isOfflinePassAndPlay) "CONNECTED" else "PENDING_HOST_APPROVAL"
                var pendingName = ""
                var partnerLocked = false
                var peerPubKey = ""

                val existingBeforeJoin = repository.getCoupleRoom(cleanCode)
                val localKeyPair = if (
                    existingBeforeJoin != null &&
                    existingBeforeJoin.ecdhPublicKeyB64.isNotBlank() &&
                    existingBeforeJoin.ecdhPrivateKeyB64.isNotBlank()
                ) {
                    CoupleEcdhKeyPair(
                        publicKeyB64 = existingBeforeJoin.ecdhPublicKeyB64,
                        privateKeyB64 = existingBeforeJoin.ecdhPrivateKeyB64
                    )
                } else {
                    withContext(Dispatchers.Default) { CoupleEcdhEngine.generateKeyPair() }
                }

                if (cloudRepo != null && uid != null) {
                    val result = cloudRepo.requestToJoinCloudRoom(
                        coupleCode = cleanCode,
                        myName = cleanName,
                        partnerPublicKey = localKeyPair.publicKeyB64
                    )
                    if (result.isFailure) {
                        val msg = result.exceptionOrNull()?.message
                            ?: "Could not request access to Room #$cleanCode."
                        showFeedback(msg)
                        return@launch
                    }
                    val joinInfo = result.getOrThrow()
                    val cloudRoom = joinInfo.room
                    spice = cloudRoom.spiceLevel.ifBlank { SpiceLevel.FLIRTY.name }
                    partnerLocked = cloudRoom.status == "CONNECTED"
                    pendingName = cloudRoom.pendingPartnerName
                    isHostFlag = cloudRoom.creatorUid == uid && !joinInfo.isPendingHostApproval
                    approvalStatus = when {
                        cloudRoom.status == "CONNECTED" -> "CONNECTED"
                        joinInfo.isPendingHostApproval -> "PENDING_HOST_APPROVAL"
                        cloudRoom.status == "PENDING_APPROVAL" && isHostFlag -> "AWAITING_MY_APPROVAL"
                        else -> "WAITING"
                    }
                    partnerName = if (!isHostFlag) {
                        cloudRoom.creatorName
                    } else {
                        cloudRoom.partnerName
                    }
                    peerPubKey = if (!isHostFlag) {
                        cloudRoom.creatorPublicKey
                    } else {
                        cloudRoom.partnerPublicKey
                    }
                }

                val derivedSharedSecret = if (peerPubKey.isNotBlank()) {
                    withContext(Dispatchers.Default) {
                        CoupleEcdhEngine.deriveSharedSecretB64(
                            myPrivateKeyB64 = localKeyPair.privateKeyB64,
                            peerPublicKeyB64 = peerPubKey,
                            coupleCode = cleanCode,
                            myPublicKeyB64 = localKeyPair.publicKeyB64
                        ).orEmpty()
                    }
                } else {
                    ""
                }

                val genesis = withContext(Dispatchers.Default) {
                    CoupleBlockchain.createDeterministicGenesisBlock(cleanCode)
                }
                val resolvedMinProtocol = if (derivedSharedSecret.isNotBlank()) {
                    CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3
                } else {
                    CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2
                }

                coupleRoomMutex.withLock {
                    val existing = repository.getCoupleRoom(cleanCode)
                    if (existing != null) {
                        val ledger = CoupleBlockchain.ledgerFromJson(existing.blockchainLedgerJson).ifEmpty { listOf(genesis) }
                        repository.saveCoupleRoom(
                            existing.copy(
                                myName = cleanName,
                                partnerName = partnerName.ifBlank { existing.partnerName },
                                spiceLevel = spice,
                                isPassAndPlay = if (isOfflinePassAndPlay) true else existing.isPassAndPlay,
                                isBonded = true,
                                partnerLocked = partnerLocked || existing.partnerLocked,
                                isHost = isHostFlag,
                                approvalStatus = approvalStatus,
                                pendingPartnerName = pendingName,
                                ecdhPublicKeyB64 = localKeyPair.publicKeyB64,
                                ecdhPrivateKeyB64 = localKeyPair.privateKeyB64,
                                peerPublicKeyB64 = peerPubKey.ifBlank { existing.peerPublicKeyB64 },
                                ecdhSharedSecretB64 = derivedSharedSecret.ifBlank { existing.ecdhSharedSecretB64 },
                                minProtocolVersion = maxOf(existing.minProtocolVersion, resolvedMinProtocol),
                                blockchainLedgerJson = CoupleBlockchain.ledgerToJson(ledger),
                                lastBlockHash = ledger.last().blockHash,
                                blockHeight = maxOf(existing.blockHeight, ledger.size)
                            )
                        )
                    } else {
                        val ledger = listOf(genesis)
                        val initialTtt = TicTacToeSyncState(mySymbol = if (isHostFlag) "X" else "O")
                        repository.saveCoupleRoom(
                            CoupleRoomRecord(
                                coupleCode = cleanCode,
                                myName = cleanName,
                                partnerName = partnerName,
                                spiceLevel = spice,
                                isPassAndPlay = isOfflinePassAndPlay,
                                isBonded = true,
                                partnerLocked = partnerLocked,
                                isHost = isHostFlag,
                                approvalStatus = approvalStatus,
                                pendingPartnerName = pendingName,
                                ecdhPublicKeyB64 = localKeyPair.publicKeyB64,
                                ecdhPrivateKeyB64 = localKeyPair.privateKeyB64,
                                peerPublicKeyB64 = peerPubKey,
                                ecdhSharedSecretB64 = derivedSharedSecret,
                                chainSchemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
                                minProtocolVersion = resolvedMinProtocol,
                                ticTacToeStateJson = CoupleTurnCodec.ticTacToeToJsonString(initialTtt),
                                blockchainLedgerJson = CoupleBlockchain.ledgerToJson(ledger),
                                lastBlockHash = genesis.blockHash,
                                blockHeight = 1,
                                firewallPassedCount = 1,
                                firewallBlockedCount = 0,
                                lastFirewallEvent = "Awaiting Host secondary confirmation"
                            )
                        )
                    }
                }

                val currentProfile = userProfile.value ?: UserProfile(id = 1, name = cleanName, isOnboarded = true)
                repository.saveProfile(
                    currentProfile.copy(
                        name = currentProfile.name.ifBlank { cleanName },
                        activeCoupleCode = cleanCode
                    )
                )
                _activeCoupleCode.value = cleanCode

                when {
                    isOfflinePassAndPlay -> showFeedback("Pass & Play room #$cleanCode ready ♥")
                    approvalStatus == "PENDING_HOST_APPROVAL" -> {
                        _cloudSyncStatus.value = "PENDING_APPROVAL"
                        showFeedback("Request sent to ${partnerName.ifBlank { "Host" }} — waiting for Allow 🔒")
                    }
                    approvalStatus == "CONNECTED" -> showFeedback("Connected live to Room #$cleanCode ♥")
                    else -> showFeedback("Room #$cleanCode ready ✨")
                }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    fun createOrJoinCoupleRoom(myName: String, rawCode: String) {
        if (rawCode.isBlank()) {
            createUniqueCoupleRoom(myName)
        } else {
            requestJoinCoupleRoom(myName, rawCode)
        }
    }

    /**
     * Host-only secondary confirmation: allows the pending partner into the private room.
     */
    fun approvePendingPartner(coupleCode: String) {
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val cloudRepo = getCloudRepoOrNull() ?: return@launch
                val result = cloudRepo.approvePendingPartner(coupleCode)
                if (result.isFailure) {
                    showFeedback(result.exceptionOrNull()?.message ?: "Could not approve partner")
                    return@launch
                }
                val approved = result.getOrThrow()
                coupleRoomMutex.withLock {
                    val fresh = repository.getCoupleRoom(coupleCode)
                    if (fresh != null) {
                        val partnerPub = approved.partnerPublicKey.ifBlank { fresh.peerPublicKeyB64 }
                        val sharedSecret = if (partnerPub.isNotBlank() && fresh.ecdhPrivateKeyB64.isNotBlank()) {
                            withContext(Dispatchers.Default) {
                                CoupleEcdhEngine.deriveSharedSecretB64(
                                    myPrivateKeyB64 = fresh.ecdhPrivateKeyB64,
                                    peerPublicKeyB64 = partnerPub,
                                    coupleCode = coupleCode,
                                    myPublicKeyB64 = fresh.ecdhPublicKeyB64
                                ).orEmpty()
                            }
                        } else {
                            fresh.ecdhSharedSecretB64
                        }
                        val minProto = maxOf(
                            fresh.minProtocolVersion,
                            if (sharedSecret.isNotBlank()) CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3 else fresh.minProtocolVersion
                        )
                        repository.saveCoupleRoom(
                            fresh.copy(
                                partnerName = approved.partnerName.ifBlank { fresh.partnerName },
                                partnerLocked = true,
                                approvalStatus = "CONNECTED",
                                pendingPartnerName = "",
                                peerPublicKeyB64 = partnerPub,
                                ecdhSharedSecretB64 = sharedSecret,
                                minProtocolVersion = minProto
                            )
                        )
                    }
                }
                _cloudSyncStatus.value = "LIVE"
                rebroadcastedCodes.add(coupleCode)
                rebroadcastPendingLocalMoves(cloudRepo, coupleCode)
                showFeedback("Allowed ${approved.partnerName.ifBlank { "Partner" }} into Room #$coupleCode ♥")
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    /**
     * Host declines a pending join request OR the waiting partner cancels their own request.
     */
    fun declinePendingPartner(coupleCode: String) {
        viewModelScope.launch {
            _isCloudBusy.value = true
            try {
                val local = repository.getCoupleRoom(coupleCode)
                getCloudRepoOrNull()?.declineOrCancelPendingPartner(coupleCode)

                if (local != null && !local.isHost) {
                    // Joining partner cancelled their pending request
                    coupleRoomMutex.withLock {
                        repository.deleteCoupleRoom(coupleCode)
                    }
                    if (_activeCoupleCode.value == coupleCode) {
                        _activeCoupleCode.value = null
                    }
                    userProfile.value?.let { profile ->
                        if (profile.activeCoupleCode == coupleCode) {
                            repository.saveProfile(profile.copy(activeCoupleCode = ""))
                        }
                    }
                    _cloudSyncStatus.value = "WAITING"
                    showFeedback("Join request cancelled")
                } else {
                    // Host declined incoming join request
                    coupleRoomMutex.withLock {
                        val fresh = repository.getCoupleRoom(coupleCode)
                        if (fresh != null) {
                            repository.saveCoupleRoom(
                                fresh.copy(
                                    approvalStatus = "WAITING",
                                    pendingPartnerName = ""
                                )
                            )
                        }
                    }
                    _cloudSyncStatus.value = "WAITING"
                    showFeedback("Declined request — Room #$coupleCode stays private 🔒")
                }
            } finally {
                _isCloudBusy.value = false
            }
        }
    }

    fun selectCoupleRoom(code: String) {
        _activeCoupleCode.value = code
        viewModelScope.launch {
            userProfile.value?.let { profile ->
                repository.saveProfile(profile.copy(activeCoupleCode = code))
            }
        }
    }

    fun deleteCoupleRoom(code: String) {
        viewModelScope.launch {
            if (_cloudAuthUid.value != null) {
                getCloudRepoOrNull()?.unpairCloudRoom(code)
            }
            coupleRoomMutex.withLock {
                repository.deleteCoupleRoom(code)
            }
            rebroadcastedCodes.remove(code)
            if (_activeCoupleCode.value == code) {
                _activeCoupleCode.value = null
            }
            userProfile.value?.let { profile ->
                if (profile.activeCoupleCode == code) {
                    repository.saveProfile(profile.copy(activeCoupleCode = ""))
                }
            }
            showFeedback("Couple room ended")
        }
    }

    fun commitOutgoingCoupleBlock(block: CoupleBlock) {
        val code = activeCoupleRoom.value?.coupleCode ?: return
        viewModelScope.launch {
            commitOutgoingCoupleBlock(code, block)
        }
    }

    private suspend fun commitOutgoingCoupleBlock(
        coupleCode: String,
        block: CoupleBlock,
        newCloudBlockCount: Long? = null,
        newSchemaVersion: Int? = null,
        migrationAnchorHash: String? = null,
        migrationAnchorIndex: Int? = null
    ) {
        sentBlockHashes.add(block.blockHash)
        coupleRoomMutex.withLock {
            if (!LocalVaultCrypto.canSafelyWrite()) return
            val freshRoom = repository.getCoupleRoom(coupleCode) ?: return
            val currentLedger = CoupleBlockchain.ledgerFromJson(freshRoom.blockchainLedgerJson)
                .ifEmpty { listOf(CoupleBlockchain.createDeterministicGenesisBlock(freshRoom.coupleCode)) }
            val combined = if (currentLedger.any { it.blockHash == block.blockHash }) {
                currentLedger
            } else {
                currentLedger + block
            }
            val pruned = CoupleBlockchain.pruneLedgerWindow(
                blocks = combined,
                existingPrunedCount = freshRoom.prunedBlockCount,
                existingPrunedAnchorHash = freshRoom.prunedAnchorHash
            )
            val seenTurnIds = CoupleTurnCodec.stringSetFromJson(freshRoom.seenClientTurnIdsJson).apply {
                if (block.clientTurnId.isNotBlank()) add(block.clientTurnId)
            }
            val seenHashes = CoupleTurnCodec.stringSetFromJson(freshRoom.seenReplayHashesJson).apply {
                add(block.blockHash)
            }
            val resolvedHeight = newCloudBlockCount?.toInt()
                ?: maxOf(freshRoom.blockHeight, block.index + 1, pruned.retainedBlocks.size + pruned.newlyPrunedCount)
            repository.saveCoupleRoom(
                freshRoom.copy(
                    blockchainLedgerJson = CoupleBlockchain.ledgerToJson(pruned.retainedBlocks),
                    lastBlockHash = block.blockHash,
                    blockHeight = resolvedHeight,
                    chainSchemaVersion = newSchemaVersion?.coerceAtLeast(freshRoom.chainSchemaVersion) ?: freshRoom.chainSchemaVersion,
                    migrationAnchorHash = migrationAnchorHash?.ifBlank { freshRoom.migrationAnchorHash } ?: freshRoom.migrationAnchorHash,
                    migrationAnchorIndex = migrationAnchorIndex?.takeIf { it > 0 } ?: freshRoom.migrationAnchorIndex,
                    prunedBlockCount = pruned.newlyPrunedCount,
                    prunedAnchorHash = pruned.prunedAnchorHash,
                    lastConfirmedCloudIndex = maxOf(freshRoom.lastConfirmedCloudIndex, block.index),
                    seenClientTurnIdsJson = CoupleTurnCodec.stringSetToJson(seenTurnIds, maxSize = 512),
                    seenReplayHashesJson = CoupleTurnCodec.stringSetToJson(seenHashes, maxSize = CoupleBlockchain.MAX_LOCAL_LEDGER_BLOCKS),
                    firewallPassedCount = freshRoom.firewallPassedCount + 1,
                    lastFirewallEvent = "Protected move #${block.index} verified"
                )
            )
        }
    }

    private suspend fun enqueueAndDrainTurnIntent(
        coupleCode: String,
        intent: CoupleActivityIntent,
        triggerCloudDrain: Boolean = true
    ) {
        if (!LocalVaultCrypto.canSafelyWrite()) return
        try {
            repository.enqueuePendingCoupleTurn(coupleCode, intent)
        } catch (_: LocalVaultUnavailableException) {
            return
        }
        if (triggerCloudDrain) {
            drainPendingTurnsForRoom(coupleCode)
        }
    }

    /**
     * Drains all locally persisted, encrypted [com.example.data.PendingCoupleTurn] entries for [coupleCode]
     * using atomic Firestore transactions. On any [BlockPublishOutcome.HeadConflict], updates the local head
     * from Firestore and re-encrypts the activity intent with a brand-new 96-bit AES-GCM nonce & AAD.
     */
    private suspend fun drainPendingTurnsForRoom(coupleCode: String) {
        pendingDrainMutex.withLock {
            if (!LocalVaultCrypto.canSafelyWrite()) return
            val uid = _cloudAuthUid.value ?: return
            val cloudRepo = getCloudRepoOrNull() ?: return
            val initialRoom = repository.getCoupleRoom(coupleCode) ?: return
            if (initialRoom.isPassAndPlay || initialRoom.approvalStatus != "CONNECTED") return

            val pendingTurns = repository.getPendingTurnsForRoomOnce(coupleCode)
            if (pendingTurns.isEmpty()) return

            for (pending in pendingTurns) {
                if (_cloudAuthUid.value != uid) break
                val intentResult = repository.decryptPendingIntent(pending)
                if (intentResult.isFailure) {
                    repository.markPendingTurnStatus(
                        turn = pending,
                        status = "FAILED_PERMANENT",
                        retryCount = pending.retryCount + 1,
                        lastError = "Unable to decrypt queued intent"
                    )
                    continue
                }
                val intent = intentResult.getOrThrow()

                var attempt = 0
                var resolved = false
                while (attempt < 5 && !resolved && _cloudAuthUid.value == uid) {
                    attempt++
                    val currentRoom = repository.getCoupleRoom(coupleCode) ?: break
                    if (currentRoom.isPassAndPlay || currentRoom.approvalStatus != "CONNECTED") break

                    val prevHash = currentRoom.lastBlockHash.ifBlank {
                        CoupleBlockchain.createDeterministicGenesisBlock(coupleCode).blockHash
                    }
                    val nextIndex = currentRoom.blockHeight.coerceAtLeast(1)

                    // Always generate a fresh 96-bit AES-GCM nonce and fresh AAD for every rebase/send attempt
                    val (freshPacket, freshBlock) = withContext(Dispatchers.Default) {
                        CoupleTurnCodec.sealIntentWithFreshNonce(
                            coupleCode = coupleCode,
                            intent = intent,
                            previousHash = prevHash,
                            blockIndex = nextIndex,
                            ecdhSharedSecretB64 = currentRoom.ecdhSharedSecretB64
                        )
                    }
                    sentBlockHashes.add(freshBlock.blockHash)

                    val outcome = cloudRepo.publishEncryptedBlockAtomic(
                        coupleCode = coupleCode,
                        intent = intent,
                        block = freshBlock,
                        encryptedPacket = freshPacket,
                        isRetry = pending.retryCount > 0 || attempt > 1
                    )
                    when (outcome) {
                        is BlockPublishOutcome.Committed -> {
                            commitOutgoingCoupleBlock(
                                coupleCode = coupleCode,
                                block = outcome.block,
                                newCloudBlockCount = outcome.newCloudBlockCount,
                                newSchemaVersion = outcome.newSchemaVersion,
                                migrationAnchorHash = outcome.migrationAnchorHash,
                                migrationAnchorIndex = outcome.migrationAnchorIndex
                            )
                            repository.removePendingTurn(intent.clientTurnId)
                            resolved = true
                        }
                        is BlockPublishOutcome.AlreadyCommittedIdempotent -> {
                            coupleRoomMutex.withLock {
                                repository.getCoupleRoom(coupleCode)?.let { r ->
                                    val seenTurnIds = CoupleTurnCodec.stringSetFromJson(r.seenClientTurnIdsJson)
                                    seenTurnIds.add(intent.clientTurnId)
                                    repository.saveCoupleRoom(
                                        r.copy(
                                            lastConfirmedCloudIndex = maxOf(r.lastConfirmedCloudIndex, outcome.blockIndex.toInt()),
                                            seenClientTurnIdsJson = CoupleTurnCodec.stringSetToJson(seenTurnIds, maxSize = 512)
                                        )
                                    )
                                }
                            }
                            repository.removePendingTurn(intent.clientTurnId)
                            resolved = true
                        }
                        is BlockPublishOutcome.HeadConflict -> {
                            // Update local authoritative cloud head and loop to re-seal with a brand-new nonce
                            coupleRoomMutex.withLock {
                                repository.getCoupleRoom(coupleCode)?.let { r ->
                                    repository.saveCoupleRoom(
                                        r.copy(
                                            lastBlockHash = outcome.currentCloudLatestHash.ifBlank { r.lastBlockHash },
                                            blockHeight = outcome.currentCloudBlockCount.toInt().coerceAtLeast(1),
                                            chainSchemaVersion = maxOf(r.chainSchemaVersion, outcome.currentSchemaVersion),
                                            migrationAnchorHash = outcome.migrationAnchorHash.ifBlank { r.migrationAnchorHash },
                                            migrationAnchorIndex = outcome.migrationAnchorIndex.takeIf { it > 0 } ?: r.migrationAnchorIndex
                                        )
                                    )
                                }
                            }
                        }
                        is BlockPublishOutcome.RoomRetired -> {
                            repository.clearPendingTurnsForRoom(coupleCode)
                            return
                        }
                        is BlockPublishOutcome.Failed -> {
                            repository.markPendingTurnStatus(
                                turn = pending,
                                status = "PENDING",
                                retryCount = pending.retryCount + 1,
                                lastError = outcome.error.message ?: "Transient network failure"
                            )
                            return
                        }
                    }
                }
                if (!resolved) {
                    repository.markPendingTurnStatus(
                        turn = pending,
                        status = "PENDING",
                        retryCount = pending.retryCount + 1,
                        lastError = "Head conflict persisted after 5 rebase attempts"
                    )
                }
            }
        }
    }

    /**
     * Persists an outgoing couple move encrypted in the local durable pending queue BEFORE
     * transmitting atomically to Firestore, ensuring offline resilience and fresh-nonce rebasing.
     */
    fun sendLiveEncryptedTurn(packet: String, block: CoupleBlock) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            if (!LocalVaultCrypto.canSafelyWrite()) {
                showFeedback("Private vault is locked — cannot save or send move")
                return@launch
            }
            if (room.isPassAndPlay) {
                commitOutgoingCoupleBlock(room.coupleCode, block)
                return@launch
            }
            val intentResult = withContext(Dispatchers.Default) {
                CoupleTurnCodec.extractActivityIntent(
                    rawPacket = packet,
                    coupleCode = room.coupleCode,
                    ecdhSharedSecretB64 = room.ecdhSharedSecretB64
                )
            }
            intentResult.onSuccess { intent ->
                enqueueAndDrainTurnIntent(room.coupleCode, intent, triggerCloudDrain = true)
            }.onFailure {
                // Fallback if packet could not be extracted: keep local block commit without crashing
                commitOutgoingCoupleBlock(room.coupleCode, block)
            }
        }
    }

    fun updateCoupleSpiceLevel(spiceLevel: SpiceLevel) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(fresh.copy(spiceLevel = spiceLevel.name))
            }
            if (_cloudAuthUid.value != null && !room.isPassAndPlay) {
                getCloudRepoOrNull()?.updateCloudSpiceLevel(room.coupleCode, spiceLevel)
            }
            showFeedback("Vibe set to ${spiceLevel.emoji} ${spiceLevel.title}")
        }
    }

    fun toggleCouplePassAndPlay(enabled: Boolean) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(fresh.copy(isPassAndPlay = enabled))
            }
            showFeedback(if (enabled) "Pass & Play enabled (1 phone)" else "Live 2-phone mode active ✨")
        }
    }

    fun saveCoupleDoodle(strokesJson: String, prompt: String) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(fresh.copy(doodleStrokesJson = strokesJson, doodlePrompt = prompt))
            }
        }
    }

    fun saveCoupleQuizState(quizStateJson: String, bumpMatch: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        quizStateJson = quizStateJson,
                        matchCount = fresh.matchCount + if (bumpMatch) 1 else 0,
                        streakCount = fresh.streakCount + if (bumpMatch) 1 else 0
                    )
                )
            }
        }
    }

    fun saveCoupleWyrState(wyrStateJson: String, bumpMatch: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        wyrStateJson = wyrStateJson,
                        matchCount = fresh.matchCount + if (bumpMatch) 1 else 0,
                        streakCount = fresh.streakCount + if (bumpMatch) 1 else 0
                    )
                )
            }
        }
    }

    fun saveCoupleTicTacToeState(tttStateJson: String, bumpWin: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        ticTacToeStateJson = tttStateJson,
                        streakCount = fresh.streakCount + if (bumpWin) 1 else 0
                    )
                )
            }
        }
    }

    fun saveCoupleDareState(dareStateJson: String, bumpStreak: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        dareStateJson = dareStateJson,
                        streakCount = fresh.streakCount + if (bumpStreak) 1 else 0
                    )
                )
            }
        }
    }

    fun saveCoupleWavelengthState(wavelengthStateJson: String, bumpMatch: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        wavelengthStateJson = wavelengthStateJson,
                        matchCount = fresh.matchCount + if (bumpMatch) 1 else 0,
                        streakCount = fresh.streakCount + if (bumpMatch) 1 else 0
                    )
                )
            }
        }
    }

    fun saveCoupleWhisperState(whisperStateJson: String, bumpMatch: Boolean = false) {
        val room = activeCoupleRoom.value ?: return
        viewModelScope.launch {
            coupleRoomMutex.withLock {
                val fresh = repository.getCoupleRoom(room.coupleCode) ?: room
                repository.saveCoupleRoom(
                    fresh.copy(
                        whisperStateJson = whisperStateJson,
                        matchCount = fresh.matchCount + if (bumpMatch) 1 else 0,
                        streakCount = fresh.streakCount + if (bumpMatch) 1 else 0
                    )
                )
            }
        }
    }

    fun importCoupleTurnPacket(rawPacket: String, isAutoSync: Boolean = false) {
        val room = activeCoupleRoom.value
        if (room == null) {
            if (!isAutoSync) showFeedback("Join or create a Couple Room first")
            return
        }
        viewModelScope.launch {
            applyTurnPacketToRoom(room, rawPacket, isAutoSync, fromCloudLive = false)
        }
    }

    private suspend fun applyTurnPacketToRoom(
        room: CoupleRoomRecord,
        rawPacket: String,
        isAutoSync: Boolean,
        fromCloudLive: Boolean = false
    ) {
        coupleRoomMutex.withLock {
            if (!LocalVaultCrypto.canSafelyWrite()) return@withLock
            val freshRoom = repository.getCoupleRoom(room.coupleCode) ?: room
            val existingLedger = CoupleBlockchain.ledgerFromJson(freshRoom.blockchainLedgerJson)
                .ifEmpty { listOf(CoupleBlockchain.createDeterministicGenesisBlock(freshRoom.coupleCode)) }

            val seenTurnIds = CoupleTurnCodec.stringSetFromJson(freshRoom.seenClientTurnIdsJson)
            val seenHashes = CoupleTurnCodec.stringSetFromJson(freshRoom.seenReplayHashesJson)

            // Skip quietly if this block hash is already in our ledger or replay ring buffer
            if (isAutoSync) {
                val peekedHash = CoupleTurnCodec.peekBlockHash(rawPacket)
                if (peekedHash == null ||
                    peekedHash in seenHashes ||
                    existingLedger.any { it.blockHash == peekedHash }
                ) {
                    return@withLock
                }
            }

            val lockedPartner = if (freshRoom.partnerLocked && freshRoom.partnerName.isNotBlank()) freshRoom.partnerName else ""
            val effectiveMinProtocol = maxOf(
                freshRoom.minProtocolVersion,
                if (freshRoom.ecdhSharedSecretB64.isNotBlank()) CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3 else CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2
            )
            val minAcceptedIndex = if (fromCloudLive && freshRoom.lastConfirmedCloudIndex > 0) {
                freshRoom.lastConfirmedCloudIndex + 1
            } else {
                0
            }

            val decoded = withContext(Dispatchers.Default) {
                CoupleTurnCodec.decodePacket(
                    rawInput = rawPacket,
                    expectedCoupleCode = freshRoom.coupleCode,
                    existingLedger = existingLedger,
                    lockedPartnerName = lockedPartner,
                    myName = freshRoom.myName,
                    ecdhSharedSecretB64 = freshRoom.ecdhSharedSecretB64,
                    minProtocolVersion = effectiveMinProtocol,
                    minAcceptedBlockIndex = minAcceptedIndex,
                    seenClientTurnIds = seenTurnIds,
                    seenReplayHashes = seenHashes
                )
            }

            decoded.onFailure { err ->
                // Always silently record blocked threats in firewall audit counters
                val reason = err.message ?: "Blocked invalid packet"
                repository.saveCoupleRoom(
                    freshRoom.copy(
                        firewallBlockedCount = freshRoom.firewallBlockedCount + 1,
                        lastFirewallEvent = reason
                    )
                )
                if (!isAutoSync && !fromCloudLive) {
                    showFeedback("Could not verify partner move")
                }
            }.onSuccess { packet ->
                // Never apply our own outgoing block as our partner's turn
                if ((isAutoSync || fromCloudLive) && packet.senderName.equals(freshRoom.myName.trim(), ignoreCase = true)) {
                    return@onSuccess
                }

                val combined = (existingLedger + packet.block).distinctBy { it.blockHash }
                val pruned = CoupleBlockchain.pruneLedgerWindow(
                    blocks = combined,
                    existingPrunedCount = freshRoom.prunedBlockCount,
                    existingPrunedAnchorHash = freshRoom.prunedAnchorHash
                )
                if (packet.block.clientTurnId.isNotBlank()) {
                    seenTurnIds.add(packet.block.clientTurnId)
                }
                seenHashes.add(packet.block.blockHash)

                val newLastHash = pruned.retainedBlocks.last().blockHash
                val newHeight = maxOf(
                    freshRoom.blockHeight,
                    packet.block.index + 1,
                    pruned.retainedBlocks.size + pruned.newlyPrunedCount
                )
                val newLastConfirmedIdx = maxOf(freshRoom.lastConfirmedCloudIndex, packet.block.index)
                val newPassed = freshRoom.firewallPassedCount + 1
                val auditMsg = "Verified move #${packet.block.index}"

                val baseUpdatedRoom = freshRoom.copy(
                    blockchainLedgerJson = CoupleBlockchain.ledgerToJson(pruned.retainedBlocks),
                    lastBlockHash = newLastHash,
                    blockHeight = newHeight,
                    prunedBlockCount = pruned.newlyPrunedCount,
                    prunedAnchorHash = pruned.prunedAnchorHash,
                    lastConfirmedCloudIndex = newLastConfirmedIdx,
                    seenClientTurnIdsJson = CoupleTurnCodec.stringSetToJson(seenTurnIds, maxSize = 512),
                    seenReplayHashesJson = CoupleTurnCodec.stringSetToJson(seenHashes, maxSize = CoupleBlockchain.MAX_LOCAL_LEDGER_BLOCKS),
                    firewallPassedCount = newPassed,
                    lastFirewallEvent = auditMsg
                )

                when (packet) {
                    is DecodedTurnPacket.BondHandshake -> {
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                spiceLevel = packet.spiceLevel.name
                            )
                        )
                        showFeedback("Connected live with ${packet.senderName} ♥")
                    }
                    is DecodedTurnPacket.UnpairNotice -> {
                        repository.deleteCoupleRoom(freshRoom.coupleCode)
                        if (_activeCoupleCode.value == freshRoom.coupleCode) {
                            _activeCoupleCode.value = null
                        }
                        userProfile.value?.let { profile ->
                            if (profile.activeCoupleCode == freshRoom.coupleCode) {
                                repository.saveProfile(profile.copy(activeCoupleCode = ""))
                            }
                        }
                        showFeedback("${packet.senderName} ended the couple room")
                    }
                    is DecodedTurnPacket.QuizTurn -> {
                        val current = CoupleTurnCodec.roundStateFromJson(freshRoom.quizStateJson)
                        if (packet.roundIndex < current.roundIndex) {
                            repository.saveCoupleRoom(baseUpdatedRoom)
                            return@onSuccess
                        }
                        val updated = if (current.roundIndex == packet.roundIndex) {
                            current.copy(partnerChoice = packet.choiceIndex, partnerName = packet.senderName)
                        } else {
                            RoundSyncState(
                                roundIndex = packet.roundIndex,
                                myChoice = null,
                                partnerChoice = packet.choiceIndex,
                                partnerName = packet.senderName,
                                history = current.history
                            )
                        }
                        val newlyRevealed = current.partnerChoice == null &&
                            updated.myChoice != null &&
                            updated.partnerChoice != null
                        val isMatch = newlyRevealed && updated.myChoice == updated.partnerChoice
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                spiceLevel = packet.spiceLevel.name,
                                quizStateJson = CoupleTurnCodec.roundStateToJson(updated),
                                matchCount = freshRoom.matchCount + if (isMatch) 1 else 0,
                                streakCount = freshRoom.streakCount + if (isMatch) 1 else 0
                            )
                        )
                        showFeedback("${packet.senderName} answered Couple Quiz 💋")
                    }
                    is DecodedTurnPacket.WyrTurn -> {
                        val current = CoupleTurnCodec.roundStateFromJson(freshRoom.wyrStateJson)
                        if (packet.roundIndex < current.roundIndex) {
                            repository.saveCoupleRoom(baseUpdatedRoom)
                            return@onSuccess
                        }
                        val updated = if (current.roundIndex == packet.roundIndex) {
                            current.copy(partnerChoice = packet.choiceIndex, partnerName = packet.senderName)
                        } else {
                            RoundSyncState(
                                roundIndex = packet.roundIndex,
                                myChoice = null,
                                partnerChoice = packet.choiceIndex,
                                partnerName = packet.senderName,
                                history = current.history
                            )
                        }
                        val newlyRevealed = current.partnerChoice == null &&
                            updated.myChoice != null &&
                            updated.partnerChoice != null
                        val isMatch = newlyRevealed && updated.myChoice == updated.partnerChoice
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                spiceLevel = packet.spiceLevel.name,
                                wyrStateJson = CoupleTurnCodec.roundStateToJson(updated),
                                matchCount = freshRoom.matchCount + if (isMatch) 1 else 0,
                                streakCount = freshRoom.streakCount + if (isMatch) 1 else 0
                            )
                        )
                        showFeedback("${packet.senderName} picked Would You Rather 🔥")
                    }
                    is DecodedTurnPacket.DoodleTurn -> {
                        val existing = CoupleTurnCodec.strokesFromJsonString(freshRoom.doodleStrokesJson)
                        val merged = (existing + packet.strokes).distinct().takeLast(120)
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                doodlePrompt = packet.prompt.ifBlank { freshRoom.doodlePrompt },
                                doodleStrokesJson = CoupleTurnCodec.strokesToJsonString(merged)
                            )
                        )
                        if (!fromCloudLive) {
                            showFeedback("New doodle from ${packet.senderName} 🎨")
                        }
                    }
                    is DecodedTurnPacket.TicTacToeTurn -> {
                        val flippedSymbol = if (packet.state.mySymbol == "X") "O" else "X"
                        val synced = packet.state.copy(mySymbol = flippedSymbol)
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                ticTacToeStateJson = CoupleTurnCodec.ticTacToeToJsonString(synced)
                            )
                        )
                        showFeedback("${packet.senderName} played Tic-Tac-Toe ⚡")
                    }
                    is DecodedTurnPacket.DareTurn -> {
                        val prev = CoupleActivitiesDeck.dareStateFromJson(freshRoom.dareStateJson)
                        val newlyCompleted = packet.state.status == "COMPLETED" && prev.status != "COMPLETED"
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                dareStateJson = CoupleActivitiesDeck.dareStateToJson(packet.state),
                                streakCount = freshRoom.streakCount + if (newlyCompleted) 1 else 0
                            )
                        )
                        val verb = if (packet.state.status == "COMPLETED") "completed the challenge" else "sent a Truth or Dare"
                        showFeedback("${packet.senderName} $verb 🎲")
                    }
                    is DecodedTurnPacket.WavelengthTurn -> {
                        val prev = CoupleActivitiesDeck.wavelengthStateFromJson(freshRoom.wavelengthStateJson)
                        val newlyMatched = packet.state.phase == "REVEAL" &&
                            prev.phase != "REVEAL" &&
                            packet.state.lastScore >= 60
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                wavelengthStateJson = CoupleActivitiesDeck.wavelengthStateToJson(packet.state),
                                matchCount = freshRoom.matchCount + if (newlyMatched) 1 else 0,
                                streakCount = freshRoom.streakCount + if (newlyMatched) 1 else 0
                            )
                        )
                        showFeedback("${packet.senderName} updated Heart Wavelength 🌊")
                    }
                    is DecodedTurnPacket.WhisperTurn -> {
                        val current = CoupleActivitiesDeck.dateWhisperStateFromJson(freshRoom.whisperStateJson)
                        val updatedPartnerLikes = if (packet.likedDateId.isNotBlank()) {
                            current.partnerLikedIds + packet.likedDateId
                        } else {
                            current.partnerLikedIds
                        }
                        val isNewDateMatch = packet.likedDateId.isNotBlank() &&
                            packet.likedDateId in current.myLikedIds &&
                            packet.likedDateId !in current.partnerLikedIds
                        val incomingNote = packet.whisperNote
                        val updatedWhispers = if (incomingNote != null && current.whispers.none { it.id == incomingNote.id }) {
                            (current.whispers + incomingNote.copy(isRevealed = false)).takeLast(24)
                        } else {
                            current.whispers
                        }
                        val merged = current.copy(
                            partnerLikedIds = updatedPartnerLikes,
                            whispers = updatedWhispers
                        )
                        repository.saveCoupleRoom(
                            baseUpdatedRoom.copy(
                                partnerName = packet.senderName,
                                partnerLocked = true,
                                isBonded = true,
                                whisperStateJson = CoupleActivitiesDeck.dateWhisperStateToJson(merged),
                                matchCount = freshRoom.matchCount + if (isNewDateMatch) 1 else 0,
                                streakCount = freshRoom.streakCount + if (isNewDateMatch) 1 else 0
                            )
                        )
                        when {
                            isNewDateMatch -> showFeedback("It's a Date Night Match with ${packet.senderName} 💞")
                            incomingNote != null -> showFeedback("New Secret Whisper from ${packet.senderName} 💌")
                            else -> showFeedback("${packet.senderName} voted on a Date Night ✨")
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        detachCloudListeners()
        super.onCleared()
    }
}

enum class NavScreen(val label: String, val emoji: String) {
    CYCLE("Today", "🌸"),
    PERIODS("Calendar", "🩸"),
    CARE("Care", "🌿"),
    NOTES("Journal", "📝"),
    PLAY("Play", "🎮")
}

enum class CareTab(val label: String, val emoji: String) {
    WATER("Water", "💧"),
    MEDS("Meds", "💊"),
    CHECKLIST("Wishlist", "🛍️")
}
