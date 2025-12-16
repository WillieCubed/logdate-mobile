package app.logdate.client.data.quota

import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.QuotaUsage
import app.logdate.shared.model.QuotaCategoryUsage
import app.logdate.shared.model.QuotaContentType
import kotlinx.coroutines.delay

/**
 * Stub implementation of RemoteQuotaDataSource for development and testing.
 */
class StubRemoteQuotaDataSource : RemoteQuotaDataSource {
    
    override suspend fun getQuotaUsage(): QuotaResult<QuotaUsage> {
        // Simulate network delay
        delay(500)
        
        return QuotaResult.Success(
            QuotaUsage(
                totalBytes = 5L * 1024L * 1024L * 1024L, // 5 GB
                usedBytes = 2L * 1024L * 1024L * 1024L + 512L * 1024L * 1024L, // 2.5 GB
                categories = listOf(
                    QuotaCategoryUsage(
                        category = QuotaContentType.TEXT_NOTES,
                        sizeBytes = 50L * 1024L * 1024L, // 50 MB
                        objectCount = 1250
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.IMAGE_NOTES,
                        sizeBytes = 1L * 1024L * 1024L * 1024L + 200L * 1024L * 1024L, // 1.2 GB
                        objectCount = 480
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.VIDEO_NOTES,
                        sizeBytes = 800L * 1024L * 1024L, // 800 MB
                        objectCount = 15
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.VOICE_NOTES,
                        sizeBytes = 350L * 1024L * 1024L, // 350 MB
                        objectCount = 180
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.JOURNAL_DATA,
                        sizeBytes = 100L * 1024L * 1024L, // 100 MB
                        objectCount = 85
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.USER_PROFILE,
                        sizeBytes = 12L * 1024L * 1024L, // 12 MB
                        objectCount = 1
                    ),
                    QuotaCategoryUsage(
                        category = QuotaContentType.ATTACHMENTS,
                        sizeBytes = 24L * 1024L * 1024L, // 24 MB
                        objectCount = 8
                    )
                )
            )
        )
    }
    
    override suspend fun refreshQuotaUsage(): QuotaResult<QuotaUsage> {
        // For stub implementation, refresh is the same as get
        return getQuotaUsage()
    }
}