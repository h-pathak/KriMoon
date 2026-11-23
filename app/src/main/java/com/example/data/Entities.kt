package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val waterGoal: Int = 8,
    val waterRemindersEnabled: Boolean = true,
    val cycleRemindersEnabled: Boolean = true,
    val fertileRemindersEnabled: Boolean = true,
    val checkinRemindersEnabled: Boolean = true,
    val shopRemindersEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val waterIntervalHours: Int = 3,
    val isOnboarded: Boolean = false,
    val shopHideCompleted: Boolean = false,
    val shopViewMode: String = "COZY", // "COZY" or "COMPACT"
    val shopSortMode: String = "CUSTOM", // "CUSTOM", "STATUS", "PRIORITY", "ALPHA"
    val activeCoupleCode: String = "", // Persisted permanent Couple Code bond
    val userGender: String = "FEMALE" // "FEMALE" or "MALE"
)

@Entity(tableName = "period_logs")
data class PeriodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDateMillis: Long,
    val notes: String = "",
    val durationDays: Int? = null,
    val endDateMillis: Long? = null
)

@Entity(
    tableName = "daily_cycle_logs",
    indices = [
        Index(value = ["dateString"], unique = true)
    ]
)
data class DailyCycleLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String, // "yyyy-MM-dd", unique per day
    val timestampMillis: Long = System.currentTimeMillis(),
    val flowIntensity: String = "NONE", // "NONE", "SPOTTING", "LIGHT", "MEDIUM", "HEAVY"
    val symptoms: String = "", // Comma-separated: e.g. "cramps,bloating,tender_breasts,headache,fatigue,acne"
    val mood: String = "", // e.g. "Calm", "Happy", "Sensitive", "Irritable", "Anxious", "Exhausted"
    val cervicalMucus: String = "NONE", // "NONE", "DRY", "STICKY", "CREAMY", "EGG_WHITE", "WATERY"
    val painLevel: Int = 0, // 0 to 5
    val energyLevel: Int = 3, // 1 to 5
    val basalTemperature: Float? = null,
    val notes: String = "" // Private personal tracking notes
)

@Entity(tableName = "task_categories")
data class TaskCategory(
    @PrimaryKey val id: String, // e.g. "menstrual", "follicular", "ovulation", "luteal", "general", "wellness"
    val displayName: String,
    val emoji: String,
    val colorHex: String = "#D4629A",
    val orderIndex: Int = 0,
    val isDefault: Boolean = false
) {
    val isCustom: Boolean get() = !isDefault
}

@Entity(tableName = "shop_items")
data class ShopItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isDone: Boolean = false,
    val phaseCategory: String = "general", // foreign key or id matching TaskCategory.id
    val isCustom: Boolean = false,
    val orderIndex: Int = 0,
    val notes: String = "",
    val priority: String = "NORMAL" // "HIGH", "NORMAL", "LOW"
)

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey val dateString: String, // "yyyy-MM-dd"
    val glassesCount: Int = 0
)

@Entity(tableName = "user_notes")
data class UserNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val tag: String = "thought", // "gossip", "thought", "question", "mood", "reminder", "symptom"
    val timestampMillis: Long = System.currentTimeMillis(),
    val reminderTimeMillis: Long? = null,
    val reminderFired: Boolean = false
)

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "1 pill",
    val hour: Int = 9,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val categoryEmoji: String = "💊", // 💊, 🌸, 🌿, 🩸, 💧, 🩹, ☕, 🧴, 🌙
    val daysOfWeek: String = "ALL", // "ALL" or "MON,TUE..."
    val customSound: String = "cute_chime", // "cute_chime", "sparkle_bell", "gentle_harp", "sweet_bubble"
    val customReminderMessage: String = "", // Cute personalized message
    val instructions: String = "Take with water",
    val colorHex: String = "#D4629A",
    val stockCount: Int = -1,
    val lastTakenDate: String = "", // "yyyy-MM-dd"
    val lastTakenTime: String = ""  // "HH:mm"
)

typealias MedicineReminder = Medication

@Entity(tableName = "game_scores")
data class GameScoreRecord(
    @PrimaryKey val gameId: String, // e.g. "moon_merge", "neon_beat", "reaction_reflex", "block_blast", "glow_2048", "moon_hopper"
    val highScore: Int = 0,
    val bestStreak: Int = 0,
    val gamesPlayed: Int = 0,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "couple_rooms")
data class CoupleRoomRecord(
    @PrimaryKey val coupleCode: String, // Unique 6-digit room code per couple
    val myName: String = "",
    val partnerName: String = "",
    val spiceLevel: String = "FLIRTY", // "SWEET", "FLIRTY", "BOLD"
    val isPassAndPlay: Boolean = false,
    val matchCount: Int = 0,
    val streakCount: Int = 0,
    val doodleStrokesJson: String = "[]",
    val doodlePrompt: String = "",
    val quizStateJson: String = "{}",
    val wyrStateJson: String = "{}",
    val ticTacToeStateJson: String = "{}",
    val isBonded: Boolean = true,
    val partnerLocked: Boolean = false,
    val blockchainLedgerJson: String = "[]",
    val lastBlockHash: String = "",
    val blockHeight: Int = 1,
    val firewallPassedCount: Int = 0,
    val firewallBlockedCount: Int = 0,
    val lastFirewallEvent: String = "Firewall Active · AES-256-GCM + PoW Chain",
    val updatedAt: Long = System.currentTimeMillis(),
    val isHost: Boolean = true,
    val approvalStatus: String = "WAITING", // "WAITING", "PENDING_HOST_APPROVAL", "AWAITING_MY_APPROVAL", "CONNECTED"
    val pendingPartnerName: String = "",
    val dareStateJson: String = "{}",
    val wavelengthStateJson: String = "{}",
    val whisperStateJson: String = "{}",
    val ecdhPublicKeyB64: String = "",
    val ecdhPrivateKeyB64: String = "",
    val peerPublicKeyB64: String = "",
    val ecdhSharedSecretB64: String = "",
    val chainSchemaVersion: Int = 2,
    val minProtocolVersion: Int = 2,
    val migrationAnchorHash: String = "",
    val migrationAnchorIndex: Int = 0,
    val prunedBlockCount: Int = 0,
    val prunedAnchorHash: String = "",
    val lastConfirmedCloudIndex: Int = 0,
    val seenClientTurnIdsJson: String = "[]",
    val seenReplayHashesJson: String = "[]",
    val partnerCycleSyncJson: String = "{}",
    val lastSentCycleSyncFingerprint: String = ""
)

@Entity(
    tableName = "pending_couple_turns",
    indices = [
        Index(value = ["clientTurnId"], unique = true),
        Index(value = ["coupleCode", "createdAt"])
    ]
)
data class PendingCoupleTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientTurnId: String,
    val coupleCode: String,
    val actionType: String,
    val encryptedIntentJson: String,
    val retryCount: Int = 0,
    val status: String = "PENDING", // "PENDING", "SYNCING", "FAILED_PERMANENT"
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


