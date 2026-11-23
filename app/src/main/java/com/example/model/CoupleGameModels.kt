package com.example.model

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class SpiceLevel(
    val title: String,
    val emoji: String,
    val subtitle: String
) {
    SWEET("Sweet", "🍯", "Cute & heartwarming"),
    FLIRTY("Flirty", "💋", "Playful & teasing"),
    BOLD("After-Dark", "🔥", "Steamy & daring")
}

data class CoupleQuizQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val spiceLevel: SpiceLevel
)

data class WouldYouRatherPrompt(
    val id: String,
    val optionA: String,
    val optionB: String,
    val spiceLevel: SpiceLevel
)

data class DoodleStrokeData(
    val author: String,
    val colorHex: Long,
    val strokeWidth: Float,
    val points: List<Pair<Float, Float>> // normalized 0f..1f coordinates
)

data class RoundSyncState(
    val roundIndex: Int = 0,
    val myChoice: Int? = null,
    val partnerChoice: Int? = null,
    val partnerName: String = "",
    val history: List<RoundOutcome> = emptyList()
)

data class TicTacToeSyncState(
    val board: List<String> = List(9) { "" }, // "", "X", "O"
    val xQueue: List<Int> = emptyList(),
    val oQueue: List<Int> = emptyList(),
    val nextTurn: String = "X",
    val isBoltTwist: Boolean = true,
    val mySymbol: String = "X",
    val roundCount: Int = 1
)

/**
 * Immutable block in the couple's private hash-chained ledger.
 * Every handshake, doodle stroke batch, quiz pick, WYR pick, or Tic-Tac-Toe move
 * is mined with Proof-of-Work and linked via SHA-256 to the previous block.
 */
data class CoupleBlock(
    val index: Int,
    val timestamp: Long,
    val actionType: String, // "GENESIS_BOND", "HANDSHAKE", "QUIZ", "WYR", "DOODLE", "TTT", "DARE", "WAVELENGTH", "WHISPER", "UNPAIR"
    val senderName: String,
    val previousHash: String,
    val payloadHash: String,
    val nonce: Int,
    val blockHash: String,
    val cipherPreview: String = "",
    val signaturePreview: String = "",
    val clientTurnId: String = ""
)

data class EncryptedEnvelope(
    val ivBase64: String,
    val cipherBase64: String,
    val hmacHex: String,
    val cipherSuite: String = "AES-256-GCM/PBKDF2-HMAC-SHA256"
)

enum class ChainVerificationScope {
    FULL_FROM_GENESIS,
    MIGRATED_FROM_V1_ANCHOR,
    PRUNED_WINDOW_ONLY,
    INVALID
}

data class ChainVerificationResult(
    val isValid: Boolean,
    val verifiedBlockCount: Int,
    val latestBlockHash: String,
    val summary: String,
    val scope: ChainVerificationScope = ChainVerificationScope.FULL_FROM_GENESIS,
    val prunedBlockCount: Int = 0,
    val migrationAnchorHash: String = ""
)

/**
 * Plaintext activity intent stored encrypted inside the local pending-turn vault.
 * Whenever a queued intent must be rebased onto a newer cloud chain head, a fresh block is mined
 * and the intent payload is re-encrypted with a brand-new 96-bit AES-GCM nonce and updated AAD.
 */
data class CoupleActivityIntent(
    val clientTurnId: String = UUID.randomUUID().toString(),
    val actionType: String,
    val senderName: String,
    val payloadJson: String
) {
    fun toJsonString(): String = JSONObject().apply {
        put("ctid", clientTurnId)
        put("act", actionType)
        put("snd", senderName)
        put("payload", payloadJson)
    }.toString()

    companion object {
        fun fromJsonString(raw: String): CoupleActivityIntent? = runCatching {
            val o = JSONObject(raw)
            val ctid = o.optString("ctid", "").ifBlank { UUID.randomUUID().toString() }
            val act = o.optString("act", "")
            val snd = o.optString("snd", "")
            val payload = o.optString("payload", "")
            if (act.isBlank() || payload.isBlank()) null
            else CoupleActivityIntent(ctid, act, snd, payload)
        }.getOrNull()
    }
}

sealed class DecodedTurnPacket {
    abstract val coupleCode: String
    abstract val senderName: String
    abstract val block: CoupleBlock

    data class BondHandshake(
        override val coupleCode: String,
        override val senderName: String,
        val spiceLevel: SpiceLevel,
        override val block: CoupleBlock,
        val partnerCycleSync: PartnerCycleSyncSnapshot? = null
    ) : DecodedTurnPacket()

    data class QuizTurn(
        override val coupleCode: String,
        override val senderName: String,
        val roundIndex: Int,
        val choiceIndex: Int,
        val spiceLevel: SpiceLevel,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class WyrTurn(
        override val coupleCode: String,
        override val senderName: String,
        val roundIndex: Int,
        val choiceIndex: Int,
        val spiceLevel: SpiceLevel,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class DoodleTurn(
        override val coupleCode: String,
        override val senderName: String,
        val prompt: String,
        val strokes: List<DoodleStrokeData>,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class TicTacToeTurn(
        override val coupleCode: String,
        override val senderName: String,
        val state: TicTacToeSyncState,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class DareTurn(
        override val coupleCode: String,
        override val senderName: String,
        val state: DareSyncState,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class WavelengthTurn(
        override val coupleCode: String,
        override val senderName: String,
        val state: WavelengthSyncState,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class WhisperTurn(
        override val coupleCode: String,
        override val senderName: String,
        val likedDateId: String,
        val whisperNote: SecretWhisperNote?,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()

    data class UnpairNotice(
        override val coupleCode: String,
        override val senderName: String,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()
}

data class CoupleEcdhKeyPair(
    val publicKeyB64: String,
    val privateKeyB64: String
)

/**
 * True Zero-Knowledge Elliptic-Curve Diffie-Hellman (P-256 / secp256r1) + RFC 5869 HKDF-SHA256.
 * Private keys are generated on-device and stored encrypted via Android Keystore — they never leave the phone.
 * Only X.509 SPKI public keys are exchanged in Firestore, making Couple Lounge payloads unreadable
 * to Firebase servers or anyone who knows the 6-digit room code.
 */
object CoupleEcdhEngine {
    private const val CURVE_NAME = "secp256r1"

    // NIST P-256 / secp256r1 domain parameters (FIPS 186-4 / SEC 2)
    private val P256_P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val P256_A = BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16)
    private val P256_B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    private val P256_GX = BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16)
    private val P256_GY = BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)
    private val P256_N = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)

    fun generateKeyPair(): CoupleEcdhKeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        val kp = kpg.generateKeyPair()
        val pubB64 = com.example.security.SafeBase64.encodeNoWrap(kp.public.encoded)
        val privB64 = com.example.security.SafeBase64.encodeNoWrap(kp.private.encoded)
        return CoupleEcdhKeyPair(publicKeyB64 = pubB64, privateKeyB64 = privB64)
    }

    /**
     * Strictly validates that [pub] is an [ECPublicKey] on NIST P-256 (secp256r1),
     * not the point at infinity, with coordinates in [0, p-1] satisfying y^2 = x^3 + ax + b (mod p).
     */
    fun validateNistP256PublicKey(pub: PublicKey): ECPublicKey? {
        val ecPub = pub as? ECPublicKey ?: return null
        val params = ecPub.params ?: return null
        val field = params.curve.field as? ECFieldFp ?: return null
        if (field.p != P256_P) return null
        if (params.curve.a != P256_A || params.curve.b != P256_B) return null
        if (params.generator.affineX != P256_GX || params.generator.affineY != P256_GY) return null
        if (params.order != P256_N || params.cofactor != 1) return null

        val w = ecPub.w ?: return null
        if (w == ECPoint.POINT_INFINITY) return null
        val x = w.affineX ?: return null
        val y = w.affineY ?: return null
        if (x < BigInteger.ZERO || x >= P256_P || y < BigInteger.ZERO || y >= P256_P) return null

        val lhs = y.multiply(y).mod(P256_P)
        val rhs = x.pow(3).add(P256_A.multiply(x)).add(P256_B).mod(P256_P)
        if (lhs != rhs) return null
        return ecPub
    }

    fun isValidPublicKeyB64(publicKeyB64: String): Boolean {
        if (publicKeyB64.length !in 80..256) return false
        return try {
            val bytes = com.example.security.SafeBase64.decode(publicKeyB64)
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
            validateNistP256PublicKey(pub) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Derives a 256-bit zero-knowledge shared master secret (Base64) from local private key + peer public key,
     * after strict NIST P-256 curve/point validation and rejection of all-zero raw ECDH secrets,
     * bound via HKDF-SHA256 to the room code and lexicographically sorted public keys (transcript binding).
     */
    fun deriveSharedSecretB64(
        myPrivateKeyB64: String,
        peerPublicKeyB64: String,
        coupleCode: String,
        myPublicKeyB64: String
    ): String? {
        if (myPrivateKeyB64.isBlank() || peerPublicKeyB64.isBlank()) return null
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val privKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(com.example.security.SafeBase64.decode(myPrivateKeyB64))
            )
            val rawPeerPub = keyFactory.generatePublic(
                X509EncodedKeySpec(com.example.security.SafeBase64.decode(peerPublicKeyB64))
            )
            val peerPub = validateNistP256PublicKey(rawPeerPub) ?: return null

            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(privKey)
            agreement.doPhase(peerPub, true)
            val rawSecret = agreement.generateSecret()

            // Reject invalid length or all-zero ECDH shared secret
            if (rawSecret.size != 32 || rawSecret.all { it.toInt() == 0 }) {
                return null
            }

            val sortedPubs = listOf(myPublicKeyB64.trim(), peerPublicKeyB64.trim()).sorted()
            val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
            val saltInput = "KriMoon_ECDH_Salt_v3|$cleanCode|${sortedPubs[0]}|${sortedPubs[1]}"
            val salt = MessageDigest.getInstance("SHA-256")
                .digest(saltInput.toByteArray(StandardCharsets.UTF_8))

            val prk = hkdfExtract(salt = salt, ikm = rawSecret)
            val masterSecret = hkdfExpand(
                prk = prk,
                info = "KriMoon_MasterSecret_v3|$cleanCode".toByteArray(StandardCharsets.UTF_8),
                length = 32
            )
            rawSecret.fill(0)
            prk.fill(0)
            com.example.security.SafeBase64.encodeNoWrap(masterSecret)
        } catch (_: Throwable) {
            null
        }
    }

    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val output = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, output, offset, copyLen)
            offset += copyLen
            counter++
        }
        return output
    }

