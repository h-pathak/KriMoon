package com.example.data

import com.example.model.CoupleActivityIntent
import com.example.security.BackendSecurityFirewall
import com.example.security.LocalVaultCrypto
import com.example.security.LocalVaultUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class VaultDecryptionIssue(
    val table: String,
    val recordKey: String,
    val column: String,
    val reason: String
)

class PhasesRepository(private val database: AppDatabase) {

    companion object {
        const val VAULT_UNREADABLE_LABEL = "[Encrypted record unreadable — unlock vault]"
    }

    private val issuesByField = ConcurrentHashMap<String, VaultDecryptionIssue>()
    private val unreadableRecordKeys = ConcurrentHashMap.newKeySet<String>()
    private val _decryptionIssues = MutableStateFlow<List<VaultDecryptionIssue>>(emptyList())
    val decryptionIssues: StateFlow<List<VaultDecryptionIssue>> = _decryptionIssues.asStateFlow()

    fun isRecordUnreadable(table: String, recordKey: String): Boolean {
        return "$table:$recordKey" in unreadableRecordKeys
    }

    private fun recordDecryptionFailure(table: String, recordKey: String, column: String, cause: Throwable) {
        val rowKey = "$table:$recordKey"
        val fieldKey = "$rowKey:$column"
        unreadableRecordKeys.add(rowKey)
        issuesByField[fieldKey] = VaultDecryptionIssue(
            table = table,
            recordKey = recordKey,
            column = column,
            reason = cause.message?.take(120)?.ifBlank { "AES-256-GCM authentication failed" }
                ?: "AES-256-GCM authentication failed"
        )
        _decryptionIssues.value = issuesByField.values.sortedWith(
            compareBy<VaultDecryptionIssue> { it.table }.thenBy { it.recordKey }.thenBy { it.column }
        )
    }

    private fun clearDecryptionFailure(table: String, recordKey: String, column: String) {
        val rowKey = "$table:$recordKey"
        val fieldKey = "$rowKey:$column"
        if (issuesByField.remove(fieldKey) != null) {
            if (issuesByField.keys.none { it.startsWith("$rowKey:") }) {
                unreadableRecordKeys.remove(rowKey)
            }
            _decryptionIssues.value = issuesByField.values.sortedWith(
                compareBy<VaultDecryptionIssue> { it.table }.thenBy { it.recordKey }.thenBy { it.column }
            )
        }
    }

    private fun decryptFieldTracked(
        storedText: String,
        aadContext: String,
        table: String,
        recordKey: String,
        column: String,
        errorFallback: String = VAULT_UNREADABLE_LABEL
    ): String {
        val result = LocalVaultCrypto.decryptFieldResult(storedText, aadContext)
        return result.fold(
            onSuccess = { plain ->
                clearDecryptionFailure(table, recordKey, column)
                plain
            },
            onFailure = { err ->
                recordDecryptionFailure(table, recordKey, column, err)
                errorFallback
            }
        )
    }

    private fun clearDecryptionTracking() {
        issuesByField.clear()
        unreadableRecordKeys.clear()
        _decryptionIssues.value = emptyList()
    }

    // Transparently decrypted reactive streams backed by Android Keystore AES-256-GCM at rest
    // All decryption runs strictly off the Main thread via flowOn(Dispatchers.Default)
    val userProfile: Flow<UserProfile?> = database.userProfileDao().getUserProfile()
        .map { it?.decryptFromVault() }
        .flowOn(Dispatchers.Default)

    val periodLogs: Flow<List<PeriodLog>> = database.periodLogDao().getAllPeriods()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val shopItems: Flow<List<ShopItem>> = database.shopItemDao().getAllItems()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val taskCategories: Flow<List<TaskCategory>> = database.taskCategoryDao().getAllCategories()

    val userNotes: Flow<List<UserNote>> = database.userNoteDao().getAllNotes()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val medications: Flow<List<Medication>> = database.medicationDao().getAllMedications()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val medicineReminders: Flow<List<MedicineReminder>> = medications

    val dailyCycleLogs: Flow<List<DailyCycleLog>> = database.dailyCycleLogDao().getAllLogs()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val gameScores: Flow<List<GameScoreRecord>> = database.gameScoreDao().getAllScores()

    val coupleRooms: Flow<List<CoupleRoomRecord>> = database.coupleRoomDao().getAllRooms()
        .map { list -> list.map { it.decryptFromVault() } }
        .flowOn(Dispatchers.Default)

    val activePendingTurnCount: Flow<Int> = database.pendingCoupleTurnDao().observeActivePendingCount()

    fun observePendingTurnsForRoom(coupleCode: String): Flow<List<PendingCoupleTurn>> {
        val clean = coupleCode.filter { it.isDigit() }.take(6)
        return database.pendingCoupleTurnDao().observePendingForRoom(clean)
    }

