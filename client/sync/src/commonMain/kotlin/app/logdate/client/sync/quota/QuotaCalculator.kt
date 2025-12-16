package app.logdate.client.sync.quota

import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.CloudStorageCategoryUsage
import app.logdate.shared.model.CloudStorageQuota

/**
 * Calculates quota usage by examining actual cloud objects.
 * Used for full recalculation and validation of cached data.
 */
interface QuotaCalculator {
    
    /**
     * Calculates total quota usage across all object types.
     */
    suspend fun calculateTotalUsage(): CloudStorageQuota
    
    /**
     * Calculates usage for a specific object type.
     */
    suspend fun calculateCategoryUsage(objectType: CloudObjectType): CloudStorageCategoryUsage
}