    /**
     * Generates a human-verifiable 16-character safety fingerprint from both public keys
     * so partners can verify zero man-in-the-middle tampering.
     */
    fun safetyFingerprint(pubKeyA: String, pubKeyB: String, coupleCode: String): String {
        if (pubKeyA.isBlank() || pubKeyB.isBlank()) return CoupleCryptoEngine.keyFingerprint(coupleCode)
        val sorted = listOf(pubKeyA.trim(), pubKeyB.trim()).sorted()
        val hash = CoupleCryptoEngine.sha256Hex("KriMoon_ECDH_FP_v3|$coupleCode|${sorted[0]}|${sorted[1]}").uppercase()
        return "${hash.substring(0, 4)}-${hash.substring(4, 8)}-${hash.substring(8, 12)}-${hash.substring(12, 16)}"
    }
}

/**
 * End-to-End Authenticated Encryption Engine for Couple Lounge.
 * - Protocol v3 (Zero-Knowledge Connected): ECDH P-256 + HKDF-SHA256 derived AES-256-GCM + HMAC-SHA256 keys.
 * - Protocol v2 (Pass & Play / Pre-ECDH legacy fallback): PBKDF2-HMAC-SHA256 derived keys.
 */
object CoupleCryptoEngine {
    private const val PBKDF2_ITERATIONS = 4096
    private const val KEY_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val secureRandom = SecureRandom()

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun deriveAesKey(coupleCode: String, ecdhSharedSecretB64: String = ""): SecretKeySpec {
        val normalized = CoupleTurnCodec.normalizeCode(coupleCode)
        if (ecdhSharedSecretB64.isNotBlank()) {
            val master = com.example.security.SafeBase64.decode(ecdhSharedSecretB64)
            val aesBytes = CoupleEcdhEngine.hkdfExpand(
                prk = master,
                info = "KriMoon_AES256GCM_v3|$normalized".toByteArray(StandardCharsets.UTF_8),
                length = 32
            )
            master.fill(0)
            return SecretKeySpec(aesBytes, "AES")
        }
        val salt = "KriMoon_AES256_Vault_Salt_v2_$normalized".toByteArray(StandardCharsets.UTF_8)
        val keyBytes = try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(normalized.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest((normalized + "_aes_fallback").toByteArray(StandardCharsets.UTF_8))
            repeat(512) {
                digest = MessageDigest.getInstance("SHA-256").digest(digest + salt)
            }
            digest
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun deriveHmacKey(coupleCode: String, ecdhSharedSecretB64: String = ""): ByteArray {
        val normalized = CoupleTurnCodec.normalizeCode(coupleCode)
        if (ecdhSharedSecretB64.isNotBlank()) {
            val master = com.example.security.SafeBase64.decode(ecdhSharedSecretB64)
            val hmacKey = CoupleEcdhEngine.hkdfExpand(
                prk = master,
                info = "KriMoon_HMACSHA256_v3|$normalized".toByteArray(StandardCharsets.UTF_8),
                length = 32
            )
            master.fill(0)
            return hmacKey
        }
        val salt = "KriMoon_HMAC256_Sign_Salt_v2_$normalized".toByteArray(StandardCharsets.UTF_8)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec((normalized + "_hmac").toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            MessageDigest.getInstance("SHA-256")
                .digest("KriMoon_HMAC_Fallback_$normalized".toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun computeHmacHex(coupleCode: String, data: String, ecdhSharedSecretB64: String = ""): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deriveHmacKey(coupleCode, ecdhSharedSecretB64), "HmacSHA256"))
        val raw = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    fun keyFingerprint(coupleCode: String): String {
        val normalized = CoupleTurnCodec.normalizeCode(coupleCode)
        if (normalized.length != 6) return "UNBONDED"
        val hash = sha256Hex("KriMoon_Key_Fingerprint_v2_$normalized").uppercase()
        return "${hash.substring(0, 4)}-${hash.substring(4, 8)}-${hash.substring(8, 12)}-${hash.substring(12, 16)}"
    }

    fun encryptPayload(
        plainJson: String,
        coupleCode: String,
        aad: String,
        ecdhSharedSecretB64: String = ""
    ): EncryptedEnvelope {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = deriveAesKey(coupleCode, ecdhSharedSecretB64)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))

        val cipherBytes = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = com.example.security.SafeBase64.encodeNoWrap(iv)
        val cipherB64 = com.example.security.SafeBase64.encodeNoWrap(cipherBytes)
        val hmacHex = computeHmacHex(coupleCode, "$ivB64.$cipherB64.$aad", ecdhSharedSecretB64)

        return EncryptedEnvelope(
            ivBase64 = ivB64,
            cipherBase64 = cipherB64,
            hmacHex = hmacHex,
            cipherSuite = if (ecdhSharedSecretB64.isNotBlank()) {
                "ECDH-P256/HKDF-SHA256/AES-256-GCM"
            } else {
                "AES-256-GCM/PBKDF2-HMAC-SHA256"
            }
        )
    }

    fun decryptPayload(
        ivBase64: String,
        cipherBase64: String,
        expectedHmacHex: String,
        coupleCode: String,
        aad: String,
        ecdhSharedSecretB64: String = ""
    ): Result<String> {
        return try {
            val computedHmac = computeHmacHex(coupleCode, "$ivBase64.$cipherBase64.$aad", ecdhSharedSecretB64)
            if (!constantTimeEquals(computedHmac, expectedHmacHex)) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: HMAC-SHA256 signature mismatch (wrong key or tampered packet).")
                )
            }
            val iv = com.example.security.SafeBase64.decode(ivBase64)
            if (iv.size != GCM_IV_BYTES) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Invalid AES-GCM IV length."))
            }
            val cipherBytes = com.example.security.SafeBase64.decode(cipherBase64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = deriveAesKey(coupleCode, ecdhSharedSecretB64)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
            val plainBytes = cipher.doFinal(cipherBytes)
            Result.success(String(plainBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(
                SecurityException("FIREWALL BLOCKED: AES-256-GCM authenticated decryption failed (${e.javaClass.simpleName}).")
            )
        }
    }
}

/**
 * Couple Blockchain Engine.
 * Maintains an immutable Proof-of-Work hash chain for every action exchanged in a Couple Room.
 */
object CoupleBlockchain {
    const val POW_PREFIX = "00"
    const val SCHEMA_VERSION_V1 = 1
    const val SCHEMA_VERSION_V2 = 2
    const val MAX_LOCAL_LEDGER_BLOCKS = 80

    fun genesisHash(coupleCode: String): String {
        val clean = CoupleTurnCodec.normalizeCode(coupleCode)
        return "00" + CoupleCryptoEngine.sha256Hex("KRIMOON_GENESIS_ANCHOR_V2_$clean").drop(2)
    }

    /**
     * Canonical deterministic Genesis block (Index #0) for newly created V2 rooms.
     * Independent of mutable creator display name so both devices and Firestore agree identically.
     */
    fun createDeterministicGenesisBlock(coupleCode: String): CoupleBlock {
        val clean = CoupleTurnCodec.normalizeCode(coupleCode)
        val rootAnchor = genesisHash(clean)
        val payloadHash = CoupleCryptoEngine.sha256Hex("GENESIS_V2|$clean")
        val deterministicTs = 1700000000000L + (clean.toLongOrNull() ?: 0L)
        return mineBlock(
            index = 0,
            actionType = "GENESIS_BOND",
            senderName = "Genesis",
            previousHash = rootAnchor,
            payloadHash = payloadHash,
            timestamp = deterministicTs,
            cipherPreview = "AES256-GCM-GENESIS-V2",
            signaturePreview = CoupleCryptoEngine.keyFingerprint(clean)
        )
    }

    /**
     * Legacy V1 Genesis block builder retained solely for backward-compatible verification of
     * existing V1 rooms created before deterministic V2 Genesis rollout.
     */
    fun createLegacyV1GenesisBlock(coupleCode: String, creatorName: String): CoupleBlock {
        val clean = CoupleTurnCodec.normalizeCode(coupleCode)
        val rootAnchor = genesisHash(clean)
        val payloadHash = CoupleCryptoEngine.sha256Hex("GENESIS_BOND|$clean|${creatorName.trim()}")
        val deterministicTs = 1700000000000L + (clean.toLongOrNull() ?: 0L)
        return mineBlock(
            index = 0,
            actionType = "GENESIS_BOND",
            senderName = creatorName.trim().ifBlank { "Creator" },
            previousHash = rootAnchor,
            payloadHash = payloadHash,
            timestamp = deterministicTs,
            cipherPreview = "AES256-GCM-GENESIS",
            signaturePreview = CoupleCryptoEngine.keyFingerprint(clean)
        )
    }

