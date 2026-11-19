package com.example.data

import com.example.security.BackendSecurityFirewall
import kotlinx.coroutines.flow.Flow

class PhasesRepository(private val database: AppDatabase) {
    val userProfile: Flow<UserProfile?> = database.userProfileDao().getUserProfile()
    val periodLogs: Flow<List<PeriodLog>> = database.periodLogDao().getAllPeriods()
    val shopItems: Flow<List<ShopItem>> = database.shopItemDao().getAllItems()
    val taskCategories: Flow<List<TaskCategory>> = database.taskCategoryDao().getAllCategories()
    val userNotes: Flow<List<UserNote>> = database.userNoteDao().getAllNotes()
    val medications: Flow<List<Medication>> = database.medicationDao().getAllMedications()
    val medicineReminders: Flow<List<MedicineReminder>> = medications
    val dailyCycleLogs: Flow<List<DailyCycleLog>> = database.dailyCycleLogDao().getAllLogs()
    val gameScores: Flow<List<GameScoreRecord>> = database.gameScoreDao().getAllScores()
    val coupleRooms: Flow<List<CoupleRoomRecord>> = database.coupleRoomDao().getAllRooms()

    fun getDailyCycleLog(dateString: String): Flow<DailyCycleLog?> {
        return database.dailyCycleLogDao().getLogForDate(BackendSecurityFirewall.sanitizeField(dateString, 10))
    }

    suspend fun saveDailyCycleLog(log: DailyCycleLog): Long {
        return database.dailyCycleLogDao().insertOrUpdate(BackendSecurityFirewall.guardDailyCycleLog(log))
    }

    suspend fun deleteDailyCycleLog(id: Long) {
        database.dailyCycleLogDao().deleteById(id)
    }

    suspend fun deleteDailyCycleLogByDate(dateString: String) {
        database.dailyCycleLogDao().deleteByDate(dateString)
    }

    suspend fun clearDailyCycleLogs() {
        database.dailyCycleLogDao().clearAll()
    }

    fun getWaterLogForDate(dateString: String): Flow<WaterLog?> {
        return database.waterLogDao().getWaterLog(dateString)
    }

    suspend fun saveProfile(profile: UserProfile) {
        database.userProfileDao().insertOrUpdate(BackendSecurityFirewall.guardUserProfile(profile))
    }

    suspend fun updateShopPreferences(
        hideCompleted: Boolean,
        viewMode: String,
        sortMode: String
    ) {
        val current = database.userProfileDao().getProfileOnce() ?: return
        database.userProfileDao().insertOrUpdate(
            BackendSecurityFirewall.guardUserProfile(
                current.copy(
                    shopHideCompleted = hideCompleted,
                    shopViewMode = viewMode,
                    shopSortMode = sortMode
                )
            )
        )
    }

    suspend fun logPeriod(
        startDateMillis: Long,
        notes: String = "",
        durationDays: Int? = null,
        endDateMillis: Long? = null
    ): Long {
        return database.periodLogDao().insert(
            BackendSecurityFirewall.guardPeriodLog(
                PeriodLog(
                    startDateMillis = startDateMillis,
                    notes = notes,
                    durationDays = durationDays,
                    endDateMillis = endDateMillis
                )
            )
        )
    }

    suspend fun endPeriod(id: Long, endDateMillis: Long) {
        val existing = database.periodLogDao().getById(id) ?: return
        val calculatedDuration = kotlin.math.max(
            1,
            kotlin.math.round((endDateMillis - existing.startDateMillis).toDouble() / (24L * 60 * 60 * 1000)).toInt() + 1
        )
        database.periodLogDao().update(
            BackendSecurityFirewall.guardPeriodLog(
                existing.copy(
                    endDateMillis = endDateMillis,
                    durationDays = calculatedDuration
                )
            )
        )
    }

    suspend fun updatePeriod(period: PeriodLog) {
        database.periodLogDao().update(BackendSecurityFirewall.guardPeriodLog(period))
    }

    suspend fun deletePeriod(id: Long) {
        database.periodLogDao().deleteById(id)
    }

    // Categories
    suspend fun addCategory(displayName: String, emoji: String, colorHex: String = "#D4629A"): String {
        val id = "cat_" + System.currentTimeMillis()
        database.taskCategoryDao().insert(
            BackendSecurityFirewall.guardTaskCategory(
                TaskCategory(
                    id = id,
                    displayName = displayName.trim(),
                    emoji = emoji.trim(),
                    colorHex = colorHex,
                    orderIndex = database.taskCategoryDao().count(),
                    isDefault = false
                )
            )
        )
        return id
    }

