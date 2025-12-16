package app.logdate.client.sync.migration

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

/**
 * Default implementation of MigrationManager.
 */
class DefaultMigrationManager(
    private val migrationStorage: MigrationStorage,
    private val userIdentityProvider: IdentitySyncProvider,
//    private val coroutineScope: CoroutineScope,
) : MigrationManager {

    private val progressFlow = MutableStateFlow<MigrationProgress?>(null)

    override suspend fun syncCloudIdentity(cloudId: Uuid): Flow<MigrationProgress> = flow {
        // Get current ID
        val currentId = userIdentityProvider.getUserId().first()
        
        // If IDs are the same, no migration needed
        if (currentId == cloudId) {
            emit(MigrationProgress(
                inProgress = false,
                oldId = currentId,
                newId = cloudId,
                itemsProcessed = 0,
                totalItems = 0,
                lastProcessedId = null
            ))
            return@flow
        }
        
        // Simplified migration with no actual repositories
        // Start migration
        val state = MigrationState(
            inProgress = true,
            oldId = currentId,
            newId = cloudId,
            itemsProcessed = 0,
            totalItems = 1,
            lastProcessedId = null
        )
        
        // Store migration state
        migrationStorage.storeMigrationState(state)
        
        // Emit initial progress
        val progress = state.toMigrationProgress()
        progressFlow.value = progress
        emit(progress)
        
        // Simulate simple migration with one step
        val finalProgress = MigrationProgress(
            inProgress = false,
            oldId = currentId,
            newId = cloudId,
            itemsProcessed = 1,
            totalItems = 1
        )
        progressFlow.value = finalProgress
        emit(finalProgress)
        
        // Clear migration state
        migrationStorage.clearMigrationState()
    }
    
    override suspend fun isMigrationInProgress(): Boolean {
        val state = migrationStorage.retrieveMigrationState()
        return state?.inProgress == true
    }
    
    override suspend fun resumeMigrationIfNeeded(): Flow<MigrationProgress>? {
        val state = migrationStorage.retrieveMigrationState() ?: return null
        
        if (state.inProgress) {
            return syncCloudIdentity(state.newId)
        }
        
        return null
    }
    
    override fun observeMigrationProgress(): Flow<MigrationProgress> = flow {
        // Emit current value if available
        progressFlow.value?.let { emit(it) }
        
        // Collect and emit future values
        progressFlow.collect { progress ->
            progress?.let { emit(it) }
        }
    }
    
    @Deprecated("No longer needed")
    override suspend fun registerRepository(repository: Any) {
        // No-op: Repositories are no longer needed
    }
}