    fun createGenesisBlock(coupleCode: String, creatorName: String): CoupleBlock {
        return createDeterministicGenesisBlock(coupleCode)
    }

    fun computeBlockHash(
        index: Int,
        timestamp: Long,
        actionType: String,
        senderName: String,
        previousHash: String,
        payloadHash: String,
        nonce: Int
    ): String {
        val canonical = "$index|$timestamp|$actionType|${senderName.trim()}|$previousHash|$payloadHash|$nonce"
        return CoupleCryptoEngine.sha256Hex(canonical)
    }

    fun mineBlock(
        index: Int,
        actionType: String,
        senderName: String,
        previousHash: String,
        payloadHash: String,
        timestamp: Long = System.currentTimeMillis(),
        cipherPreview: String = "",
        signaturePreview: String = "",
        clientTurnId: String = ""
    ): CoupleBlock {
        var nonce = 0
        var hash = computeBlockHash(index, timestamp, actionType, senderName, previousHash, payloadHash, nonce)
        while (!hash.startsWith(POW_PREFIX) && nonce < 50_000) {
            nonce++
            hash = computeBlockHash(index, timestamp, actionType, senderName, previousHash, payloadHash, nonce)
        }
        return CoupleBlock(
            index = index,
            timestamp = timestamp,
            actionType = actionType,
            senderName = senderName.trim(),
            previousHash = previousHash,
            payloadHash = payloadHash,
            nonce = nonce,
            blockHash = hash,
            cipherPreview = cipherPreview,
            signaturePreview = signaturePreview,
            clientTurnId = clientTurnId
        )
    }

    fun verifyBlock(block: CoupleBlock): Boolean {
        if (block.index < 0 || block.previousHash.isBlank() || block.payloadHash.isBlank()) return false
        if (!block.blockHash.startsWith(POW_PREFIX)) return false
        val expected = computeBlockHash(
            index = block.index,
            timestamp = block.timestamp,
            actionType = block.actionType,
            senderName = block.senderName,
            previousHash = block.previousHash,
            payloadHash = block.payloadHash,
            nonce = block.nonce
        )
        return CoupleCryptoEngine.constantTimeEquals(expected, block.blockHash)
    }

    /**
     * Strict sequential hash-chain verifier supporting:
     * - Newly created V2 rooms anchored at deterministic Block #0 ([ChainVerificationScope.FULL_FROM_GENESIS]).
     * - Upgraded V1->V2 rooms where immutable legacy V1 blocks remain intact and V2 continuity begins
     *   from [migrationAnchorHash] at [migrationAnchorIndex] ([ChainVerificationScope.MIGRATED_FROM_V1_ANCHOR]).
     * - Locally pruned rolling windows (honestly reported as [ChainVerificationScope.PRUNED_WINDOW_ONLY]).
     *
     * Rejects: invalid PoW/SHA-256, duplicate blockHash or duplicate index, non-sequential index jumps,
     * broken previousHash linkage, and mismatched Genesis or V1 migration anchors.
     */
    fun verifyChain(
        blocks: List<CoupleBlock>,
        coupleCode: String,
        schemaVersion: Int = SCHEMA_VERSION_V2,
        migrationAnchorHash: String = "",
        migrationAnchorIndex: Int = 0,
        prunedAnchorHash: String = "",
        prunedBlockCount: Int = 0
    ): ChainVerificationResult {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val expectedGenesisAnchor = genesisHash(cleanCode)

        if (blocks.isEmpty()) {
            if (prunedBlockCount > 0) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = 0,
                    latestBlockHash = prunedAnchorHash,
                    summary = "Invalid chain state: prunedBlockCount=$prunedBlockCount with empty local window",
                    scope = ChainVerificationScope.INVALID,
                    prunedBlockCount = prunedBlockCount,
                    migrationAnchorHash = migrationAnchorHash
                )
            }
            val genesis = createDeterministicGenesisBlock(cleanCode)
            return ChainVerificationResult(
                isValid = true,
                verifiedBlockCount = 1,
                latestBlockHash = genesis.blockHash,
                summary = "Genesis Anchor Ready · PoW Verified",
                scope = ChainVerificationScope.FULL_FROM_GENESIS,
                prunedBlockCount = 0,
                migrationAnchorHash = migrationAnchorHash
            )
        }

        val seenHashes = HashSet<String>(blocks.size * 2)
        val seenIndices = HashSet<Int>(blocks.size * 2)

