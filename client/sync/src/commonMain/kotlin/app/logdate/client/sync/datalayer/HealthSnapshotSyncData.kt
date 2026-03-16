package app.logdate.client.sync.datalayer

import app.logdate.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Serializable transport representation of a health snapshot for Data Layer sync.
 *
 * This is a DTO mirroring [app.logdate.client.database.entities.HealthSnapshotEntity]
 * fields without Room annotations. Used for watch-to-phone health data transfer.
 */
@Serializable
data class HealthSnapshotSyncData(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    @Serializable(with = UuidSerializer::class)
    val noteId: Uuid? = null,
    val heartRateBpm: Int? = null,
    val heartRateVariabilityMs: Float? = null,
    val stepCount: Int? = null,
    val stressLevel: Float? = null,
    val cumulativeCalories: Float? = null,
    val timestamp: Instant,
    val source: String,
)
