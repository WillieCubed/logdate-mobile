package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.client.sync.space.centimetersToMeters
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.hundredthsToBearing
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseDecimal
import app.logdate.client.sync.space.sameRecordKey
import app.logdate.client.sync.space.toBearingHundredths
import app.logdate.client.sync.space.toCentimeters
import app.logdate.client.sync.space.toDatetime
import app.logdate.client.sync.space.toDecimalString
import app.logdate.lexicon.app.logdate.place.Sample
import app.logdate.lexicon.app.logdate.place.SamplePoint
import app.logdate.lexicon.app.logdate.place.SampleTransition
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.TravelMode
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.time.Instant

/**
 * Maps one part of a device's hour, a [LocationHourPart], to an `app.logdate.place.sample` record
 * and back.
 *
 * The first part's record key is `<UTC hour>:<device>`, such as
 * `2026-10-19T14:6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b`; later parts add `:2`, `:3` and so on, and
 * name their number in the record's `part`. [LocationHour] decides what each part holds.
 *
 * A record doesn't name its owner: every record in a personal space belongs to the space's owner,
 * and samples read from one are given that owner, [ownerId]. Accuracy, speed and bearing are kept in
 * centimeters, centimeters per second and hundredths of a degree, so a sample reads back with those
 * rounded to the record's precision. A sample whose source, activity or time zone this device
 * can't hold unchanged is refused, as is a NaN or infinite measurement.
 */