        // 1. Validate every block individually and enforce strict consecutive linkage & uniqueness
        for (i in blocks.indices) {
            val b = blocks[i]
            if (!verifyBlock(b)) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = i,
                    latestBlockHash = b.blockHash,
                    summary = "Tamper alert at Block #${b.index}: Invalid SHA-256 PoW hash",
                    scope = ChainVerificationScope.INVALID,
                    prunedBlockCount = prunedBlockCount,
                    migrationAnchorHash = migrationAnchorHash
                )
            }
            if (!seenHashes.add(b.blockHash)) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = i,
                    latestBlockHash = b.blockHash,
                    summary = "Duplicate blockHash detected at Block #${b.index}",
                    scope = ChainVerificationScope.INVALID,
                    prunedBlockCount = prunedBlockCount,
                    migrationAnchorHash = migrationAnchorHash
                )
            }
            if (!seenIndices.add(b.index)) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = i,
                    latestBlockHash = b.blockHash,
                    summary = "Duplicate block index #${b.index} detected",
                    scope = ChainVerificationScope.INVALID,
                    prunedBlockCount = prunedBlockCount,
                    migrationAnchorHash = migrationAnchorHash
                )
            }
            if (i > 0) {
                val prev = blocks[i - 1]
                if (b.index != prev.index + 1) {
                    return ChainVerificationResult(
                        isValid = false,
                        verifiedBlockCount = i,
                        latestBlockHash = b.blockHash,
                        summary = "Non-sequential block index at #${b.index} (expected #${prev.index + 1})",
                        scope = ChainVerificationScope.INVALID,
                        prunedBlockCount = prunedBlockCount,
                        migrationAnchorHash = migrationAnchorHash
                    )
                }
                val isMigrationBoundary = migrationAnchorHash.isNotBlank() &&
                    migrationAnchorIndex > 0 &&
                    b.index == migrationAnchorIndex
                if (isMigrationBoundary) {
                    // At an explicit V1->V2 migration boundary, previousHash MUST match BOTH the
                    // recorded migrationAnchorHash AND the last verified V1 block's blockHash.
                    if (!CoupleCryptoEngine.constantTimeEquals(prev.blockHash, migrationAnchorHash) ||
                        !CoupleCryptoEngine.constantTimeEquals(b.previousHash, migrationAnchorHash)
                    ) {
                        return ChainVerificationResult(
                            isValid = false,
                            verifiedBlockCount = i,
                            latestBlockHash = b.blockHash,
                            summary = "V1->V2 migration anchor mismatch at Block #${b.index}",
                            scope = ChainVerificationScope.INVALID,
                            prunedBlockCount = prunedBlockCount,
                            migrationAnchorHash = migrationAnchorHash
                        )
                    }
                } else if (!CoupleCryptoEngine.constantTimeEquals(b.previousHash, prev.blockHash)) {
                    // Legacy V1 compatibility: rooms created before V2 could link Block #1 to genesisHash(code)
                    val isLegacyV1Block1Link = schemaVersion == SCHEMA_VERSION_V1 &&
                        prev.index == 0 && b.index == 1 &&
                        CoupleCryptoEngine.constantTimeEquals(b.previousHash, expectedGenesisAnchor)
                    if (!isLegacyV1Block1Link) {
                        return ChainVerificationResult(
                            isValid = false,
                            verifiedBlockCount = i,
                            latestBlockHash = b.blockHash,
                            summary = "Broken link at Block #${b.index}: previousHash does not match Block #${prev.index}",
                            scope = ChainVerificationScope.INVALID,
                            prunedBlockCount = prunedBlockCount,
                            migrationAnchorHash = migrationAnchorHash
                        )
                    }
                }
            }
        }

        // 2. Validate root/anchor boundary of the first block in the verified window
        val first = blocks.first()
        val resolvedScope: ChainVerificationScope = when {
            first.index == 0 -> {
                if (!CoupleCryptoEngine.constantTimeEquals(first.previousHash, expectedGenesisAnchor)) {
                    return ChainVerificationResult(
                        isValid = false,
                        verifiedBlockCount = 0,
                        latestBlockHash = first.blockHash,
                        summary = "Genesis anchor mismatch at Block #0 for room #$cleanCode",
                        scope = ChainVerificationScope.INVALID,
                        prunedBlockCount = prunedBlockCount,
                        migrationAnchorHash = migrationAnchorHash
                    )
                }
                if (schemaVersion >= SCHEMA_VERSION_V2 &&
                    migrationAnchorHash.isBlank() &&
                    first.senderName == "Genesis"
                ) {
                    val expectedV2Genesis = createDeterministicGenesisBlock(cleanCode)
                    if (!CoupleCryptoEngine.constantTimeEquals(first.blockHash, expectedV2Genesis.blockHash)) {
                        return ChainVerificationResult(
                            isValid = false,
                            verifiedBlockCount = 0,
                            latestBlockHash = first.blockHash,
                            summary = "Deterministic V2 Genesis hash mismatch at Block #0",
                            scope = ChainVerificationScope.INVALID,
                            prunedBlockCount = prunedBlockCount,
                            migrationAnchorHash = migrationAnchorHash
                        )
                    }
                }
                if (migrationAnchorHash.isNotBlank()) {
                    ChainVerificationScope.MIGRATED_FROM_V1_ANCHOR
                } else {
                    ChainVerificationScope.FULL_FROM_GENESIS
                }
            }
            migrationAnchorHash.isNotBlank() && first.index == migrationAnchorIndex -> {
                if (!CoupleCryptoEngine.constantTimeEquals(first.previousHash, migrationAnchorHash)) {
                    return ChainVerificationResult(
                        isValid = false,
                        verifiedBlockCount = 0,
                        latestBlockHash = first.blockHash,
                        summary = "V2 chain head at Block #${first.index} does not match V1 migrationAnchorHash",
                        scope = ChainVerificationScope.INVALID,
                        prunedBlockCount = prunedBlockCount,
                        migrationAnchorHash = migrationAnchorHash
                    )
                }
                ChainVerificationScope.MIGRATED_FROM_V1_ANCHOR
            }
            prunedBlockCount > 0 && prunedAnchorHash.isNotBlank() -> {
                if (!CoupleCryptoEngine.constantTimeEquals(first.previousHash, prunedAnchorHash)) {
                    return ChainVerificationResult(
                        isValid = false,
                        verifiedBlockCount = 0,
                        latestBlockHash = first.blockHash,
                        summary = "Pruned window anchor mismatch at Block #${first.index}",
                        scope = ChainVerificationScope.INVALID,
                        prunedBlockCount = prunedBlockCount,
                        migrationAnchorHash = migrationAnchorHash
                    )
                }
                ChainVerificationScope.PRUNED_WINDOW_ONLY
            }
            prunedBlockCount > 0 -> {
                ChainVerificationScope.PRUNED_WINDOW_ONLY
            }
            schemaVersion == SCHEMA_VERSION_V1 && first.index == 1 &&
                CoupleCryptoEngine.constantTimeEquals(first.previousHash, expectedGenesisAnchor) -> {
                ChainVerificationScope.FULL_FROM_GENESIS
            }
            else -> {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = 0,
                    latestBlockHash = first.blockHash,
                    summary = "Genesis mismatch: chain begins at Block #${first.index} without Genesis, migration anchor, or pruned anchor",
                    scope = ChainVerificationScope.INVALID,
                    prunedBlockCount = prunedBlockCount,
                    migrationAnchorHash = migrationAnchorHash
                )
            }
        }

        val summary = when (resolvedScope) {
            ChainVerificationScope.FULL_FROM_GENESIS ->
                "Verified ${blocks.size} Blocks from Genesis · SHA-256 + PoW Intact"
            ChainVerificationScope.MIGRATED_FROM_V1_ANCHOR ->
                "Verified ${blocks.size} Blocks from V1 Migration Anchor (#$migrationAnchorIndex) · Intact"
            ChainVerificationScope.PRUNED_WINDOW_ONLY ->
                "Verified latest ${blocks.size} Blocks ($prunedBlockCount earlier blocks pruned locally)"
            ChainVerificationScope.INVALID -> "Chain verification failed"
        }

        return ChainVerificationResult(
            isValid = true,
            verifiedBlockCount = blocks.size,
            latestBlockHash = blocks.last().blockHash,
            summary = summary,
            scope = resolvedScope,
            prunedBlockCount = prunedBlockCount,
            migrationAnchorHash = migrationAnchorHash
        )
    }

    /**
     * Retains at most [maxBlocks] recent blocks while recording the exact pruned count and
     * the [prunedAnchorHash] (blockHash of the last evicted block) so [verifyChain] can
     * honestly verify and report [ChainVerificationScope.PRUNED_WINDOW_ONLY].
     */
    data class PrunedLedgerWindow(
        val retainedBlocks: List<CoupleBlock>,
        val newlyPrunedCount: Int,
        val prunedAnchorHash: String
    )

    fun pruneLedgerWindow(
        blocks: List<CoupleBlock>,
        existingPrunedCount: Int = 0,
        existingPrunedAnchorHash: String = "",
        maxBlocks: Int = MAX_LOCAL_LEDGER_BLOCKS
    ): PrunedLedgerWindow {
        if (blocks.size <= maxBlocks) {
            return PrunedLedgerWindow(
                retainedBlocks = blocks,
                newlyPrunedCount = existingPrunedCount,
                prunedAnchorHash = existingPrunedAnchorHash
            )
        }
        val dropCount = blocks.size - maxBlocks
        val lastDropped = blocks[dropCount - 1]
        return PrunedLedgerWindow(
            retainedBlocks = blocks.drop(dropCount),
            newlyPrunedCount = existingPrunedCount + dropCount,
            prunedAnchorHash = lastDropped.blockHash
        )
    }

    fun blockToJson(block: CoupleBlock): JSONObject {
        return JSONObject().apply {
            put("idx", block.index)
            put("ts", block.timestamp)
            put("act", block.actionType)
            put("snd", block.senderName)
            put("prev", block.previousHash)
            put("ph", block.payloadHash)
            put("nc", block.nonce)
            put("bh", block.blockHash)
            put("cp", block.cipherPreview)
            put("sp", block.signaturePreview)
            if (block.clientTurnId.isNotBlank()) put("ctid", block.clientTurnId)
        }
    }

    fun blockFromJson(o: JSONObject): CoupleBlock {
        return CoupleBlock(
            index = o.optInt("idx", 0),
            timestamp = o.optLong("ts", 0L),
            actionType = o.optString("act", "TURN"),
            senderName = o.optString("snd", "Partner"),
            previousHash = o.optString("prev", ""),
            payloadHash = o.optString("ph", ""),
            nonce = o.optInt("nc", 0),
            blockHash = o.optString("bh", ""),
            cipherPreview = o.optString("cp", ""),
            signaturePreview = o.optString("sp", ""),
            clientTurnId = o.optString("ctid", "")
        )
    }

    fun ledgerToJson(blocks: List<CoupleBlock>): String {
        val arr = JSONArray()
        blocks.takeLast(MAX_LOCAL_LEDGER_BLOCKS).forEach { arr.put(blockToJson(it)) }
        return arr.toString()
    }

    fun ledgerFromJson(json: String): List<CoupleBlock> {
        return runCatching {
            val arr = JSONArray(json.ifBlank { "[]" })
            val list = mutableListOf<CoupleBlock>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(blockFromJson(obj))
            }
            list
        }.getOrDefault(emptyList())
    }
}

/**
 * 12-Layer Packet & Backend Firewall for Couple Lounge.
 * Performs Rate-Limit Flood Protection, Deep Packet Inspection (DPI), Room Isolation,
 * Anti-Replay, Partner Identity Lock, HMAC-SHA256 signature verification, AES-256-GCM decryption,
 * and Blockchain PoW validation.
 */
object CoupleFirewall {
    private const val MAX_PACKET_BYTES = 65_536
    private val FORBIDDEN_REGEXES = listOf(
        Regex("<\\s*script\\b", RegexOption.IGNORE_CASE),
        Regex("<\\s*iframe\\b", RegexOption.IGNORE_CASE),
        Regex("<\\s*svg\\b", RegexOption.IGNORE_CASE),
        Regex("javascript\\s*:", RegexOption.IGNORE_CASE),
        Regex("vbscript\\s*:", RegexOption.IGNORE_CASE),
        Regex("data\\s*:\\s*text/html", RegexOption.IGNORE_CASE),
        Regex("\\bon[a-z]{2,20}\\s*=", RegexOption.IGNORE_CASE),
        Regex("\\bDROP\\s+TABLE\\b", RegexOption.IGNORE_CASE),
        Regex("\\bUNION\\s+(?:ALL\\s+)?SELECT\\b", RegexOption.IGNORE_CASE),
        Regex("\\bDELETE\\s+FROM\\b", RegexOption.IGNORE_CASE),
        Regex("\\bINSERT\\s+INTO\\b", RegexOption.IGNORE_CASE),
        Regex("\\.\\.[/\\\\]"),
        Regex("content\\s*://", RegexOption.IGNORE_CASE),
        Regex("file\\s*://", RegexOption.IGNORE_CASE)
    )

    fun sanitizeName(raw: String): String {
        return com.example.security.BackendSecurityFirewall.sanitizeField(raw, maxLength = 32)
            .ifBlank { "Partner" }
    }

