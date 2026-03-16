package app.logdate.wear.health

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class NoteHealthAnnotatorTest {

    private val healthSensorManager = mockk<WearHealthSensorManager>()
    private val healthSnapshotDao = mockk<HealthSnapshotDao>(relaxed = true)

    private fun createAnnotator(): NoteHealthAnnotator {
        return NoteHealthAnnotator(healthSensorManager, healthSnapshotDao)
    }

    // -----------------------------------------------------------------------
    // annotate -- success
    // -----------------------------------------------------------------------

    @Test
    fun `annotate saves snapshot with heart rate and step count`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(
            heartRateBpm = 72,
            stepCount = 5000,
        )
        val annotator = createAnnotator()
        val noteId = Uuid.random()

        val result = annotator.annotate(noteId)

        assertNotNull(result)
        assertEquals(72, result.heartRateBpm)
        assertEquals(5000, result.stepCount)
        assertEquals(noteId, result.noteId)
        assertEquals("wear_health_services", result.source)
    }

    @Test
    fun `annotate persists entity to DAO`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(
            heartRateBpm = 72,
            stepCount = 5000,
        )
        val annotator = createAnnotator()
        val noteId = Uuid.random()
        val entitySlot = slot<HealthSnapshotEntity>()

        annotator.annotate(noteId)

        coVerify { healthSnapshotDao.insert(capture(entitySlot)) }
        assertEquals(noteId, entitySlot.captured.noteId)
        assertEquals(72, entitySlot.captured.heartRateBpm)
        assertEquals(5000, entitySlot.captured.stepCount)
    }

    @Test
    fun `annotate saves with only heart rate`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(
            heartRateBpm = 80,
        )
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNotNull(result)
        assertEquals(80, result.heartRateBpm)
        assertNull(result.stepCount)
    }

    @Test
    fun `annotate saves with only step count`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(
            stepCount = 12000,
        )
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNotNull(result)
        assertNull(result.heartRateBpm)
        assertEquals(12000, result.stepCount)
    }

    @Test
    fun `annotate includes stress level when available`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(
            heartRateBpm = 90,
            stressLevel = 0.7f,
        )
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNotNull(result)
        assertEquals(0.7f, result.stressLevel)
    }

    // -----------------------------------------------------------------------
    // annotate -- no data
    // -----------------------------------------------------------------------

    @Test
    fun `annotate returns null when no health data available`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot()
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNull(result)
        coVerify(exactly = 0) { healthSnapshotDao.insert(any()) }
    }

    // -----------------------------------------------------------------------
    // annotate -- error handling
    // -----------------------------------------------------------------------

    @Test
    fun `annotate returns null when sensor manager throws`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } throws RuntimeException("sensor error")
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNull(result)
    }

    @Test
    fun `annotate returns null when DAO insert throws`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(heartRateBpm = 72)
        coEvery { healthSnapshotDao.insert(any()) } throws RuntimeException("DB error")
        val annotator = createAnnotator()

        val result = annotator.annotate(Uuid.random())

        assertNull(result)
    }

    // -----------------------------------------------------------------------
    // annotate -- unique IDs
    // -----------------------------------------------------------------------

    @Test
    fun `annotate generates unique snapshot IDs`() = runTest {
        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot(heartRateBpm = 72)
        val annotator = createAnnotator()
        val entities = mutableListOf<HealthSnapshotEntity>()
        coEvery { healthSnapshotDao.insert(capture(entities)) } returns Unit

        annotator.annotate(Uuid.random())
        annotator.annotate(Uuid.random())

        assertEquals(2, entities.size)
        assert(entities[0].id != entities[1].id) { "Snapshot IDs should be unique" }
    }
}
