package app.logdate.client.database.entities.sync

import androidx.room.Entity
import androidx.room.Index

/**
 * The last version of one personal-space record this device wrote or applied, for one account on one
 * server. A write sends [cid] back as its swap condition, so it only replaces what this device last
 * saw; a deletion in the change feed names only [sealedRkey], and this row says which record it was.
 *
 * @property collection The inner record's type, such as `app.logdate.journal.journal`.
 * @property innerRkey The inner record's own key.
 * @property sealedRkey The opaque key the record is stored under in the space.
 * @property cid The content identifier of the stored envelope.
 * @property seq The position of that write in the space's change log.
 */
@Entity(
    tableName = "sync_space_records",
    primaryKeys = ["ownerId", "serverOrigin", "collection", "innerRkey"],
    indices = [Index(value = ["ownerId", "serverOrigin", "sealedRkey"], unique = true)],
)
data class SpaceRecordStateEntity(
    val ownerId: String,
    val serverOrigin: String,
    val collection: String,
    val innerRkey: String,
    val sealedRkey: String,
    val cid: String,
    val seq: Long,
)
