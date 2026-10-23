package app.logdate.client.sync.space.mappers

import app.logdate.client.device.identity.sameOwnerId
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.LocationObservation

/** A personal-space record's owner is supplied by its verified local scope, not encoded on the wire. */
internal fun requireCompatibleHistoryOwners(
    first: LocationHour,
    second: LocationHour,
) {
    val owners =
        (first.samples + second.samples).mapNotNull { it.observation?.ownerId } +
            (first.transitions + second.transitions).mapNotNull { it.activity?.ownerId }
    val expected = owners.firstOrNull() ?: return
    if (owners.any { !sameOwnerId(expected, it) }) {
        throw SpaceRecordMappingException("Location history from different accounts cannot be merged")
    }
}

internal fun sameStoredLocationSample(
    first: LocationObservation?,
    second: LocationObservation?,
): Boolean =
    first != null &&
        second != null &&
        sameOwnerId(first.ownerId, second.ownerId) &&
        LocationHour.atRecordPrecision(first).copy(ownerId = second.ownerId) == LocationHour.atRecordPrecision(second)

internal fun sameStoredLocationPart(
    first: LocationHourPart,
    second: LocationHourPart,
): Boolean {
    requireCompatibleHistoryOwners(first.toHour(), second.toHour())

    fun comparison(part: LocationHourPart) =
        part.copy(
            observations = part.observations.map { LocationHour.atRecordPrecision(it).copy(ownerId = "") },
            activities = part.activities.map { it.copy(ownerId = "") },
            syncVersion = 0,
        )
    return comparison(first) == comparison(second)
}