class LocationSampleRecordMapper(
    private val ownerId: () -> String?,
) : SpaceRecordMapper<LocationHourPart> {
    override val collection: String = Sample.TYPE

    override fun rkeyOf(value: LocationHourPart): String = rkey(value.deviceId, value.hourStart, value.number)

    override fun toRecord(value: LocationHourPart): JsonObject {
        val owner = "$collection/${rkeyOf(value)}"
        if (value.observations.size + value.deletedSampleIds.size > LocationHour.MAX_SAMPLES_PER_PART ||
            value.activities.size + value.deletedTransitionIds.size > LocationHour.MAX_TRANSITIONS_PER_PART
        ) {
            throw SpaceRecordMappingException("$owner: it holds more samples or activity changes than a part can store")
        }
        return encodeRecord(
            Sample.serializer(),
            Sample(
                device = value.deviceId,
                startsAt = value.hourStart.toDatetime("$owner startsAt"),
                part = value.number.takeIf { it > 1 }?.toLong(),
                samples = value.observations.map { it.toPoint(owner) }.ifEmpty { null },
                transitions = value.activities.map { it.toTransition(owner) }.ifEmpty { null },
                deletedSampleIds = value.deletedSampleIds.sorted().ifEmpty { null },
                deletedTransitionIds = value.deletedTransitionIds.sorted().ifEmpty { null },
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): LocationHourPart {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(Sample.serializer(), collection, rkey, record)
        val hourStart = parseDatetime("$owner startsAt", decoded.startsAt)
        val number = decoded.part ?: 1L
        if (decoded.part != null && number !in 2..LocationHour.MAX_PARTS) {
            throw SpaceRecordMappingException("$owner: it is part $number, and this device reads parts 1 to ${LocationHour.MAX_PARTS}")
        }
        if (hourStart != LocationHour.hourOf(hourStart) || !sameRecordKey(rkey, rkey(decoded.device, hourStart, number.toInt()))) {
            throw SpaceRecordMappingException("$owner: it isn't stored under the key its device, hour and part give it")
        }
        val account = ownerId() ?: throw SpaceRecordMappingException("$owner: no account is signed in to own its samples")
        return LocationHourPart(
            number = number.toInt(),
            deviceId = decoded.device,
            hourStart = hourStart,
            observations = decoded.samples.orEmpty().map { it.toObservation(owner, account, decoded.device) },
            activities = decoded.transitions.orEmpty().map { it.toActivity(owner, account, decoded.device) },
            deletedSampleIds = decoded.deletedSampleIds.orEmpty(),
            deletedTransitionIds = decoded.deletedTransitionIds.orEmpty(),
        )
    }

    private fun LocationObservation.toPoint(owner: String): SamplePoint {
        val sample = "$owner sample"
        return SamplePoint(
            id = id,
            at = timestamp.toDatetime("$sample at"),
            latitude = latitude.toDecimalString("$sample latitude"),
            longitude = longitude.toDecimalString("$sample longitude"),
            altitudeMeters = altitudeMeters?.toDecimalString("$sample altitude"),
            accuracyCentimeters = accuracyMeters?.toCentimeters("$sample accuracy"),
            speedCentimetersPerSecond = speedMetersPerSecond?.toCentimeters("$sample speed"),
            bearingCentidegrees = bearingDegrees?.toDouble()?.toBearingHundredths("$sample bearing"),
            activity = activity.takeUnless { it == TravelMode.UNKNOWN }?.let { TravelModes.toLexicon(sample, it) },
            timeZone = timeZoneId,
            source = source.takeUnless { it == UNKNOWN }?.let { sources.lexiconName(sample, it) },
            mock = isMock.takeIf { it },
        )
    }

    private fun SamplePoint.toObservation(
        owner: String,
        account: String,
        deviceId: String,
    ): LocationObservation {
        val sample = "$owner sample"
        return LocationObservation(
            id = id,
            ownerId = account,
            deviceId = deviceId,
            timestamp = parseDatetime("$sample at", at),
            latitude = parseDecimal("$sample latitude", latitude),
            longitude = parseDecimal("$sample longitude", longitude),
            accuracyMeters = accuracyCentimeters?.centimetersToMeters(),
            speedMetersPerSecond = speedCentimetersPerSecond?.centimetersToMeters(),
            bearingDegrees = bearingCentidegrees?.hundredthsToBearing()?.toFloat(),
            activity = activity?.let { TravelModes.fromLexicon(sample, it) } ?: TravelMode.UNKNOWN,
            timeZoneId = timeZone,
            source = source?.let { sources.localName(sample, it) } ?: UNKNOWN,
            isMock = mock ?: false,
            altitudeMeters = altitudeMeters?.let { parseDecimal("$sample altitude", it) },
        )
    }

    private fun ActivityObservation.toTransition(owner: String): SampleTransition {
        val change = "$owner activity change"
        return SampleTransition(
            id = id,
            at = timestamp.toDatetime("$change at"),
            activity = activityTypes.lexiconName(change, activityType),
            transition = transitions.lexiconName(change, transitionType),
            timeZone = timeZoneId,
        )
    }

    private fun SampleTransition.toActivity(
        owner: String,
        account: String,
        deviceId: String,
    ): ActivityObservation {
        val change = "$owner activity change"
        return ActivityObservation(
            id = id,
            ownerId = account,
            deviceId = deviceId,
            timestamp = parseDatetime("$change at", at),
            activityType = activityTypes.localName(change, activity),
            transitionType = transitions.localName(change, transition),
            timeZoneId = timeZone,
        )
    }

    companion object {
        private const val UNKNOWN = "UNKNOWN"
        private const val HOUR_KEY_LENGTH = 13

        /**
         * The record key of part [part] of [deviceId]'s hour starting at [hourStart]. A device id
         * can't hold a colon, which would make one part's key look like another's.
         */
        fun rkey(
            deviceId: String,
            hourStart: Instant,
            part: Int = 1,
        ): String {
            if (hourStart != LocationHour.hourOf(hourStart)) throw SpaceRecordMappingException("A location hour must start on the hour")
            if (part !in
                1..LocationHour.MAX_PARTS
            ) {
                throw SpaceRecordMappingException("A location hour has parts 1 to ${LocationHour.MAX_PARTS}")
            }
            // A lowercase device id keeps the key the same in every case; a colon would make one part's key look like another's.
            if (':' in deviceId || deviceId != canonicalRecordKey(deviceId)) {
                throw SpaceRecordMappingException("A device id with a colon or capital letters can't be part of a record key")
            }
            val hour = "${hourStart.toDatetime("A location hour").take(HOUR_KEY_LENGTH)}:$deviceId"
            val key = if (part == 1) hour else "$hour:$part"
            if (!RecordKey.isValid(
                    key,
                )
            ) {
                throw SpaceRecordMappingException("A device id with these characters can't be part of a record key")
            }
            return key
        }

        private val sources =
            NameTable(
                "its source",
                mapOf(
                    "BACKGROUND_PERIODIC" to "backgroundPeriodic",
                    "PASSIVE_UPDATE" to "passiveUpdate",
                    "ACTIVITY_TRANSITION" to "activityTransition",
                    "GEOFENCE_TRANSITION" to "geofenceTransition",
                    "SERVER_NUDGE" to "serverNudge",
                    "FOREGROUND_STREAM" to "foregroundStream",
                    "JOURNAL_ENTRY" to "journalEntry",
                    "TIMELINE_REVIEW" to "timelineReview",
                    "MANUAL" to "manual",
                    "RETRY" to "retry",
                ),
            )

        private val activityTypes =
            NameTable(
                "its activity",
                mapOf(
                    "STILL" to "still",
                    "WALKING" to "walking",
                    "RUNNING" to "running",
                    "ON_FOOT" to "onFoot",
                    "ON_BICYCLE" to "onBicycle",
                    "IN_VEHICLE" to "inVehicle",
                    "UNKNOWN" to "unknown",
                ),
            )

        private val transitions = NameTable("its transition", mapOf("ENTER" to "enter", "EXIT" to "exit"))
    }
}
