package app.logdate.client.domain.export

import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Place
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock

internal fun NoteLocation.toExportLocation(): ExportLocation? {
    val lat = effectiveLatitude ?: return null
    val lng = effectiveLongitude ?: return null
    return ExportLocation(
        latitude = lat,
        longitude = lng,
        placeName = displayName,
        altitude = coordinates?.altitude,
        accuracy = coordinates?.accuracy,
    )
}

private fun SerializableEntryBlock.toMediaReferences(): List<String> =
    when (this) {
        is SerializableImageBlock -> listOfNotNull(uri)
        is SerializableVideoBlock -> listOfNotNull(uri, thumbnailUri)
        is SerializableAudioBlock -> listOfNotNull(uri)
        is SerializableCameraBlock -> listOfNotNull(uri)
        is SerializableTextBlock, is app.logdate.shared.model.SerializableHandwritingBlock -> emptyList()
    }

internal fun EditorDraft.toExportDraft(): ExportDraft {
    val location =
        blocks.firstNotNullOfOrNull { block ->
            val latitude = block.locationLat
            val longitude = block.locationLng
            if (latitude != null && longitude != null) {
                ExportLocation(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = block.altitude,
                )
            } else {
                null
            }
        }

    val content =
        blocks
            .filterIsInstance<SerializableTextBlock>()
            .joinToString("\n") { it.content }

    val mediaReferences =
        blocks
            .flatMap { it.toMediaReferences() }
            .distinct()

    return ExportDraft(
        id = id.toString(),
        journalId = selectedJournalIds.firstOrNull()?.toString(),
        journalIds = selectedJournalIds.map { it.toString() },
        content = content,
        createdAt = createdAt,
        updatedAt = lastModifiedAt,
        location = location,
        mediaReferences = mediaReferences,
        blocks = blocks,
    )
}

internal fun Place.toExportPlaceOrNull(): ExportPlace? =
    when (this) {
        is Place.UserDefined ->
            ExportPlace(
                id = id.toString(),
                displayName = displayName,
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusMeters,
                description = description,
            )
    }

internal fun LocationHistoryItem.toExportLocationHistoryItem(): ExportLocationHistoryItem =
    ExportLocationHistoryItem(
        sampleId = sampleId,
        userId = userId,
        deviceId = deviceId,
        timestamp = timestamp,
        loggedAt = loggedAt,
        latitude = location.latitude,
        longitude = location.longitude,
        altitudeMeters = location.altitude.value,
        confidence = confidence,
        isGenuine = isGenuine,
        capturePipeline = capturePipeline.name,
        captureSource = captureSource.name,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        isMock = isMock,
    )
