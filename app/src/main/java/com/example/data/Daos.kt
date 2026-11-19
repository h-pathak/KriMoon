package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileOnce(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)
}

@Dao
interface PeriodLogDao {
    @Query("SELECT * FROM period_logs ORDER BY startDateMillis DESC")
    fun getAllPeriods(): Flow<List<PeriodLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: PeriodLog): Long

    @Update
    suspend fun update(period: PeriodLog)

    @Query("SELECT * FROM period_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PeriodLog?

    @Query("DELETE FROM period_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM period_logs")
    suspend fun count(): Int

    @Query("DELETE FROM period_logs")
    suspend fun clearAll()
}

@Dao
interface TaskCategoryDao {
    @Query("SELECT * FROM task_categories ORDER BY orderIndex ASC")
    fun getAllCategories(): Flow<List<TaskCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: TaskCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<TaskCategory>)

    @Update
    suspend fun update(category: TaskCategory)

    @Query("DELETE FROM task_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM task_categories")
    suspend fun count(): Int

    @Query("DELETE FROM task_categories")
    suspend fun clearAll()
}

@Dao
interface ShopItemDao {
    @Query("SELECT * FROM shop_items ORDER BY orderIndex ASC, id ASC")
    fun getAllItems(): Flow<List<ShopItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShopItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShopItem>)

    @Update
    suspend fun update(item: ShopItem)

    @Update
    suspend fun updateAll(items: List<ShopItem>)

    @Query("UPDATE shop_items SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrder(id: Long, orderIndex: Int)

    @Query("DELETE FROM shop_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM shop_items")
    suspend fun count(): Int

    @Query("DELETE FROM shop_items")
    suspend fun clearAll()
}

@Dao
interface WaterLogDao {
    @Query("SELECT * FROM water_logs WHERE dateString = :dateString LIMIT 1")
    fun getWaterLog(dateString: String): Flow<WaterLog?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: WaterLog)

    @Query("DELETE FROM water_logs")
    suspend fun clearAll()
}

@Dao
interface UserNoteDao {
    @Query("SELECT * FROM user_notes ORDER BY timestampMillis DESC")
    fun getAllNotes(): Flow<List<UserNote>>

    @Query("SELECT * FROM user_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserNote?

    @Query("SELECT * FROM user_notes WHERE reminderTimeMillis IS NOT NULL AND reminderFired = 0 ORDER BY reminderTimeMillis ASC")
    fun getActiveReminders(): Flow<List<UserNote>>

    @Query("SELECT * FROM user_notes WHERE reminderTimeMillis IS NOT NULL AND reminderFired = 0")
    suspend fun getActiveRemindersList(): List<UserNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: UserNote): Long

    @Update
    suspend fun update(note: UserNote)

    @Query("UPDATE user_notes SET reminderTimeMillis = :reminderTimeMillis, reminderFired = :reminderFired WHERE id = :id")
    suspend fun updateReminder(id: Long, reminderTimeMillis: Long?, reminderFired: Boolean)

    @Query("DELETE FROM user_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_notes")
    suspend fun clearAll()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY hour ASC, minute ASC")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY hour ASC, minute ASC")
    fun getAllReminders(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    fun getEnabledMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    fun getEnabledReminders(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Medication): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Medication>)

    @Update
    suspend fun update(reminder: Medication)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Query("DELETE FROM medications")
    suspend fun clearAll()
}

typealias MedicineReminderDao = MedicationDao

@Dao
interface DailyCycleLogDao {
    @Query("SELECT * FROM daily_cycle_logs ORDER BY dateString DESC")
    fun getAllLogs(): Flow<List<DailyCycleLog>>

    @Query("SELECT * FROM daily_cycle_logs WHERE dateString = :dateString LIMIT 1")
    fun getLogForDate(dateString: String): Flow<DailyCycleLog?>

    @Query("SELECT * FROM daily_cycle_logs WHERE dateString = :dateString LIMIT 1")
    suspend fun getLogForDateSync(dateString: String): DailyCycleLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyCycleLog): Long

    @Query("DELETE FROM daily_cycle_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM daily_cycle_logs WHERE dateString = :dateString")
    suspend fun deleteByDate(dateString: String)

    @Query("DELETE FROM daily_cycle_logs")
    suspend fun clearAll()
}

@Dao
interface GameScoreDao {
    @Query("SELECT * FROM game_scores")
    fun getAllScores(): Flow<List<GameScoreRecord>>

    @Query("SELECT * FROM game_scores WHERE gameId = :gameId LIMIT 1")
    suspend fun getScoreById(gameId: String): GameScoreRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: GameScoreRecord)

    @Query("DELETE FROM game_scores")
    suspend fun clearAll()
}

@Dao
interface CoupleRoomDao {
    @Query("SELECT * FROM couple_rooms ORDER BY updatedAt DESC")
    fun getAllRooms(): Flow<List<CoupleRoomRecord>>

    @Query("SELECT * FROM couple_rooms WHERE coupleCode = :coupleCode LIMIT 1")
    suspend fun getRoomByCode(coupleCode: String): CoupleRoomRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(room: CoupleRoomRecord)

    @Query("DELETE FROM couple_rooms WHERE coupleCode = :coupleCode")
    suspend fun deleteByCode(coupleCode: String)

    @Query("DELETE FROM couple_rooms")
    suspend fun clearAll()
}


