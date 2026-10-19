package app.logdate.client.sync.space.mappers

import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.TravelMode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal const val TEST_OWNER = "account-1"
internal const val TEST_DEVICE = "6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b"
internal val TEST_HOUR: Instant = Instant.parse("2026-10-19T14:00:00Z")

/** A sample [index] seconds into [hour], wrapping around inside the hour, with measurements a record keeps exactly. */
internal fun sampleAt(
    index: Int,
    hour: Instant = TEST_HOUR,
    device: String = TEST_DEVICE,
    id: String = "sample-${index.toString().padStart(5, '0')}",
) = LocationObservation(
    id = id,
    ownerId = TEST_OWNER,
    deviceId = device,
    timestamp = hour + (index % 3_600).seconds,
    latitude = 41.8781136 + index * 0.00001,
    longitude = -87.6297982 - index * 0.00001,
    accuracyMeters = 12.5f,
    speedMetersPerSecond = 1.42f,
    bearingDegrees = 271.35f,
    activity = TravelMode.WALKING,
    timeZoneId = "America/Chicago",
    source = "FOREGROUND_STREAM",
)

/** An activity change [index] seconds into [hour], wrapping around inside the hour. */
internal fun changeAt(
    index: Int,
    hour: Instant = TEST_HOUR,
    device: String = TEST_DEVICE,
    id: String = "change-${index.toString().padStart(4, '0')}",
) = ActivityObservation(
    id = id,
    ownerId = TEST_OWNER,
    deviceId = device,
    timestamp = hour + (index % 3_600).seconds,
    activityType = if (index % 2 == 0) "WALKING" else "STILL",
    transitionType = if (index % 2 == 0) "ENTER" else "EXIT",
    timeZoneId = "America/Chicago",
)

/** An hour holding [samples] samples and [changes] activity changes, the first [deletedSamples] and [deletedChanges] of them deleted. */
internal fun hourOf(
    samples: Int,
    changes: Int = 0,
    deletedSamples: Int = 0,
    deletedChanges: Int = 0,
    hour: Instant = TEST_HOUR,
    device: String = TEST_DEVICE,
): LocationHour =
    LocationHour(
        deviceId = device,
        hourStart = hour,
        samples =
            (0 until samples)
                .map { index ->
                    val sample = sampleAt(index, hour, device)
                    if (index < deletedSamples) HourSample(sample.id, sample.timestamp) else HourSample(sample.id, sample.timestamp, sample)
                }.sortedWith(LocationHour.sampleOrder),
        transitions =
            (0 until changes)
                .map { index ->
                    val change = changeAt(index, hour, device)
                    if (index <
                        deletedChanges
                    ) {
                        HourTransition(change.id, change.timestamp)
                    } else {
                        HourTransition(change.id, change.timestamp, change)
                    }
                }.sortedWith(LocationHour.transitionOrder),
    )
