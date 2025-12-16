package app.logdate.client.domain.quota

import app.logdate.client.sync.quota.CloudQuotaManager
import app.logdate.client.sync.quota.CloudStorageQuota
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ObserveCloudQuotaUseCaseTest {

    private class MockCloudQuotaManager : CloudQuotaManager {
        var observeQuotaResult: Flow<CloudStorageQuota> = flowOf(CloudStorageQuota.Unknown)

        override fun observeQuota(): Flow<CloudStorageQuota> = observeQuotaResult

        override suspend fun refreshQuota(): CloudStorageQuota = CloudStorageQuota.Unknown
        override suspend fun getCurrentQuota(): CloudStorageQuota = CloudStorageQuota.Unknown
    }

    @Test
    fun `invoke should return flow of quota from quota manager`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val expectedQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000L, // 1GB
            usedBytes = 250000000L,   // 250MB
            availableBytes = 750000000L // 750MB
        )
        mockQuotaManager.observeQuotaResult = flowOf(expectedQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(expectedQuota, emittedValues.first())
    }

    @Test
    fun `invoke should return unknown quota when quota manager returns unknown`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        mockQuotaManager.observeQuotaResult = flowOf(CloudStorageQuota.Unknown)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(CloudStorageQuota.Unknown, emittedValues.first())
    }

    @Test
    fun `invoke should return quota at capacity when storage is full`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val fullQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000L,  // 1GB
            usedBytes = 1000000000L,   // 1GB (full)
            availableBytes = 0L        // 0 bytes available
        )
        mockQuotaManager.observeQuotaResult = flowOf(fullQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(fullQuota, emittedValues.first())
    }

    @Test
    fun `invoke should return quota with no usage when storage is empty`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val emptyQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000L,  // 1GB
            usedBytes = 0L,            // 0 bytes used
            availableBytes = 1000000000L // 1GB available
        )
        mockQuotaManager.observeQuotaResult = flowOf(emptyQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(emptyQuota, emittedValues.first())
    }

    @Test
    fun `invoke should handle multiple quota emissions from manager`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val firstQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000L,
            usedBytes = 100000000L,   // 100MB
            availableBytes = 900000000L
        )
        val secondQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000L,
            usedBytes = 200000000L,   // 200MB (increased usage)
            availableBytes = 800000000L
        )
        mockQuotaManager.observeQuotaResult = flowOf(firstQuota, secondQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(2, emittedValues.size)
        assertEquals(firstQuota, emittedValues[0])
        assertEquals(secondQuota, emittedValues[1])
    }

    @Test
    fun `invoke should handle quota transitions from unknown to available`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val availableQuota = CloudStorageQuota.Available(
            totalBytes = 2000000000L,  // 2GB
            usedBytes = 500000000L,    // 500MB
            availableBytes = 1500000000L // 1.5GB
        )
        mockQuotaManager.observeQuotaResult = flowOf(CloudStorageQuota.Unknown, availableQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(2, emittedValues.size)
        assertEquals(CloudStorageQuota.Unknown, emittedValues[0])
        assertEquals(availableQuota, emittedValues[1])
    }

    @Test
    fun `invoke should handle large quota values`() = runTest {
        // Given
        val mockQuotaManager = MockCloudQuotaManager()
        val largeQuota = CloudStorageQuota.Available(
            totalBytes = 1000000000000L,  // 1TB
            usedBytes = 250000000000L,    // 250GB
            availableBytes = 750000000000L // 750GB
        )
        mockQuotaManager.observeQuotaResult = flowOf(largeQuota)
        val useCase = ObserveCloudQuotaUseCase(mockQuotaManager)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(largeQuota, emittedValues.first())
    }
}