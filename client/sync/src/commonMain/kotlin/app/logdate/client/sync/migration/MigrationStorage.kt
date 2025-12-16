package app.logdate.client.sync.migration

/**
 * Interface for securely storing migration-related information.
 */
interface MigrationStorage {
    /**
     * Stores migration state information.
     * 
     * @param state The migration state to store
     */
    suspend fun storeMigrationState(state: MigrationState)
    
    /**
     * Retrieves the stored migration state.
     * 
     * @return The stored migration state, or null if none exists
     */
    suspend fun retrieveMigrationState(): MigrationState?
    
    /**
     * Clears the stored migration state.
     */
    suspend fun clearMigrationState()
}