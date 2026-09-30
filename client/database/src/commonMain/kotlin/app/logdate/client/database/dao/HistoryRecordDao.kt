package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.logdate.client.database.entities.HistoryCursorEntity
import app.logdate.client.database.entities.HistoryRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryRecordDao {
    @Query("SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin")
    abstract fun observe(
        owner: String,
        origin: String,
    ): Flow<List<HistoryRecordEntity>>

    @Query(
        "SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin " +
            "AND (observedAt IS NULL OR (observedAt >= :startMillis AND observedAt < :endMillis))",
    )
    abstract fun observeRange(
        owner: String,
        origin: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<HistoryRecordEntity>>

    @Query("SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin")
    abstract suspend fun records(
        owner: String,
        origin: String,
    ): List<HistoryRecordEntity>

    @Query("SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin AND id = :id")
    abstract suspend fun record(
        owner: String,
        origin: String,
        id: String,
    ): HistoryRecordEntity?

    @Query(
        "SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin AND deviceId = :device " +
            "AND recordType = 'observation' AND deleted = 0 " +
            "AND (observedAt < :before OR (observedAt = :before AND id < :beforeId)) " +
            "ORDER BY observedAt DESC, id DESC LIMIT :limit",
    )
    abstract suspend fun observationsBefore(
        owner: String,
        origin: String,
        device: String,
        before: Long,
        beforeId: String,
        limit: Int,
    ): List<HistoryRecordEntity>

    @Query(
        "SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin AND deviceId = :device " +
            "AND recordType = 'activity' AND deleted = 0 AND observedAt >= :start AND observedAt < :end",
    )
    abstract suspend fun activitiesBetween(
        owner: String,
        origin: String,
        device: String,
        start: Long,
        end: Long,
    ): List<HistoryRecordEntity>

    @Upsert
    abstract suspend fun upsert(record: HistoryRecordEntity)

    @Transaction
    open suspend fun putPreservingTombstone(incoming: HistoryRecordEntity) {
        val existing = record(incoming.ownerId, incoming.origin, incoming.id)
        if (existing?.deleted == true && !incoming.deleted) return
        upsert(incoming)
        purgeDeletedObservation(incoming)
    }

    @Transaction
    open suspend fun putBatchPreservingTombstones(records: List<HistoryRecordEntity>) {
        records.forEach { putPreservingTombstone(it) }
    }

    @Query(
        "DELETE FROM location_logs WHERE sample_id = :sampleId AND device_id = :deviceId " +
            "AND (user_id = :owner OR user_id = 'default_user')",
    )
    protected abstract suspend fun deleteRawObservation(
        owner: String,
        deviceId: String,
        sampleId: String,
    )

    private suspend fun purgeDeletedObservation(record: HistoryRecordEntity) {
        if (record.deleted && record.recordType == "observation" && record.id.startsWith("observation:")) {
            deleteRawObservation(record.ownerId, record.deviceId, record.id.removePrefix("observation:"))
        }
    }

    @Upsert
    abstract suspend fun upsertCursor(cursor: HistoryCursorEntity)

    @Query("SELECT cursor FROM history_cursors WHERE ownerId = :owner AND origin = :origin")
    abstract suspend fun cursor(
        owner: String,
        origin: String,
    ): Long?

    @Query("SELECT * FROM history_records WHERE ownerId = :owner AND origin = :origin AND dirty = 1 ORDER BY id LIMIT :limit")
    abstract suspend fun pending(
        owner: String,
        origin: String,
        limit: Int,
    ): List<HistoryRecordEntity>

    @Query(
        "UPDATE history_records SET dirty = " +
            "CASE WHEN deviceVersion = :version AND :serverVersion >= serverVersion THEN 0 ELSE dirty END, " +
            "serverVersion = MAX(serverVersion, :serverVersion) " +
            "WHERE ownerId = :owner AND origin = :origin AND id = :id",
    )
    abstract suspend fun acknowledge(
        owner: String,
        origin: String,
        id: String,
        version: Long,
        serverVersion: Long,
    )

    @Transaction
    open suspend fun applyPage(
        owner: String,
        origin: String,
        records: List<HistoryRecordEntity>,
        cursor: Long,
    ) {
        records.forEach { incoming ->
            val local = record(owner, origin, incoming.id)
            if (incoming.serverVersion >= (local?.serverVersion ?: 0)) {
                if (local?.deleted == true) {
                    upsert(local.copy(serverVersion = incoming.serverVersion, dirty = !incoming.deleted))
                } else if (incoming.deleted || local?.dirty != true) {
                    upsert(incoming)
                } else {
                    upsert(local.copy(serverVersion = incoming.serverVersion))
                }
            }
            record(owner, origin, incoming.id)?.let { purgeDeletedObservation(it) }
        }
        upsertCursor(HistoryCursorEntity(owner, origin, maxOf(cursor(owner, origin) ?: 0, cursor)))
    }
}
