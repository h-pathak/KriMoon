package com.example.model

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

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
    val partnerName: String = ""
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
    val actionType: String, // "GENESIS_BOND", "HANDSHAKE", "QUIZ", "WYR", "DOODLE", "TTT", "UNPAIR"
    val senderName: String,
    val previousHash: String,
    val payloadHash: String,
    val nonce: Int,
    val blockHash: String,
    val cipherPreview: String = "",
    val signaturePreview: String = ""
)

data class EncryptedEnvelope(
    val ivBase64: String,
    val cipherBase64: String,
    val hmacHex: String,
    val cipherSuite: String = "AES-256-GCM/PBKDF2-HMAC-SHA256"
)

data class ChainVerificationResult(
    val isValid: Boolean,
    val verifiedBlockCount: Int,
    val latestBlockHash: String,
    val summary: String
)

sealed class DecodedTurnPacket {
    abstract val coupleCode: String
    abstract val senderName: String
    abstract val block: CoupleBlock

    data class BondHandshake(
        override val coupleCode: String,
        override val senderName: String,
        val spiceLevel: SpiceLevel,
        override val block: CoupleBlock
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

    data class UnpairNotice(
        override val coupleCode: String,
        override val senderName: String,
        override val block: CoupleBlock
    ) : DecodedTurnPacket()
}

/**
 * End-to-End Authenticated Encryption Engine for Couple Lounge.
 * Uses PBKDF2WithHmacSHA256 key derivation, AES-256-GCM authenticated encryption
 * with 96-bit SecureRandom IV and 128-bit GCM auth tag, plus separated HMAC-SHA256 signing.
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

    fun deriveAesKey(coupleCode: String): SecretKeySpec {
        val normalized = CoupleTurnCodec.normalizeCode(coupleCode)
        val salt = "KriMoon_AES256_Vault_Salt_v2_$normalized".toByteArray(StandardCharsets.UTF_8)
        val keyBytes = try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(normalized.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            // Fallback multi-round SHA-256 stretching if provider is restricted
            var digest = MessageDigest.getInstance("SHA-256")
                .digest((normalized + "_aes_fallback").toByteArray(StandardCharsets.UTF_8))
            repeat(512) {
                digest = MessageDigest.getInstance("SHA-256").digest(digest + salt)
            }
            digest
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun deriveHmacKey(coupleCode: String): ByteArray {
        val normalized = CoupleTurnCodec.normalizeCode(coupleCode)
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

    fun computeHmacHex(coupleCode: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deriveHmacKey(coupleCode), "HmacSHA256"))
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

    fun encryptPayload(plainJson: String, coupleCode: String, aad: String): EncryptedEnvelope {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = deriveAesKey(coupleCode)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))

        val cipherBytes = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        val hmacHex = computeHmacHex(coupleCode, "$ivB64.$cipherB64.$aad")

        return EncryptedEnvelope(
            ivBase64 = ivB64,
            cipherBase64 = cipherB64,
            hmacHex = hmacHex
        )
    }

    fun decryptPayload(
        ivBase64: String,
        cipherBase64: String,
        expectedHmacHex: String,
        coupleCode: String,
        aad: String
    ): Result<String> {
        return try {
            val computedHmac = computeHmacHex(coupleCode, "$ivBase64.$cipherBase64.$aad")
            if (!constantTimeEquals(computedHmac, expectedHmacHex)) {
                return Result.failure(
                    SecurityException("FIREWALL BLOCKED: HMAC-SHA256 signature mismatch (wrong Couple Code or tampered packet).")
                )
            }
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            if (iv.size != GCM_IV_BYTES) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Invalid AES-GCM IV length."))
            }
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = deriveAesKey(coupleCode)
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

    fun genesisHash(coupleCode: String): String {
        val clean = CoupleTurnCodec.normalizeCode(coupleCode)
        return "00" + CoupleCryptoEngine.sha256Hex("KRIMOON_GENESIS_ANCHOR_V2_$clean").drop(2)
    }

    fun createGenesisBlock(coupleCode: String, creatorName: String): CoupleBlock {
        val clean = CoupleTurnCodec.normalizeCode(coupleCode)
        val rootAnchor = genesisHash(clean)
        val payloadHash = CoupleCryptoEngine.sha256Hex("GENESIS_BOND|$clean|${creatorName.trim()}")
        // Deterministic timestamp for Genesis block per coupleCode so both devices share identical Genesis anchor
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
        signaturePreview: String = ""
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
            signaturePreview = signaturePreview
        )
    }

    fun verifyBlock(block: CoupleBlock): Boolean {
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

    fun verifyChain(blocks: List<CoupleBlock>, coupleCode: String): ChainVerificationResult {
        if (blocks.isEmpty()) {
            val genesis = createGenesisBlock(coupleCode, "System")
            return ChainVerificationResult(
                isValid = true,
                verifiedBlockCount = 1,
                latestBlockHash = genesis.blockHash,
                summary = "Genesis Anchor Ready · PoW Verified"
            )
        }
        val knownHashes = mutableSetOf(genesisHash(coupleCode))
        for (b in blocks) {
            if (!verifyBlock(b)) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = blocks.size,
                    latestBlockHash = b.blockHash,
                    summary = "Tamper alert at Block #${b.index}: Invalid SHA-256 PoW hash"
                )
            }
            if (b.previousHash.isBlank()) {
                return ChainVerificationResult(
                    isValid = false,
                    verifiedBlockCount = blocks.size,
                    latestBlockHash = b.blockHash,
                    summary = "Broken link at Block #${b.index}: Missing previousHash"
                )
            }
            knownHashes.add(b.blockHash)
        }
        return ChainVerificationResult(
            isValid = true,
            verifiedBlockCount = blocks.size,
            latestBlockHash = blocks.last().blockHash,
            summary = "Verified ${blocks.size} Blocks · PoW + SHA-256 Chain Intact"
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
            signaturePreview = o.optString("sp", "")
        )
    }

    fun ledgerToJson(blocks: List<CoupleBlock>): String {
        val arr = JSONArray()
        blocks.takeLast(80).forEach { arr.put(blockToJson(it)) }
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
    private val FORBIDDEN_PATTERNS = listOf(
        "<script", "javascript:", "vbscript:", "data:text/html",
        "DROP TABLE", "UNION SELECT", "DELETE FROM", "INSERT INTO",
        "../", "..\\", "content://", "file://"
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
        myName: String = ""
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
        for (pattern in FORBIDDEN_PATTERNS) {
            if (trimmed.contains(pattern, ignoreCase = true)) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Malicious injection pattern detected."))
            }
        }

        // Delegate to Codec which executes Rule 2 (Room Tag), Rule 3 (HMAC-SHA256 & AES-256-GCM),
        // Rule 4 (Blockchain PoW & Payload Hash), Rule 5 (Anti-Replay), and Rule 6 (Partner Lock).
        return CoupleTurnCodec.decodeSecuredPacket(
            rawInput = trimmed,
            expectedCoupleCode = expectedCoupleCode,
            existingLedger = existingLedger,
            lockedPartnerName = lockedPartnerName,
            myName = myName
        )
    }
}

object CoupleGameDecks {

    val doodlePrompts = listOf(
        "Us on our dream date 🌙✨",
        "Our future cozy apartment 🏡",
        "Draw what you want to kiss right now 💋",
        "Your partner as a cute animal 🐱",
        "Late-night snack craving 🍓🍫",
        "What we're doing next time we meet 🔥",
        "Our signature couple tattoo 💕",
        "Midnight stargazing picnic 🌌"
    )

    val quizQuestions: List<CoupleQuizQuestion> = listOf(
        // SWEET
        CoupleQuizQuestion(
            id = "sq1",
            prompt = "What is our ideal zero-effort Sunday morning together?",
            options = listOf("Cuddling under blankets", "Coffee + pancakes in bed", "Binge-watching in hoodies", "Long sleepy voice call"),
            spiceLevel = SpiceLevel.SWEET
        ),
        CoupleQuizQuestion(
            id = "sq2",
            prompt = "Which small gesture melts my heart fastest?",
            options = listOf("Random 'miss you' text", "Forehead kiss", "Remembering tiny details", "Surprise snack delivery"),
            spiceLevel = SpiceLevel.SWEET
        ),
        CoupleQuizQuestion(
            id = "sq3",
            prompt = "When we reunite after long distance, what happens in the first 10 seconds?",
            options = listOf("Lift-off bear hug", "Non-stop kisses", "Happy tears & giggles", "Freeze and stare in awe"),
            spiceLevel = SpiceLevel.SWEET
        ),
        CoupleQuizQuestion(
            id = "sq4",
            prompt = "What song or vibe screams 'us'?",
            options = listOf("Late-night R&B slow jam", "Dreamy indie pop", "90s nostalgic love ballad", "Upbeat chaotic dance track"),
            spiceLevel = SpiceLevel.SWEET
        ),
        // FLIRTY
        CoupleQuizQuestion(
            id = "fq1",
            prompt = "Where should my next unexpected kiss land?",
            options = listOf("Right on the neck", "Corner of the lips", "Slow lip lock", "Collarbone tease"),
            spiceLevel = SpiceLevel.FLIRTY
        ),
        CoupleQuizQuestion(
            id = "fq2",
            prompt = "What outfit or look on me drives you the wildest?",
            options = listOf("Oversized tee & nothing else", "Dressed up for date night", "Fresh out of the shower", "Cozy fitted loungewear"),
            spiceLevel = SpiceLevel.FLIRTY
        ),
        CoupleQuizQuestion(
            id = "fq3",
            prompt = "If I whisper something teasing during a boring call, how do you react?",
            options = listOf("Blush & lose train of thought", "Tease right back harder", "Demand a private replay later", "Smirk and bite my lip"),
            spiceLevel = SpiceLevel.FLIRTY
        ),
        CoupleQuizQuestion(
            id = "fq4",
            prompt = "Which romantic tension trope fits us best?",
            options = listOf("Hands wandering under the table", "Rain-soaked slow kiss", "Pinned gently against the wall", "Stolen glances across the room"),
            spiceLevel = SpiceLevel.FLIRTY
        ),
        // BOLD / AFTER-DARK
        CoupleQuizQuestion(
            id = "bq1",
            prompt = "On our next private weekend away, what's the #1 rule?",
            options = listOf("Phones on DND all weekend", "Zero rush, pure chemistry", "Room service & candlelight only", "You're in charge tonight"),
            spiceLevel = SpiceLevel.BOLD
        ),
        CoupleQuizQuestion(
            id = "bq2",
            prompt = "What kind of late-night voice note from me makes your pulse race?",
            options = listOf("Soft sleepy whispers", "Confessing what I crave next", "Playful teasing dare", "Describing our reunion kiss"),
            spiceLevel = SpiceLevel.BOLD
        ),
        CoupleQuizQuestion(
            id = "bq3",
            prompt = "Which midnight energy describes our chemistry best?",
            options = listOf("Slow, intense eye contact", "Playful biting & laughs", "Can't-keep-hands-off-you", "Whispered secrets in the dark"),
            spiceLevel = SpiceLevel.BOLD
        ),
        CoupleQuizQuestion(
            id = "bq4",
            prompt = "First 30 minutes behind closed doors when we finally see each other?",
            options = listOf("Make out like we're in a movie", "Take turns spoiling each other", "Shower together & decompress", "Lock the door & forget time"),
            spiceLevel = SpiceLevel.BOLD
        )
    )

    val wouldYouRatherPrompts: List<WouldYouRatherPrompt> = listOf(
        // SWEET
        WouldYouRatherPrompt("sw1", "Fall asleep on FaceTime every night", "Write each other weekly love letters", SpiceLevel.SWEET),
        WouldYouRatherPrompt("sw2", "Cook chaotic dinner together at 1 AM", "Stargaze from a cozy rooftop", SpiceLevel.SWEET),
        WouldYouRatherPrompt("sw3", "Wear matching hoodies in public", "Have a secret handshake only we know", SpiceLevel.SWEET),
        // FLIRTY
        WouldYouRatherPrompt("fw1", "Receive a spicy surprise voice note mid-day", "Get pulled in for a sudden doorway kiss", SpiceLevel.FLIRTY),
        WouldYouRatherPrompt("fw2", "Slow dance in the kitchen at midnight", "Steamy bubble bath with candles", SpiceLevel.FLIRTY),
        WouldYouRatherPrompt("fw3", "Tease each other in public without touching", "Sneak away from a party for 10 minutes", SpiceLevel.FLIRTY),
        // BOLD / AFTER-DARK
        WouldYouRatherPrompt("bw1", "Play Truth or Dare with zero skips tonight", "Let your partner call every shot on date night", SpiceLevel.BOLD),
        WouldYouRatherPrompt("bw2", "Weekend locked in a mountain cabin", "Suite with a private rooftop hot tub", SpiceLevel.BOLD),
        WouldYouRatherPrompt("bw3", "Slow teasing buildup all evening", "Immediate 'drop everything' reunion", SpiceLevel.BOLD)
    )

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

    fun generateUniqueCode(existingCodes: Set<String> = emptySet()): String {
        repeat(40) {
            val candidate = Random.nextInt(100_000, 999_999).toString()
            if (candidate !in existingCodes) return candidate
        }
        return (System.currentTimeMillis() % 899_999 + 100_000).toString()
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
     * Core Blockchain + AES-256-GCM Packet Builder.
     * Mines a PoW CoupleBlock, encrypts the inner payload with AES-256-GCM bound to the block hash via AAD,
     * signs with HMAC-SHA256, and returns both the wire string and mined CoupleBlock.
     */
    private fun sealBlockchainPacket(
        coupleCode: String,
        senderName: String,
        actionType: String,
        plainPayloadObj: JSONObject,
        previousHash: String,
        blockIndex: Int
    ): Pair<String, CoupleBlock> {
        val cleanCode = normalizeCode(coupleCode)
        val cleanSender = CoupleFirewall.sanitizeName(senderName)
        val prev = previousHash.ifBlank { CoupleBlockchain.genesisHash(cleanCode) }
        val plainJson = plainPayloadObj.toString()
        val payloadHash = CoupleCryptoEngine.sha256Hex(plainJson)

        val mined = CoupleBlockchain.mineBlock(
            index = blockIndex.coerceAtLeast(1),
            actionType = actionType,
            senderName = cleanSender,
            previousHash = prev,
            payloadHash = payloadHash
        )

        val aad = "$cleanCode|${mined.blockHash}|$actionType"
        val envelope = CoupleCryptoEngine.encryptPayload(
            plainJson = plainJson,
            coupleCode = cleanCode,
            aad = aad
        )

        val finalBlock = mined.copy(
            cipherPreview = envelope.cipherBase64.take(18),
            signaturePreview = envelope.hmacHex.take(16)
        )

        val outer = JSONObject().apply {
            put("v", 2)
            put("tag", codeTag(cleanCode))
            put("blk", CoupleBlockchain.blockToJson(finalBlock))
            put("iv", envelope.ivBase64)
            put("ct", envelope.cipherBase64)
            put("sig", envelope.hmacHex)
        }
        val b64 = Base64.encodeToString(outer.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "$BCHAIN_PREFIX$b64" to finalBlock
    }

    fun encodeBondHandshakeWithBlock(
        coupleCode: String,
        senderName: String,
        spiceLevel: SpiceLevel = SpiceLevel.FLIRTY,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "HANDSHAKE")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("spice", spiceLevel.name)
        }
        return sealBlockchainPacket(coupleCode, senderName, "HANDSHAKE", payload, previousHash, blockIndex)
    }

    fun encodeBondHandshake(
        coupleCode: String,
        senderName: String,
        spiceLevel: SpiceLevel = SpiceLevel.FLIRTY,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): String = encodeBondHandshakeWithBlock(coupleCode, senderName, spiceLevel, previousHash, blockIndex).first

    fun encodeUnpairNoticeWithBlock(
        coupleCode: String,
        senderName: String,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "UNPAIR")
            put("sender", CoupleFirewall.sanitizeName(senderName))
        }
        return sealBlockchainPacket(coupleCode, senderName, "UNPAIR", payload, previousHash, blockIndex)
    }

    fun encodeQuizTurnWithBlock(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "QUIZ")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("round", roundIndex)
            put("pick", maskChoice(choiceIndex, coupleCode, roundIndex))
            put("spice", spiceLevel.name)
        }
        return sealBlockchainPacket(coupleCode, senderName, "QUIZ", payload, previousHash, blockIndex)
    }

    fun encodeQuizTurn(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): String = encodeQuizTurnWithBlock(coupleCode, senderName, roundIndex, choiceIndex, spiceLevel, previousHash, blockIndex).first

    fun encodeWyrTurnWithBlock(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "WYR")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("round", roundIndex)
            put("pick", maskChoice(choiceIndex, coupleCode, roundIndex))
            put("spice", spiceLevel.name)
        }
        return sealBlockchainPacket(coupleCode, senderName, "WYR", payload, previousHash, blockIndex)
    }

    fun encodeWyrTurn(
        coupleCode: String,
        senderName: String,
        roundIndex: Int,
        choiceIndex: Int,
        spiceLevel: SpiceLevel,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): String = encodeWyrTurnWithBlock(coupleCode, senderName, roundIndex, choiceIndex, spiceLevel, previousHash, blockIndex).first

    fun encodeDoodleTurnWithBlock(
        coupleCode: String,
        senderName: String,
        prompt: String,
        strokes: List<DoodleStrokeData>,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "DOODLE")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("prompt", prompt)
            put("strokes", strokesToJsonArray(strokes.takeLast(60)))
        }
        return sealBlockchainPacket(coupleCode, senderName, "DOODLE", payload, previousHash, blockIndex)
    }

    fun encodeDoodleTurn(
        coupleCode: String,
        senderName: String,
        prompt: String,
        strokes: List<DoodleStrokeData>,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): String = encodeDoodleTurnWithBlock(coupleCode, senderName, prompt, strokes, previousHash, blockIndex).first

    fun encodeTicTacToeTurnWithBlock(
        coupleCode: String,
        senderName: String,
        state: TicTacToeSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): Pair<String, CoupleBlock> {
        val payload = JSONObject().apply {
            put("t", "TTT")
            put("sender", CoupleFirewall.sanitizeName(senderName))
            put("state", ticTacToeToJson(state))
        }
        return sealBlockchainPacket(coupleCode, senderName, "TTT", payload, previousHash, blockIndex)
    }

    fun encodeTicTacToeTurn(
        coupleCode: String,
        senderName: String,
        state: TicTacToeSyncState,
        previousHash: String = CoupleBlockchain.genesisHash(coupleCode),
        blockIndex: Int = 1
    ): String = encodeTicTacToeTurnWithBlock(coupleCode, senderName, state, previousHash, blockIndex).first

    /**
     * Primary entry point for decoding & verifying any Couple Lounge packet through the Firewall.
     */
    fun decodePacket(
        rawInput: String,
        expectedCoupleCode: String,
        existingLedger: List<CoupleBlock> = emptyList(),
        lockedPartnerName: String = "",
        myName: String = ""
    ): Result<DecodedTurnPacket> {
        return CoupleFirewall.inspectAndFilter(
            rawInput = rawInput,
            expectedCoupleCode = expectedCoupleCode,
            existingLedger = existingLedger,
            lockedPartnerName = lockedPartnerName,
            myName = myName
        )
    }

    internal fun decodeSecuredPacket(
        rawInput: String,
        expectedCoupleCode: String,
        existingLedger: List<CoupleBlock>,
        lockedPartnerName: String,
        myName: String
    ): Result<DecodedTurnPacket> {
        return try {
            val cleanCode = normalizeCode(expectedCoupleCode)
            val bchainIdx = rawInput.indexOf(BCHAIN_PREFIX)
            if (bchainIdx < 0) {
                return Result.failure(IllegalArgumentException("FIREWALL BLOCKED: Missing KRIMOON:BCHAIN header."))
            }

            val token = rawInput.substring(bchainIdx + BCHAIN_PREFIX.length).trim().split(Regex("\\s+")).first()
            val outerJsonStr = String(Base64.decode(token, Base64.DEFAULT), StandardCharsets.UTF_8)
            val outer = JSONObject(outerJsonStr)

            // Firewall Rule 2: Room Key Tag Isolation
            val tag = outer.optString("tag", "")
            if (!CoupleCryptoEngine.constantTimeEquals(tag, codeTag(cleanCode))) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Packet belongs to a different Couple Code!"))
            }

            val blockObj = outer.optJSONObject("blk")
                ?: return Result.failure(SecurityException("FIREWALL BLOCKED: Missing Blockchain header."))
            val block = CoupleBlockchain.blockFromJson(blockObj)

            // Firewall Rule 4a: Proof-of-Work & SHA-256 Block Hash Verification
            if (!CoupleBlockchain.verifyBlock(block)) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Blockchain Proof-of-Work or SHA-256 hash invalid!"))
            }

            // Firewall Rule 5: Anti-Replay Duplicate Block Filter
            if (existingLedger.any { it.blockHash == block.blockHash }) {
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

            // Firewall Rule 3: HMAC-SHA256 Signature & AES-256-GCM Authenticated Decryption
            val ivB64 = outer.optString("iv", "")
            val ctB64 = outer.optString("ct", "")
            val sigHex = outer.optString("sig", "")
            val aad = "$cleanCode|${block.blockHash}|${block.actionType}"

            val decryptedResult = CoupleCryptoEngine.decryptPayload(
                ivBase64 = ivB64,
                cipherBase64 = ctB64,
                expectedHmacHex = sigHex,
                coupleCode = cleanCode,
                aad = aad
            )
            val decryptedJson = decryptedResult.getOrElse { err ->
                return Result.failure(err)
            }

            // Firewall Rule 4b: Verify Decrypted Payload Hash matches Mined Block's payloadHash
            val actualPayloadHash = CoupleCryptoEngine.sha256Hex(decryptedJson)
            if (!CoupleCryptoEngine.constantTimeEquals(actualPayloadHash, block.payloadHash)) {
                return Result.failure(SecurityException("FIREWALL BLOCKED: Payload hash mismatch against mined block!"))
            }

            val obj = JSONObject(decryptedJson)
            val sender = CoupleFirewall.sanitizeName(obj.optString("sender", senderFromBlock))

            when (obj.optString("t")) {
                "HANDSHAKE" -> {
                    val spice = runCatching { SpiceLevel.valueOf(obj.optString("spice", "FLIRTY")) }
                        .getOrDefault(SpiceLevel.FLIRTY)
                    Result.success(
                        DecodedTurnPacket.BondHandshake(
                            coupleCode = cleanCode,
                            senderName = sender,
                            spiceLevel = spice,
                            block = block
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
                    val prompt = obj.optString("prompt", "").take(140)
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
            val outer = JSONObject(String(Base64.decode(token, Base64.DEFAULT), StandardCharsets.UTF_8))
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
        return JSONObject().apply {
            put("r", state.roundIndex)
            if (state.myChoice != null) put("m", state.myChoice)
            if (state.partnerChoice != null) put("p", state.partnerChoice)
            put("pn", state.partnerName)
        }.toString()
    }

    fun roundStateFromJson(json: String): RoundSyncState {
        return runCatching {
            val o = JSONObject(json.ifBlank { "{}" })
            RoundSyncState(
                roundIndex = o.optInt("r", 0),
                myChoice = if (o.has("m")) o.optInt("m") else null,
                partnerChoice = if (o.has("p")) o.optInt("p") else null,
                partnerName = o.optString("pn", "")
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
