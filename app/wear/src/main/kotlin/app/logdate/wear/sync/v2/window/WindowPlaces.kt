package app.logdate.wear.sync.v2.window

import app.logdate.client.database.dao.PlaceDao
import app.logdate.client.database.entities.PlaceEntity
import app.logdate.client.repository.journals.JournalNote

/** Called inside the note's apply transaction. Existing and soft-deleted local places win. */
class WindowPlaces(
    private val places: PlaceDao,
) {
    suspend fun ensure(note: JournalNote) {
        val place = note.location?.place ?: return
        places.insertIfAbsent(
            PlaceEntity(
                id = place.id,
                name = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                created = note.creationTimestamp,
                lastUpdated = note.lastUpdated,
            ),
        )
    }
}
