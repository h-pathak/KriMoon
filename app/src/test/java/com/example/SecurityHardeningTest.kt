package com.example

import com.example.model.CoupleBlockchain
import com.example.model.CoupleEcdhEngine
import com.example.model.CoupleTurnCodec
import com.example.model.DecodedTurnPacket
import com.example.model.SecretWhisperNote
import com.example.model.SpiceLevel
import com.example.security.BackendSecurityFirewall
import com.example.security.LocalVaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityHardeningTest {

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
        val swappedResult = LocalVaultCrypto.decryptField(cipher, "user_profile:name")
        assertEquals("", swappedResult)

        // Bit-flipping ciphertext must fail GCM tag verification
        val tampered = cipher.dropLast(2) + if (cipher.endsWith("AA")) "BB" else "AA"
        assertEquals("", LocalVaultCrypto.decryptField(tampered, "user_notes:content"))
    }

    @Test
    fun `LocalVaultCrypto passes through legacy unprefixed plaintext and erases on key rotation`() {
        val legacyPlain = "Pre-upgrade diary entry"
        assertEquals(legacyPlain, LocalVaultCrypto.decryptField(legacyPlain, "user_notes:content"))

        val encryptedBeforeWipe = LocalVaultCrypto.encryptField("Top secret health log", "period_logs:note")
        assertEquals("Top secret health log", LocalVaultCrypto.decryptField(encryptedBeforeWipe, "period_logs:note"))

        // Cryptographic erasure via key rotation makes old ciphertext permanently unrecoverable
        LocalVaultCrypto.rotateVaultKey()
        assertEquals("", LocalVaultCrypto.decryptField(encryptedBeforeWipe, "period_logs:note"))

        // Fresh writes after rotation work cleanly with the new key
        val freshCipher = LocalVaultCrypto.encryptField("Post-reset entry", "period_logs:note")
        assertEquals("Post-reset entry", LocalVaultCrypto.decryptField(freshCipher, "period_logs:note"))
    }

    @Test
    fun `CoupleEcdhEngine derives identical shared secrets and safety fingerprints for both partners`() {
        val coupleCode = "482917"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()

        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(hostKeys.publicKeyB64))
        assertTrue(CoupleEcdhEngine.isValidPublicKeyB64(partnerKeys.publicKeyB64))
        assertFalse(CoupleEcdhEngine.isValidPublicKeyB64("not-a-valid-key"))

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
    fun `CoupleTurnCodec v3 zero-knowledge packets cannot be decrypted with room code alone or MITM key`() {
        val coupleCode = "739154"
        val hostKeys = CoupleEcdhEngine.generateKeyPair()
        val partnerKeys = CoupleEcdhEngine.generateKeyPair()
        val attackerKeys = CoupleEcdhEngine.generateKeyPair()

        val sharedSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            hostKeys.privateKeyB64,
            partnerKeys.publicKeyB64,
            coupleCode,
            hostKeys.publicKeyB64
        )!!
        val attackerSecret = CoupleEcdhEngine.deriveSharedSecretB64(
            attackerKeys.privateKeyB64,
            hostKeys.publicKeyB64,
            coupleCode,
            attackerKeys.publicKeyB64
        )!!

        val genesis = CoupleBlockchain.createGenesisBlock(coupleCode, "Maya")
        val secretWhisper = SecretWhisperNote(
            id = "w_1",
            senderName = "Maya",
            text = "Meet me on the rooftop at 9pm",
            timestamp = System.currentTimeMillis(),
            isRevealed = false
        )

        val (packet, block) = CoupleTurnCodec.encodeWhisperTurnWithBlock(
            coupleCode = coupleCode,
            senderName = "Maya",
            likedDateId = "date_stargaze",
            whisperNote = secretWhisper,
            previousHash = genesis.blockHash,
            blockIndex = 1,
            ecdhSharedSecretB64 = sharedSecret
        )

        assertFalse("Plaintext must never appear in encrypted packet", packet.contains("rooftop"))

        // 1. Legitimate partner with matching ECDH shared secret decrypts cleanly
        val partnerDecoded = CoupleTurnCodec.decodePacket(
            rawInput = packet,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret
        )
        assertTrue("Partner with ECDH shared secret must decode v3 packet", partnerDecoded.isSuccess)
        val whisperPacket = partnerDecoded.getOrThrow() as DecodedTurnPacket.WhisperTurn
        assertEquals("Meet me on the rooftop at 9pm", whisperPacket.whisperNote?.text)
        assertEquals("date_stargaze", whisperPacket.likedDateId)

        // 2. Eavesdropper / Firebase admin knowing ONLY the 6-digit room code (no ECDH secret) FAILS
        val codeOnlyAttempt = CoupleTurnCodec.decodePacket(
            rawInput = packet,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = ""
        )
        assertTrue("Room code alone must NOT decrypt v3 ECDH packets", codeOnlyAttempt.isFailure)

        // 3. MITM attacker with wrong ECDH key FAILS
        val mitmAttempt = CoupleTurnCodec.decodePacket(
            rawInput = packet,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = attackerSecret
        )
        assertTrue("Wrong ECDH secret must NOT decrypt v3 packets", mitmAttempt.isFailure)

        // 4. Replay of an already-processed block hash FAILS
        val replayAttempt = CoupleTurnCodec.decodePacket(
            rawInput = packet,
            expectedCoupleCode = coupleCode,
            existingLedger = listOf(genesis, block),
            lockedPartnerName = "Maya",
            myName = "Leo",
            ecdhSharedSecretB64 = sharedSecret
        )
        assertTrue("Replayed block must be rejected by firewall", replayAttempt.isFailure)
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
