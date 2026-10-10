package app.logdate.client.repository.moments

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Where a mark is in its life. Merges take the highest: OPEN, then DISMISSED, then FILLED. */
enum class MarkState {
    OPEN,
    DISMISSED,
    FILLED,
}

/** Where a mark was captured, as precisely as the source knew it. */
data class MarkLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

/**
 * A moment someone marked on the watch to fill in later.
 *
 * Its own record rather than a note: it is left out of moment inference, Rewind, widgets and the
 * library, and appears in the timeline as a mark to finish.
 *
 * @property resolvedNoteIds The notes that filled this mark in.
 * @property originDeviceId The device that captured the mark.
 */
data class MarkedMoment(
    val id: Uuid,
    val capturedAt: Instant,
    val timeZoneId: String,
    val location: MarkLocation?,
    val heartRateBpm: Int?,
    val state: MarkState,
    val caption: String,
    val resolvedNoteIds: Set<Uuid>,
    val originDeviceId: String,
    val lastUpdated: Instant,
    val syncVersion: Long,
)

interface MarkedMomentRepository {
    /** Marks waiting to be filled in, newest first; deleted marks are left out. */
    fun observeOpenMarks(): Flow<List<MarkedMoment>>

    /** The mark with [id], or null when there is none or it was deleted. */
    suspend fun getMark(id: Uuid): MarkedMoment?

    /**
     * Records [location] for a mark captured without one. False when the mark already has a place or
     * no longer exists, so a place set elsewhere is never overwritten.
     */
    suspend fun fillLocationIfMissing(
        id: Uuid,
        location: MarkLocation,
        updatedAt: Instant,
    ): Boolean
}
