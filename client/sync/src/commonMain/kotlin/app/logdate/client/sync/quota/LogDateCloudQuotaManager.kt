package app.logdate.client.sync.quota

import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.CloudStorageCategoryUsage
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.QuotaContentType
import app.logdate.shared.model.QuotaUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.Clock

/**
 * LogDate Cloud implementation of quota management.
 * 
 * Uses server as source of truth for quota data, with local database as cache.
 * Applies incremental updates to cache when local objects change, but server sync
 * will override any local modifications with authoritative server data.
 */
class LogDateCloudQuotaManager(
    private val quotaCalculator: QuotaCalculator,
    private val remoteQuotaDataSource: RemoteQuotaDataSource,
) : CloudQuotaManager {

    private val _quotaFlow = MutableStateFlow<CloudStorageQuota?>(null)
    private var cachedQuota: CloudStorageQuota? = null
    private var lastServerSyncTime: kotlinx.datetime.Instant? = null
    
    override fun observeQuota(): Flow<CloudStorageQuota> = _quotaFlow.filterNotNull()
    
    override suspend fun getCurrentQuota(): CloudStorageQuota {
        // Return cached data if available and not stale (within 5 minutes)
        val now = Clock.System.now()
        cachedQuota?.let { cached ->
            val lastSync = lastServerSyncTime
            if (lastSync != null && (now - lastSync) < CACHE_DURATION) {
                _quotaFlow.value = cached
                return cached
            }
        }
        
        // Try to sync with server first, fall back to local calculation
        return try {
            syncWithServer()
        } catch (e: Exception) {
            // If server sync fails, use local calculation
            recalculateQuota()
        }
    }
    
    override suspend fun recordObjectCreation(objectType: CloudObjectType, bytes: Long) {
        updateCachedCategory(objectType, bytes)
        emitUpdatedQuota()
    }
    
    override suspend fun recordObjectDeletion(objectType: CloudObjectType, bytes: Long) {
        updateCachedCategory(objectType, -bytes)
        emitUpdatedQuota()
    }
    
    override suspend fun recordObjectUpdate(objectType: CloudObjectType, oldBytes: Long, newBytes: Long) {
        val deltaBytes = newBytes - oldBytes
        updateCachedCategory(objectType, deltaBytes)
        emitUpdatedQuota()
    }
    
    override suspend fun recalculateQuota(): CloudStorageQuota {
        val calculatedQuota = quotaCalculator.calculateTotalUsage()
        cachedQuota = calculatedQuota
        _quotaFlow.value = calculatedQuota
        return calculatedQuota
    }
    
    override suspend fun setQuotaLimit(totalBytes: Long) {
        val currentQuota = getCurrentQuota()
        val updatedQuota = currentQuota.copy(totalBytes = totalBytes)
        cachedQuota = updatedQuota
        _quotaFlow.value = updatedQuota
    }
    
    private fun updateCachedCategory(objectType: CloudObjectType, deltaBytes: Long) {
        val currentQuota = cachedQuota ?: return
        
        val updatedCategories = currentQuota.categories.map { category ->
            if (category.category == objectType) {
                category.copy(
                    sizeBytes = (category.sizeBytes + deltaBytes).coerceAtLeast(0),
                    objectCount = if (deltaBytes > 0) category.objectCount + 1 else maxOf(0, category.objectCount - 1)
                )
            } else {
                category
            }
        }
        
        val updatedQuota = currentQuota.copy(
            usedBytes = (currentQuota.usedBytes + deltaBytes).coerceAtLeast(0),
            categories = updatedCategories
        )
        
        cachedQuota = updatedQuota
    }
    
    private suspend fun emitUpdatedQuota() {
        cachedQuota?.let { quota ->
            _quotaFlow.value = quota
        }
    }
    
    override suspend fun syncWithServer(): CloudStorageQuota {
        return when (val result = remoteQuotaDataSource.getQuotaUsage()) {
            is QuotaResult.Success -> {
                val serverQuota = mapToCloudStorageQuota(result.data)
                cachedQuota = serverQuota
                lastServerSyncTime = Clock.System.now()
                _quotaFlow.value = serverQuota
                serverQuota
            }
            is QuotaResult.Error -> {
                throw Exception("Failed to sync with server: ${result.message}", result.throwable)
            }
        }
    }
    
    override suspend fun getLastServerSyncTime(): kotlinx.datetime.Instant? = lastServerSyncTime
    
    /**
     * Maps shared model QuotaUsage to sync layer CloudStorageQuota.
     */
    private fun mapToCloudStorageQuota(quotaUsage: QuotaUsage): CloudStorageQuota {
        val categories = quotaUsage.categories.map { category ->
            CloudStorageCategoryUsage(
                category = mapToCloudObjectType(category.category),
                sizeBytes = category.sizeBytes,
                objectCount = category.objectCount
            )
        }
        
        return CloudStorageQuota(
            totalBytes = quotaUsage.totalBytes,
            usedBytes = quotaUsage.usedBytes,
            categories = categories
        )
    }
    
    /**
     * Maps shared model QuotaContentType to shared model CloudObjectType.
     */
    private fun mapToCloudObjectType(contentType: QuotaContentType): CloudObjectType {
        return when (contentType) {
            QuotaContentType.TEXT_NOTES -> CloudObjectType.TEXT_NOTES
            QuotaContentType.IMAGE_NOTES -> CloudObjectType.IMAGE_NOTES
            QuotaContentType.VIDEO_NOTES -> CloudObjectType.VIDEO_NOTES
            QuotaContentType.VOICE_NOTES -> CloudObjectType.VOICE_NOTES
            QuotaContentType.JOURNAL_DATA -> CloudObjectType.JOURNAL_DATA
            QuotaContentType.USER_PROFILE -> CloudObjectType.USER_PROFILE
            QuotaContentType.ATTACHMENTS -> CloudObjectType.ATTACHMENTS
        }
    }
    
    companion object {
        private val CACHE_DURATION = kotlin.time.Duration.parse("PT5M") // 5 minutes
    }
}