    suspend fun updateCategory(category: TaskCategory) {
        database.taskCategoryDao().update(BackendSecurityFirewall.guardTaskCategory(category))
    }

    suspend fun deleteCategory(id: String) {
        database.taskCategoryDao().deleteById(id)
    }

    suspend fun checkAndSeedCategoriesIfEmpty() {
        val count = database.taskCategoryDao().count()
        if (count == 0) {
            val defaultCategories = listOf(
                TaskCategory(id = "menstrual", displayName = "Crimson & Comfort", emoji = "🌸", colorHex = "#D4629A", orderIndex = 0, isDefault = true),
                TaskCategory(id = "follicular", displayName = "Rising Energy", emoji = "🌱", colorHex = "#5E9C68", orderIndex = 1, isDefault = true),
                TaskCategory(id = "ovulation", displayName = "Vibrant & Glowing", emoji = "🌿", colorHex = "#8B62D6", orderIndex = 2, isDefault = true),
                TaskCategory(id = "luteal", displayName = "PMS & Cozy Care", emoji = "🌙", colorHex = "#7C3FA6", orderIndex = 3, isDefault = true),
                TaskCategory(id = "general", displayName = "Everyday Essentials", emoji = "🛍️", colorHex = "#3B82F6", orderIndex = 4, isDefault = true),
                TaskCategory(id = "wellness", displayName = "Self-Care & Mind", emoji = "✨", colorHex = "#E6723D", orderIndex = 5, isDefault = false)
            )
            database.taskCategoryDao().insertAll(defaultCategories)
        }
    }

    // Shop / Tasks
    suspend fun toggleShopItem(item: ShopItem) {
        database.shopItemDao().update(BackendSecurityFirewall.guardShopItem(item.copy(isDone = !item.isDone)))
    }

    suspend fun updateShopItem(item: ShopItem) {
        database.shopItemDao().update(BackendSecurityFirewall.guardShopItem(item))
    }

    suspend fun addShopItem(
        text: String,
        category: String = "general",
        isCustom: Boolean = true,
        notes: String = "",
        priority: String = "NORMAL"
    ): Long {
        val count = database.shopItemDao().count()
        return database.shopItemDao().insert(
            BackendSecurityFirewall.guardShopItem(
                ShopItem(
                    text = text.trim(),
                    phaseCategory = category,
                    isCustom = isCustom,
                    orderIndex = count,
                    notes = notes.trim(),
                    priority = priority
                )
            )
        )
    }

    suspend fun deleteShopItem(id: Long) {
        database.shopItemDao().deleteById(id)
    }

    suspend fun clearCompletedShopItems(items: List<ShopItem>) {
        items.filter { it.isDone }.forEach {
            database.shopItemDao().deleteById(it.id)
        }
    }

    suspend fun moveShopItem(item: ShopItem, allCategoryItems: List<ShopItem>, direction: Int) {
        val currentIndex = allCategoryItems.indexOfFirst { it.id == item.id }
        if (currentIndex == -1) return
        val targetIndex = currentIndex + direction
        if (targetIndex in allCategoryItems.indices) {
            val targetItem = allCategoryItems[targetIndex]
            val currentOrder = item.orderIndex
            val targetOrder = targetItem.orderIndex

            // Swap order indices
            val newCurrentOrder = if (currentOrder == targetOrder) {
                if (direction > 0) currentOrder + 1 else currentOrder - 1
            } else {
                targetOrder
            }
            database.shopItemDao().updateOrder(item.id, newCurrentOrder)
            database.shopItemDao().updateOrder(targetItem.id, currentOrder)
        }
    }

    suspend fun updateShopItemOrders(items: List<ShopItem>) {
        items.forEachIndexed { index, shopItem ->
            database.shopItemDao().updateOrder(shopItem.id, index)
        }
    }

