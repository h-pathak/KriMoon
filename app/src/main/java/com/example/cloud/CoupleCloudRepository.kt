package com.example.cloud

import android.content.Context
import com.example.R
import com.example.model.CoupleBlock
import com.example.model.CoupleTurnCodec
import com.example.model.SpiceLevel
import com.example.security.BackendSecurityFirewall
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudJoinResult(
    val room: CoupleCloudRoom,
    val createdNew: Boolean,
    val justJoinedAsPartner: Boolean
)

class CoupleCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Please sign in with Google to connect 2 phones live.")
    }

    private fun roomsCollection() = db.collection("coupleRooms")

    private fun blocksCollection(coupleCode: String) =
        roomsCollection().document(coupleCode).collection("blocks")

    /**
     * Real-time stream of all bonded rooms for the authenticated user.
     * Matches firestore.rules allow list via .whereArrayContains("memberUids", uid).
     */
    fun observeUserRooms(userId: String): Flow<List<CoupleCloudRoom>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "coupleRooms"
        emitAll(
            roomsCollection()
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CoupleCloudRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.status != "UNPAIRED" }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Real-time stream of a single bonded room document.
     */
    fun observeRoom(coupleCode: String, userId: String): Flow<CoupleCloudRoom?> = flow {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        if (cleanCode.length != 6 || userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "coupleRooms/$cleanCode"
        emitAll(
            roomsCollection()
                .document(cleanCode)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CoupleCloudRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Real-time stream of AES-256-GCM encrypted blockchain move packets for a room.
     * Matches firestore.rules allow list via .whereArrayContains("memberUids", userId).
     */
    fun observeEncryptedBlocks(coupleCode: String, userId: String): Flow<List<CoupleEncryptedBlock>> = flow {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        if (cleanCode.length != 6 || userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "coupleRooms/$cleanCode/blocks"
        emitAll(
            blocksCollection(cleanCode)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CoupleEncryptedBlock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<CoupleEncryptedBlock> { it.createdAt?.seconds ?: 0L }
                            .thenBy { it.createdAt?.nanoseconds ?: 0 }
                            .thenBy { it.blockIndex }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Creates a new 6-digit cloud room or joins an existing WAITING room live over the internet.
     */
    suspend fun createOrJoinCloudRoom(
        coupleCode: String,
        myName: String,
        spiceLevel: SpiceLevel,
        genesisHash: String
    ): Result<CloudJoinResult> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            val uid = requireUserId()
            require(cleanCode.length == 6) { "Couple code must be 6 digits." }
            val cleanName = BackendSecurityFirewall.sanitizeField(myName, maxLength = 32).ifBlank { "Partner" }
            val safeSpice = if (spiceLevel.name in setOf("SWEET", "FLIRTY", "BOLD")) spiceLevel.name else "FLIRTY"
            val docRef = roomsCollection().document(cleanCode)

            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "coupleCode" to cleanCode,
                    "creatorUid" to uid,
                    "creatorName" to cleanName,
                    "partnerUid" to "",
                    "partnerName" to "",
                    "memberUids" to listOf(uid),
                    "spiceLevel" to safeSpice,
                    "status" to "WAITING",
                    "blockCount" to 1L,
                    "latestBlockHash" to genesisHash.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = CoupleCloudRoom(
                    id = cleanCode,
                    coupleCode = cleanCode,
                    creatorUid = uid,
                    creatorName = cleanName,
                    partnerUid = "",
                    partnerName = "",
                    memberUids = listOf(uid),
                    spiceLevel = safeSpice,
                    status = "WAITING",
                    blockCount = 1L,
                    latestBlockHash = genesisHash.take(128)
                )
                return Result.success(CloudJoinResult(created, createdNew = true, justJoinedAsPartner = false))
            }

            val existing = existingSnap.toObject(
                CoupleCloudRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Unable to read room data."))

            if (existing.status == "UNPAIRED") {
                return Result.failure(IllegalStateException("This couple code has ended. Create a fresh room code."))
            }

            // Already a bonded member (returning or testing on 2nd phone with same account)
            if (uid in existing.memberUids) {
                if (existing.creatorUid == uid && existing.creatorName != cleanName && existing.partnerName.isBlank()) {
                    // Same-account 2nd phone joining with a different display name
                    docRef.update(
                        mapOf(
                            "partnerName" to cleanName,
                            "status" to "CONNECTED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(
                        CloudJoinResult(
                            room = existing.copy(partnerName = cleanName, status = "CONNECTED"),
                            createdNew = false,
                            justJoinedAsPartner = true
                        )
                    )
                }
                return Result.success(CloudJoinResult(existing, createdNew = false, justJoinedAsPartner = false))
            }

            // Joining an open WAITING 1-member room as the 2nd partner
            if (existing.status == "WAITING" && existing.memberUids.size == 1 && existing.partnerUid.isBlank()) {
                val updatedMembers = listOf(existing.creatorUid, uid)
                docRef.update(
                    mapOf(
                        "partnerUid" to uid,
                        "partnerName" to cleanName,
                        "memberUids" to updatedMembers,
                        "status" to "CONNECTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                val joinedRoom = existing.copy(
                    partnerUid = uid,
                    partnerName = cleanName,
                    memberUids = updatedMembers,
                    status = "CONNECTED"
                )
                return Result.success(CloudJoinResult(joinedRoom, createdNew = false, justJoinedAsPartner = true))
            }

            Result.failure(SecurityException("This room is private and already locked to 2 partners."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Publishes an AES-256-GCM + PoW Blockchain encrypted packet live to the bonded partner.
     */
    suspend fun publishEncryptedBlock(
        coupleCode: String,
        senderName: String,
        block: CoupleBlock,
        encryptedPacket: String
    ): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val blockId = block.blockHash.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        val path = "coupleRooms/$cleanCode/blocks/$blockId"
        return try {
            val uid = requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            require(blockId.length >= 8) { "Invalid block hash." }
            require(encryptedPacket.startsWith("KRIMOON:BCHAIN:")) { "Packet must be encrypted." }

            val roomRef = roomsCollection().document(cleanCode)
            val roomSnap = roomRef.get().await()
            val cloudRoom = roomSnap.toObject(
                CoupleCloudRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Couple room not found."))

            if (cloudRoom.status == "UNPAIRED") {
                return Result.failure(IllegalStateException("Couple room has been unpaired."))
            }
            if (uid !in cloudRoom.memberUids) {
                return Result.failure(SecurityException("Not a bonded member of this room."))
            }

            val cleanSender = BackendSecurityFirewall.sanitizeField(senderName, maxLength = 32).ifBlank { "Partner" }
            val allowedActions = setOf("GENESIS_BOND", "HANDSHAKE", "QUIZ", "WYR", "DOODLE", "TTT", "UNPAIR")
            val safeAction = if (block.actionType in allowedActions) block.actionType else "HANDSHAKE"

            val blockPayload = mapOf(
                "coupleCode" to cleanCode,
                "senderUid" to uid,
                "senderName" to cleanSender,
                "memberUids" to cloudRoom.memberUids,
                "blockIndex" to block.index.toLong().coerceIn(0L, 100000L),
                "actionType" to safeAction,
                "blockHash" to block.blockHash.take(128),
                "encryptedPacket" to encryptedPacket.take(65536),
                "createdAt" to FieldValue.serverTimestamp()
            )
            blocksCollection(cleanCode).document(blockId).set(blockPayload).await()

            roomRef.update(
                mapOf(
                    "blockCount" to (cloudRoom.blockCount + 1L).coerceAtMost(100000L),
                    "latestBlockHash" to block.blockHash.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Syncs shared couple vibe (Sweet / Flirty / After-Dark) live to both phones.
     */
    suspend fun updateCloudSpiceLevel(coupleCode: String, spiceLevel: SpiceLevel): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            roomsCollection().document(cleanCode).update(
                mapOf(
                    "spiceLevel" to spiceLevel.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Permanently ends the couple bond in Firestore (terminal UNPAIRED state).
     */
    suspend fun unpairCloudRoom(coupleCode: String): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            roomsCollection().document(cleanCode).update(
                mapOf(
                    "status" to "UNPAIRED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
