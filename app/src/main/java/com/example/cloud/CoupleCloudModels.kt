package com.example.cloud

import androidx.annotation.Keep
import com.example.model.CoupleBlock
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CoupleCloudInvite(
    @DocumentId val id: String = "",
    val coupleCode: String = "",
    val status: String = "WAITING", // WAITING, PENDING, LOCKED, RETIRED
    val hostDisplayHint: String = "",
    val schemaVersion: Int = 2,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CoupleCloudRoom(
    @DocumentId val id: String = "",
    val coupleCode: String = "",
    val creatorUid: String = "",
    val creatorName: String = "",
    val creatorPublicKey: String = "",
    val partnerUid: String = "",
    val partnerName: String = "",
    val partnerPublicKey: String = "",
    val pendingPartnerUid: String = "",
    val pendingPartnerName: String = "",
    val pendingPartnerPublicKey: String = "",
    val memberUids: List<String> = emptyList(),
    val spiceLevel: String = "FLIRTY",
    val status: String = "WAITING",
    val blockCount: Long = 0L,
    val latestBlockHash: String = "",
    val schemaVersion: Int = 1,
    val migrationAnchorHash: String = "",
    val migrationAnchorIndex: Long = 0L,
    val tombstoned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CoupleEncryptedBlock(
    @DocumentId val id: String = "",
    val coupleCode: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val blockIndex: Long = 0L,
    val actionType: String = "",
    val previousHash: String = "",
    val blockHash: String = "",
    val schemaVersion: Int = 1,
    val clientTurnId: String = "",
    val encryptedPacket: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Explicit outcome of an atomic Firestore block + room-head transaction.
 */
sealed class BlockPublishOutcome {
    data class Committed(
        val block: CoupleBlock,
        val encryptedPacket: String,
        val newCloudBlockCount: Long,
        val newSchemaVersion: Int,
        val migrationAnchorHash: String,
        val migrationAnchorIndex: Int
    ) : BlockPublishOutcome()

    data class AlreadyCommittedIdempotent(
        val blockHash: String,
        val blockIndex: Long
    ) : BlockPublishOutcome()

    data class HeadConflict(
        val currentCloudBlockCount: Long,
        val currentCloudLatestHash: String,
        val currentSchemaVersion: Int,
        val migrationAnchorHash: String,
        val migrationAnchorIndex: Int
    ) : BlockPublishOutcome()

    data object RoomRetired : BlockPublishOutcome()

    data class Failed(val error: Throwable) : BlockPublishOutcome()
}
