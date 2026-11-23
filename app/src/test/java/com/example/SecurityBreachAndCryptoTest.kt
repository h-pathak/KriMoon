package com.example

import com.example.data.CoupleRoomRecord
import com.example.data.DailyCycleLog
import com.example.data.Medication
import com.example.data.PeriodLog
import com.example.data.ShopItem
import com.example.data.UserNote
import com.example.data.UserProfile
import com.example.model.ChainVerificationScope
import com.example.model.CoupleBlockchain
import com.example.model.CoupleCryptoEngine
import com.example.model.CoupleEcdhEngine
import com.example.model.CoupleFirewall
import com.example.model.CoupleTurnCodec
import com.example.security.BackendSecurityFirewall
import com.example.security.LocalVaultCrypto
import com.example.security.LocalVaultUnavailableException
import com.example.security.VaultState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Pure-JVM defensive security, encryption, firewall, and data-breach verification test suite.
 * Exercises real cryptographic primitives (AES-256-GCM, NIST P-256 ECDH, HKDF-SHA256, HMAC-SHA256,
 * SHA-256 PoW blockchain), 12-layer firewall DPI/bounds/rate-limit rules, and fail-closed vault erasure.
 */
class SecurityBreachAndCryptoTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "krimoon_sec_test_${System.nanoTime()}").apply {
            mkdirs()
        }
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
    }

    @After
    fun tearDown() {
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. Firewall Breach & Bypass Resistance Tests (DPI, Bounds, IPC, Flooding)
    // -------------------------------------------------------------------------

    @Test
    fun `firewall neutralizes obfuscated SQL injection, XSS event handlers, path traversal, and bidi spoofing`() {
        val payloads = listOf(
            "DROP\tTABLE user_notes;--",
            "UNION\nALL\tSELECT * FROM couple_rooms",
            "DELETE   FROM period_logs /* comment */",
            "UPDATE user_profile SET name='hacked'",
            "<script >alert(1)</script>",
            "<iframe src='https://evil.example'>",
            "<svg onload=alert(1)>",
            "click me onclick = steal()",
            "javascript :alert(1)",
            "vbscript:msgbox(1)",
            "data: text/html,<script>x</script>",
            "../../etc/passwd",
            "..\\..\\windows\\system32",
            "file://data/data/com.aistudio.phases.vclzq/databases/phases_local.db",
            "content://com.example.provider/secret",
            "Safe\u0000NullByte\u202EReverseBidi\u2066Isolate"
        )

        payloads.forEach { attack ->
            val sanitized = BackendSecurityFirewall.sanitizeField(attack)
            assertFalse("SQL DROP must be neutralized: $sanitized", Regex("DROP\\s+TABLE", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("SQL UNION must be neutralized: $sanitized", Regex("UNION\\s+(?:ALL\\s+)?SELECT", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("SQL DELETE must be neutralized: $sanitized", Regex("DELETE\\s+FROM", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("SQL UPDATE SET must be neutralized: $sanitized", Regex("UPDATE\\s+\\w+\\s+SET", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("SQL comment must be neutralized: $sanitized", sanitized.contains("/*"))
            assertFalse("Script tag must be neutralized: $sanitized", sanitized.contains("<script", ignoreCase = true))
            assertFalse("Iframe tag must be neutralized: $sanitized", sanitized.contains("<iframe", ignoreCase = true))
            assertFalse("SVG tag must be neutralized: $sanitized", sanitized.contains("<svg", ignoreCase = true))
            assertFalse("Event handler must be neutralized: $sanitized", Regex("\\bon[a-z]+\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("JS URI must be neutralized: $sanitized", Regex("javascript\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
            assertFalse("Path traversal must be neutralized: $sanitized", sanitized.contains("../") || sanitized.contains("..\\"))
            assertFalse("Null byte must be stripped", sanitized.contains('\u0000'))
            assertFalse("Unicode bidi override must be stripped", sanitized.contains('\u202E') || sanitized.contains('\u2066'))
        }

        // Normal user journal prose must remain completely intact (no false positives)
        val normalJournal = "Feeling cozy today! Need to update my water bottle and select herbal tea for evening cramps 🌸"
        assertEquals(normalJournal, BackendSecurityFirewall.sanitizeField(normalJournal))
    }

    @Test
    fun `firewall entity guards enforce medical bounds, schema allowlists, and sanitize pendingPartnerName`() {
        val evilProfile = BackendSecurityFirewall.guardUserProfile(
            UserProfile(
                id = 999,
                name = "Maya <svg onload=alert(1)>",
                cycleLength = 500,
                periodLength = -5,
                waterGoal = 999,
                reminderHour = 42,
                waterIntervalHours = 0,
                shopViewMode = "DROP TABLE",
                shopSortMode = "<script>",
                activeCoupleCode = "12ab34"
            )
        )
        assertEquals(1, evilProfile.id)
        assertEquals(60, evilProfile.cycleLength)
        assertEquals(2, evilProfile.periodLength)
        assertEquals(20, evilProfile.waterGoal)
        assertEquals(23, evilProfile.reminderHour)
        assertEquals(1, evilProfile.waterIntervalHours)
        assertEquals("COZY", evilProfile.shopViewMode)
        assertEquals("CUSTOM", evilProfile.shopSortMode)
        assertEquals("", evilProfile.activeCoupleCode)
        assertFalse(evilProfile.name.contains("<svg", ignoreCase = true))

        val evilLog = BackendSecurityFirewall.guardDailyCycleLog(
            DailyCycleLog(
                dateString = "2026-11-22'; DROP TABLE--",
                timestampMillis = 1L,
                flowIntensity = "INJECTED",
                cervicalMucus = "INVALID",
                painLevel = 99,
                energyLevel = -10,
                basalTemperature = 99.9f,
                notes = "Normal note UNION\tSELECT secret"
            )
        )
        assertEquals("2026-11-22", evilLog.dateString)
        assertEquals("NONE", evilLog.flowIntensity)
        assertEquals("NONE", evilLog.cervicalMucus)
        assertEquals(5, evilLog.painLevel)
        assertEquals(1, evilLog.energyLevel)
        assertEquals(42.5f, evilLog.basalTemperature)
        assertFalse(evilLog.notes.contains("UNION", ignoreCase = true))

        val evilRoom = BackendSecurityFirewall.guardCoupleRoom(
            CoupleRoomRecord(
                coupleCode = "98-66-07-extra",
                myName = "Host <script>x</script>",
                partnerName = "Partner ../secret",
                pendingPartnerName = "Intruder <iframe src=x>",
                spiceLevel = "HACKED",
                doodlePrompt = "Draw DROP\nTABLE",
                lastFirewallEvent = "ok"
            )
        )
        assertEquals("986607", evilRoom.coupleCode)
        assertEquals("FLIRTY", evilRoom.spiceLevel)
        assertFalse(evilRoom.myName.contains("<script", ignoreCase = true))
        assertFalse(evilRoom.partnerName.contains("../"))
        assertFalse(evilRoom.pendingPartnerName.contains("<iframe", ignoreCase = true))
        assertFalse(evilRoom.doodlePrompt.contains("DROP", ignoreCase = true))

        val evilPeriod = BackendSecurityFirewall.guardPeriodLog(
            PeriodLog(startDateMillis = 5000L, endDateMillis = 1000L, durationDays = 99, notes = "ok")
        )
        assertEquals(5000L, evilPeriod.endDateMillis)
        assertEquals(20, evilPeriod.durationDays)

        val evilNote = BackendSecurityFirewall.guardUserNote(UserNote(content = "Hi", tag = "admin_override"))
        assertEquals("thought", evilNote.tag)

        val evilMed = BackendSecurityFirewall.guardMedication(Medication(name = "Iron", hour = 99, minute = -3))
        assertEquals(23, evilMed.hour)
        assertEquals(0, evilMed.minute)

        val evilShop = BackendSecurityFirewall.guardShopItem(ShopItem(text = "Tea", priority = "ROOT"))
        assertEquals("NORMAL", evilShop.priority)
    }

    @Test
    fun `firewall blocks unauthorized IPC navigation targets, malformed packets, oversized packets, and floods`() {
        assertTrue(BackendSecurityFirewall.isAllowedNavigationTarget("CYCLE"))
        assertTrue(BackendSecurityFirewall.isAllowedNavigationTarget("PLAY"))
        assertFalse(BackendSecurityFirewall.isAllowedNavigationTarget("ADMIN_SHELL"))
        assertFalse(BackendSecurityFirewall.isAllowedNavigationTarget("../settings"))
        assertFalse(BackendSecurityFirewall.isAllowedNavigationTarget(""))
        assertFalse(BackendSecurityFirewall.isAllowedNavigationTarget(null))

        // CoupleFirewall DPI blocks whitespace-obfuscated SQL/XSS and >64KB packets
        assertTrue(CoupleFirewall.inspectAndFilter("", "123456").isFailure)
        assertTrue(CoupleFirewall.inspectAndFilter("A".repeat(70_000), "123456").isFailure)
        assertTrue(CoupleFirewall.inspectAndFilter("KRIMOON:BCHAIN:DROP\tTABLE", "123456").isFailure)
        assertTrue(CoupleFirewall.inspectAndFilter("KRIMOON:BCHAIN:<iframe src=x>", "123456").isFailure)
        assertTrue(CoupleFirewall.inspectAndFilter("KRIMOON:BCHAIN:onclick=steal()", "123456").isFailure)

        // Sliding-window flood guard throttles brute-force requests
        val key = "sec_flood_${System.nanoTime()}"
        repeat(5) {
            assertTrue(BackendSecurityFirewall.allowRateLimitedOperation(key, maxOpsPerWindow = 5, windowMillis = 60_000L))
        }
        assertFalse(BackendSecurityFirewall.allowRateLimitedOperation(key, maxOpsPerWindow = 5, windowMillis = 60_000L))
    }

    // -------------------------------------------------------------------------
    // 2. Local At-Rest Vault Encryption & Data-Breach Resistance Tests
    // -------------------------------------------------------------------------

    @Test
    fun `LocalVaultCrypto enforces AES-256-GCM unique IVs, tamper detection, and cross-column AAD isolation`() {
        val sensitive = "Private health entry: iron pill taken at 08:30, mild cramps."
        val aad = "daily_cycle_logs:notes"

        val c1 = LocalVaultCrypto.encryptField(sensitive, aad)
        val c2 = LocalVaultCrypto.encryptField(sensitive, aad)

        assertTrue(c1.startsWith(LocalVaultCrypto.VAULT_PREFIX_V2))
        assertTrue(LocalVaultCrypto.isEncrypted(c1))
        assertTrue(LocalVaultCrypto.isLatestFormat(c1))
        assertFalse("Ciphertext must never contain plaintext", c1.contains("iron pill"))
        assertNotEquals("Identical plaintexts must produce distinct random 96-bit IVs", c1, c2)

        assertEquals(sensitive, LocalVaultCrypto.decryptField(c1, aad))
        assertEquals(sensitive, LocalVaultCrypto.decryptField(c2, aad))

        // Cross-column AAD replay attack MUST fail GCM tag verification
        val swapped = LocalVaultCrypto.decryptFieldResult(c1, "user_notes:content")
        assertTrue("Cross-column AAD swap must fail authentication", swapped.isFailure)

        // Bit-flip tampering of ciphertext/GCM tag MUST fail
        val tampered = c1.dropLast(2) + if (c1.endsWith("AA")) "BB" else "AA"
        assertTrue("Tampered ciphertext must fail authentication", LocalVaultCrypto.decryptFieldResult(tampered, aad).isFailure)

        // Malformed legacy ciphertext MUST fail (never leak raw ciphertext string)
        assertTrue(LocalVaultCrypto.decryptFieldResult("ENC:AES256GCM:corrupted_no_colon", aad).isFailure)
        assertTrue(LocalVaultCrypto.decryptFieldResult("ENC:AKSV2:corrupted_no_colon", aad).isFailure)
    }

    @Test
    fun `LocalVaultCrypto fails closed on key unavailability and cryptographic erasure makes old ciphertext unrecoverable`() {
        val cipher = LocalVaultCrypto.encryptField("Top-secret journal reflection", "user_notes:content")
        assertEquals("Top-secret journal reflection", LocalVaultCrypto.decryptField(cipher, "user_notes:content"))

        // Simulate Keystore lock/unavailability -> writes MUST fail closed
        LocalVaultCrypto.simulateKeystoreFailureForTest(VaultState.KEY_UNAVAILABLE)
        assertFalse(LocalVaultCrypto.canSafelyWrite())
        assertTrue(LocalVaultCrypto.decryptFieldResult(cipher, "user_notes:content").isFailure)
        try {
            LocalVaultCrypto.encryptField("Blocked write", "user_notes:content")
            fail("Expected LocalVaultUnavailableException when vault is locked")
        } catch (e: LocalVaultUnavailableException) {
            assertEquals(VaultState.KEY_UNAVAILABLE, e.vaultState)
        }

        // Wrapped DEK file preservation check on KEY_CORRUPTED
        val dekFile = File(tempDir, "krimoon_vault_dek_v2.bin")
        val sentinelBytes = ByteArray(60) { (it * 13 + 5).toByte() }
        dekFile.writeBytes(sentinelBytes)
        LocalVaultCrypto.simulateKeystoreFailureForTest(VaultState.KEY_CORRUPTED)
        assertArrayEquals("Fail-closed state must preserve wrapped DEK bytes", sentinelBytes, dekFile.readBytes())

        // Cryptographic erasure rotates DEK -> old ciphertext is permanently unrecoverable
        LocalVaultCrypto.cryptographicEraseAndResetVault()
        assertTrue(LocalVaultCrypto.canSafelyWrite())
        assertFalse("Old wrapped DEK file must be wiped on cryptographic erasure", dekFile.exists())
        assertTrue(
            "Pre-erasure ciphertext must be permanently undecryptable after key erasure",
            LocalVaultCrypto.decryptFieldResult(cipher, "user_notes:content").isFailure
        )
        val postResetCipher = LocalVaultCrypto.encryptField("Fresh entry", "user_notes:content")
        assertEquals("Fresh entry", LocalVaultCrypto.decryptField(postResetCipher, "user_notes:content"))
    }

    // -------------------------------------------------------------------------
    // 3. End-to-End Couple Encryption (ECDH P-256, HKDF-SHA256, AES-GCM + HMAC)
    // -------------------------------------------------------------------------

    @Test
    fun `CoupleEcdhEngine validates NIST P-256 curve strictly and binds shared secrets to room transcript`() {
        val alice = CoupleEcdhEngine.generateKeyPair()
        val bob = CoupleEcdhEngine.generateKeyPair()
        val mallory = CoupleEcdhEngine.generateKeyPair()

        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(alice.publicKeyB64))
        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(bob.publicKeyB64))
        assertFalse(CoupleEcdhEngine.isValidPublicKeyB64(""))
        assertFalse(CoupleEcdhEngine.isValidPublicKeyB64("invalid-base64!!!"))

        // Reject non-P256 curve (secp384r1)
        val p384Pub = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair().public
        val p384B64 = Base64.getEncoder().encodeToString(p384Pub.encoded)
        assertFalse("P-384 key must be rejected", CoupleEcdhEngine.isValidPublicKeyB64(p384B64))
        assertNull(CoupleEcdhEngine.deriveSharedSecretB64(alice.privateKeyB64, p384B64, "986607", alice.publicKeyB64))

        // Reject non-EC (RSA) public key
        val rsaPub = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        val rsaB64 = Base64.getEncoder().encodeToString(rsaPub.encoded)
        assertFalse("RSA public key must be rejected", CoupleEcdhEngine.isValidPublicKeyB64(rsaB64))

        // Symmetric agreement between Alice and Bob
        val secretAlice = CoupleEcdhEngine.deriveSharedSecretB64(alice.privateKeyB64, bob.publicKeyB64, "986607", alice.publicKeyB64)
        val secretBob = CoupleEcdhEngine.deriveSharedSecretB64(bob.privateKeyB64, alice.publicKeyB64, "986607", bob.publicKeyB64)
        assertNotNull(secretAlice)
        assertEquals(secretAlice, secretBob)

        // Transcript binding: different couple code MUST produce different master secret
        val secretOtherRoom = CoupleEcdhEngine.deriveSharedSecretB64(alice.privateKeyB64, bob.publicKeyB64, "112233", alice.publicKeyB64)
        assertNotEquals("Shared secret must be bound to room code", secretAlice, secretOtherRoom)

        // Third-party attacker cannot derive the shared secret
        val secretMallory = CoupleEcdhEngine.deriveSharedSecretB64(mallory.privateKeyB64, alice.publicKeyB64, "986607", mallory.publicKeyB64)
        assertNotEquals(secretAlice, secretMallory)

        // Safety fingerprints match identically for legitimate pair and differ on MITM key substitution
        val fpA = CoupleEcdhEngine.safetyFingerprint(alice.publicKeyB64, bob.publicKeyB64, "986607")
        val fpB = CoupleEcdhEngine.safetyFingerprint(bob.publicKeyB64, alice.publicKeyB64, "986607")
        val fpMitm = CoupleEcdhEngine.safetyFingerprint(alice.publicKeyB64, mallory.publicKeyB64, "986607")
        assertEquals(fpA, fpB)
        assertNotEquals(fpA, fpMitm)
        assertTrue(fpA.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}")))
    }

    @Test
    fun `CoupleCryptoEngine enforces AES-256-GCM and HMAC-SHA256 authenticity and rejects key or AAD tampering`() {
        val code = "739154"
        val alice = CoupleEcdhEngine.generateKeyPair()
        val bob = CoupleEcdhEngine.generateKeyPair()
        val mallory = CoupleEcdhEngine.generateKeyPair()
        val sharedSecret = CoupleEcdhEngine.deriveSharedSecretB64(alice.privateKeyB64, bob.publicKeyB64, code, alice.publicKeyB64)!!
        val attackerSecret = CoupleEcdhEngine.deriveSharedSecretB64(mallory.privateKeyB64, bob.publicKeyB64, code, mallory.publicKeyB64)!!

        val pulsePayload = """{"t":"WHISPER","sender":"Alice","wtext":"🫂 wants to give you a warm hug"}"""
        val aad = "$code|00abc123|00gen000|1|WHISPER|v3|turn-001"

        val env1 = CoupleCryptoEngine.encryptPayload(pulsePayload, code, aad, sharedSecret)
        val env2 = CoupleCryptoEngine.encryptPayload(pulsePayload, code, aad, sharedSecret)

        assertEquals("ECDH-P256/HKDF-SHA256/AES-256-GCM", env1.cipherSuite)
        assertNotEquals("Every encrypted packet must use a fresh 96-bit IV", env1.ivBase64, env2.ivBase64)
        assertFalse("Ciphertext must not expose plaintext", env1.cipherBase64.contains("warm hug"))

        // Legitimate partner decrypts cleanly
        val ok = CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, env1.hmacHex, code, aad, sharedSecret)
        assertTrue(ok.isSuccess)
        assertEquals(pulsePayload, ok.getOrThrow())

        // Wrong ECDH key, missing ECDH key (v2 downgrade attempt), wrong code, wrong AAD, tampered HMAC, tampered ciphertext MUST fail
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, env1.hmacHex, code, aad, attackerSecret).isFailure)
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, env1.hmacHex, code, aad, "").isFailure)
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, env1.hmacHex, "999999", aad, sharedSecret).isFailure)
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, env1.hmacHex, code, "$aad-forged", sharedSecret).isFailure)

        val forgedHmac = (if (env1.hmacHex.startsWith("0")) "1" else "0") + env1.hmacHex.drop(1)
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, env1.cipherBase64, forgedHmac, code, aad, sharedSecret).isFailure)

        val forgedCt = env1.cipherBase64.dropLast(2) + if (env1.cipherBase64.endsWith("AA")) "BB" else "AA"
        assertTrue(CoupleCryptoEngine.decryptPayload(env1.ivBase64, forgedCt, env1.hmacHex, code, aad, sharedSecret).isFailure)

        // Constant-time string comparison sanity
        assertTrue(CoupleCryptoEngine.constantTimeEquals("abcdef", "abcdef"))
        assertFalse(CoupleCryptoEngine.constantTimeEquals("abcdef", "abcdeg"))
        assertFalse(CoupleCryptoEngine.constantTimeEquals("abc", "abcd"))
    }

    // -------------------------------------------------------------------------
    // 4. Hash-Chain Integrity & Tamper-Proofing Tests
    // -------------------------------------------------------------------------

    @Test
    fun `CoupleBlockchain rejects tampered PoW, broken links, skipped indices, duplicates, and forged anchors`() {
        val code = "614928"
        val genesis = CoupleBlockchain.createDeterministicGenesisBlock(code)
        val b1 = CoupleBlockchain.mineBlock(1, "WHISPER", "Maya", genesis.blockHash, CoupleCryptoEngine.sha256Hex("hug-1"), clientTurnId = "t-1")
        val b2 = CoupleBlockchain.mineBlock(2, "QUIZ", "Leo", b1.blockHash, CoupleCryptoEngine.sha256Hex("quiz-2"), clientTurnId = "t-2")
        val b3 = CoupleBlockchain.mineBlock(3, "WYR", "Maya", b2.blockHash, CoupleCryptoEngine.sha256Hex("wyr-3"), clientTurnId = "t-3")

        assertTrue(b1.blockHash.startsWith(CoupleBlockchain.POW_PREFIX))
        assertTrue(CoupleBlockchain.verifyBlock(b1))

        // Valid chain
        val valid = CoupleBlockchain.verifyChain(listOf(genesis, b1, b2, b3), code)
        assertTrue(valid.isValid)
        assertEquals(ChainVerificationScope.FULL_FROM_GENESIS, valid.scope)
        assertEquals(4, valid.verifiedBlockCount)

        // Tampered payload hash breaks block verification
        assertFalse(CoupleBlockchain.verifyBlock(b1.copy(payloadHash = CoupleCryptoEngine.sha256Hex("forged"))))
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, b1.copy(senderName = "Mallory")), code).isValid)

        // Broken previousHash link
        val badLink = CoupleBlockchain.mineBlock(2, "QUIZ", "Leo", "00deadbeef", "p2")
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, b1, badLink), code).isValid)

        // Skipped index & duplicate block
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, b1, b3), code).isValid)
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, b1, b1), code).isValid)

        // Wrong room code genesis mismatch
        assertFalse(CoupleBlockchain.verifyChain(listOf(genesis, b1, b2), "999999").isValid)

        // Pruned window verification & forged pruned anchor rejection
        val pruned = CoupleBlockchain.pruneLedgerWindow(listOf(genesis, b1, b2, b3), maxBlocks = 2)
        val okPruned = CoupleBlockchain.verifyChain(
            blocks = pruned.retainedBlocks,
            coupleCode = code,
            prunedAnchorHash = pruned.prunedAnchorHash,
            prunedBlockCount = pruned.newlyPrunedCount
        )
        assertTrue(okPruned.isValid)
        assertEquals(ChainVerificationScope.PRUNED_WINDOW_ONLY, okPruned.scope)

        val forgedPruned = CoupleBlockchain.verifyChain(
            blocks = pruned.retainedBlocks,
            coupleCode = code,
            prunedAnchorHash = "00forgedanchor",
            prunedBlockCount = pruned.newlyPrunedCount
        )
        assertFalse(forgedPruned.isValid)

        // Secure 6-digit code generator check
        val generated = CoupleTurnCodec.generateUniqueCode(setOf("100000"))
        assertEquals(6, generated.length)
        assertTrue(generated.all { it.isDigit() })
        assertNotEquals("100000", generated)
    }
}
