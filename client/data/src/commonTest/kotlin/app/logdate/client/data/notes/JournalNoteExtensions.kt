package app.logdate.client.data.notes

import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation

private fun testLocation(
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    accuracy: Float?,
): NoteLocation? {
    if (latitude == null || longitude == null) {
        return null
    }

    return NoteLocation(
        coordinates =
            NoteCoordinates(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
            ),
    )
}

/**
 * Extension functions for converting between model and entity classes in tests.
 *
 * Convert a [JournalNote.Text] to a [TextNoteEntity].
 */
fun JournalNote.Text.toEntity(): TextNoteEntity =
    TextNoteEntity(
        content = content,
        uid = uid,
        lastUpdated = lastUpdated,
        created = creationTimestamp,
        syncVersion = syncVersion,
        latitude = location?.coordinates?.latitude,
        longitude = location?.coordinates?.longitude,
        altitude = location?.coordinates?.altitude,
        locationAccuracy = location?.coordinates?.accuracy,
        placeId = location?.place?.id,
    )

/**
 * Convert a [JournalNote.Image] to an [ImageNoteEntity].
 */
fun JournalNote.Image.toEntity(): ImageNoteEntity =
    ImageNoteEntity(
        contentUri = mediaRef,
        uid = uid,
        lastUpdated = lastUpdated,
        created = creationTimestamp,
        syncVersion = syncVersion,
        latitude = location?.coordinates?.latitude,
        longitude = location?.coordinates?.longitude,
        altitude = location?.coordinates?.altitude,
        locationAccuracy = location?.coordinates?.accuracy,
        placeId = location?.place?.id,
    )

/**
 * Convert a [TextNoteEntity] to a [JournalNote.Text].
 */
fun TextNoteEntity.toModel(): JournalNote.Text =
    JournalNote.Text(
        uid = uid,
        content = content,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = testLocation(latitude, longitude, altitude, locationAccuracy),
    )

/**
 * Convert an [ImageNoteEntity] to a [JournalNote.Image].
 */
fun ImageNoteEntity.toModel(): JournalNote.Image =
    JournalNote.Image(
        uid = uid,
        mediaRef = contentUri,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = testLocation(latitude, longitude, altitude, locationAccuracy),
    )
