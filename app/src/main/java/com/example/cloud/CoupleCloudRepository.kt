package com.example.cloud

import android.content.Context
import com.example.R
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
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
    val isPendingHostApproval: Boolean,
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
     * Real-time stream of a single room document (works for bonded members AND pending join requesters).
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
     * Atomically reserves a globally unique, never-before-used 6-digit room code via Firestore transaction.
     * Because rooms can never be deleted, once reserved here, NO other couple can ever claim this code.
     */
    suspend fun createUniqueCloudRoom(
        myName: String,
        spiceLevel: SpiceLevel,
        preferredCode: String? = null,
        creatorPublicKey: String = ""
    ): Result<CloudJoinResult> {
        val path = "coupleRooms"
        return try {
            val uid = requireUserId()
            val cleanName = BackendSecurityFirewall.sanitizeField(myName, maxLength = 32).ifBlank { "Host" }
            val safeSpice = if (spiceLevel.name in setOf("SWEET", "FLIRTY", "BOLD")) spiceLevel.name else "FLIRTY"
            val safePubKey = creatorPublicKey.trim().take(256)
            val explicitCode = preferredCode?.let { CoupleTurnCodec.normalizeCode(it) }?.takeIf { it.length == 6 }

            val attempts = if (explicitCode != null) 1 else 10
            val triedCodes = mutableSetOf<String>()

            repeat(attempts) {
                val candidate = explicitCode ?: CoupleTurnCodec.generateUniqueCode(triedCodes)
                triedCodes.add(candidate)
                val docRef = roomsCollection().document(candidate)
                val genesisHash = CoupleBlockchain.genesisHash(candidate).take(128)

                val claimed: Boolean = try {
                    db.runTransaction { tx ->
                        val snap = tx.get(docRef)
                        if (snap.exists()) {
                            false
                        } else {
                            val payload = mapOf(
                                "coupleCode" to candidate,
                                "creatorUid" to uid,
                                "creatorName" to cleanName,
                                "creatorPublicKey" to safePubKey,
                                "partnerUid" to "",
                                "partnerName" to "",
                                "partnerPublicKey" to "",
                                "pendingPartnerUid" to "",
                                "pendingPartnerName" to "",
                                "pendingPartnerPublicKey" to "",
                                "memberUids" to listOf(uid),
                                "spiceLevel" to safeSpice,
                                "status" to "WAITING",
                                "blockCount" to 1L,
                                "latestBlockHash" to genesisHash,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            tx.set(docRef, payload)
                            true
                        }
                    }.await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // Existing room owned by another couple rejects read/write -> treat as already taken
                        false
                    } else {
                        throw e
                    }
                }

                if (claimed) {
                    val created = CoupleCloudRoom(
                        id = candidate,
                        coupleCode = candidate,
                        creatorUid = uid,
                        creatorName = cleanName,
                        creatorPublicKey = safePubKey,
                        partnerUid = "",
                        partnerName = "",
                        partnerPublicKey = "",
                        pendingPartnerUid = "",
                        pendingPartnerName = "",
                        pendingPartnerPublicKey = "",
                        memberUids = listOf(uid),
                        spiceLevel = safeSpice,
                        status = "WAITING",
                        blockCount = 1L,
                        latestBlockHash = genesisHash
                    )
                    return Result.success(
                        CloudJoinResult(
                            room = created,
                            createdNew = true,
                            isPendingHostApproval = false,
                            justJoinedAsPartner = false
                        )
                    )
                }
            }

            if (explicitCode != null) {
                Result.failure(
                    SecurityException("Room #$explicitCode is already permanently claimed by another couple. Create a fresh room code instead.")
                )
            } else {
                Result.failure(IllegalStateException("Could not reserve a unique room code. Please try again."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Submits a join request to an existing Host's room code.
     * The joining partner enters PENDING_APPROVAL until the Host explicitly taps Allow.
     */
    suspend fun requestToJoinCloudRoom(
        coupleCode: String,
        myName: String,
        partnerPublicKey: String = ""
    ): Result<CloudJoinResult> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            val uid = requireUserId()
            if (!BackendSecurityFirewall.allowRateLimitedOperation("couple_join_attempt_$uid", maxOpsPerWindow = 10, windowMillis = 60_000L)) {
                return Result.failure(
                    SecurityException("Too many join attempts — wait a moment before trying another code.")
                )
            }
            require(cleanCode.length == 6) { "Room code must be 6 digits." }
            val cleanName = BackendSecurityFirewall.sanitizeField(myName, maxLength = 32).ifBlank { "Partner" }
            val safePubKey = partnerPublicKey.trim().take(256)
            val docRef = roomsCollection().document(cleanCode)

            val existingSnap = try {
                docRef.get().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    return Result.failure(
                        SecurityException("Room #$cleanCode is private and already locked to another couple.")
                    )
                }
                throw e
            }

            if (!existingSnap.exists()) {
                return Result.failure(
                    IllegalStateException("Room #$cleanCode doesn't exist yet. Ask your partner to create the room first.")
                )
            }

            val existing = existingSnap.toObject(
                CoupleCloudRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Unable to read room data."))

            if (existing.status == "UNPAIRED") {
                return Result.failure(
                    IllegalStateException("Room #$cleanCode has permanently ended and cannot be reused.")
                )
            }

            // Already awaiting approval by this user
            if (existing.status == "PENDING_APPROVAL" && existing.pendingPartnerUid == uid) {
                return Result.success(
                    CloudJoinResult(
                        room = existing,
                        createdNew = false,
                        isPendingHostApproval = true,
                        justJoinedAsPartner = false
                    )
                )
            }

            // Already a bonded member
            if (uid in existing.memberUids) {
                // Same-account 2nd phone testing: if status is WAITING and name differs, submit pending request so Host can approve
                if (existing.creatorUid == uid &&
                    existing.status == "WAITING" &&
                    existing.memberUids.size == 1 &&
                    !existing.creatorName.equals(cleanName, ignoreCase = true)
                ) {
                    docRef.update(
                        mapOf(
                            "pendingPartnerUid" to uid,
                            "pendingPartnerName" to cleanName,
                            "pendingPartnerPublicKey" to safePubKey,
                            "status" to "PENDING_APPROVAL",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(
                        CloudJoinResult(
                            room = existing.copy(
                                pendingPartnerUid = uid,
                                pendingPartnerName = cleanName,
                                pendingPartnerPublicKey = safePubKey,
                                status = "PENDING_APPROVAL"
                            ),
                            createdNew = false,
                            isPendingHostApproval = true,
                            justJoinedAsPartner = false
                        )
                    )
                }
                return Result.success(
                    CloudJoinResult(
                        room = existing,
                        createdNew = false,
                        isPendingHostApproval = false,
                        justJoinedAsPartner = false
                    )
                )
            }

            // Open WAITING room -> submit join request with Partner's ECDH public key for Host confirmation
            if (existing.status == "WAITING" &&
                existing.memberUids.size == 1 &&
                existing.partnerUid.isBlank() &&
                existing.pendingPartnerUid.isBlank()
            ) {
                docRef.update(
                    mapOf(
                        "pendingPartnerUid" to uid,
                        "pendingPartnerName" to cleanName,
                        "pendingPartnerPublicKey" to safePubKey,
                        "status" to "PENDING_APPROVAL",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                val pendingRoom = existing.copy(
                    pendingPartnerUid = uid,
                    pendingPartnerName = cleanName,
                    pendingPartnerPublicKey = safePubKey,
                    status = "PENDING_APPROVAL"
                )
                return Result.success(
                    CloudJoinResult(
                        room = pendingRoom,
                        createdNew = false,
                        isPendingHostApproval = true,
                        justJoinedAsPartner = false
                    )
                )
            }

            if (existing.status == "PENDING_APPROVAL") {
                return Result.failure(
                    SecurityException("Room #$cleanCode already has a pending request awaiting the Host.")
                )
            }

            Result.failure(SecurityException("Room #$cleanCode is private and permanently locked to 2 partners."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Host-only secondary confirmation: allows the pending partner into the room,
     * locking memberUids to the couple and transitioning status to CONNECTED.
     */
    suspend fun approvePendingPartner(coupleCode: String): Result<CoupleCloudRoom> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            val uid = requireUserId()
            require(cleanCode.length == 6) { "Invalid room code." }
            val docRef = roomsCollection().document(cleanCode)

            val approvedRoom = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val existing = snap.toObject(
                    CoupleCloudRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Room not found.")

                if (existing.creatorUid != uid) {
                    throw SecurityException("Only the room creator can approve entry.")
                }
                if (existing.status != "PENDING_APPROVAL" || existing.pendingPartnerUid.isBlank()) {
                    throw IllegalStateException("No pending partner request to approve.")
                }

                val isSameAccountTest = existing.pendingPartnerUid == existing.creatorUid
                val finalPartnerUid = if (isSameAccountTest) "" else existing.pendingPartnerUid
                val finalMembers = if (isSameAccountTest) {
                    existing.memberUids
                } else {
                    listOf(existing.creatorUid, existing.pendingPartnerUid)
                }

                tx.update(
                    docRef,
                    mapOf(
                        "partnerUid" to finalPartnerUid,
                        "partnerName" to existing.pendingPartnerName,
                        "partnerPublicKey" to existing.pendingPartnerPublicKey,
                        "memberUids" to finalMembers,
                        "pendingPartnerUid" to "",
                        "pendingPartnerName" to "",
                        "pendingPartnerPublicKey" to "",
                        "status" to "CONNECTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                existing.copy(
                    partnerUid = finalPartnerUid,
                    partnerName = existing.pendingPartnerName,
                    partnerPublicKey = existing.pendingPartnerPublicKey,
                    memberUids = finalMembers,
                    pendingPartnerUid = "",
                    pendingPartnerName = "",
                    pendingPartnerPublicKey = "",
                    status = "CONNECTED"
                )
            }.await()

            Result.success(approvedRoom)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Host declines a join request OR the waiting partner cancels their own request,
     * returning the room cleanly to WAITING.
     */
    suspend fun declineOrCancelPendingPartner(coupleCode: String): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid room code." }
            roomsCollection().document(cleanCode).update(
                mapOf(
                    "pendingPartnerUid" to "",
                    "pendingPartnerName" to "",
                    "pendingPartnerPublicKey" to "",
                    "status" to "WAITING",
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
            val allowedActions = setOf(
                "GENESIS_BOND", "HANDSHAKE", "QUIZ", "WYR", "DOODLE", "TTT",
                "DARE", "WAVELENGTH", "WHISPER", "UNPAIR"
            )
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
     * Permanently retires the couple room in Firestore (terminal UNPAIRED state).
     * Document is never deleted so no other couple can ever reuse this 6-digit code.
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
                    "pendingPartnerUid" to "",
                    "pendingPartnerName" to "",
                    "pendingPartnerPublicKey" to "",
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
