package app.logdate.client.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import io.github.aakira.napier.Napier

internal suspend fun handleHealthSync(
    event: PhoneDataLayerSnapshotEvent,
    path: String,
    healthSnapshotDao: HealthSnapshotDao,
    healthSnapshotDataMapper: HealthSnapshotDataMapper,
) {
    try {
        val syncData = healthSnapshotDataMapper.fromDataMap(event.data)
        Napier.d("Received health snapshot from watch: ${syncData.id}")
        healthSnapshotDao.insert(
            HealthSnapshotEntity(
                id = syncData.id,
                noteId = syncData.noteId,
                heartRateBpm = syncData.heartRateBpm,
                heartRateVariabilityMs = syncData.heartRateVariabilityMs,
                stepCount = syncData.stepCount,
                stressLevel = syncData.stressLevel,
                cumulativeCalories = syncData.cumulativeCalories,
                timestamp = syncData.timestamp,
                source = syncData.source,
            ),
        )
    } catch (e: Exception) {
        Napier.w("Failed to process synced health snapshot from watch at path: $path", e)
    }
}
