package app.logdate.client.location.tracking

import android.location.Location
import androidx.core.location.LocationCompat
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal fun Location.observedAt(fallback: Instant): Instant = if (time > 0) Instant.fromEpochMilliseconds(time) else fallback

internal fun Location.captureMetadata(
    recordedAt: Instant,
    pipeline: LocationCapturePipeline,
    source: LocationCaptureSource,
    activityType: String? = null,
): Map<String, Any> =
    buildMap {
        put("loggedAt", recordedAt)
        put("capturePipeline", pipeline)
        put("captureSource", source)
        put("timeZoneId", TimeZone.currentSystemDefault().id)
        if (hasAccuracy()) put("accuracyMeters", accuracy)
        if (hasSpeed()) put("speedMetersPerSecond", speed)
        if (hasBearing()) put("bearingDegrees", bearing)
        put("isMock", LocationCompat.isMock(this@captureMetadata))
        activityType?.let { put("activityType", it) }
    }
