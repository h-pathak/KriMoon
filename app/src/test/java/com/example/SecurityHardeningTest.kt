package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PhasesRepository
import com.example.model.ChainVerificationScope
import com.example.model.CoupleActivityIntent
import com.example.model.CoupleBlockchain
import com.example.model.CoupleEcdhEngine
import com.example.model.CoupleTurnCodec
import com.example.model.DecodedTurnPacket
import com.example.model.SecretWhisperNote
import com.example.model.SpiceLevel
import com.example.security.BackendSecurityFirewall
import com.example.security.LocalVaultCrypto
import com.example.security.LocalVaultUnavailableException
import com.example.security.VaultState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import android.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityHardeningTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        tempDir = File(ctx.cacheDir, "vault_test_${System.nanoTime()}").apply { mkdirs() }
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
    }

    @After
    fun tearDown() {
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
        tempDir.deleteRecursively()
    }

    @Test
    fun `LocalVaultCrypto encrypts with AES-256-GCM unique IVs and decrypts cleanly`() {
        val secretDiary = "Private cycle note: mild cramps and tea before bed."
        val aad = "user_notes:content"

        val cipher1 = LocalVaultCrypto.encryptField(secretDiary, aad)
        val cipher2 = LocalVaultCrypto.encryptField(secretDiary, aad)

        assertTrue(cipher1.startsWith(LocalVaultCrypto.VAULT_PREFIX_V2))
        assertTrue(LocalVaultCrypto.isEncrypted(cipher1))
        assertTrue(LocalVaultCrypto.isLatestFormat(cipher1))
        assertFalse(cipher1.contains("Private cycle note"))
        assertNotEquals("Each encryption must use a unique 96-bit IV", cipher1, cipher2)

        assertEquals(secretDiary, LocalVaultCrypto.decryptField(cipher1, aad))
        assertEquals(secretDiary, LocalVaultCrypto.decryptField(cipher2, aad))
    }

    @Test
    fun `LocalVaultCrypto rejects AAD column swap and ciphertext tampering`() {
        val sensitiveNote = "Doctor appointment notes"
        val cipher = LocalVaultCrypto.encryptField(sensitiveNote, "user_notes:content")

        // Swapping ciphertext into a different column AAD context must fail GCM tag verification
        val swappedResult = LocalVaultCrypto.decryptFieldResult(cipher, "user_profile:name")
        assertTrue(swappedResult.isFailure)
        assertEquals("", LocalVaultCrypto.decryptField(cipher, "user_profile:name"))

        // Bit-flipping ciphertext must fail GCM tag verification
        val tampered = cipher.dropLast(2) + if (cipher.endsWith("AA")) "BB" else "AA"
        assertTrue(LocalVaultCrypto.decryptFieldResult(tampered, "user_notes:content").isFailure)
    }

    @Test
    fun `LocalVaultCrypto fails closed without overwriting wrapped DEK file or existing ciphertext`() {
        // Simulate an existing wrapped DEK blob persisted on disk from a prior healthy install
        val dekFile = File(tempDir, "krimoon_vault_dek_v2.bin")
        val originalWrappedBytes = ByteArray(60) { (it * 7 + 3).toByte() }
        dekFile.writeBytes(originalWrappedBytes)

        // Disable test ephemeral fallback to exercise production fail-closed behavior when Keystore KEK is missing
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = false)

        val state = LocalVaultCrypto.initializeAndVerify(ApplicationProvider.getApplicationContext())
        assertEquals(VaultState.KEY_CORRUPTED, state)
        assertFalse("Vault must refuse writes when KEK cannot unwrap existing DEK", LocalVaultCrypto.canSafelyWrite())

        // Attempting to encrypt MUST throw LocalVaultUnavailableException (never silent ephemeral fallback)
        try {
            LocalVaultCrypto.encryptField("Sensitive symptom log", "daily_cycle_logs:notes")
            fail("Expected LocalVaultUnavailableException when vault is KEY_CORRUPTED")
        } catch (e: LocalVaultUnavailableException) {
            assertEquals(VaultState.KEY_CORRUPTED, e.vaultState)
        }

        // Wrapped DEK file bytes on disk MUST remain completely untouched
        assertTrue(dekFile.exists())
        assertArrayEquals(
            "Existing wrapped DEK file must never be overwritten on unwrap failure",
            originalWrappedBytes,
            dekFile.readBytes()
        )

        // Explicit user-confirmed cryptographic reset clears the corrupt DEK and restores usability in test env
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
        LocalVaultCrypto.cryptographicEraseAndResetVault()
        assertFalse("Explicit vault reset should remove unreadable DEK file", dekFile.exists())
        assertTrue(LocalVaultCrypto.canSafelyWrite())
    }

    @Test
    fun `LocalVaultCrypto simulated KEY_UNAVAILABLE blocks writes and rewrap preserves readability`() {
        val cipher = LocalVaultCrypto.encryptField("Cycle entry before lock", "period_logs:notes")
        assertTrue(LocalVaultCrypto.rewrapDekWithFreshKek())
        assertEquals("Cycle entry before lock", LocalVaultCrypto.decryptField(cipher, "period_logs:notes"))

        LocalVaultCrypto.simulateKeystoreFailureForTest(VaultState.KEY_UNAVAILABLE)
        assertFalse(LocalVaultCrypto.canSafelyWrite())
        assertEquals(VaultState.KEY_UNAVAILABLE, LocalVaultCrypto.vaultState.value)
        assertTrue(LocalVaultCrypto.decryptFieldResult(cipher, "period_logs:notes").isFailure)

        try {
            LocalVaultCrypto.encryptField("Should fail closed", "period_logs:notes")
            fail("Expected LocalVaultUnavailableException")
        } catch (e: LocalVaultUnavailableException) {
            assertEquals(VaultState.KEY_UNAVAILABLE, e.vaultState)
        }

        // Explicit cryptographic reset makes old ciphertext permanently unrecoverable and restores fresh key
        LocalVaultCrypto.cryptographicEraseAndResetVault()
        assertTrue(LocalVaultCrypto.canSafelyWrite())
        assertTrue(LocalVaultCrypto.decryptFieldResult(cipher, "period_logs:notes").isFailure)
        val freshCipher = LocalVaultCrypto.encryptField("Post-reset entry", "period_logs:notes")
        assertEquals("Post-reset entry", LocalVaultCrypto.decryptField(freshCipher, "period_logs:notes"))
    }

    @Test
    fun `CoupleEcdhEngine enforces strict NIST P-256 curve validation and rejects non-P256 keys`() {
        val coupleCode = "482917"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()

        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(hostKeys.publicKeyB64))
        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(partnerKeys.publicKeyB64))
        assertFalse(CoupleEcdhEngine.isValidPublicKeyB64("not-a-valid-key"))
        assertFalse(CoupleEcdhEngine.isValidPublicKeyB64(""))

        // Non-P256 EC curves (secp384r1 / secp521r1) MUST be rejected by strict domain parameter check
        val p384Pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        val p384PubB64 = Base64.encodeToString(p384Pair.public.encoded, Base64.NO_WRAP)
        assertFalse("P-384 key must be rejected by strict NIST P-256 validator", CoupleEcdhEngine.isValidPublicKeyB64(p384PubB64))
        assertEquals(
            null,
            CoupleEcdhEngine.deriveSharedSecretB64(
                myPrivateKeyB64 = hostKeys.privateKeyB64,
                peerPublicKeyB64 = p384PubB64,
                coupleCode = coupleCode,
                myPublicKeyB64 = hostKeys.publicKeyB64
            )
        )

        // Non-EC (RSA) public key MUST be rejected
        val rsaPub = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        val rsaPubB64 = Base64.encodeToString(rsaPub.encoded, Base64.NO_WRAP)
        assertFalse("RSA public key must be rejected", CoupleEcdhEngine.isValidPublicKeyB64(rsaPubB64))

        // Valid NIST P-256 keys derive identical shared secrets and safety fingerprints
        val hostSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            myPrivateKeyB64 = hostKeys.privateKeyB64,
            peerPublicKeyB64 = partnerKeys.publicKeyB64,
            coupleCode = coupleCode,
            myPublicKeyB64 = hostKeys.publicKeyB64
        )
        val partnerSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            myPrivateKeyB64 = partnerKeys.privateKeyB64,
            peerPublicKeyB64 = hostKeys.publicKeyB64,
            coupleCode = coupleCode,
            myPublicKeyB64 = partnerKeys.publicKeyB64
        )

        assertNotNull(hostSecret)
        assertEquals(hostSecret, partnerSecret)

        val hostFp = CoupleEcdhEngine.safetyFingerprint(hostKeys.publicKeyB64, partnerKeys.publicKeyB64, coupleCode)
        val partnerFp = CoupleEcdhEngine.safetyFingerprint(partnerKeys.publicKeyB64, hostKeys.publicKeyB64, coupleCode)
        assertEquals(hostFp, partnerFp)
        assertTrue(hostFp.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}")))
    }

    @Test
    fun `CoupleBlockchain enforces strict sequential linkage, V1-to-V2 migration anchor, and honest pruned window`() {
        val code = "614928"
        val genesis = CoupleBlockchain.createDeterministicGenesisBlock(code)
        val b1 = CoupleBlockchain.mineBlock(1, "QUIZ", "Maya", genesis.blockHash, "p1", clientTurnId = "turn-1")
        val b2 = CoupleBlockchain.mineBlock(2, "WYR", "Leo", b1.blockHash, "p2", clientTurnId = "turn-2")
        val b3 = CoupleBlockchain.mineBlock(3, "DARE", "Maya", b2.blockHash, "p3", clientTurnId = "turn-3")

        // 1. Valid V2 chain from deterministic Genesis
        val validV2 = CoupleBlockchain.verifyChain(listOf(genesis, b1, b2, b3), code, schemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2)
        assertTrue(validV2.isValid)
        assertEquals(ChainVerificationScope.FULL_FROM_GENESIS, validV2.scope)
        assertEquals(4, validV2.verifiedBlockCount)

        // 2. Broken previousHash link MUST fail
        val badLinkB2 = CoupleBlockchain.mineBlock(2, "WYR", "Leo", "00deadbeef1234567890", "p2")
        val brokenLinkRes = CoupleBlockchain.verifyChain(listOf(genesis, b1, badLinkB2), code)
        assertFalse("Broken previousHash must fail verification", brokenLinkRes.isValid)
        assertEquals(ChainVerificationScope.INVALID, brokenLinkRes.scope)

        // 3. Skipped index (0 -> 1 -> 3) MUST fail
        val skippedRes = CoupleBlockchain.verifyChain(listOf(genesis, b1, b3), code)
        assertFalse("Skipped index must fail verification", skippedRes.isValid)

        // 4. Duplicate blockHash MUST fail
        val dupHashRes = CoupleBlockchain.verifyChain(listOf(genesis, b1, b1), code)
        assertFalse("Duplicate blockHash must fail verification", dupHashRes.isValid)

        // 5. Tampered PoW / hash MUST fail
        val tamperedPoW = b1.copy(blockHash = "ff" + b1.blockHash.drop(2))
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, tamperedPoW), code).isValid)

        // 6. V1->V2 migration anchor without mutating legacy V1 history
        val legacyV1Gen = CoupleBlockchain.createLegacyV1GenesisBlock(code, "Maya")
        val legacyB1 = CoupleBlockchain.mineBlock(1, "QUIZ", "Maya", legacyV1Gen.blockHash, "v1p1")
        val migratedB2 = CoupleBlockchain.mineBlock(2, "WYR", "Leo", legacyB1.blockHash, "v2p2", clientTurnId = "mig-2")
        val migratedRes = CoupleBlockchain.verifyChain(
            blocks = listOf(legacyV1Gen, legacyB1, migratedB2),
            coupleCode = code,
            schemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
            migrationAnchorHash = legacyB1.blockHash,
            migrationAnchorIndex = 2
        )
        assertTrue("Valid V1->V2 migration anchor must pass", migratedRes.isValid)
        assertEquals(ChainVerificationScope.MIGRATED_FROM_V1_ANCHOR, migratedRes.scope)

        val badAnchorRes = CoupleBlockchain.verifyChain(
            blocks = listOf(legacyV1Gen, legacyB1, migratedB2),
            coupleCode = code,
            schemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
            migrationAnchorHash = "00wronganchorhash",
            migrationAnchorIndex = 2
        )
        assertFalse("Mismatched V1->V2 migration anchor must fail", badAnchorRes.isValid)

        // 7. Pruned window honestly reports PRUNED_WINDOW_ONLY and validates prunedAnchorHash
        val prunedWindow = CoupleBlockchain.pruneLedgerWindow(
            blocks = listOf(genesis, b1, b2, b3),
            maxBlocks = 2
        )
        assertEquals(2, prunedWindow.newlyPrunedCount)
        assertEquals(b1.blockHash, prunedWindow.prunedAnchorHash)
        val prunedVerify = CoupleBlockchain.verifyChain(
            blocks = prunedWindow.retainedBlocks,
            coupleCode = code,
            prunedAnchorHash = prunedWindow.prunedAnchorHash,
            prunedBlockCount = prunedWindow.newlyPrunedCount
        )
        assertTrue(prunedVerify.isValid)
        assertEquals(ChainVerificationScope.PRUNED_WINDOW_ONLY, prunedVerify.scope)
        assertEquals(2, prunedVerify.prunedBlockCount)

        val badPrunedAnchor = CoupleBlockchain.verifyChain(
            blocks = prunedWindow.retainedBlocks,
            coupleCode = code,
            prunedAnchorHash = "00tamperedanchor",
            prunedBlockCount = prunedWindow.newlyPrunedCount
        )
        assertFalse("Tampered prunedAnchorHash must fail", badPrunedAnchor.isValid)
    }

    @Test
    fun `CoupleTurnCodec enforces v3 anti-downgrade, primary replay protection, and fresh-nonce rebasing`() {
        val coupleCode = "739154"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()
        val attackerKeys = CoupleEcdhEngine.generateKeyPair()

        val sharedSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            hostKeys.privateKeyB64, partnerKeys.publicKeyB64, coupleCode, hostKeys.publicKeyB64
        )!!
        val attackerSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            attackerKeys.privateKeyB64, hostKeys.publicKeyB64, coupleCode, attackerKeys.publicKeyB64
        )!!

        val genesis = CoupleBlockchain.createDeterministicGenesisBlock(coupleCode)
        val secretWhisper = SecretWhisperNote(
            id = "w_1",
            senderName = "Maya",
            text = "Meet me on the rooftop at 9pm",
            timestamp = System.currentTimeMillis(),
            isRevealed = false
        )

        val (packetV3, block1) = CoupleTurnCodec.encodeWhisperTurnWithBlock(
            coupleCode = coupleCode,
            senderName = "Maya",
            likedDateId = "date_stargaze",
            whisperNote = secretWhisper,
            previousHash = genesis.blockHash,
            blockIndex = 1,
            ecdhSharedSecretB64 = sharedSecret
        )

        assertFalse("Plaintext must never appear in encrypted packet", packetV3.contains("rooftop"))

        // 1. Legitimate partner decrypts v3 cleanly
        val partnerDecoded = CoupleTurnCodec.decodePacket(
            rawInput = packetV3,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret,
            minProtocolVersion = CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3
        )
        assertTrue(partnerDecoded.isSuccess)
        val whisperPacket = partnerDecoded.getOrThrow() as DecodedTurnPacket.WhisperTurn
        assertEquals("Meet me on the rooftop at 9pm", whisperPacket.whisperNote?.text)

        // 2. Room code alone or wrong ECDH key MUST fail
        assertTrue(
            CoupleTurnCodec.decodePacket(packetV3, coupleCode, listOf(genesis), "Maya", "Leo", "").isFailure
        )
        assertTrue(
            CoupleTurnCodec.decodePacket(packetV3, coupleCode, listOf(genesis), "Maya", "Leo", attackerSecret).isFailure
        )

        // 3. Anti-downgrade: forged legacy v2 packet MUST be rejected whenever ECDH v3 is established
        val (downgradedV2Packet, _) = CoupleTurnCodec.encodeQuizTurnWithBlock(
            coupleCode = coupleCode,
            senderName = "Maya",
            roundIndex = 0,
            choiceIndex = 1,
            spiceLevel = SpiceLevel.FLIRTY,
            previousHash = block1.blockHash,
            blockIndex = 2,
            ecdhSharedSecretB64 = "" // crafts a v2 packet using room code only
        )
        val downgradeResult = CoupleTurnCodec.decodePacket(
            rawInput = downgradedV2Packet,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis, block1),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret,
            minProtocolVersion = CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3
        )
        assertTrue("Protocol v2 downgrade must be rejected on ECDH v3 room", downgradeResult.isFailure)

        // 4. Primary replay protection: duplicate clientTurnId rejected even with empty rolling ledger
        val duplicateTurnIdResult = CoupleTurnCodec.decodePacket(
            rawInput = packetV3,
            expectedCoupleCode = coupleCode,
            existingLedger = emptyList(),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret,
            seenClientTurnIds = setOf(block1.clientTurnId)
        )
        assertTrue("Duplicate clientTurnId must be rejected even outside 80-block window", duplicateTurnIdResult.isFailure)

        // 5. Primary replay protection: stale sequence index rejected even with empty rolling ledger
        val staleSeqResult = CoupleTurnCodec.decodePacket(
            rawInput = packetV3,
            expectedCoupleCode = coupleCode,
            existingLedger = emptyList(),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret,
            minAcceptedBlockIndex = 85
        )
        assertTrue("Stale block index < minAcceptedBlockIndex must be rejected", staleSeqResult.isFailure)

        // 6. Conflict rebase: extractActivityIntent + sealIntentWithFreshNonce generates a brand-new 96-bit IV & AAD
        val extractedIntent = CoupleTurnCodec.extractActivityIntent(packetV3, coupleCode, sharedSecret).getOrThrow()
        assertEquals(block1.clientTurnId, extractedIntent.clientTurnId)
        val iv1 = CoupleTurnCodec.peekPacketIvBase64(packetV3)
        val (rebasedPacket, rebasedBlock) = CoupleTurnCodec.sealIntentWithFreshNonce(
            coupleCode = coupleCode,
            intent = extractedIntent,
            previousHash = block1.blockHash,
            blockIndex = 2,
            ecdhSharedSecretB64 = sharedSecret
        )
        val iv2 = CoupleTurnCodec.peekPacketIvBase64(rebasedPacket)
        assertNotNull(iv1)
        assertNotNull(iv2)
        assertNotEquals("Rebased packet MUST use a fresh 96-bit AES-GCM IV", iv1, iv2)
        assertEquals(2, rebasedBlock.index)
        assertEquals(block1.blockHash, rebasedBlock.previousHash)
        assertEquals(extractedIntent.clientTurnId, rebasedBlock.clientTurnId)

        // Rebased packet decodes cleanly with updated AAD
        val rebasedDecoded = CoupleTurnCodec.decodePacket(
            rawInput = rebasedPacket,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis, block1),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret
        )
        assertTrue("Rebased packet with fresh IV & AAD must decode cleanly", rebasedDecoded.isSuccess)
    }

    @Test
    fun `Room MIGRATION_14_15 preserves legacy couple_rooms, upgrades minProtocolVersion to 3, and encrypts PendingCoupleTurn queue`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_14_15_test_${System.nanoTime()}.db"
        val dbFile = ctx.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Seed a genuine v14 SQLite database on disk (including a legacy couple_rooms row with an ECDH shared secret)
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.version = 14
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `cycleLength` INTEGER NOT NULL, `periodLength` INTEGER NOT NULL, `waterGoal` INTEGER NOT NULL, `waterRemindersEnabled` INTEGER NOT NULL, `cycleRemindersEnabled` INTEGER NOT NULL, `fertileRemindersEnabled` INTEGER NOT NULL, `checkinRemindersEnabled` INTEGER NOT NULL, `shopRemindersEnabled` INTEGER NOT NULL, `reminderHour` INTEGER NOT NULL, `waterIntervalHours` INTEGER NOT NULL, `isOnboarded` INTEGER NOT NULL, `shopHideCompleted` INTEGER NOT NULL, `shopViewMode` TEXT NOT NULL, `shopSortMode` TEXT NOT NULL, `activeCoupleCode` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `period_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startDateMillis` INTEGER NOT NULL, `notes` TEXT NOT NULL, `durationDays` INTEGER, `endDateMillis` INTEGER)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `task_categories` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `shop_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `phaseCategory` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `notes` TEXT NOT NULL, `priority` TEXT NOT NULL)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `categoryEmoji` TEXT NOT NULL, `daysOfWeek` TEXT NOT NULL, `customSound` TEXT NOT NULL, `customReminderMessage` TEXT NOT NULL, `instructions` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `stockCount` INTEGER NOT NULL, `lastTakenDate` TEXT NOT NULL, `lastTakenTime` TEXT NOT NULL)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `water_logs` (`dateString` TEXT NOT NULL, `glassesCount` INTEGER NOT NULL, PRIMARY KEY(`dateString`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `user_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `tag` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `reminderTimeMillis` INTEGER, `reminderFired` INTEGER NOT NULL DEFAULT 0)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `daily_cycle_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateString` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `flowIntensity` TEXT NOT NULL, `symptoms` TEXT NOT NULL, `mood` TEXT NOT NULL, `cervicalMucus` TEXT NOT NULL, `painLevel` INTEGER NOT NULL, `energyLevel` INTEGER NOT NULL, `basalTemperature` REAL, `notes` TEXT NOT NULL)")
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_cycle_logs_dateString` ON `daily_cycle_logs` (`dateString`)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `game_scores` (`gameId` TEXT NOT NULL, `highScore` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL, `gamesPlayed` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`gameId`))")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `couple_rooms` (
                `coupleCode` TEXT NOT NULL,
                `myName` TEXT NOT NULL,
                `partnerName` TEXT NOT NULL,
                `spiceLevel` TEXT NOT NULL,
                `isPassAndPlay` INTEGER NOT NULL,
                `matchCount` INTEGER NOT NULL,
                `streakCount` INTEGER NOT NULL,
                `doodleStrokesJson` TEXT NOT NULL,
                `doodlePrompt` TEXT NOT NULL,
                `quizStateJson` TEXT NOT NULL,
                `wyrStateJson` TEXT NOT NULL,
                `ticTacToeStateJson` TEXT NOT NULL,
                `isBonded` INTEGER NOT NULL DEFAULT 1,
                `partnerLocked` INTEGER NOT NULL DEFAULT 0,
                `blockchainLedgerJson` TEXT NOT NULL DEFAULT '[]',
                `lastBlockHash` TEXT NOT NULL DEFAULT '',
                `blockHeight` INTEGER NOT NULL DEFAULT 1,
                `firewallPassedCount` INTEGER NOT NULL DEFAULT 0,
                `firewallBlockedCount` INTEGER NOT NULL DEFAULT 0,
                `lastFirewallEvent` TEXT NOT NULL DEFAULT 'Firewall Active · AES-256-GCM + PoW Chain',
                `updatedAt` INTEGER NOT NULL,
                `isHost` INTEGER NOT NULL DEFAULT 1,
                `approvalStatus` TEXT NOT NULL DEFAULT 'WAITING',
                `pendingPartnerName` TEXT NOT NULL DEFAULT '',
                `dareStateJson` TEXT NOT NULL DEFAULT '{}',
                `wavelengthStateJson` TEXT NOT NULL DEFAULT '{}',
                `whisperStateJson` TEXT NOT NULL DEFAULT '{}',
                `ecdhPublicKeyB64` TEXT NOT NULL DEFAULT '',
                `ecdhPrivateKeyB64` TEXT NOT NULL DEFAULT '',
                `peerPublicKeyB64` TEXT NOT NULL DEFAULT '',
                `ecdhSharedSecretB64` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`coupleCode`)
            )
            """.trimIndent()
        )
        val encryptedSecret = LocalVaultCrypto.encryptField("mockSharedSecretB64Value", "couple_rooms:424242:ecdhSharedSecretB64")
        val encryptedName = LocalVaultCrypto.encryptField("Maya", "couple_rooms:424242:myName")
        rawDb.execSQL(
            """
            INSERT INTO couple_rooms (
                coupleCode, myName, partnerName, spiceLevel, isPassAndPlay,
                matchCount, streakCount, doodleStrokesJson, doodlePrompt,
                quizStateJson, wyrStateJson, ticTacToeStateJson,
                isBonded, partnerLocked, blockchainLedgerJson, lastBlockHash,
                blockHeight, firewallPassedCount, firewallBlockedCount,
                lastFirewallEvent, updatedAt, isHost, approvalStatus,
                pendingPartnerName, dareStateJson, wavelengthStateJson,
                whisperStateJson, ecdhPublicKeyB64, ecdhPrivateKeyB64,
                peerPublicKeyB64, ecdhSharedSecretB64
            ) VALUES (
                '424242', ?, 'Leo', 'FLIRTY', 0,
                3, 5, '', '',
                '', '', '',
                1, 1, '[]', '00legacyhead1234',
                4, 4, 0,
                'ok', 1700000000000, 1, 'CONNECTED',
                '', '{}', '{}',
                '{}', 'pubA', 'privA',
                'pubB', ?
            )
            """.trimIndent(),
            arrayOf(encryptedName, encryptedSecret)
        )
        rawDb.close()

        // 2. Open via Room with explicit non-destructive MIGRATION_14_15
        val migratedDb = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PhasesRepository(migratedDb)
            val migratedRoom = repo.getCoupleRoom("424242")
            assertNotNull("Legacy v14 couple room must survive migration intact", migratedRoom)
            assertEquals("Maya", migratedRoom!!.myName)
            assertEquals(1, migratedRoom.chainSchemaVersion)
            assertEquals(
                "Legacy room with existing ECDH secret must enforce minProtocolVersion=3 after migration",
                3,
                migratedRoom.minProtocolVersion
            )

            // 3. Verify encrypted PendingCoupleTurn queue works & deduplicates by clientTurnId
            val intent = CoupleActivityIntent(
                clientTurnId = "turn-offline-001",
                actionType = "QUIZ",
                senderName = "Maya",
                payloadJson = "{\"t\":\"QUIZ\",\"round\":1,\"pick\":2}"
            )
            val queued = repo.enqueuePendingCoupleTurn("424242", intent)
            assertTrue("Pending intent payload must be encrypted at rest", LocalVaultCrypto.isEncrypted(queued.encryptedIntentJson))
            assertFalse("Plaintext payload must not appear in Room row", queued.encryptedIntentJson.contains("QUIZ"))

            // Idempotent re-enqueue of same clientTurnId must NOT duplicate row
            repo.enqueuePendingCoupleTurn("424242", intent)
            val pendingList = repo.getPendingTurnsForRoomOnce("424242")
            assertEquals(1, pendingList.size)

            val decrypted = repo.decryptPendingIntent(pendingList.first()).getOrThrow()
            assertEquals(intent.clientTurnId, decrypted.clientTurnId)
            assertEquals(intent.payloadJson, decrypted.payloadJson)

            repo.removePendingTurn(intent.clientTurnId)
            assertTrue(repo.getPendingTurnsForRoomOnce("424242").isEmpty())
        } finally {
            migratedDb.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun `BackendSecurityFirewall sanitizes injection payloads and throttles brute-force attempts`() {
        val malicious = "Hello <script>alert(1)</script> DROP TABLE user_notes"
        val cleaned = BackendSecurityFirewall.sanitizeField(malicious)
        assertFalse(cleaned.contains("<script", ignoreCase = true))
        assertFalse(cleaned.contains("DROP TABLE", ignoreCase = true))

        val rateKey = "test_brute_force_${System.nanoTime()}"
        repeat(3) {
            assertTrue(BackendSecurityFirewall.allowRateLimitedOperation(rateKey, maxOpsPerWindow = 3, windowMillis = 60_000L))
        }
        assertFalse(BackendSecurityFirewall.allowRateLimitedOperation(rateKey, maxOpsPerWindow = 3, windowMillis = 60_000L))

        val generated = CoupleTurnCodec.generateUniqueCode(setOf("100000"))
        assertEquals(6, generated.length)
        assertTrue(generated.all { it.isDigit() })
        assertNotEquals("100000", generated)
    }

    @Test
    fun `Vault decryption failures never appear as empty records, never leak ciphertext, and block destructive overwrite`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "vault_error_semantics_${System.nanoTime()}.db"
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PhasesRepository(db)

            // 1. Malformed legacy static ciphertext must return Result.failure (never leak raw ciphertext)
            val malformedLegacy = "ENC:AES256GCM:malformed_without_colon"
            assertTrue(
                "Malformed legacy ciphertext must fail instead of returning raw ciphertext",
                LocalVaultCrypto.decryptFieldResult(malformedLegacy, "user_notes:content").isFailure
            )

            // 2. Insert real encrypted records across all sensitive tables
            repo.saveProfile(com.example.data.UserProfile(id = 1, name = "Aanya", cycleLength = 29, periodLength = 5, isOnboarded = true))
            val periodId = repo.logPeriod(startDateMillis = 1700000000000L, notes = "Cramps day 1", durationDays = 5)
            repo.saveDailyCycleLog(
                com.example.data.DailyCycleLog(
                    dateString = "2025-11-10",
                    timestampMillis = 1700000000000L,
                    flowIntensity = "MEDIUM",
                    symptoms = "Headache, Fatigue",
                    mood = "Calm",
                    notes = "Drank chamomile tea"
                )
            )
            val noteId = repo.addNote("Private gynecologist follow-up questions", "thought")
            val medId = repo.addMedication(name = "Iron Bisglycinate", dosage = "25mg", instructions = "After breakfast")
            val shopId = repo.addShopItem(text = "Heating pad refill", notes = "Lavender scented")

            // 3. Tamper with ciphertext directly in SQLite (simulating bit-flip / key mismatch / AAD swap)
            val sqlDb = db.openHelper.writableDatabase
            val wrongAadCipher = LocalVaultCrypto.encryptField("Swapped", "wrong:aad")
            val corruptedCipher = LocalVaultCrypto.VAULT_PREFIX_V2 + "AAAAAAAAAAAA:BBBBBBBBBBBBBBBBBBBBBBBB"
            sqlDb.execSQL("UPDATE user_profile SET name = ? WHERE id = 1", arrayOf<Any>(corruptedCipher))
            sqlDb.execSQL("UPDATE period_logs SET notes = ? WHERE id = ?", arrayOf<Any>(corruptedCipher, periodId))
            sqlDb.execSQL("UPDATE daily_cycle_logs SET notes = ? WHERE dateString = '2025-11-10'", arrayOf<Any>(wrongAadCipher))
            sqlDb.execSQL("UPDATE user_notes SET content = ? WHERE id = ?", arrayOf<Any>(corruptedCipher, noteId))
            sqlDb.execSQL("UPDATE medications SET name = ? WHERE id = ?", arrayOf<Any>(corruptedCipher, medId))
            sqlDb.execSQL("UPDATE shop_items SET text = ? WHERE id = ?", arrayOf<Any>(corruptedCipher, shopId))

            // 4. Read every record back — NONE may look empty ("") and NONE may expose raw ciphertext
            val profile = repo.getProfileOnce()!!
            val period = repo.getPeriodLogsOnce().first { it.id == periodId }
            val dailyLog = repo.getDailyCycleLog("2025-11-10").first()!!
            val note = repo.getNoteById(noteId)!!
            val med = repo.getMedicationById(medId)!!
            val shop = repo.shopItems.first().first { it.id == shopId }

            listOf(profile.name, period.notes, dailyLog.notes, note.content, med.name, shop.text).forEach { value ->
                assertEquals(PhasesRepository.VAULT_UNREADABLE_LABEL, value)
                assertNotEquals("", value)
                assertFalse("Must never expose raw ciphertext", value.contains("ENC:"))
            }

            // Explicit decryption issues state must be populated for all 6 affected tables
            val issueTables = repo.decryptionIssues.value.map { it.table }.toSet()
            assertTrue(issueTables.containsAll(setOf("user_profile", "period_logs", "daily_cycle_logs", "user_notes", "medications", "shop_items")))
            assertTrue(repo.isRecordUnreadable("user_profile", "1"))
            assertTrue(repo.isRecordUnreadable("medications", medId.toString()))

            // 5. Mutating ANY unreadable record MUST throw LocalVaultUnavailableException and preserve raw SQLite ciphertext
            val writeAttempts: List<suspend () -> Unit> = listOf(
                { repo.saveProfile(profile.copy(waterGoal = 10)) },
                { repo.updateShopPreferences(true, "COMPACT", "ALPHA") },
                { repo.updatePeriod(period.copy(durationDays = 6)) },
                { repo.saveDailyCycleLog(dailyLog.copy(painLevel = 3)) },
                { repo.updateMedication(med.copy(hour = 10)) },
                { repo.toggleMedication(medId, false) },
                { repo.markMedicationTakenToday(medId, "2025-11-10", "09:00") },
                { repo.toggleShopItem(shop) },
                { repo.updateShopItem(shop.copy(priority = "HIGH")) }
            )
            for (attempt in writeAttempts) {
                try {
                    attempt()
                    fail("Expected LocalVaultUnavailableException when writing to an unreadable encrypted record")
                } catch (_: LocalVaultUnavailableException) {
                    // Expected fail-closed protection
                }
            }

            // Raw SQLite ciphertext on disk MUST remain completely intact
            assertEquals(corruptedCipher, db.userProfileDao().getProfileOnce()!!.name)
            assertEquals(corruptedCipher, db.periodLogDao().getById(periodId)!!.notes)
            assertEquals(corruptedCipher, db.medicationDao().getById(medId)!!.name)
            assertEquals(corruptedCipher, db.userNoteDao().getById(noteId)!!.content)
        } finally {
            db.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun `Representative V14 to V15 migration preserves all menstrual, journal, med, and legacy V1 couple data and failed migration never wipes disk`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "full_v14_migration_${System.nanoTime()}.db"
        val dbFile = ctx.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Generate real ECDH P-256 keys & real 3-block legacy V1 chain
        val code = "551234"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()
        val sharedSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            hostKeys.privateKeyB64, partnerKeys.publicKeyB64, code, hostKeys.publicKeyB64
        )!!
        val v1Genesis = CoupleBlockchain.createLegacyV1GenesisBlock(code, "Aanya")
        val v1Block1 = CoupleBlockchain.mineBlock(1, "QUIZ", "Aanya", v1Genesis.blockHash, "hash_q1")
        val v1Block2 = CoupleBlockchain.mineBlock(2, "WHISPER", "Rohan", v1Block1.blockHash, "hash_w2")
        val v1LedgerJson = CoupleBlockchain.ledgerToJson(listOf(v1Genesis, v1Block1, v1Block2))

        // Seed realistic V14 SQLite file with ALL domain tables
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.version = 14
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `cycleLength` INTEGER NOT NULL, `periodLength` INTEGER NOT NULL, `waterGoal` INTEGER NOT NULL, `waterRemindersEnabled` INTEGER NOT NULL, `cycleRemindersEnabled` INTEGER NOT NULL, `fertileRemindersEnabled` INTEGER NOT NULL, `checkinRemindersEnabled` INTEGER NOT NULL, `shopRemindersEnabled` INTEGER NOT NULL, `reminderHour` INTEGER NOT NULL, `waterIntervalHours` INTEGER NOT NULL, `isOnboarded` INTEGER NOT NULL, `shopHideCompleted` INTEGER NOT NULL, `shopViewMode` TEXT NOT NULL, `shopSortMode` TEXT NOT NULL, `activeCoupleCode` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `period_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startDateMillis` INTEGER NOT NULL, `notes` TEXT NOT NULL, `durationDays` INTEGER, `endDateMillis` INTEGER)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `task_categories` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `shop_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `phaseCategory` TEXT NOT NULL, `isCustom` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `notes` TEXT NOT NULL, `priority` TEXT NOT NULL)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `categoryEmoji` TEXT NOT NULL, `daysOfWeek` TEXT NOT NULL, `customSound` TEXT NOT NULL, `customReminderMessage` TEXT NOT NULL, `instructions` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `stockCount` INTEGER NOT NULL, `lastTakenDate` TEXT NOT NULL, `lastTakenTime` TEXT NOT NULL)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `water_logs` (`dateString` TEXT NOT NULL, `glassesCount` INTEGER NOT NULL, PRIMARY KEY(`dateString`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `user_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `tag` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `reminderTimeMillis` INTEGER, `reminderFired` INTEGER NOT NULL DEFAULT 0)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `daily_cycle_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateString` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `flowIntensity` TEXT NOT NULL, `symptoms` TEXT NOT NULL, `mood` TEXT NOT NULL, `cervicalMucus` TEXT NOT NULL, `painLevel` INTEGER NOT NULL, `energyLevel` INTEGER NOT NULL, `basalTemperature` REAL, `notes` TEXT NOT NULL)")
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_cycle_logs_dateString` ON `daily_cycle_logs` (`dateString`)")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `game_scores` (`gameId` TEXT NOT NULL, `highScore` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL, `gamesPlayed` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`gameId`))")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `couple_rooms` (
                `coupleCode` TEXT NOT NULL, `myName` TEXT NOT NULL, `partnerName` TEXT NOT NULL,
                `spiceLevel` TEXT NOT NULL, `isPassAndPlay` INTEGER NOT NULL, `matchCount` INTEGER NOT NULL,
                `streakCount` INTEGER NOT NULL, `doodleStrokesJson` TEXT NOT NULL, `doodlePrompt` TEXT NOT NULL,
                `quizStateJson` TEXT NOT NULL, `wyrStateJson` TEXT NOT NULL, `ticTacToeStateJson` TEXT NOT NULL,
                `isBonded` INTEGER NOT NULL DEFAULT 1, `partnerLocked` INTEGER NOT NULL DEFAULT 0,
                `blockchainLedgerJson` TEXT NOT NULL DEFAULT '[]', `lastBlockHash` TEXT NOT NULL DEFAULT '',
                `blockHeight` INTEGER NOT NULL DEFAULT 1, `firewallPassedCount` INTEGER NOT NULL DEFAULT 0,
                `firewallBlockedCount` INTEGER NOT NULL DEFAULT 0, `lastFirewallEvent` TEXT NOT NULL DEFAULT '',
                `updatedAt` INTEGER NOT NULL, `isHost` INTEGER NOT NULL DEFAULT 1,
                `approvalStatus` TEXT NOT NULL DEFAULT 'WAITING', `pendingPartnerName` TEXT NOT NULL DEFAULT '',
                `dareStateJson` TEXT NOT NULL DEFAULT '{}', `wavelengthStateJson` TEXT NOT NULL DEFAULT '{}',
                `whisperStateJson` TEXT NOT NULL DEFAULT '{}', `ecdhPublicKeyB64` TEXT NOT NULL DEFAULT '',
                `ecdhPrivateKeyB64` TEXT NOT NULL DEFAULT '', `peerPublicKeyB64` TEXT NOT NULL DEFAULT '',
                `ecdhSharedSecretB64` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`coupleCode`)
            )
            """.trimIndent()
        )

        rawDb.execSQL(
            "INSERT INTO user_profile VALUES (1, ?, 29, 5, 9, 1, 1, 1, 1, 1, 20, 3, 1, 0, 'COZY', 'CUSTOM', ?)",
            arrayOf(LocalVaultCrypto.encryptField("Aanya", "user_profile:name"), code)
        )
        rawDb.execSQL(
            "INSERT INTO period_logs (startDateMillis, notes, durationDays, endDateMillis) VALUES (1728000000000, ?, 5, 1728345600000)",
            arrayOf(LocalVaultCrypto.encryptField("October period start — mild cramps", "period_logs:notes"))
        )
        rawDb.execSQL(
            "INSERT INTO period_logs (startDateMillis, notes, durationDays, endDateMillis) VALUES (1730505600000, ?, 5, 1730851200000)",
            arrayOf(LocalVaultCrypto.encryptField("November period start — rested well", "period_logs:notes"))
        )
        rawDb.execSQL(
            "INSERT INTO daily_cycle_logs (dateString, timestampMillis, flowIntensity, symptoms, mood, cervicalMucus, painLevel, energyLevel, basalTemperature, notes) VALUES ('2025-11-02', 1730505600000, 'MEDIUM', ?, ?, 'NONE', 3, 2, 36.7, ?)",
            arrayOf(
                LocalVaultCrypto.encryptField("Cramps, Bloating", "daily_cycle_logs:symptoms"),
                LocalVaultCrypto.encryptField("Cozy", "daily_cycle_logs:mood"),
                LocalVaultCrypto.encryptField("Warm tea helped a lot", "daily_cycle_logs:notes")
            )
        )
        rawDb.execSQL(
            "INSERT INTO user_notes (content, tag, timestampMillis, reminderTimeMillis, reminderFired) VALUES (?, 'symptom', 1730505600000, 1730600000000, 0)",
            arrayOf(LocalVaultCrypto.encryptField("Ask doctor about magnesium dose", "user_notes:content"))
        )
        rawDb.execSQL(
            "INSERT INTO medications (name, dosage, hour, minute, isEnabled, categoryEmoji, daysOfWeek, customSound, customReminderMessage, instructions, colorHex, stockCount, lastTakenDate, lastTakenTime) VALUES (?, ?, 9, 30, 1, '💊', 'ALL', 'cute_chime', ?, ?, '#D4629A', 30, '2025-11-02', '09:32')",
            arrayOf(
                LocalVaultCrypto.encryptField("Magnesium Glycinate", "medications:name"),
                LocalVaultCrypto.encryptField("200mg", "medications:dosage"),
                LocalVaultCrypto.encryptField("Time for evening magnesium!", "medications:customReminderMessage"),
                LocalVaultCrypto.encryptField("Take with water after dinner", "medications:instructions")
            )
        )
        rawDb.execSQL(
            "INSERT INTO shop_items (text, isDone, phaseCategory, isCustom, orderIndex, notes, priority) VALUES (?, 0, 'luteal', 1, 0, ?, 'HIGH')",
            arrayOf(
                LocalVaultCrypto.encryptField("Raspberry leaf tea", "shop_items:text"),
                LocalVaultCrypto.encryptField("Organic loose leaf", "shop_items:notes")
            )
        )
        rawDb.execSQL(
            """
            INSERT INTO couple_rooms VALUES (
                ?, ?, ?, 'FLIRTY', 0, 7, 4,
                ?, ?, ?, ?, ?,
                1, 1, ?, ?, 3, 3, 0, 'verified', 1730505600000,
                1, 'CONNECTED', '', ?, ?, ?,
                ?, ?, ?, ?
            )
            """.trimIndent(),
            arrayOf(
                code,
                LocalVaultCrypto.encryptField("Aanya", "couple_rooms:$code:myName"),
                LocalVaultCrypto.encryptField("Rohan", "couple_rooms:$code:partnerName"),
                LocalVaultCrypto.encryptField("[]", "couple_rooms:$code:doodleStrokesJson"),
                LocalVaultCrypto.encryptField("Sunset roof", "couple_rooms:$code:doodlePrompt"),
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:quizStateJson"),
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:wyrStateJson"),
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:ticTacToeStateJson"),
                LocalVaultCrypto.encryptField(v1LedgerJson, "couple_rooms:$code:blockchainLedgerJson"),
                v1Block2.blockHash,
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:dareStateJson"),
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:wavelengthStateJson"),
                LocalVaultCrypto.encryptField("{}", "couple_rooms:$code:whisperStateJson"),
                hostKeys.publicKeyB64,
                LocalVaultCrypto.encryptField(hostKeys.privateKeyB64, "couple_rooms:$code:ecdhPrivateKeyB64"),
                partnerKeys.publicKeyB64,
                LocalVaultCrypto.encryptField(sharedSecret, "couple_rooms:$code:ecdhSharedSecretB64")
            )
        )
        rawDb.close()

        // Make a copy to verify failed migration (missing migration path) NEVER silently wipes data
        val failedMigDbName = "failed_v14_migration_${System.nanoTime()}.db"
        val failedMigFile = ctx.getDatabasePath(failedMigDbName)
        dbFile.copyTo(failedMigFile, overwrite = true)

        val noMigrationDb = Room.databaseBuilder(ctx, AppDatabase::class.java, failedMigDbName)
            .allowMainThreadQueries()
            .build()
        try {
            try {
                noMigrationDb.periodLogDao().count()
                fail("Opening v14 DB at v15 without migration must throw IllegalStateException instead of wiping")
            } catch (_: IllegalStateException) {
                // Expected fail-closed behavior (no fallbackToDestructiveMigration)
            }
        } finally {
            noMigrationDb.close()
        }
        // Inspect the untouched disk file directly to prove ZERO silent data wipe occurred
        val inspectRaw = SQLiteDatabase.openDatabase(failedMigFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(14, inspectRaw.version)
            inspectRaw.rawQuery("SELECT COUNT(*) FROM period_logs", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
            inspectRaw.rawQuery("SELECT COUNT(*) FROM couple_rooms", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        } finally {
            inspectRaw.close()
            ctx.deleteDatabase(failedMigDbName)
        }

        // Now run the real non-destructive V14 -> V15 migration on the primary copy
        val migratedDb = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PhasesRepository(migratedDb)
            val profile = repo.getProfileOnce()!!
            assertEquals("Aanya", profile.name)
            assertEquals(29, profile.cycleLength)
            assertEquals(code, profile.activeCoupleCode)

            val periods = repo.getPeriodLogsOnce()
            assertEquals(2, periods.size)
            assertTrue(periods.any { it.notes == "October period start — mild cramps" })
            assertTrue(periods.any { it.notes == "November period start — rested well" })

            val daily = repo.getDailyCycleLog("2025-11-02").first()!!
            assertEquals("Cramps, Bloating", daily.symptoms)
            assertEquals("Cozy", daily.mood)
            assertEquals("Warm tea helped a lot", daily.notes)

            val notes = repo.getAllNotesOnce()
            assertEquals(1, notes.size)
            assertEquals("Ask doctor about magnesium dose", notes.first().content)

            val meds = repo.getMedicationsOnce()
            assertEquals(1, meds.size)
            assertEquals("Magnesium Glycinate", meds.first().name)
            assertEquals("200mg", meds.first().dosage)

            val items = repo.shopItems.first()
            assertEquals(1, items.size)
            assertEquals("Raspberry leaf tea", items.first().text)

            val room = repo.getCoupleRoom(code)!!
            assertEquals("Aanya", room.myName)
            assertEquals("Rohan", room.partnerName)
            assertEquals(hostKeys.privateKeyB64, room.ecdhPrivateKeyB64)
            assertEquals(sharedSecret, room.ecdhSharedSecretB64)
            assertEquals(1, room.chainSchemaVersion)
            assertEquals(3, room.minProtocolVersion)

            val restoredLedger = CoupleBlockchain.ledgerFromJson(room.blockchainLedgerJson)
            assertEquals(3, restoredLedger.size)
            val verifyV1 = CoupleBlockchain.verifyChain(
                blocks = restoredLedger,
                coupleCode = code,
                schemaVersion = CoupleBlockchain.SCHEMA_VERSION_V1
            )
            assertTrue("Restored legacy V1 ledger must verify intact after V14->V15 migration", verifyV1.isValid)
            assertTrue("Zero vault decryption issues expected on clean migration", repo.decryptionIssues.value.isEmpty())
        } finally {
            migratedDb.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun `Offline pending turn queue survives simulated process death encrypted at rest, deduplicates, and rebases with fresh nonce`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "offline_queue_crash_recovery_${System.nanoTime()}.db"
        val code = "819273"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()
        val sharedSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            hostKeys.privateKeyB64, partnerKeys.publicKeyB64, code, hostKeys.publicKeyB64
        )!!

        val genesis = CoupleBlockchain.createDeterministicGenesisBlock(code)
        val (initialQuizPacket, _) = CoupleTurnCodec.encodeQuizTurnWithBlock(
            coupleCode = code,
            senderName = "Aanya",
            roundIndex = 0,
            choiceIndex = 2,
            spiceLevel = SpiceLevel.FLIRTY,
            previousHash = genesis.blockHash,
            blockIndex = 1,
            ecdhSharedSecretB64 = sharedSecret
        )
        val (initialWhisperPacket, _) = CoupleTurnCodec.encodeWhisperTurnWithBlock(
            coupleCode = code,
            senderName = "Aanya",
            likedDateId = "date_picnic",
            whisperNote = SecretWhisperNote(
                id = "w9",
                senderName = "Aanya",
                text = "Secret stargazing note",
                timestamp = System.currentTimeMillis(),
                isRevealed = false
            ),
            previousHash = genesis.blockHash,
            blockIndex = 1,
            ecdhSharedSecretB64 = sharedSecret
        )
        val intent1 = CoupleTurnCodec.extractActivityIntent(initialQuizPacket, code, sharedSecret).getOrThrow()
        val intent2 = CoupleTurnCodec.extractActivityIntent(initialWhisperPacket, code, sharedSecret).getOrThrow()

        // Process 1: enqueue turns offline, then close DB to simulate process death
        val dbProcess1 = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val repo1 = PhasesRepository(dbProcess1)
            repo1.enqueuePendingCoupleTurn(code, intent1)
            repo1.enqueuePendingCoupleTurn(code, intent2)
            // Duplicate enqueue of same clientTurnId must be idempotent
            repo1.enqueuePendingCoupleTurn(code, intent1)
            assertEquals(2, repo1.getPendingTurnsForRoomOnce(code).size)
        } finally {
            dbProcess1.close()
        }

        // Verify raw SQLite bytes contain NO plaintext whisper or quiz payload
        val rawDb = SQLiteDatabase.openDatabase(ctx.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            rawDb.rawQuery("SELECT encryptedIntentJson FROM pending_couple_turns", null).use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    val stored = cursor.getString(0)
                    assertTrue(LocalVaultCrypto.isEncrypted(stored))
                    assertFalse(stored.contains("stargazing"))
                    assertFalse(stored.contains("FLIRTY"))
                    count++
                }
                assertEquals(2, count)
            }
        } finally {
            rawDb.close()
        }

        // Process 2 (after restart): recover pending intents, seal against head #1, simulate HeadConflict, rebase onto head #2 with fresh nonce
        val dbProcess2 = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val repo2 = PhasesRepository(dbProcess2)
            val recovered = repo2.getPendingTurnsForRoomOnce(code)
            assertEquals(2, recovered.size)
            val recoveredIntent1 = repo2.decryptPendingIntent(recovered[0]).getOrThrow()
            assertEquals(intent1.clientTurnId, recoveredIntent1.clientTurnId)

            val genesis = CoupleBlockchain.createDeterministicGenesisBlock(code)
            val (attempt1Packet, attempt1Block) = CoupleTurnCodec.sealIntentWithFreshNonce(
                coupleCode = code,
                intent = recoveredIntent1,
                previousHash = genesis.blockHash,
                blockIndex = 1,
                ecdhSharedSecretB64 = sharedSecret
            )

            // Partner concurrently committed block #1 -> conflict rebase onto partnerBlock1 at index 2
            val partnerBlock1 = CoupleBlockchain.mineBlock(1, "DARE", "Rohan", genesis.blockHash, "partner_hash")
            val (rebasedPacket, rebasedBlock) = CoupleTurnCodec.sealIntentWithFreshNonce(
                coupleCode = code,
                intent = recoveredIntent1,
                previousHash = partnerBlock1.blockHash,
                blockIndex = 2,
                ecdhSharedSecretB64 = sharedSecret
            )

            assertNotEquals(
                "Conflict rebase MUST generate a brand-new 96-bit AES-GCM IV",
                CoupleTurnCodec.peekPacketIvBase64(attempt1Packet),
                CoupleTurnCodec.peekPacketIvBase64(rebasedPacket)
            )
            assertEquals(partnerBlock1.blockHash, rebasedBlock.previousHash)
            assertEquals(2, rebasedBlock.index)
            assertEquals(intent1.clientTurnId, rebasedBlock.clientTurnId)

            // Rebased packet authenticates & decodes cleanly under new AAD
            val decoded = CoupleTurnCodec.decodePacket(
                rawInput = rebasedPacket,
                expectedCoupleCode = code,
                existingLedger = listOf(genesis, partnerBlock1),
                lockedPartnerName = "Aanya",
                myName = "Rohan",
                ecdhSharedSecretB64 = sharedSecret
            )
            assertTrue(decoded.isSuccess)
        } finally {
            dbProcess2.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun `NotificationHelper schedules and cancels note alarms with matching package-scoped PendingIntents`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val futureMillis = System.currentTimeMillis() + 3_600_000L
        val note = com.example.data.UserNote(
            id = 77L,
            content = "Take vitamin D",
            tag = "reminder",
            timestampMillis = System.currentTimeMillis(),
            reminderTimeMillis = futureMillis,
            reminderFired = false
        )

        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val shadowAlarmManager = org.robolectric.Shadows.shadowOf(alarmManager)

        com.example.notifications.NotificationHelper.scheduleNoteReminder(ctx, note)
        assertTrue(
            "Note reminder alarm should be scheduled",
            shadowAlarmManager.scheduledAlarms.isNotEmpty()
        )

        com.example.notifications.NotificationHelper.cancelNoteReminder(ctx, note.id)
        assertTrue(
            "cancelNoteReminder must match package-scoped PendingIntent and remove the scheduled alarm",
            shadowAlarmManager.scheduledAlarms.isEmpty()
        )
    }
}
