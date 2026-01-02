package app.logdate.client.intelligence.rewind

import app.logdate.shared.model.Rewind
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

class DefaultRewindGenerator : RewindGenerator {
    override suspend fun generateRewind(): Rewind {
        val now = Clock.System.now()
        return Rewind(
            uid = Uuid.random(),
            startDate = now,
            endDate = now,
            generationDate = now,
            label = "unknown",
            title = "Rewind",
        )
    }
}
