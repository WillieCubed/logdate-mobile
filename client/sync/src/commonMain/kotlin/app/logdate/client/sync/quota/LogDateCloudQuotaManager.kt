package app.logdate.client.sync.quota

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.CloudStorageCategoryUsage
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.QuotaContentType
import app.logdate.shared.model.QuotaUsage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * LogDate Cloud implementation of quota management.
 *
 * Uses server as source of truth for quota data, with local database as cache.
 * Applies incremental updates to cache when local objects change, but server sync
 * will override any local modifications with authoritative server data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogDateCloudQuotaManager(
    private val quotaCalculator: QuotaCalculator,
    private val remoteQuotaDataSource: RemoteQuotaDataSource,
    private val sessionStorage: SessionStorage? = null,
) : CloudQuotaManager {
    private val quotaStateFlow = MutableStateFlow<CloudStorageQuota?>(null)
    private var cachedQuota: CloudStorageQuota? = null
    private var cachedAccountId: String? = null
    private var lastServerSyncTime: kotlin.time.Instant? = null
    private var hasAuthoritativeQuota = false

    override fun observeQuota(): Flow<CloudStorageQuota> {
        val session = sessionStorage ?: return quotaStateFlow.filterNotNull().filter { hasAuthoritativeQuota }
        return session
            .getSessionFlow()
            .flatMapLatest {
                flow {
                    // Re-fetch whenever the canonical Cloud identity changes. The previous
                    // account's stream is cancelled before this request starts. Local fallback
                    // remains available to callers of getCurrentQuota, but is not presented as
                    // authoritative Cloud usage.
                    runCatching { syncWithServer() }.getOrNull()?.let { emit(it) }
                    emitAll(quotaStateFlow.filterNotNull().filter { hasAuthoritativeQuota })
                }
            }.distinctUntilChanged()
    }

    override suspend fun getCurrentQuota(): CloudStorageQuota {
        invalidateCacheIfAccountChanged()
        // Return cached data if available and not stale (within 5 minutes)
        val now = Clock.System.now()
        cachedQuota?.let { cached ->
            val lastSync = lastServerSyncTime
            if (lastSync != null && (now - lastSync) < CACHE_DURATION) {
                quotaStateFlow.value = cached
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

    override suspend fun recordObjectCreation(
        objectType: CloudObjectType,
        bytes: Long,
    ) {
        invalidateCacheIfAccountChanged()
        updateCachedCategory(objectType, bytes)
        emitUpdatedQuota()
    }

    override suspend fun recordObjectDeletion(
        objectType: CloudObjectType,
        bytes: Long,
    ) {
        invalidateCacheIfAccountChanged()
        updateCachedCategory(objectType, -bytes)
        emitUpdatedQuota()
    }

    override suspend fun recordObjectUpdate(
        objectType: CloudObjectType,
        oldBytes: Long,
        newBytes: Long,
    ) {
        invalidateCacheIfAccountChanged()
        val deltaBytes = newBytes - oldBytes
        updateCachedCategory(objectType, deltaBytes)
        emitUpdatedQuota()
    }

    override suspend fun recalculateQuota(): CloudStorageQuota {
        val calculatedQuota = quotaCalculator.calculateTotalUsage()
        cachedQuota = calculatedQuota
        cachedAccountId = currentAccountId()
        lastServerSyncTime = null
        hasAuthoritativeQuota = false
        quotaStateFlow.value = calculatedQuota
        return calculatedQuota
    }

    override suspend fun setQuotaLimit(totalBytes: Long) {
        val currentQuota = getCurrentQuota()
        val updatedQuota = currentQuota.copy(totalBytes = totalBytes)
        cachedQuota = updatedQuota
        quotaStateFlow.value = updatedQuota
    }

    private fun updateCachedCategory(
        objectType: CloudObjectType,
        deltaBytes: Long,
    ) {
        val currentQuota = cachedQuota ?: return

        val updatedCategories =
            currentQuota.categories.map { category ->
                if (category.category == objectType) {
                    category.copy(
                        sizeBytes = (category.sizeBytes + deltaBytes).coerceAtLeast(0),
                        objectCount = if (deltaBytes > 0) category.objectCount + 1 else maxOf(0, category.objectCount - 1),
                    )
                } else {
                    category
                }
            }

        val updatedQuota =
            currentQuota.copy(
                usedBytes = (currentQuota.usedBytes + deltaBytes).coerceAtLeast(0),
                categories = updatedCategories,
            )

        cachedQuota = updatedQuota
    }

    private suspend fun emitUpdatedQuota() {
        cachedQuota?.let { quota ->
            quotaStateFlow.value = quota
        }
    }

    override suspend fun syncWithServer(): CloudStorageQuota =
        when (val result = remoteQuotaDataSource.getQuotaUsage()) {
            is QuotaResult.Success -> {
                val serverQuota = mapToCloudStorageQuota(result.data)
                cachedQuota = serverQuota
                cachedAccountId = currentAccountId()
                lastServerSyncTime = Clock.System.now()
                hasAuthoritativeQuota = true
                quotaStateFlow.value = serverQuota
                serverQuota
            }
            is QuotaResult.Error -> {
                throw Exception("Failed to sync with server: ${result.message}", result.throwable)
            }
        }

    /**
     * A quota cache belongs to one Cloud identity. Session storage is optional for backwards
     * compatibility with JVM-only callers, but Android/iOS production wiring always supplies it.
     * Clearing on account changes prevents a signed-out user or a newly signed-in user from
     * seeing the previous identity's usage while the first server request is in flight.
     */
    private fun invalidateCacheIfAccountChanged() {
        val accountId = currentAccountId()
        if (cachedAccountId == accountId) return
        cachedQuota = null
        cachedAccountId = accountId
        lastServerSyncTime = null
        hasAuthoritativeQuota = false
        quotaStateFlow.value = null
    }

    private fun currentAccountId(): String? = sessionStorage?.getSession()?.accountId

    override suspend fun getLastServerSyncTime(): kotlin.time.Instant? = lastServerSyncTime

    /**
     * Maps shared model QuotaUsage to sync layer CloudStorageQuota.
     */
    private fun mapToCloudStorageQuota(quotaUsage: QuotaUsage): CloudStorageQuota {
        val categories =
            quotaUsage.categories.map { category ->
                CloudStorageCategoryUsage(
                    category = mapToCloudObjectType(category.category),
                    sizeBytes = category.sizeBytes,
                    objectCount = category.objectCount,
                )
            }

        return CloudStorageQuota(
            totalBytes = quotaUsage.totalBytes,
            usedBytes = quotaUsage.usedBytes,
            categories = categories,
        )
    }

    /**
     * Maps shared model QuotaContentType to shared model CloudObjectType.
     */
    private fun mapToCloudObjectType(contentType: QuotaContentType): CloudObjectType =
        when (contentType) {
            QuotaContentType.TEXT_NOTES -> CloudObjectType.TEXT_NOTES
            QuotaContentType.IMAGE_NOTES -> CloudObjectType.IMAGE_NOTES
            QuotaContentType.VIDEO_NOTES -> CloudObjectType.VIDEO_NOTES
            QuotaContentType.VOICE_NOTES -> CloudObjectType.VOICE_NOTES
            QuotaContentType.JOURNAL_DATA -> CloudObjectType.JOURNAL_DATA
            QuotaContentType.USER_PROFILE -> CloudObjectType.USER_PROFILE
            QuotaContentType.ATTACHMENTS -> CloudObjectType.ATTACHMENTS
        }

    companion object {
        private val CACHE_DURATION = kotlin.time.Duration.parse("PT5M") // 5 minutes
    }
}
