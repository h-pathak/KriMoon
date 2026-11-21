package com.example.cloud

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

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
    val blockHash: String = "",
    val encryptedPacket: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