    suspend fun resetShopItemsToDefaults() {
        database.shopItemDao().clearAll()
        val defaultItems = listOf(
            ShopItem(text = "Pads or tampons", phaseCategory = "menstrual", orderIndex = 0),
            ShopItem(text = "Heating pad or patch", phaseCategory = "menstrual", orderIndex = 1),
            ShopItem(text = "Pain relief (Ibuprofen/Paracetamol)", phaseCategory = "menstrual", orderIndex = 2),
            ShopItem(text = "Dark chocolate (70%+)", phaseCategory = "menstrual", orderIndex = 3),
            ShopItem(text = "Cozy warm socks", phaseCategory = "menstrual", orderIndex = 4),
            ShopItem(text = "Fresh leafy greens & berries", phaseCategory = "follicular", orderIndex = 5),
            ShopItem(text = "Probiotic yogurt or kefir", phaseCategory = "follicular", orderIndex = 6),
            ShopItem(text = "Pre-workout energy snacks", phaseCategory = "follicular", orderIndex = 7),
            ShopItem(text = "Extra water / electrolyte tablets", phaseCategory = "ovulation", orderIndex = 8),
            ShopItem(text = "Fiber-rich snacks & seeds", phaseCategory = "ovulation", orderIndex = 9),
            ShopItem(text = "High-protein meal prep", phaseCategory = "ovulation", orderIndex = 10),
            ShopItem(text = "Magnesium supplement", phaseCategory = "luteal", orderIndex = 11),
            ShopItem(text = "Chamomile or peppermint herbal tea", phaseCategory = "luteal", orderIndex = 12),
            ShopItem(text = "Comfort snacks & soothing soup", phaseCategory = "luteal", orderIndex = 13),
            ShopItem(text = "Epsom bath salts", phaseCategory = "luteal", orderIndex = 14)
        )
        database.shopItemDao().insertAll(defaultItems)
    }

    suspend fun checkAndSeedShopItemsIfEmpty() {
        val count = database.shopItemDao().count()
        if (count == 0) {
            resetShopItemsToDefaults()
        }
    }

    // Medications
    suspend fun addMedication(
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
    ): Long {
        return database.medicationDao().insert(
            BackendSecurityFirewall.guardMedication(
                Medication(
                    name = name.trim(),
                    dosage = dosage.trim(),
                    hour = hour,
                    minute = minute,
                    categoryEmoji = categoryEmoji,
                    daysOfWeek = daysOfWeek,
                    customSound = customSound,
                    customReminderMessage = customReminderMessage.trim(),
                    instructions = instructions.trim(),
                    colorHex = colorHex,
                    isEnabled = true
                )
            )
        )
    }

    suspend fun updateMedication(medication: Medication) {
        database.medicationDao().update(BackendSecurityFirewall.guardMedication(medication))
    }

    suspend fun deleteMedication(id: Long) {
        database.medicationDao().deleteById(id)
    }

    suspend fun toggleMedication(id: Long, isEnabled: Boolean) {
        val existing = database.medicationDao().getById(id)
        if (existing != null) {
            database.medicationDao().update(BackendSecurityFirewall.guardMedication(existing.copy(isEnabled = isEnabled)))
        }
    }

    suspend fun markMedicationTakenToday(id: Long, todayDateString: String, timeString: String = "") {
        val existing = database.medicationDao().getById(id)
        if (existing != null) {
            database.medicationDao().update(
                BackendSecurityFirewall.guardMedication(
                    existing.copy(
                        lastTakenDate = BackendSecurityFirewall.sanitizeField(todayDateString, 10),
                        lastTakenTime = BackendSecurityFirewall.sanitizeField(timeString, 16)
                    )
                )
            )
        }
    }

    // Backward compatibility bridges
    suspend fun addMedicineReminder(
        name: String,
        dosage: String = "1 pill",
        hour: Int = 9,
        minute: Int = 0,
        categoryEmoji: String = "💊",
        daysOfWeek: String = "ALL"
    ): Long = addMedication(
        name = name,
        dosage = dosage,
        hour = hour,
        minute = minute,
        categoryEmoji = categoryEmoji,
        daysOfWeek = daysOfWeek
    )

    suspend fun updateMedicineReminder(reminder: MedicineReminder) = updateMedication(reminder)
    suspend fun deleteMedicineReminder(id: Long) = deleteMedication(id)
    suspend fun toggleMedicineReminder(id: Long, isEnabled: Boolean) = toggleMedication(id, isEnabled)
    suspend fun markMedicineTakenToday(id: Long, todayDateString: String) = markMedicationTakenToday(id, todayDateString)

    suspend fun checkAndSeedMedicineRemindersIfEmpty() {
        val count = database.medicationDao().count()
        if (count == 0) {
            database.medicationDao().insert(
                Medication(
                    name = "Daily Multivitamin / Iron",
                    dosage = "1 capsule with water",
                    hour = 9,
                    minute = 0,
                    isEnabled = true,
                    categoryEmoji = "🌸",
                    customSound = "cute_chime",
                    customReminderMessage = "Good morning queen! Time for your glow vitamins 🌸 Drink plenty of water and have a lovely day!",
                    instructions = "Take after breakfast with water"
                )
            )
        }
    }

