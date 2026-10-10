package app.logdate.client.database.entities.sync

import androidx.room.Entity

/**
 * Where one record stands on its way between this device and its peer, so a delivery decision
 * survives the process being killed.
 *
 * @property peerScope The peer this row is about, such as the paired phone's link.
 * @property direction Which way the record travels, such as `TO_PHONE`.
 * @property contentVersion The record version this row describes; a newer edit starts over.
 * @property lastCloudAttemptVersion The version last uploaded straight to the cloud, so the same version
 *   is never sent there twice.
 * @property holdReason Why the record is waiting, when it is.
 */
@Entity(
    tableName = "peer_delivery",
    primaryKeys = ["peerScope", "direction", "entityType", "entityId"],
)
data class PeerDeliveryEntity(
    val peerScope: String,
    val direction: String,
    val entityType: String,
    val entityId: String,
    val contentVersion: Long,
    val state: String,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextAttemptAt: Long? = null,
    val lastCloudAttemptVersion: Long? = null,
    val stateChangedAt: Long,
    val holdReason: String? = null,
)
