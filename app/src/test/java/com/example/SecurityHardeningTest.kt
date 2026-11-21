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
}
