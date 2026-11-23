package com.example

import com.example.data.CoupleRoomRecord
import com.example.data.PeriodLog
import com.example.data.UserProfile
import com.example.model.CoupleCryptoEngine
import com.example.model.CoupleEcdhEngine
import com.example.model.CycleCalculator
import com.example.model.CyclePhase
import com.example.model.PartnerCareGuideProvider
import com.example.model.PartnerCycleSyncSnapshot
import com.example.model.UserGender
import com.example.security.BackendSecurityFirewall
import com.example.security.LocalVaultCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pure-JVM verification suite for Dual UI (Male/Female) models, Partner Cycle Care Playbooks,
 * midnight projection, firewall gender enforcement, and two-phone E2EE live cycle sync.
 */
class DualUiAndLiveSyncTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "krimoon_dual_ui_test_${System.nanoTime()}").apply {
            mkdirs()
        }
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
    }

    @After
    fun tearDown() {
        LocalVaultCrypto.resetForTest(noBackupDir = tempDir, enableTestMode = true)
        tempDir.deleteRecursively()
    }

    @Test
    fun `UserGender parsing and firewall guard enforce strict MALE or FEMALE values`() {
        assertEquals(UserGender.MALE, UserGender.fromStorage("MALE"))
        assertEquals(UserGender.MALE, UserGender.fromStorage("male"))
        assertEquals(UserGender.FEMALE, UserGender.fromStorage("FEMALE"))
        assertEquals(UserGender.FEMALE, UserGender.fromStorage("PARTNER"))
        assertEquals(UserGender.FEMALE, UserGender.fromStorage(null))

        val guardedMale = BackendSecurityFirewall.guardUserProfile(
            UserProfile(name = "Alex", userGender = "male")
        )
        assertEquals("MALE", guardedMale.userGender)

        val guardedInjected = BackendSecurityFirewall.guardUserProfile(
            UserProfile(name = "Alex", userGender = "MALE; DROP TABLE user_profile;--")
        )
        assertEquals("FEMALE", guardedInjected.userGender)
    }

    @Test
    fun `PartnerCycleSyncSnapshot serializes cleanly, sanitizes inputs, and detects fingerprint changes`() {
        val msPerDay = 24L * 60L * 60L * 1000L
        val now = 1_750_000_000_000L
        val periodStart = now - (13L * msPerDay) // Day 14 -> Ovulation in 28d cycle
        val status = CycleCalculator.compute(
            periods = listOf(PeriodLog(id = 1L, startDateMillis = periodStart, durationDays = 5)),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = now
        )

        val snapshot = PartnerCycleSyncSnapshot.fromCycleStatus(
            partnerName = "Aria <script>alert(1)</script>",
            status = status,
            nowMillis = now
        )
        val wireJson = snapshot.toJsonString()
        assertFalse("Wire JSON must sanitize XSS", wireJson.contains("<script", ignoreCase = true))

        val parsed = PartnerCycleSyncSnapshot.fromJsonString(wireJson)
        assertNotNull(parsed)
        parsed!!
        assertEquals(status.currentPhase, parsed.currentPhase)
        assertEquals(status.nextPhase, parsed.nextPhase)
        assertEquals(status.dayInCycle, parsed.dayInCycle)
        assertEquals(status.totalCycleDays, parsed.totalCycleDays)
        assertEquals(status.periodLength, parsed.periodLength)
        assertEquals(status.daysUntilNextPeriod, parsed.daysUntilNextPeriod)
        assertEquals(status.daysUntilNextPhase, parsed.daysUntilNextPhase)
        assertEquals(status.isPeriodCurrentlyActive, parsed.isPeriodCurrentlyActive)
        assertEquals(status.lastPeriodStartDateMillis ?: 0L, parsed.lastPeriodStartMillis)
        assertEquals(now, parsed.syncedAtMillis)

        // Fingerprint must be stable for identical snapshots and change when cycle day/phase shifts
        assertEquals(snapshot.fingerprint(), parsed.fingerprint())
        assertNotEquals(
            snapshot.fingerprint(),
            parsed.copy(dayInCycle = parsed.dayInCycle + 1).fingerprint()
        )
        assertNull(PartnerCycleSyncSnapshot.fromJsonString("{}"))
        assertNull(PartnerCycleSyncSnapshot.fromJsonString(""))
    }

    @Test
    fun `PartnerCycleSyncSnapshot projects forward accurately across midnight phase transitions`() {
        val msPerDay = 24L * 60L * 60L * 1000L
        val periodStart = 1_750_000_000_000L
        val day3Millis = periodStart + (2L * msPerDay) // Day 3 -> MENSTRUAL
        val day14Millis = periodStart + (13L * msPerDay) // Day 14 -> OVULATION
        val day26Millis = periodStart + (25L * msPerDay) // Day 26 -> LATE_LUTEAL

        val initialStatus = CycleCalculator.compute(
            periods = listOf(PeriodLog(id = 1L, startDateMillis = periodStart, durationDays = 5)),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = day3Millis
        )
        val snapshot = PartnerCycleSyncSnapshot.fromCycleStatus(
            partnerName = "Maya",
            status = initialStatus,
            nowMillis = day3Millis
        )
        assertEquals(CyclePhase.MENSTRUAL, snapshot.currentPhase)
        assertEquals(3, snapshot.dayInCycle)

        // Without re-syncing, projecting 11 days later should land on Day 14 (OVULATION)
        val projectedDay14 = snapshot.projectToTime(nowMillis = day14Millis)
        assertEquals(14, projectedDay14.dayInCycle)
        assertEquals(CyclePhase.OVULATION, projectedDay14.currentPhase)
        assertEquals(CyclePhase.LUTEAL, projectedDay14.nextPhase)

        // Projecting to Day 26 should land in LATE_LUTEAL (PMS) with MENSTRUAL up next
        val projectedDay26 = snapshot.projectToTime(nowMillis = day26Millis)
        assertEquals(26, projectedDay26.dayInCycle)
        assertEquals(CyclePhase.LATE_LUTEAL, projectedDay26.currentPhase)
        assertEquals(CyclePhase.MENSTRUAL, projectedDay26.nextPhase)
    }

    @Test
    fun `PartnerCareGuideProvider supplies complete current and next phase playbooks for every CyclePhase`() {
        CyclePhase.entries.forEach { phase ->
            val guide = PartnerCareGuideProvider.guideFor(phase)
            assertEquals(phase, guide.phase)
            assertTrue("displayTitle missing for $phase", guide.displayTitle.isNotBlank())
            assertTrue("energyLabel missing for $phase", guide.energyLabel.isNotBlank())
            assertTrue("statusHeadline missing for $phase", guide.statusHeadline.isNotBlank())
            assertTrue("moodSummary missing for $phase", guide.moodSummary.isNotBlank())
            assertTrue("careRightNow must have >= 3 actions for $phase", guide.careRightNow.size >= 3)
            assertTrue("avoidList must not be empty for $phase", guide.avoidList.isNotEmpty())
            assertTrue("prepForNextHeadline missing for $phase", guide.prepForNextHeadline.isNotBlank())
            assertTrue("prepForNextActions must have >= 2 steps for $phase", guide.prepForNextActions.size >= 2)
        }
    }

    @Test
    fun `two-phone E2EE live cycle sync encrypts with ECDH v3 AEAD and blocks eavesdroppers`() {
        val roomCode = "742819"
        val femaleKeyPair = CoupleEcdhEngine.generateKeyPair()
        val maleKeyPair = CoupleEcdhEngine.generateKeyPair()
        val eavesdropperKeyPair = CoupleEcdhEngine.generateKeyPair()

        val femaleSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            myPrivateKeyB64 = femaleKeyPair.privateKeyB64,
            peerPublicKeyB64 = maleKeyPair.publicKeyB64,
            coupleCode = roomCode,
            myPublicKeyB64 = femaleKeyPair.publicKeyB64
        )
        val maleSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            myPrivateKeyB64 = maleKeyPair.privateKeyB64,
            peerPublicKeyB64 = femaleKeyPair.publicKeyB64,
            coupleCode = roomCode,
            myPublicKeyB64 = maleKeyPair.publicKeyB64
        )
        val mallorySecret = CoupleEcdhEngine.deriveSharedSecretB64(
            myPrivateKeyB64 = eavesdropperKeyPair.privateKeyB64,
            peerPublicKeyB64 = femaleKeyPair.publicKeyB64,
            coupleCode = roomCode,
            myPublicKeyB64 = eavesdropperKeyPair.publicKeyB64
        )

        assertNotNull(femaleSecret)
        assertEquals("Female and Male phones must derive identical ECDH shared secret", femaleSecret, maleSecret)
        assertNotEquals("Eavesdropper must never match room shared secret", femaleSecret, mallorySecret)

        // Female phone builds current cycle snapshot and encrypts with ECDH v3 AEAD
        val msPerDay = 24L * 60L * 60L * 1000L
        val now = 1_755_000_000_000L
        val status = CycleCalculator.compute(
            periods = listOf(PeriodLog(id = 1L, startDateMillis = now - 25L * msPerDay, durationDays = 5)),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = now
        )
        val outgoing = PartnerCycleSyncSnapshot.fromCycleStatus("Sophia", status, now)
        val wirePlaintext = outgoing.toJsonString()
        val blockAad = "$roomCode|00abc123|00gen000|1|HANDSHAKE|v3|cycle-sync-001"

        val encryptedEnv = CoupleCryptoEngine.encryptPayload(
            plainJson = wirePlaintext,
            coupleCode = roomCode,
            aad = blockAad,
            ecdhSharedSecretB64 = femaleSecret!!
        )
        assertEquals("ECDH-P256/HKDF-SHA256/AES-256-GCM", encryptedEnv.cipherSuite)
        assertFalse("Ciphertext must not leak phase name", encryptedEnv.cipherBase64.contains("LATE_LUTEAL"))
        assertFalse("Ciphertext must not leak partner name", encryptedEnv.cipherBase64.contains("Sophia"))

        // Male phone decrypts & recovers exact partner cycle state
        val decryptedResult = CoupleCryptoEngine.decryptPayload(
            ivBase64 = encryptedEnv.ivBase64,
            cipherBase64 = encryptedEnv.cipherBase64,
            expectedHmacHex = encryptedEnv.hmacHex,
            coupleCode = roomCode,
            aad = blockAad,
            ecdhSharedSecretB64 = maleSecret!!
        )
        assertTrue("Male phone must decrypt live partner cycle payload", decryptedResult.isSuccess)
        val received = PartnerCycleSyncSnapshot.fromJsonString(decryptedResult.getOrThrow())
        assertNotNull(received)
        received!!
        assertEquals("Sophia", received.partnerName)
        assertEquals(CyclePhase.LATE_LUTEAL, received.currentPhase)
        assertEquals(CyclePhase.MENSTRUAL, received.nextPhase)
        assertEquals(status.dayInCycle, received.dayInCycle)
        assertEquals(status.daysUntilNextPeriod, received.daysUntilNextPeriod)

        // Eavesdropper decryption MUST fail authentication
        val malloryResult = CoupleCryptoEngine.decryptPayload(
            ivBase64 = encryptedEnv.ivBase64,
            cipherBase64 = encryptedEnv.cipherBase64,
            expectedHmacHex = encryptedEnv.hmacHex,
            coupleCode = roomCode,
            aad = blockAad,
            ecdhSharedSecretB64 = mallorySecret!!
        )
        assertTrue("Eavesdropper must be blocked from decrypting partner cycle data", malloryResult.isFailure)

        // At-rest vault encryption of partnerCycleSyncJson with per-column AAD
        val room = CoupleRoomRecord(coupleCode = roomCode, partnerCycleSyncJson = wirePlaintext)
        val aad = "couple_rooms:$roomCode:partnerCycleSyncJson"
        val atRestCipher = LocalVaultCrypto.encryptField(room.partnerCycleSyncJson, aad)
        assertTrue(LocalVaultCrypto.isEncrypted(atRestCipher))
        assertEquals(wirePlaintext, LocalVaultCrypto.decryptField(atRestCipher, aad))
        assertTrue(
            "Cross-column AAD swap must fail authentication",
            LocalVaultCrypto.decryptFieldResult(atRestCipher, "couple_rooms:$roomCode:quizStateJson").isFailure
        )
    }
}
