package app.logdate.wear.health

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Annotates journal notes with health context at save time.
 * Samples the current health state from [WearHealthSensorManager] and
 * persists it as a [HealthSnapshotEntity] linked to the note.
 */
class NoteHealthAnnotator(
    private val healthSensorManager: WearHealthSensorManager,
    private val healthSnapshotDao: HealthSnapshotDao,
) {
    /**
     * Sample current health data and attach it to the given note.
     * Silently succeeds with no annotation if health data is unavailable.
     *
     * @param noteId The ID of the note to annotate.
     * @return The created [HealthSnapshotEntity], or null if no data was available.
     */
    suspend fun annotate(noteId: Uuid): HealthSnapshotEntity? {
        return try {
            val snapshot = healthSensorManager.sampleCurrent()
            if (snapshot.heartRateBpm == null && snapshot.stepCount == null) {
                Napier.d("No health data available for annotation")
                return null
            }

            val entity = HealthSnapshotEntity(
                id = Uuid.random(),
                noteId = noteId,
                heartRateBpm = snapshot.heartRateBpm,
                stepCount = snapshot.stepCount,
                stressLevel = snapshot.stressLevel,
                timestamp = Clock.System.now(),
                source = "wear_health_services",
            )
            healthSnapshotDao.insert(entity)
            Napier.d("Health annotation saved for note $noteId: HR=${snapshot.heartRateBpm}, steps=${snapshot.stepCount}")
            entity
        } catch (e: Exception) {
            Napier.w("Failed to annotate note with health data", e)
            null
        }
    }
}
