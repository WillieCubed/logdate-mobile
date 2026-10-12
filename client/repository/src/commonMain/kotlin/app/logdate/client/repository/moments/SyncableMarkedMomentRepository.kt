package app.logdate.client.repository.moments

import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Remote mark application preserves local resolution and never clears local outbox work. */
interface SyncableMarkedMomentRepository : MarkedMomentRepository {
    /** Stores a durable deletion marker even before the live mark has been downloaded. */
    suspend fun applyDeletionFromSync(
        id: Uuid,
        deletedAt: Instant,
        serverVersion: Long,
    )

    /** Applies an explicit authenticated phone deletion without inventing cloud confirmation.
     * Replaces pending live work with DELETE; repeated delivery preserves an existing DELETE operation.
     */
    suspend fun applyDeletionFromPeer(
        id: Uuid,
        deletedAt: Instant,
    )

    /** Returns the merged millisecond-precision stored projection, or null when a tombstone or incompatible capture prevents applying it. */
    suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment?
}