    private fun requireHealthyVaultForWrite() {
        if (!LocalVaultCrypto.canSafelyWrite()) {
            throw LocalVaultUnavailableException(
                vaultState = LocalVaultCrypto.vaultState.value,
                message = "Local vault is locked (${LocalVaultCrypto.vaultState.value}); refusing write to protect existing encrypted records."
            )
        }
    }

    private fun requireWritableRecord(table: String, recordKey: String, vararg candidateValues: String) {
        requireHealthyVaultForWrite()
        if (isRecordUnreadable(table, recordKey) || candidateValues.any { it.contains(VAULT_UNREADABLE_LABEL) }) {
            throw LocalVaultUnavailableException(
                vaultState = LocalVaultCrypto.vaultState.value,
                message = "Refusing write to unreadable encrypted record ($table:$recordKey) to preserve original ciphertext."
            )
        }
    }

    // --- At-Rest AES-256-GCM Field Encryption & Decryption (Per-Column AAD) ---

    private fun UserProfile.encryptForVault(): UserProfile {
        val guarded = BackendSecurityFirewall.guardUserProfile(this)
        return guarded.copy(
            name = LocalVaultCrypto.encryptField(guarded.name, "user_profile:name")
        )
    }

    private fun UserProfile.decryptFromVault(): UserProfile {
        val key = id.toString()
        return copy(
            name = decryptFieldTracked(name, "user_profile:name", "user_profile", key, "name")
        )
    }

    private fun PeriodLog.encryptForVault(): PeriodLog {
        val guarded = BackendSecurityFirewall.guardPeriodLog(this)
        return guarded.copy(
            notes = LocalVaultCrypto.encryptField(guarded.notes, "period_logs:notes")
        )
    }

    private fun PeriodLog.decryptFromVault(): PeriodLog {
        val key = id.toString()
        return copy(
            notes = decryptFieldTracked(notes, "period_logs:notes", "period_logs", key, "notes")
        )
    }

    private fun DailyCycleLog.encryptForVault(): DailyCycleLog {
        val guarded = BackendSecurityFirewall.guardDailyCycleLog(this)
        return guarded.copy(
            symptoms = LocalVaultCrypto.encryptField(guarded.symptoms, "daily_cycle_logs:symptoms"),
            mood = LocalVaultCrypto.encryptField(guarded.mood, "daily_cycle_logs:mood"),
            notes = LocalVaultCrypto.encryptField(guarded.notes, "daily_cycle_logs:notes")
        )
    }

    private fun DailyCycleLog.decryptFromVault(): DailyCycleLog {
        val key = dateString.ifBlank { id.toString() }
        return copy(
            symptoms = decryptFieldTracked(symptoms, "daily_cycle_logs:symptoms", "daily_cycle_logs", key, "symptoms"),
            mood = decryptFieldTracked(mood, "daily_cycle_logs:mood", "daily_cycle_logs", key, "mood"),
            notes = decryptFieldTracked(notes, "daily_cycle_logs:notes", "daily_cycle_logs", key, "notes")
        )
    }

    private fun UserNote.encryptForVault(): UserNote {
        val guarded = BackendSecurityFirewall.guardUserNote(this)
        return guarded.copy(
            content = LocalVaultCrypto.encryptField(guarded.content, "user_notes:content")
        )
    }

    private fun UserNote.decryptFromVault(): UserNote {
        val key = id.toString()
        return copy(
            content = decryptFieldTracked(content, "user_notes:content", "user_notes", key, "content")
        )
    }

    private fun Medication.encryptForVault(): Medication {
        val guarded = BackendSecurityFirewall.guardMedication(this)
        return guarded.copy(
            name = LocalVaultCrypto.encryptField(guarded.name, "medications:name"),
            dosage = LocalVaultCrypto.encryptField(guarded.dosage, "medications:dosage"),
            customReminderMessage = LocalVaultCrypto.encryptField(
                guarded.customReminderMessage,
                "medications:customReminderMessage"
            ),
            instructions = LocalVaultCrypto.encryptField(guarded.instructions, "medications:instructions")
        )
    }

    private fun Medication.decryptFromVault(): Medication {
        val key = id.toString()
        return copy(
            name = decryptFieldTracked(name, "medications:name", "medications", key, "name"),
            dosage = decryptFieldTracked(dosage, "medications:dosage", "medications", key, "dosage"),
            customReminderMessage = decryptFieldTracked(
                customReminderMessage,
                "medications:customReminderMessage",
                "medications",
                key,
                "customReminderMessage"
            ),
            instructions = decryptFieldTracked(instructions, "medications:instructions", "medications", key, "instructions")
        )
    }

    private fun ShopItem.encryptForVault(): ShopItem {
        val guarded = BackendSecurityFirewall.guardShopItem(this)
        return guarded.copy(
            text = LocalVaultCrypto.encryptField(guarded.text, "shop_items:text"),
            notes = LocalVaultCrypto.encryptField(guarded.notes, "shop_items:notes")
        )
    }

