package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        PeriodLog::class,
        DailyCycleLog::class,
        ShopItem::class,
        WaterLog::class,
        UserNote::class,
        Medication::class,
        TaskCategory::class,
        GameScoreRecord::class,
        CoupleRoomRecord::class,
        PendingCoupleTurn::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun periodLogDao(): PeriodLogDao
    abstract fun dailyCycleLogDao(): DailyCycleLogDao
    abstract fun shopItemDao(): ShopItemDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun userNoteDao(): UserNoteDao
    abstract fun taskCategoryDao(): TaskCategoryDao
    abstract fun medicationDao(): MedicationDao
    abstract fun gameScoreDao(): GameScoreDao
    abstract fun coupleRoomDao(): CoupleRoomDao
    abstract fun pendingCoupleTurnDao(): PendingCoupleTurnDao
    fun medicineReminderDao(): MedicationDao = medicationDao()

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun addColumnIfNotExists(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String
        ) {
            val cleanTable = table.replace("`", "").trim()
            val cleanColumn = column.replace("`", "").trim()
            var exists = false
            try {
                db.query("PRAGMA table_info($cleanTable)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx >= 0) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIdx).equals(cleanColumn, ignoreCase = true)) {
                                exists = true
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (!exists) {
                try {
                    db.execSQL("ALTER TABLE `$cleanTable` ADD COLUMN `$cleanColumn` $definition")
                } catch (_: Throwable) {}
            }
        }

        private fun ensureTablesAndColumns(db: SupportSQLiteDatabase) {
            // User profile table & columns
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_profile` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`cycleLength` INTEGER NOT NULL, " +
                    "`periodLength` INTEGER NOT NULL, " +
                    "`waterGoal` INTEGER NOT NULL, " +
                    "`waterRemindersEnabled` INTEGER NOT NULL, " +
                    "`cycleRemindersEnabled` INTEGER NOT NULL, " +
                    "`fertileRemindersEnabled` INTEGER NOT NULL, " +
                    "`checkinRemindersEnabled` INTEGER NOT NULL, " +
                    "`shopRemindersEnabled` INTEGER NOT NULL, " +
                    "`reminderHour` INTEGER NOT NULL, " +
                    "`waterIntervalHours` INTEGER NOT NULL, " +
                    "`isOnboarded` INTEGER NOT NULL, " +
                    "`shopHideCompleted` INTEGER NOT NULL, " +
                    "`shopViewMode` TEXT NOT NULL, " +
                    "`shopSortMode` TEXT NOT NULL, " +
                    "`activeCoupleCode` TEXT NOT NULL DEFAULT '', " +
                    "PRIMARY KEY(`id`))"
            )
            addColumnIfNotExists(db, "user_profile", "shopHideCompleted", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "user_profile", "shopViewMode", "TEXT NOT NULL DEFAULT 'COZY'")
            addColumnIfNotExists(db, "user_profile", "shopSortMode", "TEXT NOT NULL DEFAULT 'CUSTOM'")
            addColumnIfNotExists(db, "user_profile", "waterIntervalHours", "INTEGER NOT NULL DEFAULT 3")
            addColumnIfNotExists(db, "user_profile", "activeCoupleCode", "TEXT NOT NULL DEFAULT ''")

            // Period logs table & columns
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `period_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`startDateMillis` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`durationDays` INTEGER, " +
                    "`endDateMillis` INTEGER)"
            )
            addColumnIfNotExists(db, "period_logs", "durationDays", "INTEGER")
            addColumnIfNotExists(db, "period_logs", "endDateMillis", "INTEGER")

            // Shop items
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shop_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`isDone` INTEGER NOT NULL, " +
                    "`phaseCategory` TEXT NOT NULL, " +
                    "`isCustom` INTEGER NOT NULL, " +
                    "`orderIndex` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`priority` TEXT NOT NULL)"
            )
            addColumnIfNotExists(db, "shop_items", "notes", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "shop_items", "priority", "TEXT NOT NULL DEFAULT 'NORMAL'")
            addColumnIfNotExists(db, "shop_items", "orderIndex", "INTEGER NOT NULL DEFAULT 0")

            // Water logs
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `water_logs` (" +
                    "`dateString` TEXT NOT NULL, " +
                    "`glassesCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`dateString`))"
            )

            // User notes
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_notes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`tag` TEXT NOT NULL, " +
                    "`timestampMillis` INTEGER NOT NULL, " +
                    "`reminderTimeMillis` INTEGER, " +
                    "`reminderFired` INTEGER NOT NULL DEFAULT 0)"
            )
            addColumnIfNotExists(db, "user_notes", "reminderTimeMillis", "INTEGER")
            addColumnIfNotExists(db, "user_notes", "reminderFired", "INTEGER NOT NULL DEFAULT 0")

            // Medications
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medications` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`dosage` TEXT NOT NULL, " +
                    "`hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, " +
                    "`isEnabled` INTEGER NOT NULL, " +
                    "`categoryEmoji` TEXT NOT NULL, " +
                    "`daysOfWeek` TEXT NOT NULL, " +
                    "`customSound` TEXT NOT NULL, " +
                    "`customReminderMessage` TEXT NOT NULL, " +
                    "`instructions` TEXT NOT NULL, " +
                    "`colorHex` TEXT NOT NULL, " +
                    "`stockCount` INTEGER NOT NULL, " +
                    "`lastTakenDate` TEXT NOT NULL, " +
                    "`lastTakenTime` TEXT NOT NULL)"
            )

            // Task categories
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `task_categories` (" +
                    "`id` TEXT NOT NULL, " +
                    "`displayName` TEXT NOT NULL, " +
                    "`emoji` TEXT NOT NULL, " +
                    "`colorHex` TEXT NOT NULL, " +
                    "`orderIndex` INTEGER NOT NULL, " +
                    "`isDefault` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )

            // Daily cycle and symptoms log for private tracking
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_cycle_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dateString` TEXT NOT NULL, " +
                    "`timestampMillis` INTEGER NOT NULL, " +
                    "`flowIntensity` TEXT NOT NULL, " +
                    "`symptoms` TEXT NOT NULL, " +
                    "`mood` TEXT NOT NULL, " +
                    "`cervicalMucus` TEXT NOT NULL, " +
                    "`painLevel` INTEGER NOT NULL, " +
                    "`energyLevel` INTEGER NOT NULL, " +
                    "`basalTemperature` REAL, " +
                    "`notes` TEXT NOT NULL)"
            )
            // Deduplicate if multiple rows exist for the same dateString before indexing
            try {
                db.execSQL(
                    "DELETE FROM `daily_cycle_logs` WHERE id NOT IN (" +
                        "SELECT MAX(id) FROM `daily_cycle_logs` GROUP BY `dateString`" +
                    ")"
                )
            } catch (_: Throwable) {}
            // Ensure unique index matches Room's TableInfo expectation
            try {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_cycle_logs_dateString` ON `daily_cycle_logs` (`dateString`)")
            } catch (_: Throwable) {}

            // Offline Arcade game high scores
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `game_scores` (" +
                    "`gameId` TEXT NOT NULL, " +
                    "`highScore` INTEGER NOT NULL, " +
                    "`bestStreak` INTEGER NOT NULL, " +
                    "`gamesPlayed` INTEGER NOT NULL, " +
                    "`lastPlayedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`gameId`))"
            )

            // Long-distance Couple Lounge rooms (keyed by unique 6-digit Couple Code)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `couple_rooms` (" +
                    "`coupleCode` TEXT NOT NULL, " +
                    "`myName` TEXT NOT NULL, " +
                    "`partnerName` TEXT NOT NULL, " +
                    "`spiceLevel` TEXT NOT NULL, " +
                    "`isPassAndPlay` INTEGER NOT NULL, " +
                    "`matchCount` INTEGER NOT NULL, " +
                    "`streakCount` INTEGER NOT NULL, " +
                    "`doodleStrokesJson` TEXT NOT NULL, " +
                    "`doodlePrompt` TEXT NOT NULL, " +
                    "`quizStateJson` TEXT NOT NULL, " +
                    "`wyrStateJson` TEXT NOT NULL, " +
                    "`ticTacToeStateJson` TEXT NOT NULL, " +
                    "`isBonded` INTEGER NOT NULL DEFAULT 1, " +
                    "`partnerLocked` INTEGER NOT NULL DEFAULT 0, " +
                    "`blockchainLedgerJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`lastBlockHash` TEXT NOT NULL DEFAULT '', " +
                    "`blockHeight` INTEGER NOT NULL DEFAULT 1, " +
                    "`firewallPassedCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`firewallBlockedCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastFirewallEvent` TEXT NOT NULL DEFAULT 'Firewall Active · AES-256-GCM + PoW Chain', " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`isHost` INTEGER NOT NULL DEFAULT 1, " +
                    "`approvalStatus` TEXT NOT NULL DEFAULT 'WAITING', " +
                    "`pendingPartnerName` TEXT NOT NULL DEFAULT '', " +
                    "`dareStateJson` TEXT NOT NULL DEFAULT '{}', " +
                    "`wavelengthStateJson` TEXT NOT NULL DEFAULT '{}', " +
                    "`whisperStateJson` TEXT NOT NULL DEFAULT '{}', " +
                    "`ecdhPublicKeyB64` TEXT NOT NULL DEFAULT '', " +
                    "`ecdhPrivateKeyB64` TEXT NOT NULL DEFAULT '', " +
                    "`peerPublicKeyB64` TEXT NOT NULL DEFAULT '', " +
                    "`ecdhSharedSecretB64` TEXT NOT NULL DEFAULT '', " +
                    "`chainSchemaVersion` INTEGER NOT NULL DEFAULT 1, " +
                    "`minProtocolVersion` INTEGER NOT NULL DEFAULT 2, " +
                    "`migrationAnchorHash` TEXT NOT NULL DEFAULT '', " +
                    "`migrationAnchorIndex` INTEGER NOT NULL DEFAULT 0, " +
                    "`prunedBlockCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`prunedAnchorHash` TEXT NOT NULL DEFAULT '', " +
                    "`lastConfirmedCloudIndex` INTEGER NOT NULL DEFAULT 0, " +
                    "`seenClientTurnIdsJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`seenReplayHashesJson` TEXT NOT NULL DEFAULT '[]', " +
                    "PRIMARY KEY(`coupleCode`))"
            )
            addColumnIfNotExists(db, "couple_rooms", "isBonded", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfNotExists(db, "couple_rooms", "partnerLocked", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "blockchainLedgerJson", "TEXT NOT NULL DEFAULT '[]'")
            addColumnIfNotExists(db, "couple_rooms", "lastBlockHash", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "blockHeight", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfNotExists(db, "couple_rooms", "firewallPassedCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "firewallBlockedCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "lastFirewallEvent", "TEXT NOT NULL DEFAULT 'Firewall Active · AES-256-GCM + PoW Chain'")
            addColumnIfNotExists(db, "couple_rooms", "isHost", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfNotExists(db, "couple_rooms", "approvalStatus", "TEXT NOT NULL DEFAULT 'WAITING'")
            addColumnIfNotExists(db, "couple_rooms", "pendingPartnerName", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "dareStateJson", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfNotExists(db, "couple_rooms", "wavelengthStateJson", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfNotExists(db, "couple_rooms", "whisperStateJson", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfNotExists(db, "couple_rooms", "ecdhPublicKeyB64", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "ecdhPrivateKeyB64", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "peerPublicKeyB64", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "ecdhSharedSecretB64", "TEXT NOT NULL DEFAULT ''")
            // Existing v14 rooms default to chainSchemaVersion=1 so they lazily upgrade to V2 with an explicit migration anchor
            addColumnIfNotExists(db, "couple_rooms", "chainSchemaVersion", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfNotExists(db, "couple_rooms", "minProtocolVersion", "INTEGER NOT NULL DEFAULT 2")
            addColumnIfNotExists(db, "couple_rooms", "migrationAnchorHash", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "migrationAnchorIndex", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "prunedBlockCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "prunedAnchorHash", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "couple_rooms", "lastConfirmedCloudIndex", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "couple_rooms", "seenClientTurnIdsJson", "TEXT NOT NULL DEFAULT '[]'")
            addColumnIfNotExists(db, "couple_rooms", "seenReplayHashesJson", "TEXT NOT NULL DEFAULT '[]'")

            // Preserve ECDH protocol v3 floor for any migrated room that already established an ECDH secret
            try {
                db.execSQL(
                    "UPDATE `couple_rooms` SET `minProtocolVersion` = 3 " +
                        "WHERE `ecdhSharedSecretB64` != '' AND `minProtocolVersion` < 3"
                )
            } catch (_: Throwable) {}

            // Durable encrypted pending-turn queue for offline Couple Lounge moves
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_couple_turns` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`clientTurnId` TEXT NOT NULL, " +
                    "`coupleCode` TEXT NOT NULL, " +
                    "`actionType` TEXT NOT NULL, " +
                    "`encryptedIntentJson` TEXT NOT NULL, " +
                    "`retryCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`status` TEXT NOT NULL DEFAULT 'PENDING', " +
                    "`lastError` TEXT NOT NULL DEFAULT '', " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            try {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_couple_turns_clientTurnId` " +
                        "ON `pending_couple_turns` (`clientTurnId`)"
                )
            } catch (_: Throwable) {}
            try {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_couple_turns_coupleCode_createdAt` " +
                        "ON `pending_couple_turns` (`coupleCode`, `createdAt`)"
                )
            } catch (_: Throwable) {}
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_8_9 = object : Migration(8, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_9_10 = object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_8_10 = object : Migration(8, 10) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_10 = object : Migration(1, 10) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_9 = object : Migration(1, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_2_9 = object : Migration(2, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_3_9 = object : Migration(3, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_4_9 = object : Migration(4, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_5_9 = object : Migration(5, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_6_9 = object : Migration(6, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_7_9 = object : Migration(7, 9) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_8 = object : Migration(1, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_2_8 = object : Migration(2, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_3_8 = object : Migration(3, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_4_8 = object : Migration(4, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_5_8 = object : Migration(5, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_6_8 = object : Migration(6, 8) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_7 = object : Migration(1, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_2_7 = object : Migration(2, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_3_7 = object : Migration(3, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_4_7 = object : Migration(4, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_5_7 = object : Migration(5, 7) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_10_11 = object : Migration(10, 11) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_9_11 = object : Migration(9, 11) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_8_11 = object : Migration(8, 11) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_11 = object : Migration(1, 11) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_11_12 = object : Migration(11, 12) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_10_12 = object : Migration(10, 12) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_12 = object : Migration(1, 12) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_12_13 = object : Migration(12, 13) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_11_13 = object : Migration(11, 13) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_10_13 = object : Migration(10, 13) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_13 = object : Migration(1, 13) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_13_14 = object : Migration(13, 14) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_12_14 = object : Migration(12, 14) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_11_14 = object : Migration(11, 14) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_10_14 = object : Migration(10, 14) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_14 = object : Migration(1, 14) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        internal val MIGRATION_14_15 = object : Migration(14, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_13_15 = object : Migration(13, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_12_15 = object : Migration(12, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_11_15 = object : Migration(11, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_10_15 = object : Migration(10, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }
        private val MIGRATION_1_15 = object : Migration(1, 15) { override fun migrate(db: SupportSQLiteDatabase) = ensureTablesAndColumns(db) }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = "phases_local.db"
                val appContext = context.applicationContext
                com.example.security.LocalVaultCrypto.init(appContext)

                val builder = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    dbName
                )

                val instance = builder.addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_8_10,
                    MIGRATION_1_10,
                    MIGRATION_1_9,
                    MIGRATION_2_9,
                    MIGRATION_3_9,
                    MIGRATION_4_9,
                    MIGRATION_5_9,
                    MIGRATION_6_9,
                    MIGRATION_7_9,
                    MIGRATION_1_8,
                    MIGRATION_2_8,
                    MIGRATION_3_8,
                    MIGRATION_4_8,
                    MIGRATION_5_8,
                    MIGRATION_6_8,
                    MIGRATION_1_7,
                    MIGRATION_2_7,
                    MIGRATION_3_7,
                    MIGRATION_4_7,
                    MIGRATION_5_7,
                    MIGRATION_10_11,
                    MIGRATION_9_11,
                    MIGRATION_8_11,
                    MIGRATION_1_11,
                    MIGRATION_11_12,
                    MIGRATION_10_12,
                    MIGRATION_1_12,
                    MIGRATION_12_13,
                    MIGRATION_11_13,
                    MIGRATION_10_13,
                    MIGRATION_1_13,
                    MIGRATION_13_14,
                    MIGRATION_12_14,
                    MIGRATION_11_14,
                    MIGRATION_10_14,
                    MIGRATION_1_14,
                    MIGRATION_14_15,
                    MIGRATION_13_15,
                    MIGRATION_12_15,
                    MIGRATION_11_15,
                    MIGRATION_10_15,
                    MIGRATION_1_15
                )
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        com.example.security.BackendSecurityFirewall.hardenSqliteConnection(db)
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        com.example.security.BackendSecurityFirewall.hardenSqliteConnection(db)
                        // Pre-populate default categories and curated items
                        CoroutineScope(Dispatchers.IO).launch {
                            val defaultCategories = listOf(
                                TaskCategory(id = "menstrual", displayName = "Crimson & Comfort", emoji = "🌸", colorHex = "#D4629A", orderIndex = 0, isDefault = true),
                                TaskCategory(id = "follicular", displayName = "Rising Energy", emoji = "🌱", colorHex = "#5E9C68", orderIndex = 1, isDefault = true),
                                TaskCategory(id = "ovulation", displayName = "Vibrant & Glowing", emoji = "🌿", colorHex = "#8B62D6", orderIndex = 2, isDefault = true),
                                TaskCategory(id = "luteal", displayName = "PMS & Cozy Care", emoji = "🌙", colorHex = "#7C3FA6", orderIndex = 3, isDefault = true),
                                TaskCategory(id = "general", displayName = "Everyday Essentials", emoji = "🛍️", colorHex = "#3B82F6", orderIndex = 4, isDefault = true),
                                TaskCategory(id = "wellness", displayName = "Self-Care & Mind", emoji = "✨", colorHex = "#E6723D", orderIndex = 5, isDefault = false)
                            )
                            INSTANCE?.taskCategoryDao()?.insertAll(defaultCategories)

                            val defaultItems = listOf(
                                // Menstrual
                                ShopItem(text = "Pads or tampons", phaseCategory = "menstrual", orderIndex = 0),
                                ShopItem(text = "Heating pad or patch", phaseCategory = "menstrual", orderIndex = 1),
                                ShopItem(text = "Pain relief (Ibuprofen/Paracetamol)", phaseCategory = "menstrual", orderIndex = 2),
                                ShopItem(text = "Dark chocolate (70%+)", phaseCategory = "menstrual", orderIndex = 3),
                                ShopItem(text = "Cozy warm socks", phaseCategory = "menstrual", orderIndex = 4),
                                // Follicular
                                ShopItem(text = "Fresh leafy greens & berries", phaseCategory = "follicular", orderIndex = 5),
                                ShopItem(text = "Probiotic yogurt or kefir", phaseCategory = "follicular", orderIndex = 6),
                                ShopItem(text = "Pre-workout energy snacks", phaseCategory = "follicular", orderIndex = 7),
                                // Ovulation
                                ShopItem(text = "Extra water / electrolyte tablets", phaseCategory = "ovulation", orderIndex = 8),
                                ShopItem(text = "Fiber-rich snacks & seeds", phaseCategory = "ovulation", orderIndex = 9),
                                ShopItem(text = "High-protein meal prep", phaseCategory = "ovulation", orderIndex = 10),
                                // Luteal / PMS
                                ShopItem(text = "Magnesium supplement", phaseCategory = "luteal", orderIndex = 11),
                                ShopItem(text = "Chamomile or peppermint herbal tea", phaseCategory = "luteal", orderIndex = 12),
                                ShopItem(text = "Comfort snacks & soothing soup", phaseCategory = "luteal", orderIndex = 13),
                                ShopItem(text = "Epsom bath salts", phaseCategory = "luteal", orderIndex = 14)
                            )
                            INSTANCE?.shopItemDao()?.insertAll(defaultItems)
                            
                            val defaultMedicines = listOf(
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
                            INSTANCE?.medicationDao()?.insertAll(defaultMedicines)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