    fun inspectAndFilter(
        rawInput: String,
        expectedCoupleCode: String,
        existingLedger: List<CoupleBlock> = emptyList(),
        lockedPartnerName: String = "",
        myName: String = "",
        ecdhSharedSecretB64: String = "",
        minProtocolVersion: Int = if (ecdhSharedSecretB64.isNotBlank()) CoupleTurnCodec.PROTOCOL_VERSION_ECDH_V3 else CoupleTurnCodec.PROTOCOL_VERSION_LEGACY_V2,
        minAcceptedBlockIndex: Int = 0,
        seenClientTurnIds: Set<String> = emptySet(),
        seenReplayHashes: Set<String> = emptySet()
    ): Result<DecodedTurnPacket> {
        // Rule 0: Sliding-Window Rate Limiter & Flood Guard
        if (!com.example.security.BackendSecurityFirewall.allowRateLimitedOperation("couple_pkt_$expectedCoupleCode", maxOpsPerWindow = 50)) {
            return Result.failure(SecurityException("FIREWALL BLOCKED: Rate limit exceeded — flood protection active."))
        }

        // Rule 1: Framing & Deep Packet Inspection (DPI)
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("FIREWALL BLOCKED: Empty packet."))
        }
        if (trimmed.length > MAX_PACKET_BYTES) {
            return Result.failure(IllegalArgumentException("FIREWALL BLOCKED: Packet exceeds 64KB security limit."))
        }
        for (regex in FORBIDDEN_REGEXES) {
            if (regex.containsMatchIn(trimmed)) {
                com.example.security.BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(SecurityException("FIREWALL BLOCKED: Malicious injection pattern detected."))
            }
        }

        // Delegate to Codec which executes Room Tag, Protocol Anti-Downgrade, Sequence+clientTurnId Replay,
        // ECDH+HKDF / HMAC-SHA256 & AES-256-GCM, Blockchain PoW & Payload Hash, and Partner Lock.
        return CoupleTurnCodec.decodeSecuredPacket(
            rawInput = trimmed,
            expectedCoupleCode = expectedCoupleCode,
            existingLedger = existingLedger,
            lockedPartnerName = lockedPartnerName,
            myName = myName,
            ecdhSharedSecretB64 = ecdhSharedSecretB64,
            minProtocolVersion = minProtocolVersion,
            minAcceptedBlockIndex = minAcceptedBlockIndex,
            seenClientTurnIds = seenClientTurnIds,
            seenReplayHashes = seenReplayHashes
        )
    }
}

object CoupleGameDecks {

    val doodlePrompts: List<String>
        get() = LocalCoupleQuestionEngine.expandedDoodlePrompts

    val quizQuestions: List<CoupleQuizQuestion>
        get() = LocalCoupleQuestionEngine.taggedQuizBank.map { it.question }

    val wouldYouRatherPrompts: List<WouldYouRatherPrompt>
        get() = LocalCoupleQuestionEngine.taggedWyrBank.map { it.prompt }

    fun quizForSpice(spiceLevel: SpiceLevel): List<CoupleQuizQuestion> {
        val exact = quizQuestions.filter { it.spiceLevel == spiceLevel }
        return if (exact.isNotEmpty()) exact else quizQuestions
    }

    fun wyrForSpice(spiceLevel: SpiceLevel): List<WouldYouRatherPrompt> {
        val exact = wouldYouRatherPrompts.filter { it.spiceLevel == spiceLevel }
        return if (exact.isNotEmpty()) exact else wouldYouRatherPrompts
    }
}

object CoupleTurnCodec {
    private const val PREFIX = "KRIMOON:"
    private const val BCHAIN_PREFIX = "KRIMOON:BCHAIN:"

    /** Independent cryptographic wire protocol versions (decoupled from DB/Firestore schemaVersion). */
    const val PROTOCOL_VERSION_LEGACY_V2 = 2
    const val PROTOCOL_VERSION_ECDH_V3 = 3

    private val codeRandom = SecureRandom()

    fun generateUniqueCode(existingCodes: Set<String> = emptySet()): String {
        repeat(60) {
            val candidate = (100_000 + codeRandom.nextInt(900_000)).toString()
            if (candidate !in existingCodes) return candidate
        }
        return (100_000 + codeRandom.nextInt(900_000)).toString()
    }

    fun normalizeCode(raw: String): String {
        return raw.filter { it.isDigit() }.take(6)
    }

    /**
     * Extracts a 6-digit Couple Code from either a plain 6-digit number or an invite message
     * containing "Room #123456" or "Code: 123456".
     */
    fun extractCoupleCodeFromText(raw: String): String {
        val match = Regex("(?:Room\\s*#|Code:?\\s*|#)(\\d{6})", RegexOption.IGNORE_CASE).find(raw)
        if (match != null) return match.groupValues[1]
        val sixDigitMatch = Regex("\\b(\\d{6})\\b").find(raw)
        if (sixDigitMatch != null) return sixDigitMatch.groupValues[1]
        return normalizeCode(raw)
    }

