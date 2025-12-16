package app.logdate.client.sync.migration

import kotlin.uuid.Uuid

/**
 * Represents the state of a data migration.
 */
data class MigrationState(
    /**
     * Whether migration is currently in progress.
     */
    val inProgress: Boolean,
    
    /**
     * The old user ID being migrated from.
     */
    val oldId: Uuid,
    
    /**
     * The new user ID being migrated to.
     */
    val newId: Uuid,
    
    /**
     * Number of items processed so far.
     */
    val itemsProcessed: Int,
    
    /**
     * Total number of items to process.
     */
    val totalItems: Int,
    
    /**
     * ID of the last processed item, used for resuming migration.
     */
    val lastProcessedId: String? = null
) {
    /**
     * Creates a MigrationProgress object from this state.
     */
    fun toMigrationProgress(): MigrationProgress {
        return MigrationProgress(
            inProgress = inProgress,
            oldId = oldId,
            newId = newId,
            itemsProcessed = itemsProcessed,
            totalItems = totalItems,
            lastProcessedId = lastProcessedId
        )
    }
}