package app.logdate.client.device.identity

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Represents the state of an identity migration stored on-device.
 */
@Serializable
data class MigrationState(
    val fromUserId: Uuid,
    val toUserId: Uuid,
    val progress: Float,
    val timestamp: Long,
)
