@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.logdate.client.data.rewind

import app.logdate.client.database.dao.rewind.CachedRewindDao
import app.logdate.client.database.entities.rewind.RewindEntity
import app.logdate.client.database.entities.rewind.RewindImageContentEntity
import app.logdate.client.database.entities.rewind.RewindTextContentEntity
import app.logdate.client.database.entities.rewind.RewindVideoContentEntity
import app.logdate.shared.model.ActivityType
import app.logdate.shared.model.LocationSummary
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies that [OfflineFirstRewindRepository] correctly serializes and deserializes
 * [RewindMetadata] when saving and retrieving rewinds.
 */
class OfflineFirstRewindRepositoryMetadataTest {
    @Test
    fun `metadata round-trips through save and retrieve`() =
        runTest {
            val dao = FakeCachedRewindDao()
            val repository = OfflineFirstRewindRepository(dao, UnconfinedTestDispatcher(testScheduler))

            val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
            val metadata =
                RewindMetadata(
                    detectedActivities = listOf(ActivityType.TRAVEL, ActivityType.SOCIAL),
                    locationSummary =
                        LocationSummary(
                            distinctLocations = 4,
                            newPlaces = 1,
                            primaryLocation = "Tokyo",
                        ),
                    milestones = listOf("Found the hidden ramen shop"),
                    peopleHighlighted = listOf("Akira", "Yuki"),
                )
            val rewind =
                Rewind(
                    uid = Uuid.random(),
                    startDate = now,
                    endDate = now,
                    generationDate = now,
                    label = "2025#01",
                    title = "A week in Tokyo",
                    content = emptyList(),
                    metadata = metadata,
                )

            repository.saveRewind(rewind)
            val retrieved = repository.getRewind(rewind.uid).first()

            assertNotNull(retrieved.metadata)
            assertEquals(listOf(ActivityType.TRAVEL, ActivityType.SOCIAL), retrieved.metadata!!.detectedActivities)
            assertEquals(4, retrieved.metadata!!.locationSummary?.distinctLocations)
            assertEquals(1, retrieved.metadata!!.locationSummary?.newPlaces)
            assertEquals("Tokyo", retrieved.metadata!!.locationSummary?.primaryLocation)
            assertEquals(listOf("Found the hidden ramen shop"), retrieved.metadata!!.milestones)
            assertEquals(listOf("Akira", "Yuki"), retrieved.metadata!!.peopleHighlighted)
        }

    @Test
    fun `null metadata round-trips correctly`() =
        runTest {
            val dao = FakeCachedRewindDao()
            val repository = OfflineFirstRewindRepository(dao, UnconfinedTestDispatcher(testScheduler))

            val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
            val rewind =
                Rewind(
                    uid = Uuid.random(),
                    startDate = now,
                    endDate = now,
                    generationDate = now,
                    label = "2025#02",
                    title = "Quiet week",
                    content = emptyList(),
                    metadata = null,
                )

            repository.saveRewind(rewind)
            val retrieved = repository.getRewind(rewind.uid).first()

            assertNull(retrieved.metadata)
        }

    @Test
    fun `metadata with null location summary round-trips`() =
        runTest {
            val dao = FakeCachedRewindDao()
            val repository = OfflineFirstRewindRepository(dao, UnconfinedTestDispatcher(testScheduler))

            val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
            val metadata =
                RewindMetadata(
                    detectedActivities = listOf(ActivityType.QUIET),
                    locationSummary = null,
                    milestones = emptyList(),
                    peopleHighlighted = emptyList(),
                )
            val rewind =
                Rewind(
                    uid = Uuid.random(),
                    startDate = now,
                    endDate = now,
                    generationDate = now,
                    label = "2025#03",
                    title = "Stayed in",
                    content = emptyList(),
                    metadata = metadata,
                )

            repository.saveRewind(rewind)
            val retrieved = repository.getRewind(rewind.uid).first()

            assertNotNull(retrieved.metadata)
            assertNull(retrieved.metadata!!.locationSummary)
            assertEquals(listOf(ActivityType.QUIET), retrieved.metadata!!.detectedActivities)
        }
}

private class FakeCachedRewindDao : CachedRewindDao {
    private val rewinds = MutableStateFlow<List<RewindEntity>>(emptyList())
    private val textContent = mutableListOf<RewindTextContentEntity>()
    private val imageContent = mutableListOf<RewindImageContentEntity>()
    private val videoContent = mutableListOf<RewindVideoContentEntity>()

    override fun getAllRewinds(): Flow<List<RewindEntity>> = rewinds

    override fun getRewindById(uid: Uuid): Flow<RewindEntity> = rewinds.map { list -> list.first { it.uid == uid } }

    override fun getRewindForPeriod(
        start: Instant,
        end: Instant,
    ): Flow<RewindEntity?> = rewinds.map { list -> list.firstOrNull { it.startDate == start && it.endDate == end } }

    override fun getRewindsContainedIn(
        rangeStart: Instant,
        rangeEnd: Instant,
    ): Flow<List<RewindEntity>> =
        rewinds.map { list ->
            list
                .filter { it.startDate >= rangeStart && it.endDate <= rangeEnd }
                .sortedBy { it.startDate }
        }

    override suspend fun rewindExistsForPeriod(
        start: Instant,
        end: Instant,
    ): Boolean = rewinds.value.any { it.startDate == start && it.endDate == end }

    override suspend fun insertRewind(rewind: RewindEntity) {
        rewinds.value = rewinds.value.filterNot { it.uid == rewind.uid } + rewind
    }

    override suspend fun getTextContentForRewind(rewindId: Uuid): List<RewindTextContentEntity> =
        textContent.filter { it.rewindId == rewindId }

    override suspend fun getImageContentForRewind(rewindId: Uuid): List<RewindImageContentEntity> =
        imageContent.filter { it.rewindId == rewindId }

    override suspend fun getVideoContentForRewind(rewindId: Uuid): List<RewindVideoContentEntity> =
        videoContent.filter { it.rewindId == rewindId }

    override suspend fun insertTextContent(content: List<RewindTextContentEntity>) {
        textContent.addAll(content)
    }

    override suspend fun insertImageContent(content: List<RewindImageContentEntity>) {
        imageContent.addAll(content)
    }

    override suspend fun insertVideoContent(content: List<RewindVideoContentEntity>) {
        videoContent.addAll(content)
    }

    override suspend fun markAsFirstViewed(
        uid: Uuid,
        now: Instant,
    ): Int {
        val existing = rewinds.value.firstOrNull { it.uid == uid } ?: return 0
        if (existing.isViewed) return 0
        rewinds.value =
            rewinds.value.map { rewind ->
                if (rewind.uid == uid) {
                    rewind.copy(
                        isViewed = true,
                        firstViewedAt = now,
                        viewCount = 1,
                    )
                } else {
                    rewind
                }
            }
        return 1
    }

    override suspend fun incrementViewCount(uid: Uuid) {
        rewinds.value =
            rewinds.value.map { rewind ->
                if (rewind.uid == uid && rewind.isViewed) {
                    rewind.copy(viewCount = rewind.viewCount + 1)
                } else {
                    rewind
                }
            }
    }

    override suspend fun deleteRewind(uid: Uuid) {
        rewinds.value = rewinds.value.filterNot { it.uid == uid }
        textContent.removeAll { it.rewindId == uid }
        imageContent.removeAll { it.rewindId == uid }
        videoContent.removeAll { it.rewindId == uid }
    }
}
