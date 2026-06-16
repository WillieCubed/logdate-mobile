package app.logdate.client.sync.quota

import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.CloudStorageCategoryUsage
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.QuotaCategoryUsage
import app.logdate.shared.model.QuotaContentType
import app.logdate.shared.model.QuotaUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogDateCloudQuotaManagerTest {
    @Test
    fun `syncWithServer publishes authoritative server quota to observers`() =
        runTest {
            val remote =
                ScriptedRemoteQuotaDataSource(
                    QuotaUsage(
                        totalBytes = 1_000,
                        usedBytes = 250,
                        categories =
                            listOf(
                                QuotaCategoryUsage(
                                    category = QuotaContentType.IMAGE_NOTES,
                                    sizeBytes = 250,
                                    objectCount = 1,
                                ),
                            ),
                    ),
                )
            val manager = LogDateCloudQuotaManager(StaticQuotaCalculator(), remote)

            manager.syncWithServer()
            val observed = manager.observeQuota().first()

            assertEquals(1_000, observed.totalBytes)
            assertEquals(250, observed.usedBytes)
            assertEquals(CloudObjectType.IMAGE_NOTES, observed.categories.single().category)
            assertEquals(1, remote.getQuotaUsageCalls)
        }

    @Test
    fun `recordObjectCreation emits quota delta without waiting for another server fetch`() =
        runTest {
            val remote =
                ScriptedRemoteQuotaDataSource(
                    QuotaUsage(
                        totalBytes = 1_000,
                        usedBytes = 100,
                        categories =
                            listOf(
                                QuotaCategoryUsage(
                                    category = QuotaContentType.TEXT_NOTES,
                                    sizeBytes = 100,
                                    objectCount = 1,
                                ),
                            ),
                    ),
                )
            val manager = LogDateCloudQuotaManager(StaticQuotaCalculator(), remote)
            manager.syncWithServer()

            manager.recordObjectCreation(CloudObjectType.TEXT_NOTES, 50)
            val observed = manager.observeQuota().first()

            assertEquals(150, observed.usedBytes)
            assertEquals(150, observed.categories.single().sizeBytes)
            assertEquals(2, observed.categories.single().objectCount)
            assertEquals(1, remote.getQuotaUsageCalls)
        }

    private class StaticQuotaCalculator : QuotaCalculator {
        private val quota =
            CloudStorageQuota(
                totalBytes = 1_000,
                usedBytes = 0,
                categories = emptyList(),
            )

        override suspend fun calculateTotalUsage(): CloudStorageQuota = quota

        override suspend fun calculateCategoryUsage(objectType: CloudObjectType): CloudStorageCategoryUsage =
            CloudStorageCategoryUsage(
                category = objectType,
                sizeBytes = 0,
                objectCount = 0,
            )
    }

    private class ScriptedRemoteQuotaDataSource(
        private val quota: QuotaUsage,
    ) : RemoteQuotaDataSource {
        var getQuotaUsageCalls = 0
            private set

        override suspend fun getQuotaUsage(): QuotaResult<QuotaUsage> {
            getQuotaUsageCalls += 1
            return QuotaResult.Success(quota)
        }

        override suspend fun refreshQuotaUsage(): QuotaResult<QuotaUsage> = getQuotaUsage()
    }
}