    fun codeTag(coupleCode: String): String {
        val clean = normalizeCode(coupleCode)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("kri_couple_$clean".toByteArray(StandardCharsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun maskChoice(choice: Int, coupleCode: String, roundIndex: Int): Int {
        val salt = (coupleCode.toIntOrNull() ?: 42) + (roundIndex * 17)
        return choice xor (salt and 0xFF)
    }

    private fun unmaskChoice(masked: Int, coupleCode: String, roundIndex: Int): Int {
        val salt = (coupleCode.toIntOrNull() ?: 42) + (roundIndex * 17)
        return masked xor (salt and 0xFF)
    }

    /**
     * Re-mines and re-encrypts a queued [CoupleActivityIntent] against a new chain head
     * `(previousHash, blockIndex)`. Always generates a fresh 96-bit AES-GCM nonce/IV and fresh
     * authenticated metadata (AAD) — never reuses ciphertext across rebases.
     */
    fun sealIntentWithFreshNonce(
        coupleCode: String,
        intent: CoupleActivityIntent,
        previousHash: String,
        blockIndex: Int,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payloadObj = JSONObject(intent.payloadJson)
        return sealBlockchainPacket(
            coupleCode = coupleCode,
            senderName = intent.senderName,
            actionType = intent.actionType,
            plainPayloadObj = payloadObj,
            previousHash = previousHash,
            blockIndex = blockIndex,
            ecdhSharedSecretB64 = ecdhSharedSecretB64,
            clientTurnId = intent.clientTurnId
        )
    }

    /**
     * Extracts the outer IV Base64 string from a sealed KRIMOON:BCHAIN: packet for testing & audit
     * of fresh-nonce generation during conflict rebasing.
     */
    fun peekPacketIvBase64(rawInput: String): String? {
        return try {
            val idx = rawInput.indexOf(BCHAIN_PREFIX)
            if (idx < 0) return null
            val token = rawInput.substring(idx + BCHAIN_PREFIX.length).trim().split(Regex("\\s+")).first()
            val outer = JSONObject(String(com.example.security.SafeBase64.decode(token), StandardCharsets.UTF_8))
            outer.optString("iv", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recovers a [CoupleActivityIntent] from an outgoing locally-sealed packet so the turn can be
     * persisted encrypted in [com.example.data.PendingCoupleTurn] and re-encrypted with a brand-new
     * 96-bit AES-GCM nonce if the cloud chain head advances before commit.
     */
    fun extractActivityIntent(
        rawPacket: String,
        coupleCode: String,
        ecdhSharedSecretB64: String = ""
    ): Result<CoupleActivityIntent> {
        return try {
            val cleanCode = normalizeCode(coupleCode)
            val bchainIdx = rawPacket.indexOf(BCHAIN_PREFIX)
            if (bchainIdx < 0) {
                return Result.failure(IllegalArgumentException("Missing KRIMOON:BCHAIN header"))
            }
            val token = rawPacket.substring(bchainIdx + BCHAIN_PREFIX.length).trim().split(Regex("\\s+")).first()
            val outer = JSONObject(String(com.example.security.SafeBase64.decode(token), StandardCharsets.UTF_8))
            val pv = if (outer.has("pv")) outer.optInt("pv", 2) else outer.optInt("v", 2)
            val ctid = outer.optString("ctid", "").ifBlank { UUID.randomUUID().toString() }
            val blkObj = outer.optJSONObject("blk")
                ?: return Result.failure(IllegalArgumentException("Missing blk header"))
            val block = CoupleBlockchain.blockFromJson(blkObj)
            val aad = if (pv >= PROTOCOL_VERSION_ECDH_V3) {
                "$cleanCode|${block.blockHash}|${block.previousHash}|${block.index}|${block.actionType}|v$pv|$ctid"
            } else {
                "$cleanCode|${block.blockHash}|${block.actionType}|v$pv"
            }
            val plain = CoupleCryptoEngine.decryptPayload(
                ivBase64 = outer.optString("iv", ""),
                cipherBase64 = outer.optString("ct", ""),
                expectedHmacHex = outer.optString("sig", ""),
                coupleCode = cleanCode,
                aad = aad,
                ecdhSharedSecretB64 = if (pv >= PROTOCOL_VERSION_ECDH_V3) ecdhSharedSecretB64 else ""
            ).getOrElse { return Result.failure(it) }
            Result.success(
                CoupleActivityIntent(
                    clientTurnId = ctid,
                    actionType = block.actionType,
                    senderName = block.senderName,
                    payloadJson = plain
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stringSetFromJson(json: String): LinkedHashSet<String> {
        val result = LinkedHashSet<String>()
        runCatching {
            val arr = JSONArray(json.ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "")
                if (item.isNotBlank()) result.add(item)
            }
        }
        return result
    }

    fun stringSetToJson(items: Collection<String>, maxSize: Int = 512): String {
        val trimmed = if (items.size > maxSize) items.toList().takeLast(maxSize) else items
        val arr = JSONArray()
        trimmed.forEach { if (it.isNotBlank()) arr.put(it) }
        return arr.toString()
    }

    /**
     * Core Blockchain + ECDH-P256 / AES-256-GCM Packet Builder.
     * Mines a PoW CoupleBlock, encrypts the inner payload with AES-256-GCM using a fresh 96-bit nonce
     * bound to (coupleCode, blockHash, previousHash, blockIndex, actionType, protocolVersion, clientTurnId)
     * via AAD + HMAC-SHA256, and returns both the wire string and mined CoupleBlock.
     * Uses zero-knowledge Protocol v3 when [ecdhSharedSecretB64] is present.
     */
    private fun sealBlockchainPacket(
        coupleCode: String,
        senderName: String,
        actionType: String,
        plainPayloadObj: JSONObject,
        previousHash: String,
        blockIndex: Int,
        ecdhSharedSecretB64: String = "",
        clientTurnId: String = UUID.randomUUID().toString()
    ): Pair<String, CoupleBlock> {
        val cleanCode = normalizeCode(coupleCode)
        val cleanSender = CoupleFirewall.sanitizeName(senderName)
        val prev = previousHash.ifBlank { CoupleBlockchain.createDeterministicGenesisBlock(cleanCode).blockHash }
        val cleanTurnId = clientTurnId.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(64)
            .ifBlank { UUID.randomUUID().toString() }
        val plainJson = plainPayloadObj.toString()
        val payloadHash = CoupleCryptoEngine.sha256Hex(plainJson)

        val mined = CoupleBlockchain.mineBlock(
            index = blockIndex.coerceAtLeast(1),
            actionType = actionType,
            senderName = cleanSender,
            previousHash = prev,
            payloadHash = payloadHash,
            clientTurnId = cleanTurnId
        )

        val protocolVersion = if (ecdhSharedSecretB64.isNotBlank()) {
            PROTOCOL_VERSION_ECDH_V3
        } else {
            PROTOCOL_VERSION_LEGACY_V2
        }
        val aad = if (protocolVersion >= PROTOCOL_VERSION_ECDH_V3) {
            "$cleanCode|${mined.blockHash}|${mined.previousHash}|${mined.index}|$actionType|v$protocolVersion|$cleanTurnId"
        } else {
            "$cleanCode|${mined.blockHash}|$actionType|v$protocolVersion"
        }
        val envelope = CoupleCryptoEngine.encryptPayload(
            plainJson = plainJson,
            coupleCode = cleanCode,
            aad = aad,
            ecdhSharedSecretB64 = ecdhSharedSecretB64
        )

        val finalBlock = mined.copy(
            cipherPreview = envelope.cipherBase64.take(18),
            signaturePreview = envelope.hmacHex.take(16)
        )

        val outer = JSONObject().apply {
            put("v", protocolVersion)
            put("pv", protocolVersion)
            put("ctid", cleanTurnId)
            put("tag", codeTag(cleanCode))
            put("blk", CoupleBlockchain.blockToJson(finalBlock))
            put("iv", envelope.ivBase64)
            put("ct", envelope.cipherBase64)
            put("sig", envelope.hmacHex)
        }
        val b64 = com.example.security.SafeBase64.encodeNoWrap(outer.toString().toByteArray(StandardCharsets.UTF_8))
        return "$BCHAIN_PREFIX$b64" to finalBlock
    }

    fun encodeBondHandshakeWithBlock(
        coupleCode: String,
        senderName: String,
        spiceLevel: SpiceLevel = SpiceLevel.FLIRTY,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = "",
        cycleSync: PartnerCycleSyncSnapshot? = null
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "HANDSHAKE")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("spice", spiceLevel.name)
            if (cycleSync != null) {
                put("cs", cycleSync.toJsonString())
            }
        }
        return sealBlockchainPacket(coupleCode, senderName, "HANDSHAKE", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeBondHandshake(
        coupleCode: String,
        senderName: String,
        spiceLevel: SpiceLevel = SpiceLevel.FLIRTY,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = "",
        cycleSync: PartnerCycleSyncSnapshot? = null
    ): String = encodeBondHandshakeWithBlock(coupleCode, senderName, spiceLevel, previousHash, blockIndex, ecdhSharedSecretB64, cycleSync).first

    fun encodeUnpairNoticeWithBlock(
        coupleCode: String,
        senderName: String,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "UNPAIR")
            put("sender", CoupleFirewall.sanitizeName(senderName))
        }
        return sealBlockchainPacket(coupleCode, senderName, "UNPAIR", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeQuizTurnWithBlock(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "QUIZ")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("round", roundIndex)
            put("pick", maskChoice(choiceIndex, coupleCode, roundIndex))
            put("spice", spiceLevel.name)
        }
        return sealBlockchainPacket(coupleCode, senderName, "QUIZ", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeQuizTurn(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): String = encodeQuizTurnWithBlock(coupleCode, senderName, roundIndex, choiceIndex, spiceLevel, previousHash, blockIndex, ecdhSharedSecretB64).first

    fun encodeWyrTurnWithBlock(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "WYR")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("round", roundIndex)
            put("pick", maskChoice(choiceIndex, coupleCode, roundIndex))
            put("spice", spiceLevel.name)
        }
        return sealBlockchainPacket(coupleCode, senderName, "WYR", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeWyrTurn(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): String = encodeWyrTurnWithBlock(coupleCode, senderName, roundIndex, choiceIndex, spiceLevel, previousHash, blockIndex, ecdhSharedSecretB64).first

    fun encodeDoodleTurnWithBlock(
        coupleCode: String,
        senderName: String,
        prompt: String,
        strokes: List<DoodleStrokeData>,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "DOODLE")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("prompt", prompt)
            put("strokes", strokesToJsonArray(strokes.takeLast(60)))
        }
        return sealBlockchainPacket(coupleCode, senderName, "DOODLE", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeDoodleTurn(
        coupleCode: String,
        senderName: String,
        prompt: String,
        strokes: List<DoodleStrokeData>,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): String = encodeDoodleTurnWithBlock(coupleCode, senderName, prompt, strokes, previousHash, blockIndex, ecdhSharedSecretB64).first

    fun encodeTicTacToeTurnWithBlock(
        coupleCode: String,
        senderName: String,
        state: TicTacToeSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "TTT")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("state", ticTacToeToJson(state))
        }
        return sealBlockchainPacket(coupleCode, senderName, "TTT", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeTicTacToeTurn(
        coupleCode: String,
        senderName: String,
        state: TicTacToeSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): String = encodeTicTacToeTurnWithBlock(coupleCode, senderName, state, previousHash, blockIndex, ecdhSharedSecretB64).first

    fun encodeDareTurnWithBlock(
        coupleCode: String,
        senderName: String,
        state: DareSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "DARE")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("stateJson", CoupleActivitiesDeck.dareStateToJson(state))
        }
        return sealBlockchainPacket(coupleCode, senderName, "DARE", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeWavelengthTurnWithBlock(
        coupleCode: String,
        senderName: String,
        state: WavelengthSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "WAVELENGTH")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("stateJson", CoupleActivitiesDeck.wavelengthStateToJson(state))
        }
        return sealBlockchainPacket(coupleCode, senderName, "WAVELENGTH", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    fun encodeWhisperTurnWithBlock(
        coupleCode: String,
        senderName: String,
        likedDateId: String = "",
        whisperNote: SecretWhisperNote? = null,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1,
        ecdhSharedSecretB64: String = ""
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "WHISPER")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("likedDateId", likedDateId.take(32))
            if (whisperNote != null) {
                put("wid", whisperNote.id.take(40))
                put("wtext", whisperNote.text.take(260))
                put("wts", whisperNote.timestamp)
            }
        }
        return sealBlockchainPacket(coupleCode, senderName, "WHISPER", payload, previousHash, blockIndex, ecdhSharedSecretB64)
    }

    /**
     * Primary entry point for decoding & verifying any Couple Lounge packet through the Firewall.
     */
    fun decodePacket(
        rawInput: String,
        expectedCoupleCode: String,
        existingLedger: List<CoupleBlock> = emptyList(),
        lockedPartnerName: String = "",
        myName: String = "",
        ecdhSharedSecretB64: String = "",
        minProtocolVersion: Int = if (ecdhSharedSecretB64.isNotBlank()) PROTOCOL_VERSION_ECDH_V3 else PROTOCOL_VERSION_LEGACY_V2,
        minAcceptedBlockIndex: Int = 0,
        seenClientTurnIds: Set<String> = emptySet(),
        seenReplayHashes: Set<String> = emptySet()
    ): Result<DecodedTurnPacket> {
        return CoupleFirewall.inspectAndFilter(
            rawInput = rawInput,
            expectedCoupleCode = expectedCoupleCode,
            existingLedger = existingLedger,
            lockedPartnerName = lockedPartnerName,
            myName = myName,
            ecdhSharedSecretB64 = ecdhSharedSecretB64,
            minProtocolVersion = minProtocolVersion,
            minAcceptedBlockIndex = minAcceptedBlockIndex,
            seenClientTurnIds = seenClientTurnIds,
            seenReplayHashes = seenReplayHashes
        )
    }

    internal fun decodeSecuredPacket(
        rawInput: String,
        expectedCoupleCode: String,
        existingLedger: List<CoupleBlock>,
        lockedPartnerName: String,
        myName: String,
        ecdhSharedSecretB64: String = "",
        minProtocolVersion: Int = if (ecdhSharedSecretB64.isNotBlank()) PROTOCOL_VERSION_ECDH_V3 else PROTOCOL_VERSION_LEGACY_V2,
        minAcceptedBlockIndex: Int = 0,
        seenClientTurnIds: Set<String> = emptySet(),
        seenReplayHashes: Set<String> = emptySet()
    ): Result<DecodedTurnPacket> {
        return try {
            val cleanCode = normalizeCode(expectedCoupleCode)
            val bchainIdx = rawInput.indexOf(BCHAIN_PREFIX)
            if (bchainIdx < 0) {
                return Result.failure(IllegalArgumentException("FIREWALL BLOCKED: Missing KRIMOON:BCHAIN header."))
            }

            val token = rawInput.substring(bchainIdx + BCHAIN_PREFIX.length).trim().split(Regex("\\s+")).first()
            val outerJsonStr = String(com.example.security.SafeBase64.decode(token), StandardCharsets.UTF_8)
            val outer = JSONObject(outerJsonStr)
            val packetVersion = if (outer.has("pv")) outer.optInt("pv", 2) else outer.optInt("v", 2)
            val outerClientTurnId = outer.optString("ctid", "")

            // Firewall Rule 2a: Cryptographic Protocol Anti-Downgrade (independent of DB schemaVersion)
            val effectiveMinProtocol = maxOf(
                minProtocolVersion,
                if (ecdhSharedSecretB64.isNotBlank()) PROTOCOL_VERSION_ECDH_V3 else PROTOCOL_VERSION_LEGACY_V2
            )
            if (packetVersion < effectiveMinProtocol) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Cryptographic protocol downgrade rejected (packet v$packetVersion < required v$effectiveMinProtocol).")
                )
            }

            // Firewall Rule 2b: Room Key Tag Isolation
            val tag = outer.optString("tag", "")
            if (!CoupleCryptoEngine.constantTimeEquals(tag, codeTag(cleanCode))) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Packet belongs to a different Couple Code!"))
            }

            val blockObj = outer.optJSONObject("blk")
                ?: return Result.failure(SecurityException("FIREWALL BLOCKED: Missing Blockchain header."))
            val block = CoupleBlockchain.blockFromJson(blockObj).let { parsed ->
                if (parsed.clientTurnId.isBlank() && outerClientTurnId.isNotBlank()) {
                    parsed.copy(clientTurnId = outerClientTurnId)
                } else parsed
            }

            // Firewall Rule 4a: Proof-of-Work & SHA-256 Block Hash Verification
            if (!CoupleBlockchain.verifyBlock(block)) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Blockchain Proof-of-Work or SHA-256 hash invalid!"))
            }

            // Firewall Rule 5a (PRIMARY Replay Protection): Persistent Monotonic Sequence & Unique clientTurnId
            if (minAcceptedBlockIndex > 0 && block.index < minAcceptedBlockIndex) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Stale sequence replay — Block #${block.index} < minimum expected #$minAcceptedBlockIndex.")
                )
            }
            val effectiveTurnId = block.clientTurnId.ifBlank { outerClientTurnId }
            if (effectiveTurnId.isNotBlank() && effectiveTurnId in seenClientTurnIds) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Replay detected — clientTurnId '$effectiveTurnId' already committed.")
                )
            }

            // Firewall Rule 5b (SUPPLEMENTARY Replay Protection): Rolling Hash Ring Buffer & Ledger
            if (block.blockHash in seenReplayHashes || existingLedger.any { it.blockHash == block.blockHash }) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Replay detected — Block #${block.index} already committed."))
            }

