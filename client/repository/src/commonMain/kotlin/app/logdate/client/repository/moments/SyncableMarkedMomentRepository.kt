package app.logdate.client.repository.moments

/** Remote mark application preserves local resolution and never clears local outbox work. */
interface SyncableMarkedMomentRepository : MarkedMomentRepository {
    /** Returns the merged stored mark, or null when a tombstone or incompatible capture prevents applying it. */
    suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment?
}
