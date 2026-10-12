package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID

/**
 * A marked moment as the server stores it. [payload] is one client-encrypted `LDSE2:` value containing
 * everything but the times; the server never reads it. [schema] versions that payload.
 */
data class LogDateMoment(
    val id: String,
    val capturedAt: Long,
    val lastUpdated: Long,
    val version: Long,
    val deviceId: DeviceId,
    val payload: String,
    val schema: Int,
)

data class LogDateMomentDeletion(
    val id: String,
    val deletedAt: Long,
    val serverVersion: Long,
)

sealed interface MomentUpdateResult {
    data class Updated(
        val moment: LogDateMoment,
    ) : MomentUpdateResult

    data object Conflict : MomentUpdateResult

    data object Missing : MomentUpdateResult
}

/**
 * Marked moments live in their own collection: the entry and journal feeds never include them, so clients
 * that do not know moments never download one.
 */
interface LogDateMomentsRepository {
    suspend fun momentState(
        userId: UUID,
        id: String,
    ): LogDateRecordState

    /** Checks the version and stores the transformed value within one account mutation boundary. */
    suspend fun updateMomentIfVersion(
        userId: UUID,
        id: String,
        expectedVersion: Long?,
        transform: suspend (LogDateMoment) -> LogDateMoment,
    ): MomentUpdateResult

    suspend fun upsertMoment(
        userId: UUID,
        moment: LogDateMoment,
    ): LogDateMoment

    /** See [LogDateCollectionsRepository.createEntryIfAbsent]. */
    suspend fun createMomentIfAbsent(
        userId: UUID,
        moment: LogDateMoment,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateMoment>

    suspend fun getMoment(
        userId: UUID,
        id: String,
    ): LogDateMoment?

    suspend fun deleteMoment(
        userId: UUID,
        id: String,
        deletedAt: Long,
    )

    suspend fun momentChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateMoment, LogDateMomentDeletion>
}