            // Firewall Rule 6: Bonded Partner Identity Lock
            val senderFromBlock = CoupleFirewall.sanitizeName(block.senderName)
            if (lockedPartnerName.isNotBlank() &&
                !senderFromBlock.equals(lockedPartnerName.trim(), ignoreCase = true) &&
                (myName.isBlank() || !senderFromBlock.equals(myName.trim(), ignoreCase = true))
            ) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Unauthorized sender '$senderFromBlock'. Room is locked to $lockedPartnerName.")
                )
            }

            // Firewall Rule 3: ECDH-P256 + HKDF-SHA256 (v3) or Legacy (v2) HMAC-SHA256 & AES-256-GCM Decryption
            val ivB64 = outer.optString("iv", "")
            val ctB64 = outer.optString("ct", "")
            val sigHex = outer.optString("sig", "")

            val decryptedResult = if (packetVersion >= PROTOCOL_VERSION_ECDH_V3) {
                if (ecdhSharedSecretB64.isBlank()) {
                    return Result.failure(
                        SecurityException("FIREWALL BLOCKED: Zero-knowledge ECDH shared secret required to decrypt v3 packet.")
                    )
                }
                if (effectiveTurnId.isNotBlank()) {
                    CoupleCryptoEngine.decryptPayload(
                        ivBase64 = ivB64,
                        cipherBase64 = ctB64,
                        expectedHmacHex = sigHex,
                        coupleCode = cleanCode,
                        aad = "$cleanCode|${block.blockHash}|${block.previousHash}|${block.index}|${block.actionType}|v$packetVersion|$effectiveTurnId",
                        ecdhSharedSecretB64 = ecdhSharedSecretB64
                    )
                } else {
                    // Backward compatibility with existing pre-patch v3 blocks lacking clientTurnId
                    CoupleCryptoEngine.decryptPayload(
                        ivBase64 = ivB64,
                        cipherBase64 = ctB64,
                        expectedHmacHex = sigHex,
                        coupleCode = cleanCode,
                        aad = "$cleanCode|${block.blockHash}|${block.actionType}|v$packetVersion",
                        ecdhSharedSecretB64 = ecdhSharedSecretB64
                    )
                }
            } else {
                // Only reachable when effectiveMinProtocol < 3 (e.g., Pass & Play / pre-ECDH room)
                val v2Tagged = CoupleCryptoEngine.decryptPayload(
                    ivBase64 = ivB64,
                    cipherBase64 = ctB64,
                    expectedHmacHex = sigHex,
                    coupleCode = cleanCode,
                    aad = "$cleanCode|${block.blockHash}|${block.actionType}|v2",
                    ecdhSharedSecretB64 = ""
                )
                if (v2Tagged.isSuccess) {
                    v2Tagged
                } else {
                    CoupleCryptoEngine.decryptPayload(
                        ivBase64 = ivB64,
                        cipherBase64 = ctB64,
                        expectedHmacHex = sigHex,
                        coupleCode = cleanCode,
                        aad = "$cleanCode|${block.blockHash}|${block.actionType}",
                        ecdhSharedSecretB64 = ""
                    )
                }
            }
            val decryptedJson = decryptedResult.getOrElse { err ->
                return Result.failure(err)
            }

            // Firewall Rule 4b: Verify Decrypted Payload Hash matches Mined Block's payloadHash
            val actualPayloadHash = CoupleCryptoEngine.sha256Hex(decryptedJson)
            if (!CoupleCryptoEngine.constantTimeEquals(actualPayloadHash, block.payloadHash)) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Payload hash mismatch against mined block!"))
            }

            val obj = JSONObject(decryptedJson)
            val innerType = obj.optString("t", "").trim()
            if (innerType.isBlank() || innerType != block.actionType) {
                com.example.security.BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Packet actionType mismatch (header=${block.actionType}, payload=$innerType).")
                )
            }
            val sender = CoupleFirewall.sanitizeName(obj.optString("sender", senderFromBlock))
            if (lockedPartnerName.isNotBlank() &&
                !sender.equals(lockedPartnerName.trim(), ignoreCase = true) &&
                (myName.isBlank() || !sender.equals(myName.trim(), ignoreCase = true))
            ) {
                com.example.security.BackendSecurityFirewall.recordNeutralizedThreat()
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: Decrypted sender '$sender' violates partner lock ($lockedPartnerName).")
                )
            }

            when (innerType) {
                "HANDSHAKE" -> {
                    val spice = runCatching { SpiceLevel.valueOf(obj.optString("spice", "FLIRTY")) }
                        .getOrDefault(SpiceLevel.FLIRTY)
                    val cycleSync = obj.optString("cs", "").takeIf { it.isNotBlank() }?.let {
                        PartnerCycleSyncSnapshot.fromJsonString(it)
                    }
                    Result.success(
                        DecodedTurnPacket.BondHandshake(
                            coupleCode = cleanCode,
                            senderName = sender,
                            spiceLevel = spice,
                            block = block,
                            partnerCycleSync = cycleSync
                        )
                    )
                }
                "UNPAIR" -> {
                    Result.success(
                        DecodedTurnPacket.UnpairNotice(
                            coupleCode = cleanCode,
                            senderName = sender,
                            block = block
                        )
                    )
                }
                "QUIZ" -> {
                    val round = obj.getInt("round").coerceAtLeast(0)
                    val maskedPick = obj.getInt("pick")
                    val unmasked = unmaskChoice(maskedPick, cleanCode, round).coerceIn(0, 10)
                    val spice = runCatching { SpiceLevel.valueOf(obj.optString("spice", "FLIRTY")) }
                        .getOrDefault(SpiceLevel.FLIRTY)
                    Result.success(
                        DecodedTurnPacket.QuizTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            roundIndex = round,
                            choiceIndex = unmasked,
                            spiceLevel = spice,
                            block = block
                        )
                    )
                }
                "WYR" -> {
                    val round = obj.getInt("round").coerceAtLeast(0)
                    val maskedPick = obj.getInt("pick")
                    val unmasked = unmaskChoice(maskedPick, cleanCode, round).coerceIn(0, 10)
                    val spice = runCatching { SpiceLevel.valueOf(obj.optString("spice", "FLIRTY")) }
                        .getOrDefault(SpiceLevel.FLIRTY)
                    Result.success(
                        DecodedTurnPacket.WyrTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            roundIndex = round,
                            choiceIndex = unmasked,
                            spiceLevel = spice,
                            block = block
                        )
                    )
                }
                "DOODLE" -> {
                    val prompt = com.example.security.BackendSecurityFirewall.sanitizeField(
                        obj.optString("prompt", ""),
                        maxLength = 140
                    )
                    val strokes = strokesFromJsonArray(obj.optJSONArray("strokes") ?: JSONArray())
                    Result.success(
                        DecodedTurnPacket.DoodleTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            prompt = prompt,
                            strokes = strokes,
                            block = block
                        )
                    )
                }
                "TTT" -> {
                    val stateObj = obj.getJSONObject("state")
                    Result.success(
                        DecodedTurnPacket.TicTacToeTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            state = ticTacToeFromJson(stateObj),
                            block = block
                        )
                    )
                }
                "DARE" -> {
                    val stateJson = obj.optString("stateJson", "{}")
                    Result.success(
                        DecodedTurnPacket.DareTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            state = CoupleActivitiesDeck.dareStateFromJson(stateJson),
                            block = block
                        )
                    )
                }
                "WAVELENGTH" -> {
                    val stateJson = obj.optString("stateJson", "{}")
                    Result.success(
                        DecodedTurnPacket.WavelengthTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            state = CoupleActivitiesDeck.wavelengthStateFromJson(stateJson),
                            block = block
                        )
                    )
                }
                "WHISPER" -> {
                    val likedDateId = com.example.security.BackendSecurityFirewall.sanitizeField(
                        obj.optString("likedDateId", ""),
                        maxLength = 32
                    )
                    val wText = com.example.security.BackendSecurityFirewall.sanitizeField(
                        obj.optString("wtext", ""),
                        maxLength = 260
                    )
                    val noteId = com.example.security.BackendSecurityFirewall.sanitizeField(
                        obj.optString("wid", "w_${block.blockHash.take(8)}"),
                        maxLength = 48
                    ).ifBlank { "w_${block.blockHash.take(8)}" }
                    val note = if (wText.isNotBlank()) {
                        SecretWhisperNote(
                            id = noteId,
                            senderName = sender,
                            text = wText,
                            timestamp = obj.optLong("wts", System.currentTimeMillis()),
                            isRevealed = false
                        )
                    } else null
                    Result.success(
                        DecodedTurnPacket.WhisperTurn(
                            coupleCode = cleanCode,
                            senderName = sender,
                            likedDateId = likedDateId,
                            whisperNote = note,
                            block = block
                        )
                    )
                }
                else -> Result.failure(IllegalArgumentException("FIREWALL BLOCKED: Unknown game turn type."))
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("FIREWALL BLOCKED: ${e.message ?: "Malformed encrypted block"}"))
        }
    }

    /**
     * Peeks at the outer block metadata of a KRIMOON:BCHAIN: packet without decrypting,
     * used by the Auto-Sync clipboard watcher to check if a clipboard packet is already committed.
     */
    fun peekBlockHash(rawInput: String): String? {
        return try {
            val idx = rawInput.indexOf(BCHAIN_PREFIX)
            if (idx < 0) return null
            val token = rawInput.substring(idx + BCHAIN_PREFIX.length).trim().split(Regex("\\s+")).first()
            val outer = JSONObject(String(com.example.security.SafeBase64.decode(token), StandardCharsets.UTF_8))
            outer.optJSONObject("blk")?.optString("bh")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun strokesToJsonString(strokes: List<DoodleStrokeData>): String = strokesToJsonArray(strokes).toString()

    fun strokesFromJsonString(json: String): List<DoodleStrokeData> {
        return runCatching { strokesFromJsonArray(JSONArray(json)) }.getOrDefault(emptyList())
    }

    private fun strokesToJsonArray(strokes: List<DoodleStrokeData>): JSONArray {
        val arr = JSONArray()
        strokes.forEach { stroke ->
            val ptsArr = JSONArray()
            stroke.points.forEach { (x, y) ->
                ptsArr.put(Math.round(x.coerceIn(0f, 1f) * 1000f) / 1000.0)
                ptsArr.put(Math.round(y.coerceIn(0f, 1f) * 1000f) / 1000.0)
            }
            arr.put(
                JSONObject().apply {
                    put("a", CoupleFirewall.sanitizeName(stroke.author))
                    put("c", stroke.colorHex)
                    put("w", stroke.strokeWidth.toDouble())
                    put("p", ptsArr)
                }
            )
        }
        return arr
    }

    private fun strokesFromJsonArray(arr: JSONArray): List<DoodleStrokeData> {
        val list = mutableListOf<DoodleStrokeData>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ptsArr = o.optJSONArray("p") ?: continue
            val pts = mutableListOf<Pair<Float, Float>>()
            var j = 0
            while (j + 1 < ptsArr.length()) {
                val x = ptsArr.optDouble(j).toFloat().coerceIn(0f, 1f)
                val y = ptsArr.optDouble(j + 1).toFloat().coerceIn(0f, 1f)
                pts.add(x to y)
                j += 2
            }
            list.add(
                DoodleStrokeData(
                    author = CoupleFirewall.sanitizeName(o.optString("a", "Partner")),
                    colorHex = o.optLong("c", 0xFFD4629A),
                    strokeWidth = o.optDouble("w", 8.0).toFloat().coerceIn(2f, 32f),
                    points = pts
                )
            )
        }
        return list
    }

    fun roundStateToJson(state: RoundSyncState): String {
        val historyArr = JSONArray()
        state.history.takeLast(12).forEach { entry ->
            historyArr.put(
                JSONObject().apply {
                    put("ri", entry.roundIndex)
                    put("th", entry.theme.name)
                    put("mt", entry.matched)
                    put("cs", entry.choiceSignature)
                    put("sp", entry.spiceLevel.name)
                }
            )
        }
        return JSONObject().apply {
            put("r", state.roundIndex)
            if (state.myChoice != null) put("m", state.myChoice)
            if (state.partnerChoice != null) put("p", state.partnerChoice)
            put("pn", state.partnerName)
            if (historyArr.length() > 0) put("h", historyArr)
        }.toString()
    }

    fun roundStateFromJson(json: String): RoundSyncState {
        return runCatching {
            val o = JSONObject(json.ifBlank { "{}" })
            val historyList = mutableListOf<RoundOutcome>()
            val hArr = o.optJSONArray("h")
            if (hArr != null) {
                for (i in 0 until hArr.length()) {
                    val ho = hArr.optJSONObject(i) ?: continue
                    val theme = runCatching { QuestionTheme.valueOf(ho.optString("th", "ROMANCE")) }
                        .getOrDefault(QuestionTheme.ROMANCE)
                    val spice = runCatching { SpiceLevel.valueOf(ho.optString("sp", "FLIRTY")) }
                        .getOrDefault(SpiceLevel.FLIRTY)
                    historyList.add(
                        RoundOutcome(
                            roundIndex = ho.optInt("ri", 0),
                            theme = theme,
                            matched = ho.optBoolean("mt", false),
                            choiceSignature = ho.optInt("cs", 0),
                            spiceLevel = spice
                        )
                    )
                }
            }
            RoundSyncState(
                roundIndex = o.optInt("r", 0),
                myChoice = if (o.has("m")) o.optInt("m") else null,
                partnerChoice = if (o.has("p")) o.optInt("p") else null,
                partnerName = o.optString("pn", ""),
                history = historyList
            )
        }.getOrDefault(RoundSyncState())
    }

    fun ticTacToeToJsonString(state: TicTacToeSyncState): String = ticTacToeToJson(state).toString()

    fun ticTacToeFromJsonString(json: String): TicTacToeSyncState {
        return runCatching { ticTacToeFromJson(JSONObject(json.ifBlank { "{}" })) }
            .getOrDefault(TicTacToeSyncState())
    }

    private fun ticTacToeToJson(state: TicTacToeSyncState): JSONObject {
        return JSONObject().apply {
            put("b", JSONArray(state.board))
            put("xq", JSONArray(state.xQueue))
            put("oq", JSONArray(state.oQueue))
            put("nt", state.nextTurn)
            put("bt", state.isBoltTwist)
            put("ms", state.mySymbol)
            put("rc", state.roundCount)
        }
    }

    private fun ticTacToeFromJson(o: JSONObject): TicTacToeSyncState {
        val boardArr = o.optJSONArray("b")
        val board = if (boardArr != null && boardArr.length() == 9) {
            List(9) { idx ->
                val cell = boardArr.optString(idx, "")
                if (cell == "X" || cell == "O") cell else ""
            }
        } else {
            List(9) { "" }
        }
        val xqArr = o.optJSONArray("xq")
        val xq = if (xqArr != null) List(xqArr.length()) { xqArr.optInt(it).coerceIn(0, 8) } else emptyList()
        val oqArr = o.optJSONArray("oq")
        val oq = if (oqArr != null) List(oqArr.length()) { oqArr.optInt(it).coerceIn(0, 8) } else emptyList()
        val nt = o.optString("nt", "X").let { if (it == "O") "O" else "X" }
        val ms = o.optString("ms", "X").let { if (it == "O") "O" else "X" }
        return TicTacToeSyncState(
            board = board,
            xQueue = xq,
            oQueue = oq,
            nextTurn = nt,
            isBoltTwist = o.optBoolean("bt", true),
            mySymbol = ms,
            roundCount = o.optInt("rc", 1).coerceAtLeast(1)
        )
    }
}
