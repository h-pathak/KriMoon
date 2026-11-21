package com.example.cloud

import android.content.Context
import com.example.R
import com.example.model.CoupleActivityIntent
import com.example.model.CoupleBlock
import com.example.model.CoupleBlockchain
import com.example.model.CoupleEcdhEngine
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
    private val auth: FirebaseAuth = Firebase.auth,
    private val inviteLookupClient: CoupleInviteLookupClient = FirebaseCallableInviteLookupClient()
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

    private fun invitesCollection() = db.collection("coupleInvites")

    private fun blocksCollection(coupleCode: String) =
        roomsCollection().document(coupleCode).collection("blocks")

    /**
     * Real-time stream of all active bonded rooms for the authenticated user.
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
                    ).filter { it.status != "UNPAIRED" && !it.tombstoned }
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
     * Real-time stream of a single room document (strictly for bonded members OR active pending partner).
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
                        compareBy<CoupleEncryptedBlock> { it.blockIndex }
                            .thenBy { it.createdAt?.seconds ?: 0L }
                            .thenBy { it.createdAt?.nanoseconds ?: 0 }
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
     * Looks up minimal non-sensitive invite metadata exclusively via the server-side
     * rate-limited `lookupCoupleInvite` Firebase Callable Cloud Function.
     * Never performs direct client Firestore reads on /coupleInvites/{coupleCode}.
     */
    suspend fun lookupCoupleInvite(coupleCode: String): Result<CoupleCloudInvite?> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Room code must be 6 digits." }
            when (val outcome = inviteLookupClient.lookup(cleanCode)) {
                is InviteLookupOutcome.Available -> Result.success(outcome.invite)
                is InviteLookupOutcome.Unavailable -> {
                    if (outcome.status == "NOT_FOUND") {
                        Result.success(null)
                    } else {
                        Result.success(
                            CoupleCloudInvite(
                                id = cleanCode,
                                coupleCode = cleanCode,
                                status = outcome.status
                            )
                        )
                    }
                }
                is InviteLookupOutcome.RateLimited -> Result.failure(
                    InviteRateLimitedException(
                        message = outcome.message,
                        reason = outcome.reason,
                        retryAfterSeconds = outcome.retryAfterSeconds
                    )
                )
                is InviteLookupOutcome.Failed -> Result.failure(
                    InviteLookupException(
                        message = outcome.message,
                        isRetryable = outcome.isRetryable,
                        cause = outcome.cause
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically reserves a globally unique, never-before-used 6-digit V2 room code via Firestore transaction,
     * anchoring latestBlockHash to the deterministic V2 Genesis Block #0 hash and publishing the minimal
     * /coupleInvites/{code} lookup record in the exact same transaction.
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
            require(CoupleEcdhEngine.isValidPublicKeyB64(safePubKey)) {
                "Valid NIST P-256 public key is required to create a zero-knowledge room."
            }
            val explicitCode = preferredCode?.let { CoupleTurnCodec.normalizeCode(it) }?.takeIf { it.length == 6 }

            val attempts = if (explicitCode != null) 1 else 10
            val triedCodes = mutableSetOf<String>()

            repeat(attempts) {
                val candidate = explicitCode ?: CoupleTurnCodec.generateUniqueCode(triedCodes)
                triedCodes.add(candidate)
                val roomRef = roomsCollection().document(candidate)
                val inviteRef = invitesCollection().document(candidate)
                val deterministicGenesis = CoupleBlockchain.createDeterministicGenesisBlock(candidate)
                val genesisBlockHash = deterministicGenesis.blockHash.take(128)
                val hostHint = cleanName.take(12)

                val claimed: Boolean = try {
                    db.runTransaction { tx ->
                        // Check /coupleRooms/{candidate} only (allowed when resource == null);
                        // never perform direct client reads on /coupleInvites/{candidate}.
                        val roomSnap = tx.get(roomRef)
                        if (roomSnap.exists()) {
                            return@runTransaction false
                        }
                        val roomPayload = mapOf(
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
                            "latestBlockHash" to genesisBlockHash,
                            "schemaVersion" to CoupleBlockchain.SCHEMA_VERSION_V2,
                            "migrationAnchorHash" to "",
                            "migrationAnchorIndex" to 0L,
                            "tombstoned" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        val invitePayload = mapOf(
                            "coupleCode" to candidate,
                            "status" to "WAITING",
                            "hostDisplayHint" to hostHint,
                            "schemaVersion" to CoupleBlockchain.SCHEMA_VERSION_V2,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        tx.set(roomRef, roomPayload)
                        tx.set(inviteRef, invitePayload)
                        true
                    }.await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
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
                        latestBlockHash = genesisBlockHash,
                        schemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
                        migrationAnchorHash = "",
                        migrationAnchorIndex = 0L,
                        tombstoned = false
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
     * Submits a join request to an existing Host's room code using minimal /coupleInvites lookup
     * and an atomic blind WriteBatch (never requiring stranger read access on /coupleRooms/{code}).
     * Strictly rejects self-joining with the same Firebase Auth account.
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
            require(CoupleEcdhEngine.isValidPublicKeyB64(safePubKey)) {
                "Valid NIST P-256 public key is required to request entry."
            }

            val roomRef = roomsCollection().document(cleanCode)
            val inviteRef = invitesCollection().document(cleanCode)

            // 1. Check if caller is ALREADY a bonded member or active pending partner (allowed under strict rules)
            val myExistingRoom: CoupleCloudRoom? = try {
                val snap = roomRef.get().await()
                if (snap.exists()) {
                    snap.toObject(CoupleCloudRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
            }

            if (myExistingRoom != null) {
                if (myExistingRoom.status == "UNPAIRED" || myExistingRoom.tombstoned) {
                    return Result.failure(
                        IllegalStateException("Room #$cleanCode has permanently ended and cannot be reused.")
                    )
                }
                if (myExistingRoom.status == "PENDING_APPROVAL" && myExistingRoom.pendingPartnerUid == uid) {
                    return Result.success(
                        CloudJoinResult(
                            room = myExistingRoom,
                            createdNew = false,
                            isPendingHostApproval = true,
                            justJoinedAsPartner = false
                        )
                    )
                }
                if (uid in myExistingRoom.memberUids) {
                    if (myExistingRoom.creatorUid == uid && myExistingRoom.status == "WAITING") {
                        return Result.failure(
                            SecurityException("You created Room #$cleanCode with this Google account. Sign in with your partner's separate Google account on their phone to join.")
                        )
                    }
                    return Result.success(
                        CloudJoinResult(
                            room = myExistingRoom,
                            createdNew = false,
                            isPendingHostApproval = false,
                            justJoinedAsPartner = false
                        )
                    )
                }
            }

            // 2. Verify room availability via trusted server-side rate-limited callable
            //    (never reads /coupleInvites/{code} directly from the Android client)
            val availableInvite: CoupleCloudInvite = when (val lookup = inviteLookupClient.lookup(cleanCode)) {
                is InviteLookupOutcome.Available -> lookup.invite
                is InviteLookupOutcome.RateLimited -> return Result.failure(
                    InviteRateLimitedException(
                        message = lookup.message,
                        reason = lookup.reason,
                        retryAfterSeconds = lookup.retryAfterSeconds
                    )
                )
                is InviteLookupOutcome.Failed -> return Result.failure(
                    InviteLookupException(
                        message = lookup.message,
                        isRetryable = lookup.isRetryable,
                        cause = lookup.cause
                    )
                )
                is InviteLookupOutcome.Unavailable -> {
                    return when (lookup.status) {
                        "RETIRED" -> Result.failure(
                            InviteLookupException(
                                message = "Room #$cleanCode has permanently ended and cannot be reused.",
                                isRetryable = false
                            )
                        )
                        "LOCKED" -> Result.failure(
                            InviteLookupException(
                                message = "Room #$cleanCode is private and already locked to 2 partners.",
                                isRetryable = false
                            )
                        )
                        "PENDING" -> Result.failure(
                            InviteLookupException(
                                message = "Room #$cleanCode already has a pending request awaiting the Host.",
                                isRetryable = true
                            )
                        )
                        "NOT_FOUND" -> Result.failure(
                            InviteLookupException(
                                message = "Room #$cleanCode doesn't exist yet. Ask your partner to create the room first.",
                                isRetryable = false
                            )
                        )
                        else -> Result.failure(
                            InviteLookupException(
                                message = "Room #$cleanCode is not currently open for joining.",
                                isRetryable = true
                            )
                        )
                    }
                }
            }

            // 3. Submit blind atomic join request (does not read stranger's /coupleRooms doc)
            try {
                val batch = db.batch()
                batch.update(
                    roomRef,
                    mapOf(
                        "pendingPartnerUid" to uid,
                        "pendingPartnerName" to cleanName,
                        "pendingPartnerPublicKey" to safePubKey,
                        "status" to "PENDING_APPROVAL",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (availableInvite.schemaVersion >= CoupleBlockchain.SCHEMA_VERSION_V2) {
                    batch.update(
                        inviteRef,
                        mapOf(
                            "status" to "PENDING",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            } catch (e: FirebaseFirestoreException) {
                return when (e.code) {
                    FirebaseFirestoreException.Code.NOT_FOUND -> Result.failure(
                        InviteLookupException(
                            message = "Room #$cleanCode doesn't exist yet. Ask your partner to create the room first.",
                            isRetryable = false,
                            cause = e
                        )
                    )
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> Result.failure(
                        InviteLookupException(
                            message = "Room #$cleanCode is private, already pending, or belongs to your own account.",
                            isRetryable = false,
                            cause = e
                        )
                    )
                    else -> Result.failure(
                        InviteLookupException(
                            message = "Could not send join request for Room #$cleanCode. Please retry.",
                            isRetryable = true,
                            cause = e
                        )
                    )
                }
            }

            // 4. Now that caller IS pendingPartnerUid, strict allow get permits reading the room state
            val joinedSnap = roomRef.get().await()
            val pendingRoom = joinedSnap.toObject(
                CoupleCloudRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: CoupleCloudRoom(
                id = cleanCode,
                coupleCode = cleanCode,
                pendingPartnerUid = uid,
                pendingPartnerName = cleanName,
                pendingPartnerPublicKey = safePubKey,
                status = "PENDING_APPROVAL"
            )
            Result.success(
                CloudJoinResult(
                    room = pendingRoom,
                    createdNew = false,
                    isPendingHostApproval = true,
                    justJoinedAsPartner = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Host-only secondary confirmation: atomically promotes pending partner into bonded memberUids,
     * transitions room to CONNECTED, and locks /coupleInvites/{code} to LOCKED.
     */
    suspend fun approvePendingPartner(coupleCode: String): Result<CoupleCloudRoom> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            val uid = requireUserId()
            require(cleanCode.length == 6) { "Invalid room code." }
            val roomRef = roomsCollection().document(cleanCode)
            val inviteRef = invitesCollection().document(cleanCode)

            val approvedRoom = db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val existing = roomSnap.toObject(
                    CoupleCloudRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Room not found.")

                if (existing.creatorUid != uid) {
                    throw SecurityException("Only the room creator can approve entry.")
                }
                if (existing.status != "PENDING_APPROVAL" || existing.pendingPartnerUid.isBlank()) {
                    throw IllegalStateException("No pending partner request to approve.")
                }
                if (existing.pendingPartnerUid == existing.creatorUid) {
                    throw SecurityException("Self-approval with the same account is prohibited.")
                }

                val finalMembers = listOf(existing.creatorUid, existing.pendingPartnerUid)

                tx.update(
                    roomRef,
                    mapOf(
                        "partnerUid" to existing.pendingPartnerUid,
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
                if (existing.schemaVersion >= CoupleBlockchain.SCHEMA_VERSION_V2) {
                    tx.update(
                        inviteRef,
                        mapOf(
                            "status" to "LOCKED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                existing.copy(
                    partnerUid = existing.pendingPartnerUid,
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
     * Host declines a join request OR waiting partner cancels their own request,
     * atomically returning both /coupleRooms/{code} and /coupleInvites/{code} to WAITING.
     */
    suspend fun declineOrCancelPendingPartner(coupleCode: String): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid room code." }
            val roomRef = roomsCollection().document(cleanCode)
            val inviteRef = invitesCollection().document(cleanCode)

            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val schemaVersion = (roomSnap.getLong("schemaVersion") ?: 1L).toInt()
                tx.update(
                    roomRef,
                    mapOf(
                        "pendingPartnerUid" to "",
                        "pendingPartnerName" to "",
                        "pendingPartnerPublicKey" to "",
                        "status" to "WAITING",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (schemaVersion >= CoupleBlockchain.SCHEMA_VERSION_V2) {
                    tx.update(
                        inviteRef,
                        mapOf(
                            "status" to "WAITING",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Fetches the authoritative cloud room head for rebase/sync operations.
     */
    suspend fun fetchRoomOnce(coupleCode: String): Result<CoupleCloudRoom?> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            val snap = roomsCollection().document(cleanCode).get().await()
            val room = if (snap.exists()) {
                snap.toObject(CoupleCloudRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(room)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    /**
     * Checks if a block with [clientTurnId] has already been committed to Firestore (idempotency guard
     * for crash recovery where a prior commit succeeded just before the device lost power/network).
     */
    suspend fun findBlockByClientTurnId(coupleCode: String, clientTurnId: String): CoupleEncryptedBlock? {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val uid = auth.currentUser?.uid ?: return null
        if (cleanCode.length != 6 || clientTurnId.isBlank()) return null
        return runCatching {
            val snap = blocksCollection(cleanCode)
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            snap.toObjects(CoupleEncryptedBlock::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .firstOrNull { it.clientTurnId == clientTurnId }
        }.getOrNull()
    }

    /**
     * Atomically publishes an encrypted V2 block AND advances the parent room head in ONE Firestore
     * transaction, validated bidirectionally by firestore.rules (`get()`, `getAfter()`, `existsAfter()`).
     *
     * - If the cloud room head has advanced concurrently, returns [BlockPublishOutcome.HeadConflict]
     *   so the caller can rebase the intent onto the new head with a brand-new 96-bit AES-GCM nonce.
     * - If a legacy V1 room is encountered (`schemaVersion == 1`), atomically records
     *   `migrationAnchorHash = roomBefore.latestBlockHash` and `migrationAnchorIndex = roomBefore.blockCount`
     *   without touching any immutable legacy V1 blocks.
     */
    suspend fun publishEncryptedBlockAtomic(
        coupleCode: String,
        intent: CoupleActivityIntent,
        block: CoupleBlock,
        encryptedPacket: String,
        isRetry: Boolean = false
    ): BlockPublishOutcome {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val blockId = block.blockHash.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        val path = "coupleRooms/$cleanCode/blocks/$blockId"
        return try {
            val uid = requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            require(blockId.length >= 8) { "Invalid block hash." }
            require(encryptedPacket.startsWith("KRIMOON:BCHAIN:")) { "Packet must be encrypted." }

            // Idempotency fast-path on retries / crash recovery: check if this clientTurnId was
            // already committed before power/network loss without scanning the whole subcollection on first send
            if (isRetry) {
                val existingByTurnId = findBlockByClientTurnId(cleanCode, intent.clientTurnId)
                if (existingByTurnId != null) {
                    return BlockPublishOutcome.AlreadyCommittedIdempotent(
                        blockHash = existingByTurnId.blockHash,
                        blockIndex = existingByTurnId.blockIndex
                    )
                }
            }

            val roomRef = roomsCollection().document(cleanCode)
            val blockRef = blocksCollection(cleanCode).document(blockId)
            val cleanSender = BackendSecurityFirewall.sanitizeField(intent.senderName, maxLength = 32).ifBlank { "Partner" }
            val allowedActions = setOf(
                "GENESIS_BOND", "HANDSHAKE", "QUIZ", "WYR", "DOODLE", "TTT",
                "DARE", "WAVELENGTH", "WHISPER", "UNPAIR"
            )
            val safeAction = if (block.actionType in allowedActions) block.actionType else "HANDSHAKE"
            val cleanTurnId = intent.clientTurnId.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(64)

            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val cloudRoom = roomSnap.toObject(
                    CoupleCloudRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@runTransaction BlockPublishOutcome.Failed(IllegalStateException("Couple room not found."))

                if (cloudRoom.status == "UNPAIRED" || cloudRoom.tombstoned) {
                    return@runTransaction BlockPublishOutcome.RoomRetired
                }
                if (uid !in cloudRoom.memberUids) {
                    return@runTransaction BlockPublishOutcome.Failed(SecurityException("Not a bonded member of this room."))
                }

                // Check idempotent block document existence first
                val existingBlockSnap = tx.get(blockRef)
                if (existingBlockSnap.exists()) {
                    return@runTransaction BlockPublishOutcome.AlreadyCommittedIdempotent(
                        blockHash = block.blockHash,
                        blockIndex = block.index.toLong()
                    )
                }

                // Detect concurrent head advancement -> return authoritative head so caller re-encrypts with fresh nonce
                if (cloudRoom.blockCount != block.index.toLong() ||
                    cloudRoom.latestBlockHash != block.previousHash
                ) {
                    return@runTransaction BlockPublishOutcome.HeadConflict(
                        currentCloudBlockCount = cloudRoom.blockCount,
                        currentCloudLatestHash = cloudRoom.latestBlockHash,
                        currentSchemaVersion = cloudRoom.schemaVersion.coerceAtLeast(1),
                        migrationAnchorHash = cloudRoom.migrationAnchorHash,
                        migrationAnchorIndex = cloudRoom.migrationAnchorIndex.toInt()
                    )
                }

                val isLazyV1Upgrade = cloudRoom.schemaVersion <= 1
                if (isLazyV1Upgrade && !Regex("^[0-9a-fA-F]{8,128}$").matches(cloudRoom.latestBlockHash)) {
                    return@runTransaction BlockPublishOutcome.Failed(
                        IllegalStateException("Legacy V1 room has invalid latestBlockHash; refusing unsafe V2 anchor migration.")
                    )
                }
                val newCount = (cloudRoom.blockCount + 1L).coerceAtMost(100000L)
                val anchorHash = if (isLazyV1Upgrade) cloudRoom.latestBlockHash else cloudRoom.migrationAnchorHash
                val anchorIndex = if (isLazyV1Upgrade) cloudRoom.blockCount else cloudRoom.migrationAnchorIndex

                val blockPayload = mapOf(
                    "coupleCode" to cleanCode,
                    "senderUid" to uid,
                    "senderName" to cleanSender,
                    "memberUids" to cloudRoom.memberUids,
                    "blockIndex" to block.index.toLong().coerceIn(0L, 100000L),
                    "actionType" to safeAction,
                    "previousHash" to block.previousHash.take(128),
                    "blockHash" to block.blockHash.take(128),
                    "schemaVersion" to CoupleBlockchain.SCHEMA_VERSION_V2,
                    "clientTurnId" to cleanTurnId,
                    "encryptedPacket" to encryptedPacket.take(65536),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                tx.set(blockRef, blockPayload)

                val roomUpdate = mutableMapOf<String, Any>(
                    "blockCount" to newCount,
                    "latestBlockHash" to block.blockHash.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isLazyV1Upgrade) {
                    roomUpdate["schemaVersion"] = CoupleBlockchain.SCHEMA_VERSION_V2
                    roomUpdate["migrationAnchorHash"] = anchorHash
                    roomUpdate["migrationAnchorIndex"] = anchorIndex
                }
                tx.update(roomRef, roomUpdate)

                BlockPublishOutcome.Committed(
                    block = block,
                    encryptedPacket = encryptedPacket,
                    newCloudBlockCount = newCount,
                    newSchemaVersion = CoupleBlockchain.SCHEMA_VERSION_V2,
                    migrationAnchorHash = anchorHash,
                    migrationAnchorIndex = anchorIndex.toInt()
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            BlockPublishOutcome.Failed(e)
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
     * Permanently retires the couple room in Firestore (terminal UNPAIRED state) and marks
     * /coupleInvites/{code} as RETIRED atomically.
     */
    suspend fun unpairCloudRoom(coupleCode: String): Result<Unit> {
        val cleanCode = CoupleTurnCodec.normalizeCode(coupleCode)
        val path = "coupleRooms/$cleanCode"
        return try {
            requireUserId()
            require(cleanCode.length == 6) { "Invalid couple code." }
            val roomRef = roomsCollection().document(cleanCode)
            val inviteRef = invitesCollection().document(cleanCode)
            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val schemaVersion = (roomSnap.getLong("schemaVersion") ?: 1L).toInt()
                tx.update(
                    roomRef,
                    mapOf(
                        "status" to "UNPAIRED",
                        "pendingPartnerUid" to "",
                        "pendingPartnerName" to "",
                        "pendingPartnerPublicKey" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (schemaVersion >= CoupleBlockchain.SCHEMA_VERSION_V2) {
                    tx.update(
                        inviteRef,
                        mapOf(
                            "status" to "RETIRED",
                            "hostDisplayHint" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Account-deletion tombstone: scrubs personal display names and public keys from every room
     * where the authenticated user is a member, retaining only non-identifying code reservation fields.
     * Note: Immutable append-only /blocks ciphertext subcollections cannot be deleted client-side,
     * and become cryptographically unreadable once local ECDH private keys are erased.
     */
    suspend fun tombstoneAllUserRoomsForAccountDeletion(): Result<Int> {
        val path = "coupleRooms"
        return try {
            val uid = requireUserId()
            val memberRoomsSnap = roomsCollection()
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            val pendingRoomsSnap = roomsCollection()
                .whereEqualTo("pendingPartnerUid", uid)
                .get()
                .await()

            var scrubbedCount = 0
            val failures = mutableListOf<String>()

            // 1. Tombstone every room where user is a bonded member
            for (doc in memberRoomsSnap.documents) {
                val room = doc.toObject(CoupleCloudRoom::class.java) ?: continue
                if (room.tombstoned) continue
                val code = room.coupleCode.ifBlank { doc.id }
                val roomRef = roomsCollection().document(code)
                val inviteRef = invitesCollection().document(code)
                val txRes = runCatching {
                    db.runTransaction { tx ->
                        val roomSnap = tx.get(roomRef)
                        val schemaVersion = (roomSnap.getLong("schemaVersion") ?: 1L).toInt()
                        tx.update(
                            roomRef,
                            mapOf(
                                "status" to "UNPAIRED",
                                "tombstoned" to true,
                                "creatorName" to "",
                                "partnerName" to "",
                                "creatorPublicKey" to "",
                                "partnerPublicKey" to "",
                                "pendingPartnerUid" to "",
                                "pendingPartnerName" to "",
                                "pendingPartnerPublicKey" to "",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        if (schemaVersion >= CoupleBlockchain.SCHEMA_VERSION_V2) {
                            tx.update(
                                inviteRef,
                                mapOf(
                                    "status" to "RETIRED",
                                    "hostDisplayHint" to "",
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }.await()
                }
                if (txRes.isSuccess) {
                    scrubbedCount++
                } else {
                    failures.add("member:$code")
                }
            }

            // 2. Cancel any pending join requests where deleting user is pendingPartnerUid
            for (doc in pendingRoomsSnap.documents) {
                val room = doc.toObject(CoupleCloudRoom::class.java) ?: continue
                if (room.tombstoned || room.status == "UNPAIRED") continue
                val code = room.coupleCode.ifBlank { doc.id }
                val cancelRes = declineOrCancelPendingPartner(code)
                if (cancelRes.isSuccess) {
                    scrubbedCount++
                } else {
                    failures.add("pending:$code")
                }
            }

            if (failures.isNotEmpty()) {
                Result.failure(
                    IllegalStateException("Could not scrub ${failures.size} cloud room(s) before account deletion.")
                )
            } else {
                Result.success(scrubbedCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
