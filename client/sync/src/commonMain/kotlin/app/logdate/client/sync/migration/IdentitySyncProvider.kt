package app.logdate.client.sync.migration

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Interface for providing and managing user identity.
 * This is a simplified version of UserIdentityManager that
 * only exposes the core identity functions needed by other modules.
 */
interface IdentitySyncProvider {
    /**
     * Gets the current user ID.
     * 
     * @return Flow emitting the current user ID
     */
    fun getUserId(): Flow<Uuid>
    
    /**
     * Sets the current user ID and migrates existing content.
     * 
     * @param userId The user ID to set
     * @return Flow of migration progress updates
     */
    suspend fun syncIdentity(userId: Uuid): Flow<MigrationProgress>
}