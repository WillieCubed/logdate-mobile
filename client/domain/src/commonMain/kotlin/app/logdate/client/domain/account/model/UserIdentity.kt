package app.logdate.client.domain.account.model

import kotlin.uuid.Uuid

/**
 * Represents a user's identity in the LogDate system.
 * 
 * This identity is used across all devices and persists regardless of cloud account status.
 */
data class UserIdentity(
    val userId: Uuid,
    val isCloudLinked: Boolean = false,
    val cloudAccountId: String? = null
)

/**
 * Represents the state of user identity migration.
 * 
 * Used when migrating from a local-only identity to a cloud-linked identity.
 */
data class IdentityMigrationState(
    val inProgress: Boolean,
    val oldUserId: Uuid?,
    val newUserId: Uuid?,
    val itemsProcessed: Int,
    val totalItems: Int,
    val lastProcessedId: String?
)

/**
 * Progress information for identity migration.
 */
data class MigrationProgress(
    val inProgress: Boolean,
    val itemsProcessed: Int,
    val totalItems: Int,
    val percentComplete: Float = if (totalItems > 0) itemsProcessed.toFloat() / totalItems else 0f
)