    private fun ShopItem.decryptFromVault(): ShopItem {
        val key = id.toString()
        return copy(
            text = decryptFieldTracked(text, "shop_items:text", "shop_items", key, "text"),
            notes = decryptFieldTracked(notes, "shop_items:notes", "shop_items", key, "notes")
        )
    }

    private fun CoupleRoomRecord.encryptForVault(): CoupleRoomRecord {
        val guarded = BackendSecurityFirewall.guardCoupleRoom(this)
        val code = guarded.coupleCode
        return guarded.copy(
            myName = LocalVaultCrypto.encryptField(guarded.myName, "couple_rooms:$code:myName"),
            partnerName = LocalVaultCrypto.encryptField(guarded.partnerName, "couple_rooms:$code:partnerName"),
            pendingPartnerName = LocalVaultCrypto.encryptField(guarded.pendingPartnerName, "couple_rooms:$code:pendingPartnerName"),
            doodleStrokesJson = LocalVaultCrypto.encryptField(guarded.doodleStrokesJson, "couple_rooms:$code:doodleStrokesJson"),
            doodlePrompt = LocalVaultCrypto.encryptField(guarded.doodlePrompt, "couple_rooms:$code:doodlePrompt"),
            quizStateJson = LocalVaultCrypto.encryptField(guarded.quizStateJson, "couple_rooms:$code:quizStateJson"),
            wyrStateJson = LocalVaultCrypto.encryptField(guarded.wyrStateJson, "couple_rooms:$code:wyrStateJson"),
            ticTacToeStateJson = LocalVaultCrypto.encryptField(guarded.ticTacToeStateJson, "couple_rooms:$code:ticTacToeStateJson"),
            dareStateJson = LocalVaultCrypto.encryptField(guarded.dareStateJson, "couple_rooms:$code:dareStateJson"),
            wavelengthStateJson = LocalVaultCrypto.encryptField(guarded.wavelengthStateJson, "couple_rooms:$code:wavelengthStateJson"),
            whisperStateJson = LocalVaultCrypto.encryptField(guarded.whisperStateJson, "couple_rooms:$code:whisperStateJson"),
            blockchainLedgerJson = LocalVaultCrypto.encryptField(guarded.blockchainLedgerJson, "couple_rooms:$code:blockchainLedgerJson"),
            ecdhPrivateKeyB64 = LocalVaultCrypto.encryptField(guarded.ecdhPrivateKeyB64, "couple_rooms:$code:ecdhPrivateKeyB64"),
            ecdhSharedSecretB64 = LocalVaultCrypto.encryptField(guarded.ecdhSharedSecretB64, "couple_rooms:$code:ecdhSharedSecretB64"),
            partnerCycleSyncJson = LocalVaultCrypto.encryptField(guarded.partnerCycleSyncJson, "couple_rooms:$code:partnerCycleSyncJson")
        )
    }

    private fun CoupleRoomRecord.decryptFromVault(): CoupleRoomRecord {
        val code = coupleCode
        return copy(
            myName = decryptFieldTracked(myName, "couple_rooms:$code:myName", "couple_rooms", code, "myName"),
            partnerName = decryptFieldTracked(partnerName, "couple_rooms:$code:partnerName", "couple_rooms", code, "partnerName"),
            pendingPartnerName = decryptFieldTracked(pendingPartnerName, "couple_rooms:$code:pendingPartnerName", "couple_rooms", code, "pendingPartnerName"),
            doodleStrokesJson = decryptFieldTracked(doodleStrokesJson, "couple_rooms:$code:doodleStrokesJson", "couple_rooms", code, "doodleStrokesJson", errorFallback = "[]").ifBlank { "[]" },
            doodlePrompt = decryptFieldTracked(doodlePrompt, "couple_rooms:$code:doodlePrompt", "couple_rooms", code, "doodlePrompt"),
            quizStateJson = decryptFieldTracked(quizStateJson, "couple_rooms:$code:quizStateJson", "couple_rooms", code, "quizStateJson", errorFallback = "{}").ifBlank { "{}" },
            wyrStateJson = decryptFieldTracked(wyrStateJson, "couple_rooms:$code:wyrStateJson", "couple_rooms", code, "wyrStateJson", errorFallback = "{}").ifBlank { "{}" },
            ticTacToeStateJson = decryptFieldTracked(ticTacToeStateJson, "couple_rooms:$code:ticTacToeStateJson", "couple_rooms", code, "ticTacToeStateJson", errorFallback = "{}").ifBlank { "{}" },
            dareStateJson = decryptFieldTracked(dareStateJson, "couple_rooms:$code:dareStateJson", "couple_rooms", code, "dareStateJson", errorFallback = "{}").ifBlank { "{}" },
            wavelengthStateJson = decryptFieldTracked(wavelengthStateJson, "couple_rooms:$code:wavelengthStateJson", "couple_rooms", code, "wavelengthStateJson", errorFallback = "{}").ifBlank { "{}" },
            whisperStateJson = decryptFieldTracked(whisperStateJson, "couple_rooms:$code:whisperStateJson", "couple_rooms", code, "whisperStateJson", errorFallback = "{}").ifBlank { "{}" },
            blockchainLedgerJson = decryptFieldTracked(blockchainLedgerJson, "couple_rooms:$code:blockchainLedgerJson", "couple_rooms", code, "blockchainLedgerJson", errorFallback = "[]").ifBlank { "[]" },
            ecdhPrivateKeyB64 = decryptFieldTracked(ecdhPrivateKeyB64, "couple_rooms:$code:ecdhPrivateKeyB64", "couple_rooms", code, "ecdhPrivateKeyB64", errorFallback = ""),
            ecdhSharedSecretB64 = decryptFieldTracked(ecdhSharedSecretB64, "couple_rooms:$code:ecdhSharedSecretB64", "couple_rooms", code, "ecdhSharedSecretB64", errorFallback = ""),
            partnerCycleSyncJson = decryptFieldTracked(partnerCycleSyncJson, "couple_rooms:$code:partnerCycleSyncJson", "couple_rooms", code, "partnerCycleSyncJson", errorFallback = "{}").ifBlank { "{}" }
        )
    }

