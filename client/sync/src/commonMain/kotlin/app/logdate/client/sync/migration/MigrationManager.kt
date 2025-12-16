package app.logdate.client.sync.migration

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Interface for managing data migrations.
 */
interface MigrationManager {
    /**
     * Syncs the cloud identity with the local identity.
     * If they differ, starts a migration process.
     *
     * @param cloudId The user ID from the cloud account
     * @return Flow of migration progress updates
     */
    suspend fun syncCloudIdentity(cloudId: Uuid): Flow<MigrationProgress>
    
    /**
     * Checks if a migration is currently in progress.
     *
     * @return True if a migration is in progress
     */
    suspend fun isMigrationInProgress(): Boolean
    
    /**
     * Resumes any interrupted migrations.
     *
     * @return Flow of migration progress updates, or null if no migration to resume
     */
    suspend fun resumeMigrationIfNeeded(): Flow<MigrationProgress>?
    
    /**
     * Observes the current migration progress if any is in progress.
     *
     * @return Flow of migration progress updates
     */
    fun observeMigrationProgress(): Flow<MigrationProgress>
    
    /**
     * Registers a content repository for migration operations.
     * Note: This method is not needed anymore since we're not using content repositories
     */
    @Deprecated("No longer needed")
    suspend fun registerRepository(repository: Any)
}