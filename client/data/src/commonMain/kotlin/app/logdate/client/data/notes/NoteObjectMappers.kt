package app.logdate.client.data.notes

import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.PlaceEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace

/**
 * Converts entity location fields to a NoteLocation domain model.
 * Returns null if no location data is present.
 */
private fun mapLocation(
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    accuracy: Float?,
    place: NotePlace?,
): NoteLocation? {
    // If we have no coordinates and no place reference, return null
    if (latitude == null && longitude == null && place == null) {
        return null
    }

    val coordinates =
        if (latitude != null && longitude != null) {
            NoteCoordinates(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
            )
        } else {
            null
        }

    return NoteLocation(
        coordinates = coordinates,
        place = place,
    )
}

fun TextNoteEntity.toModel(place: NotePlace? = null) =
    JournalNote.Text(
        uid = uid,
        content = content,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = mapLocation(latitude, longitude, altitude, locationAccuracy, place),
    )

fun JournalNote.Text.toEntity() =
    TextNoteEntity(
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

fun ImageNoteEntity.toModel(place: NotePlace? = null) =
    JournalNote.Image(
        uid = uid,
        mediaRef = contentUri,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = mapLocation(latitude, longitude, altitude, locationAccuracy, place),
    )

fun JournalNote.Image.toEntity() =
    ImageNoteEntity(
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

fun VideoNoteEntity.toModel(place: NotePlace? = null) =
    JournalNote.Video(
        uid = uid,
        mediaRef = contentUri,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = mapLocation(latitude, longitude, altitude, locationAccuracy, place),
    )

fun JournalNote.Video.toEntity() =
    VideoNoteEntity(
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

fun AudioNoteEntity.toModel(place: NotePlace? = null): JournalNote.Audio =
    JournalNote.Audio(
        uid = uid,
        mediaRef = contentUri,
        durationMs = durationMs,
        creationTimestamp = created,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
        location = mapLocation(latitude, longitude, altitude, locationAccuracy, place),
    )

fun JournalNote.Audio.toEntity() =
    AudioNoteEntity(
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

internal fun PlaceEntity.toNotePlace() =
    NotePlace(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
    )