    /**
     * One-time background upgrade that transparently encrypts any pre-existing plaintext rows
     * in SQLite using the hardware-backed Android Keystore AES-256-GCM vault.
     * Fail-closed: aborts immediately if the vault key is unavailable or any existing encrypted
     * field fails authenticated decryption, guaranteeing valid ciphertext is never overwritten.
     */
    suspend fun migratePlaintextRecordsToEncryptedVault() = withContext(Dispatchers.IO) {
        if (!LocalVaultCrypto.canSafelyWrite()) return@withContext
        try {
            database.userProfileDao().getProfileOnce()?.let { raw ->
                if (raw.name.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.name)) {
                    val dec = LocalVaultCrypto.decryptFieldResult(raw.name, "user_profile:name")
                    if (dec.isSuccess) {
                        database.userProfileDao().insertOrUpdate(raw.copy(name = dec.getOrThrow()).encryptForVault())
                    }
                }
            }
            database.periodLogDao().getAllPeriods().firstOrNull()?.forEach { raw ->
                if (raw.notes.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.notes)) {
                    val dec = LocalVaultCrypto.decryptFieldResult(raw.notes, "period_logs:notes")
                    if (dec.isSuccess) {
                        database.periodLogDao().update(raw.copy(notes = dec.getOrThrow()).encryptForVault())
                    }
                }
            }
            database.dailyCycleLogDao().getAllLogs().firstOrNull()?.forEach { raw ->
                val needsEnc = (raw.symptoms.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.symptoms)) ||
                    (raw.mood.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.mood)) ||
                    (raw.notes.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.notes))
                if (needsEnc) {
                    val s = LocalVaultCrypto.decryptFieldResult(raw.symptoms, "daily_cycle_logs:symptoms")
                    val m = LocalVaultCrypto.decryptFieldResult(raw.mood, "daily_cycle_logs:mood")
                    val n = LocalVaultCrypto.decryptFieldResult(raw.notes, "daily_cycle_logs:notes")
                    if (s.isSuccess && m.isSuccess && n.isSuccess) {
                        database.dailyCycleLogDao().insertOrUpdate(
                            raw.copy(symptoms = s.getOrThrow(), mood = m.getOrThrow(), notes = n.getOrThrow()).encryptForVault()
                        )
                    }
                }
            }
            database.userNoteDao().getAllNotes().firstOrNull()?.forEach { raw ->
                if (raw.content.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.content)) {
                    val dec = LocalVaultCrypto.decryptFieldResult(raw.content, "user_notes:content")
                    if (dec.isSuccess) {
                        database.userNoteDao().insert(raw.copy(content = dec.getOrThrow()).encryptForVault())
                    }
                }
            }
            database.medicationDao().getAllMedications().firstOrNull()?.forEach { raw ->
                val needsEnc = (raw.name.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.name)) ||
                    (raw.dosage.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.dosage)) ||
                    (raw.customReminderMessage.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.customReminderMessage)) ||
                    (raw.instructions.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.instructions))
                if (needsEnc) {
                    val n = LocalVaultCrypto.decryptFieldResult(raw.name, "medications:name")
                    val d = LocalVaultCrypto.decryptFieldResult(raw.dosage, "medications:dosage")
                    val c = LocalVaultCrypto.decryptFieldResult(raw.customReminderMessage, "medications:customReminderMessage")
                    val i = LocalVaultCrypto.decryptFieldResult(raw.instructions, "medications:instructions")
                    if (n.isSuccess && d.isSuccess && c.isSuccess && i.isSuccess) {
                        database.medicationDao().update(
                            raw.copy(
                                name = n.getOrThrow(),
                                dosage = d.getOrThrow(),
                                customReminderMessage = c.getOrThrow(),
                                instructions = i.getOrThrow()
                            ).encryptForVault()
                        )
                    }
                }
            }
            database.shopItemDao().getAllItems().firstOrNull()?.forEach { raw ->
                val needsEnc = (raw.text.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.text)) ||
                    (raw.notes.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(raw.notes))
                if (needsEnc) {
                    val t = LocalVaultCrypto.decryptFieldResult(raw.text, "shop_items:text")
                    val n = LocalVaultCrypto.decryptFieldResult(raw.notes, "shop_items:notes")
                    if (t.isSuccess && n.isSuccess) {
                        database.shopItemDao().update(raw.copy(text = t.getOrThrow(), notes = n.getOrThrow()).encryptForVault())
                    }
                }
            }
            database.coupleRoomDao().getAllRooms().firstOrNull()?.forEach { raw ->
                val code = raw.coupleCode
                val encryptedColumns = listOf(
                    raw.myName to "couple_rooms:$code:myName",
                    raw.partnerName to "couple_rooms:$code:partnerName",
                    raw.pendingPartnerName to "couple_rooms:$code:pendingPartnerName",
                    raw.doodleStrokesJson to "couple_rooms:$code:doodleStrokesJson",
                    raw.doodlePrompt to "couple_rooms:$code:doodlePrompt",
                    raw.quizStateJson to "couple_rooms:$code:quizStateJson",
                    raw.wyrStateJson to "couple_rooms:$code:wyrStateJson",
                    raw.ticTacToeStateJson to "couple_rooms:$code:ticTacToeStateJson",
                    raw.dareStateJson to "couple_rooms:$code:dareStateJson",
                    raw.wavelengthStateJson to "couple_rooms:$code:wavelengthStateJson",
                    raw.whisperStateJson to "couple_rooms:$code:whisperStateJson",
                    raw.blockchainLedgerJson to "couple_rooms:$code:blockchainLedgerJson",
                    raw.ecdhPrivateKeyB64 to "couple_rooms:$code:ecdhPrivateKeyB64",
                    raw.ecdhSharedSecretB64 to "couple_rooms:$code:ecdhSharedSecretB64",
                    raw.partnerCycleSyncJson to "couple_rooms:$code:partnerCycleSyncJson"
                )
                val needsEnc = encryptedColumns.any { (v, _) -> v.isNotEmpty() && !LocalVaultCrypto.isLatestFormat(v) }
                if (needsEnc) {
                    val decrypted = encryptedColumns.map { (v, aad) -> LocalVaultCrypto.decryptFieldResult(v, aad) }
                    if (decrypted.all { it.isSuccess }) {
                        database.coupleRoomDao().insertOrUpdate(
                            raw.copy(
                                myName = decrypted[0].getOrThrow(),
                                partnerName = decrypted[1].getOrThrow(),
                                pendingPartnerName = decrypted[2].getOrThrow(),
                                doodleStrokesJson = decrypted[3].getOrThrow().ifBlank { "[]" },
                                doodlePrompt = decrypted[4].getOrThrow(),
                                quizStateJson = decrypted[5].getOrThrow().ifBlank { "{}" },
                                wyrStateJson = decrypted[6].getOrThrow().ifBlank { "{}" },
                                ticTacToeStateJson = decrypted[7].getOrThrow().ifBlank { "{}" },
                                dareStateJson = decrypted[8].getOrThrow().ifBlank { "{}" },
                                wavelengthStateJson = decrypted[9].getOrThrow().ifBlank { "{}" },
                                whisperStateJson = decrypted[10].getOrThrow().ifBlank { "{}" },
                                blockchainLedgerJson = decrypted[11].getOrThrow().ifBlank { "[]" },
                                ecdhPrivateKeyB64 = decrypted[12].getOrThrow(),
                                ecdhSharedSecretB64 = decrypted[13].getOrThrow(),
                                partnerCycleSyncJson = decrypted[14].getOrThrow().ifBlank { "{}" }
                            ).encryptForVault()
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Non-fatal: vault remains fail-closed and preserves existing ciphertext
        }
    }

    // --- Decrypted One-Shot Readers (used by NotificationReceiver & ViewModel) ---

    suspend fun getProfileOnce(): UserProfile? = withContext(Dispatchers.IO) {
        database.userProfileDao().getProfileOnce()?.decryptFromVault()
    }

    suspend fun getPeriodLogsOnce(): List<PeriodLog> = withContext(Dispatchers.IO) {
        database.periodLogDao().getAllPeriods().firstOrNull()?.map { it.decryptFromVault() } ?: emptyList()
    }

    suspend fun getMedicationsOnce(): List<Medication> = withContext(Dispatchers.IO) {
        database.medicationDao().getAllMedications().firstOrNull()?.map { it.decryptFromVault() } ?: emptyList()
    }

    suspend fun getMedicationById(id: Long): Medication? = withContext(Dispatchers.IO) {
        database.medicationDao().getById(id)?.decryptFromVault()
    }

    suspend fun getActiveNoteRemindersOnce(): List<UserNote> = withContext(Dispatchers.IO) {
        database.userNoteDao().getActiveRemindersList().map { it.decryptFromVault() }
    }

    suspend fun getAllNotesOnce(): List<UserNote> = withContext(Dispatchers.IO) {
        database.userNoteDao().getAllNotes().firstOrNull()?.map { it.decryptFromVault() } ?: emptyList()
    }

    suspend fun markNoteReminderFired(id: Long, reminderTimeMillis: Long?, reminderFired: Boolean) = withContext(Dispatchers.IO) {
        database.userNoteDao().updateReminder(id, reminderTimeMillis, reminderFired)
    }

    // --- Cycle & Daily Logs ---

    fun getDailyCycleLog(dateString: String): Flow<DailyCycleLog?> {
        return database.dailyCycleLogDao()
            .getLogForDate(BackendSecurityFirewall.sanitizeField(dateString, 10))
            .map { it?.decryptFromVault() }
            .flowOn(Dispatchers.Default)
    }

    suspend fun saveDailyCycleLog(log: DailyCycleLog): Long = withContext(Dispatchers.IO) {
        val key = log.dateString.ifBlank { log.id.toString() }
        requireWritableRecord("daily_cycle_logs", key, log.symptoms, log.mood, log.notes)
        database.dailyCycleLogDao().insertOrUpdate(log.encryptForVault())
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

    suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        requireWritableRecord("user_profile", profile.id.toString(), profile.name)
        database.userProfileDao().insertOrUpdate(profile.encryptForVault())
    }

    suspend fun updateShopPreferences(
        hideCompleted: Boolean,
        viewMode: String,
        sortMode: String
    ) {
        requireWritableRecord("user_profile", "1")
        val raw = database.userProfileDao().getProfileOnce() ?: return
        val decName = LocalVaultCrypto.decryptFieldResult(raw.name, "user_profile:name")
            .getOrElse {
                recordDecryptionFailure("user_profile", "1", "name", it)
                throw LocalVaultUnavailableException(
                    LocalVaultCrypto.vaultState.value,
                    "Refusing profile preference write because profile name failed decryption."
                )
            }
        database.userProfileDao().insertOrUpdate(
            raw.copy(
                name = decName,
                shopHideCompleted = hideCompleted,
                shopViewMode = viewMode,
                shopSortMode = sortMode
            ).encryptForVault()
        )
    }

    suspend fun logPeriod(
        startDateMillis: Long,
        notes: String = "",
        durationDays: Int? = null,
        endDateMillis: Long? = null
    ): Long {
        requireHealthyVaultForWrite()
        return database.periodLogDao().insert(
            PeriodLog(
                startDateMillis = startDateMillis,
                notes = notes,
                durationDays = durationDays,
                endDateMillis = endDateMillis
            ).encryptForVault()
        )
    }

    suspend fun endPeriod(id: Long, endDateMillis: Long) {
        requireHealthyVaultForWrite()
        val raw = database.periodLogDao().getById(id) ?: return
        val decNotes = LocalVaultCrypto.decryptFieldResult(raw.notes, "period_logs:notes")
            .getOrElse { throw it }
        val existing = raw.copy(notes = decNotes)
        val calculatedDuration = kotlin.math.max(
            1,
            kotlin.math.round((endDateMillis - existing.startDateMillis).toDouble() / (24L * 60 * 60 * 1000)).toInt() + 1
        )
        database.periodLogDao().update(
            existing.copy(
                endDateMillis = endDateMillis,
                durationDays = calculatedDuration
            ).encryptForVault()
        )
    }

    suspend fun updatePeriod(period: PeriodLog) {
        requireWritableRecord("period_logs", period.id.toString(), period.notes)
        database.periodLogDao().update(period.encryptForVault())
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
        requireWritableRecord("shop_items", item.id.toString(), item.text, item.notes)
        database.shopItemDao().update(item.copy(isDone = !item.isDone).encryptForVault())
    }

    suspend fun updateShopItem(item: ShopItem) {
        requireWritableRecord("shop_items", item.id.toString(), item.text, item.notes)
        database.shopItemDao().update(item.encryptForVault())
    }

    suspend fun addShopItem(
        text: String,
        category: String = "general",
        isCustom: Boolean = true,
        notes: String = "",
        priority: String = "NORMAL"
    ): Long {
        requireHealthyVaultForWrite()
        val count = database.shopItemDao().count()
        return database.shopItemDao().insert(
            ShopItem(
                text = text.trim(),
                phaseCategory = category,
                isCustom = isCustom,
                orderIndex = count,
                notes = notes.trim(),
                priority = priority
            ).encryptForVault()
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
        ).map { it.encryptForVault() }
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
        requireHealthyVaultForWrite()
        return database.medicationDao().insert(
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
            ).encryptForVault()
        )
    }

    suspend fun updateMedication(medication: Medication) {
        requireWritableRecord(
            "medications",
            medication.id.toString(),
            medication.name,
            medication.dosage,
            medication.customReminderMessage,
            medication.instructions
        )
        database.medicationDao().update(medication.encryptForVault())
    }

    suspend fun deleteMedication(id: Long) {
        database.medicationDao().deleteById(id)
    }

    private suspend fun decryptRawMedicationStrictly(id: Long): Medication? = withContext(Dispatchers.IO) {
        val raw = database.medicationDao().getById(id) ?: return@withContext null
        val key = id.toString()
        val n = LocalVaultCrypto.decryptFieldResult(raw.name, "medications:name")
            .getOrElse { recordDecryptionFailure("medications", key, "name", it); throw LocalVaultUnavailableException(LocalVaultCrypto.vaultState.value, "Medication name failed decryption; write blocked.") }
        val d = LocalVaultCrypto.decryptFieldResult(raw.dosage, "medications:dosage")
            .getOrElse { recordDecryptionFailure("medications", key, "dosage", it); throw LocalVaultUnavailableException(LocalVaultCrypto.vaultState.value, "Medication dosage failed decryption; write blocked.") }
        val m = LocalVaultCrypto.decryptFieldResult(raw.customReminderMessage, "medications:customReminderMessage")
            .getOrElse { recordDecryptionFailure("medications", key, "customReminderMessage", it); throw LocalVaultUnavailableException(LocalVaultCrypto.vaultState.value, "Medication message failed decryption; write blocked.") }
        val i = LocalVaultCrypto.decryptFieldResult(raw.instructions, "medications:instructions")
            .getOrElse { recordDecryptionFailure("medications", key, "instructions", it); throw LocalVaultUnavailableException(LocalVaultCrypto.vaultState.value, "Medication instructions failed decryption; write blocked.") }
        raw.copy(name = n, dosage = d, customReminderMessage = m, instructions = i)
    }

    suspend fun toggleMedication(id: Long, isEnabled: Boolean) {
        requireWritableRecord("medications", id.toString())
        val existing = decryptRawMedicationStrictly(id) ?: return
        database.medicationDao().update(existing.copy(isEnabled = isEnabled).encryptForVault())
    }

    suspend fun markMedicationTakenToday(id: Long, todayDateString: String, timeString: String = "") {
        requireWritableRecord("medications", id.toString())
        val existing = decryptRawMedicationStrictly(id) ?: return
        database.medicationDao().update(
            existing.copy(
                lastTakenDate = BackendSecurityFirewall.sanitizeField(todayDateString, 10),
                lastTakenTime = BackendSecurityFirewall.sanitizeField(timeString, 16)
            ).encryptForVault()
        )
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
                ).encryptForVault()
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
        requireHealthyVaultForWrite()
        return database.userNoteDao().insert(
            UserNote(
                content = content.trim(),
                tag = tag,
                timestampMillis = System.currentTimeMillis(),
                reminderTimeMillis = reminderTimeMillis,
                reminderFired = false
            ).encryptForVault()
        )
    }

    suspend fun updateNoteReminder(id: Long, reminderTimeMillis: Long?) {
        database.userNoteDao().updateReminder(id, reminderTimeMillis, reminderFired = false)
    }

    suspend fun getNoteById(id: Long): UserNote? {
        return database.userNoteDao().getById(id)?.decryptFromVault()
    }

    suspend fun deleteNote(id: Long) {
        database.userNoteDao().deleteById(id)
    }

    /**
     * Intentionally preserves an empty user_profile table on first launch so first-time users
     * always see the Male / Female onboarding screen rather than auto-skipping with a hardcoded profile.
     * Existing onboarded users keep their persisted profile untouched.
     */
    suspend fun checkAndSeedUserProfileIfEmpty() {
        // No-op on fresh install: OnboardingScreen collects gender (Male/Female) and creates UserProfile.
    }

    // Arcade & Couple Lounge persistence
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

    suspend fun getCoupleRoom(code: String): CoupleRoomRecord? = withContext(Dispatchers.IO) {
        val cleanCode = code.filter { it.isDigit() }.take(6)
        database.coupleRoomDao().getRoomByCode(cleanCode)?.decryptFromVault()
    }

    suspend fun saveCoupleRoom(room: CoupleRoomRecord) = withContext(Dispatchers.IO) {
        requireWritableRecord("couple_rooms", room.coupleCode, room.myName, room.partnerName, room.pendingPartnerName, room.doodlePrompt)
        val encrypted = room.copy(updatedAt = System.currentTimeMillis()).encryptForVault()
        database.coupleRoomDao().insertOrUpdate(encrypted)
    }

    suspend fun deleteCoupleRoom(code: String) = withContext(Dispatchers.IO) {
        database.coupleRoomDao().deleteByCode(code)
        database.pendingCoupleTurnDao().deleteByRoom(code)
    }

    // --- Durable Encrypted Offline Pending Couple Turn Queue ---

    suspend fun enqueuePendingCoupleTurn(
        coupleCode: String,
        intent: CoupleActivityIntent
    ): PendingCoupleTurn = withContext(Dispatchers.IO) {
        requireHealthyVaultForWrite()
        val cleanCode = coupleCode.filter { it.isDigit() }.take(6)
        val existing = database.pendingCoupleTurnDao().getByClientTurnId(intent.clientTurnId)
        if (existing != null) return@withContext existing

        val aad = "pending_couple_turns:$cleanCode:${intent.clientTurnId}"
        val encryptedJson = LocalVaultCrypto.encryptField(intent.toJsonString(), aad)
        val now = System.currentTimeMillis()
        val record = PendingCoupleTurn(
            clientTurnId = intent.clientTurnId,
            coupleCode = cleanCode,
            actionType = BackendSecurityFirewall.sanitizeField(intent.actionType, 24),
            encryptedIntentJson = encryptedJson,
            retryCount = 0,
            status = "PENDING",
            lastError = "",
            createdAt = now,
            updatedAt = now
        )
        val id = database.pendingCoupleTurnDao().insert(record)
        record.copy(id = if (id > 0) id else 0L)
    }

    fun decryptPendingIntent(turn: PendingCoupleTurn): Result<CoupleActivityIntent> {
        val aad = "pending_couple_turns:${turn.coupleCode}:${turn.clientTurnId}"
        return LocalVaultCrypto.decryptFieldResult(turn.encryptedIntentJson, aad).mapCatching { json ->
            CoupleActivityIntent.fromJsonString(json)
                ?: throw IllegalStateException("Corrupted pending turn intent payload.")
        }
    }

    suspend fun getPendingTurnsForRoomOnce(coupleCode: String): List<PendingCoupleTurn> = withContext(Dispatchers.IO) {
        val clean = coupleCode.filter { it.isDigit() }.take(6)
        database.pendingCoupleTurnDao().getPendingForRoomOnce(clean)
    }

    suspend fun getAllPendingTurnsOnce(): List<PendingCoupleTurn> = withContext(Dispatchers.IO) {
        database.pendingCoupleTurnDao().getAllPendingOnce()
    }

    suspend fun markPendingTurnStatus(
        turn: PendingCoupleTurn,
        status: String,
        retryCount: Int = turn.retryCount,
        lastError: String = ""
    ) = withContext(Dispatchers.IO) {
        database.pendingCoupleTurnDao().update(
            turn.copy(
                status = status,
                retryCount = retryCount,
                lastError = BackendSecurityFirewall.sanitizeField(lastError, 240),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removePendingTurn(clientTurnId: String) = withContext(Dispatchers.IO) {
        database.pendingCoupleTurnDao().deleteByClientTurnId(clientTurnId)
    }

    suspend fun clearPendingTurnsForRoom(coupleCode: String) = withContext(Dispatchers.IO) {
        database.pendingCoupleTurnDao().deleteByRoom(coupleCode.filter { it.isDigit() }.take(6))
    }

    /**
     * Wipes all local tables and performs explicit cryptographic erasure of the AndroidKeyStore
     * KEK and wrapped DEK before re-seeding clean defaults.
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.periodLogDao().clearAll()
        database.shopItemDao().clearAll()
        database.taskCategoryDao().clearAll()
        database.medicationDao().clearAll()
        database.waterLogDao().clearAll()
        database.userNoteDao().clearAll()
        database.dailyCycleLogDao().clearAll()
        database.gameScoreDao().clearAll()
        database.coupleRoomDao().clearAll()
        database.pendingCoupleTurnDao().clearAll()
        clearDecryptionTracking()
        LocalVaultCrypto.cryptographicEraseAndResetVault()
        if (LocalVaultCrypto.canSafelyWrite()) {
            database.userProfileDao().insertOrUpdate(UserProfile(id = 1, isOnboarded = false).encryptForVault())
            checkAndSeedCategoriesIfEmpty()
            checkAndSeedShopItemsIfEmpty()
            checkAndSeedMedicineRemindersIfEmpty()
        }
    }

    /**
     * Explicit user-confirmed recovery wipe when AndroidKeyStore KEK is permanently invalidated.
     */
    suspend fun resetCorruptedVaultAndClearAllData() = clearAllData()
}
