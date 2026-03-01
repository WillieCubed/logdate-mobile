package app.logdate.client.data.notes

import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Converts entity location fields to a NoteLocation domain model.
 * Returns null if no location data is present.
 */
private fun mapLocation(
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    accuracy: Float?,
    placeId: Uuid?
): NoteLocation? {
    // If we have no coordinates and no place reference, return null
    if (latitude == null && longitude == null && placeId == null) {
        return null
    }

    val coordinates = if (latitude != null && longitude != null) {
        NoteCoordinates(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy
        )
    } else null

    // TODO: Load place from PlaceDao when place_id is present
    // For now, we only support coordinates. Place loading will be added
    // when we implement the PlaceRepository.

    return NoteLocation(
        coordinates = coordinates,
        place = null
    )
}

fun TextNoteEntity.toModel() = JournalNote.Text(
    uid = uid,
    content = content,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    location = mapLocation(latitude, longitude, altitude, locationAccuracy, placeId),
)

fun JournalNote.Text.toEntity() = TextNoteEntity(
    uid = uid,
    content = content,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    latitude = location?.coordinates?.latitude,
    longitude = location?.coordinates?.longitude,
    altitude = location?.coordinates?.altitude,
    locationAccuracy = location?.coordinates?.accuracy,
    placeId = location?.place?.id,
)

fun ImageNoteEntity.toModel() = JournalNote.Image(
    uid = uid,
    mediaRef = contentUri,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    location = mapLocation(latitude, longitude, altitude, locationAccuracy, placeId),
)

fun JournalNote.Image.toEntity() = ImageNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    latitude = location?.coordinates?.latitude,
    longitude = location?.coordinates?.longitude,
    altitude = location?.coordinates?.altitude,
    locationAccuracy = location?.coordinates?.accuracy,
    placeId = location?.place?.id,
)

fun VideoNoteEntity.toModel() = JournalNote.Video(
    uid = uid,
    mediaRef = contentUri,
    creationTimestamp = created,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    location = mapLocation(latitude, longitude, altitude, locationAccuracy, placeId),
)

fun JournalNote.Video.toEntity() = VideoNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    latitude = location?.coordinates?.latitude,
    longitude = location?.coordinates?.longitude,
    altitude = location?.coordinates?.altitude,
    locationAccuracy = location?.coordinates?.accuracy,
    placeId = location?.place?.id,
)

fun AudioNoteEntity.toModel(): JournalNote.Audio {
    val result = JournalNote.Audio(
        uid = uid,
        mediaRef = contentUri,
        durationMs = durationMs,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = mapLocation(latitude, longitude, altitude, locationAccuracy, placeId),
    )

    // Add debug logging for audio note conversion
    Napier.d(
        tag = "NoteObjectMappers",
        message = "CONVERTING AUDIO NOTE: Entity with UID $uid and URI $contentUri created at $created converted to model"
    )

    return result
}

fun JournalNote.Audio.toEntity() = AudioNoteEntity(
    uid = uid,
    contentUri = mediaRef,
    created = creationTimestamp,
    lastUpdated = lastUpdated,
    syncVersion = syncVersion,
    durationMs = durationMs,
    latitude = location?.coordinates?.latitude,
    longitude = location?.coordinates?.longitude,
    altitude = location?.coordinates?.altitude,
    locationAccuracy = location?.coordinates?.accuracy,
    placeId = location?.place?.id,
)
