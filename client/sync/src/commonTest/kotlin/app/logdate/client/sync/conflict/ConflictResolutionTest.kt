package app.logdate.client.sync.conflict

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for conflict resolution strategies.
 */
class ConflictResolutionTest {

    private val resolver = LastWriteWinsResolver<TestEntity>()

    data class TestEntity(
        val id: String,
        val content: String
    )

    @Test
    fun `remote wins when remote timestamp is newer`() {
        val local = TestEntity("1", "Local Content")
        val remote = TestEntity("1", "Remote Content")
        val localTimestamp = Instant.fromEpochMilliseconds(1000)
        val remoteTimestamp = Instant.fromEpochMilliseconds(2000)

        val result = resolver.resolve(local, remote, localTimestamp, remoteTimestamp)

        assertTrue(result is ConflictResolution.KeepRemote)
        assertEquals("Remote Content", (result as ConflictResolution.KeepRemote).value.content)
    }

    @Test
    fun `local wins when local timestamp is newer`() {
        val local = TestEntity("1", "Local Content")
        val remote = TestEntity("1", "Remote Content")
        val localTimestamp = Instant.fromEpochMilliseconds(2000)
        val remoteTimestamp = Instant.fromEpochMilliseconds(1000)

        val result = resolver.resolve(local, remote, localTimestamp, remoteTimestamp)

        assertTrue(result is ConflictResolution.KeepLocal)
        assertEquals("Local Content", (result as ConflictResolution.KeepLocal).value.content)
    }

    @Test
    fun `local wins on equal timestamps`() {
        val local = TestEntity("1", "Local Content")
        val remote = TestEntity("1", "Remote Content")
        val timestamp = Instant.fromEpochMilliseconds(1000)

        val result = resolver.resolve(local, remote, timestamp, timestamp)

        assertTrue(result is ConflictResolution.KeepLocal)
        assertEquals("Local Content", (result as ConflictResolution.KeepLocal).value.content)
    }

    @Test
    fun `handles very close timestamps correctly`() {
        val local = TestEntity("1", "Local Content")
        val remote = TestEntity("1", "Remote Content")
        val localTimestamp = Instant.fromEpochMilliseconds(1000)
        val remoteTimestamp = Instant.fromEpochMilliseconds(1001) // 1ms difference

        val result = resolver.resolve(local, remote, localTimestamp, remoteTimestamp)

        assertTrue(result is ConflictResolution.KeepRemote)
    }

    @Test
    fun `handles large timestamp differences`() {
        val local = TestEntity("1", "Local Content")
        val remote = TestEntity("1", "Remote Content")
        val localTimestamp = Instant.fromEpochMilliseconds(0) // Epoch
        val remoteTimestamp = Instant.fromEpochMilliseconds(Long.MAX_VALUE / 2) // Very far in future

        val result = resolver.resolve(local, remote, localTimestamp, remoteTimestamp)

        assertTrue(result is ConflictResolution.KeepRemote)
    }

    @Test
    fun `resolver is stateless across multiple calls`() {
        val entity1 = TestEntity("1", "Entity 1")
        val entity2 = TestEntity("2", "Entity 2")
        val olderTime = Instant.fromEpochMilliseconds(1000)
        val newerTime = Instant.fromEpochMilliseconds(2000)

        // First call: remote wins
        val result1 = resolver.resolve(entity1, entity2, olderTime, newerTime)
        assertTrue(result1 is ConflictResolution.KeepRemote)

        // Second call with reversed timestamps: local wins
        val result2 = resolver.resolve(entity1, entity2, newerTime, olderTime)
        assertTrue(result2 is ConflictResolution.KeepLocal)

        // Resolver should give consistent results regardless of call order
        val result3 = resolver.resolve(entity1, entity2, olderTime, newerTime)
        assertTrue(result3 is ConflictResolution.KeepRemote)
    }
}