    suspend fun setWaterCount(dateString: String, count: Int) {
        val clamped = count.coerceIn(0, 50)
        database.waterLogDao().insertOrUpdate(
            WaterLog(
                dateString = BackendSecurityFirewall.sanitizeField(dateString, 10),
                glassesCount = clamped
            )
        )
    }

    suspend fun addNote(content: String, tag: String, reminderTimeMillis: Long? = null): Long {
        return database.userNoteDao().insert(
            BackendSecurityFirewall.guardUserNote(
                UserNote(
                    content = content.trim(),
                    tag = tag,
                    timestampMillis = System.currentTimeMillis(),
                    reminderTimeMillis = reminderTimeMillis,
                    reminderFired = false
                )
            )
        )
    }

    suspend fun updateNoteReminder(id: Long, reminderTimeMillis: Long?) {
        database.userNoteDao().updateReminder(id, reminderTimeMillis, reminderFired = false)
    }

    suspend fun getNoteById(id: Long): UserNote? {
        return database.userNoteDao().getById(id)
    }

    suspend fun deleteNote(id: Long) {
        database.userNoteDao().deleteById(id)
    }

    suspend fun checkAndSeedUserProfileIfEmpty() {
        val existing = database.userProfileDao().getProfileOnce()
        if (existing == null) {
            val defaultProfile = UserProfile(
                id = 1,
                name = "Krishi",
                cycleLength = 28,
                periodLength = 5,
                waterGoal = 8,
                waterRemindersEnabled = true,
                cycleRemindersEnabled = true,
                fertileRemindersEnabled = true,
                checkinRemindersEnabled = true,
                shopRemindersEnabled = true,
                reminderHour = 20,
                isOnboarded = true
            )
            database.userProfileDao().insertOrUpdate(defaultProfile)

            val periodCount = database.periodLogDao().count()
            if (periodCount == 0) {
                val cal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_MONTH, -14)
                }
                logPeriod(
                    startDateMillis = cal.timeInMillis,
                    notes = "Regular cycle start",
                    durationDays = 5,
                    endDateMillis = cal.timeInMillis + (4L * 24 * 60 * 60 * 1000)
                )
            }
        }
    }

    // Offline Arcade & Couple Lounge persistence
    suspend fun recordGameScore(gameId: String, score: Int, streak: Int = 0, lowerIsBetter: Boolean = false) {
        val existing = database.gameScoreDao().getScoreById(gameId)
        val newBest = when {
            existing == null -> score
            lowerIsBetter && existing.highScore > 0 -> minOf(existing.highScore, score)
            lowerIsBetter -> score
            else -> maxOf(existing.highScore, score)
        }
        val newStreak = maxOf(existing?.bestStreak ?: 0, streak)
        database.gameScoreDao().insertOrUpdate(
            GameScoreRecord(
                gameId = gameId,
                highScore = newBest,
                bestStreak = newStreak,
                gamesPlayed = (existing?.gamesPlayed ?: 0) + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getCoupleRoom(code: String): CoupleRoomRecord? {
        val cleanCode = code.filter { it.isDigit() }.take(6)
        return database.coupleRoomDao().getRoomByCode(cleanCode)
    }

    suspend fun saveCoupleRoom(room: CoupleRoomRecord) {
        val guarded = BackendSecurityFirewall.guardCoupleRoom(room)
        database.coupleRoomDao().insertOrUpdate(guarded.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCoupleRoom(code: String) {
        database.coupleRoomDao().deleteByCode(code)
    }

    suspend fun clearAllData() {
        database.userProfileDao().insertOrUpdate(UserProfile(id = 1, isOnboarded = false))
        database.periodLogDao().clearAll()
        database.shopItemDao().clearAll()
        database.taskCategoryDao().clearAll()
        database.medicationDao().clearAll()
        database.waterLogDao().clearAll()
        database.userNoteDao().clearAll()
        database.dailyCycleLogDao().clearAll()
        database.gameScoreDao().clearAll()
        database.coupleRoomDao().clearAll()
        checkAndSeedCategoriesIfEmpty()
        checkAndSeedShopItemsIfEmpty()
        checkAndSeedMedicineRemindersIfEmpty()
    }
}
