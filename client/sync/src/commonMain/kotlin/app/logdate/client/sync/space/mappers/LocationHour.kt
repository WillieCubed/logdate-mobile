package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.client.sync.space.centimetersToMeters
import app.logdate.client.sync.space.hundredthsToBearing
import app.logdate.client.sync.space.toBearingHundredths
import app.logdate.client.sync.space.toCentimeters
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.LocationObservation
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * One location sample of a device's hour, or the fact that it was deleted.
 *
 * A deleted sample keeps its id and, when this device knows it, the time it was taken; the
 * location itself is gone. [at] of a deletion that arrived without the sample is the start of the
 * hour.
 */
data class HourSample(
    val id: String,
    val at: Instant,
    val observation: LocationObservation? = null,
) {
    val deleted: Boolean get() = observation == null
}

/** One activity change of a device's hour, or the fact that it was deleted. See [HourSample]. */
data class HourTransition(
    val id: String,
    val at: Instant,
    val activity: ActivityObservation? = null,
) {
    val deleted: Boolean get() = activity == null
}

/**
 * Everything one device recorded in one UTC hour: every sample and activity change, deleted ones
 * included, as the personal space's `app.logdate.place.sample` records hold them. An hour is how
 * this device reads its history records to decide what each part of the hour holds; the part is
 * what syncs.
 *
 * Samples never change once recorded, and a deleted one stays deleted, so two copies of an hour
 * always merge ([merge]) into the union of their samples minus every deleted id.
 *
 * An hour is stored as parts of at most [MAX_SAMPLES_PER_PART] samples and
 * [MAX_TRANSITIONS_PER_PART] activity changes, each counting the ids deleted from it: [parts] puts
 * the entries, oldest first, into parts that fill in order. A deleted sample keeps its place, so a
 * deletion lists its id in the part that held the sample and the part never outgrows its limit.
 *
 * @property deviceId The device that recorded the hour, in lowercase.
 * @property hourStart The start of the UTC hour.
 */
data class LocationHour(
    val deviceId: String,
    val hourStart: Instant,
    val samples: List<HourSample> = emptyList(),
    val transitions: List<HourTransition> = emptyList(),
) {
    init {
        if (hourStart != hourOf(hourStart)) throw SpaceRecordMappingException("A location hour must start on the hour")
        if (samples.map { it.id }.toSet().size != samples.size || transitions.map { it.id }.toSet().size != transitions.size) {
            throw SpaceRecordMappingException("A location hour lists the same id twice")
        }
        if (samples != samples.sortedWith(sampleOrder) || transitions != transitions.sortedWith(transitionOrder)) {
            throw SpaceRecordMappingException("A location hour's entries aren't in time order")
        }
        if (samples.any { hourOf(it.at) != hourStart } || transitions.any { hourOf(it.at) != hourStart }) {
            throw SpaceRecordMappingException("A location hour holds an entry from another hour")
        }
        if (samples.any { it.observation?.let { o -> o.id != it.id || o.deviceId != deviceId } == true } ||
            transitions.any { it.activity?.let { a -> a.id != it.id || a.deviceId != deviceId } == true }
        ) {
            throw SpaceRecordMappingException("A location hour holds an entry another device recorded")
        }
    }

    val isEmpty: Boolean get() = samples.isEmpty() && transitions.isEmpty()

    /**
     * This hour and [other], the same device's same hour as another copy has it: the union of the
     * entries, with a deletion in either winning. Where both copies hold a sample under one id and
     * they still differ at the precision a record keeps, the one that sorts last is kept, so every
     * device gives the same answer. A sample both copies hold keeps this copy's, which may be more
     * precise than the stored one.
     */
    fun merge(other: LocationHour): LocationHour {
        if (other.deviceId != deviceId || other.hourStart != hourStart) {
            throw SpaceRecordMappingException("Only copies of the same device and hour merge")
        }
        requireCompatibleHistoryOwners(this, other)
        return LocationHour(
            deviceId = deviceId,
            hourStart = hourStart,
            samples = union(samples, other.samples, HourSample::id, ::mergeSamples).sortedWith(sampleOrder),
            transitions = union(transitions, other.transitions, HourTransition::id, ::mergeTransitions).sortedWith(transitionOrder),
        )
    }

    /** The parts this hour is stored as, at least one. */
    fun parts(): List<LocationHourPart> {
        val sampleChunks = samples.chunked(MAX_SAMPLES_PER_PART)
        val transitionChunks = transitions.chunked(MAX_TRANSITIONS_PER_PART)
        val count = maxOf(1, sampleChunks.size, transitionChunks.size)
        if (count > MAX_PARTS) {
            throw SpaceRecordMappingException("A location hour holds more samples than $MAX_PARTS parts can store")
        }
        return (1..count).map { number ->
            val hereSamples = sampleChunks.getOrElse(number - 1) { emptyList() }
            val hereTransitions = transitionChunks.getOrElse(number - 1) { emptyList() }
            LocationHourPart(
                number = number,
                deviceId = deviceId,
                hourStart = hourStart,
                observations = hereSamples.mapNotNull { it.observation },
                activities = hereTransitions.mapNotNull { it.activity },
                deletedSampleIds = hereSamples.filter { it.deleted }.map { it.id }.sorted(),
                deletedTransitionIds = hereTransitions.filter { it.deleted }.map { it.id }.sorted(),
            )
        }
    }

    companion object {
        /** The most samples one part holds, counting the sample ids deleted from it. */
        const val MAX_SAMPLES_PER_PART: Int = 5_000

        /** The most activity changes one part holds, counting the ids deleted from it. */
        const val MAX_TRANSITIONS_PER_PART: Int = 500

        /**
         * The most parts one hour is stored as. A foreground stream records about 3,600 samples an
         * hour; this allows about 100,000.
         */
        const val MAX_PARTS: Int = 20

        val sampleOrder: Comparator<HourSample> = compareBy<HourSample>({ it.at }, { it.id })
        val transitionOrder: Comparator<HourTransition> = compareBy<HourTransition>({ it.at }, { it.id })

        private val HOUR = 1.hours

        /** The start of the UTC hour [instant] falls in. */
        fun hourOf(instant: Instant): Instant =
            Instant.fromEpochSeconds(instant.epochSeconds.floorDiv(HOUR.inWholeSeconds) * HOUR.inWholeSeconds)

        /** The hour made of [parts], which may be any parts of one device's hour, merged. */
        fun of(parts: Collection<LocationHourPart>): LocationHour {
            val first = parts.firstOrNull() ?: throw SpaceRecordMappingException("A location hour needs at least one part")
            if (parts.any { it.deviceId != first.deviceId || it.hourStart != first.hourStart }) {
                throw SpaceRecordMappingException("Parts of different hours can't be joined")
            }
            return parts
                .sortedBy { it.number }
                .map { it.toHour() }
                .reduce(LocationHour::merge)
        }

        /** The item id of part [part] of [deviceId]'s hour starting at [hourStart]: the part's record key, in lowercase. */
        fun itemId(
            deviceId: String,
            hourStart: Instant,
            part: Int = 1,
        ): String = canonicalRecordKey(LocationSampleRecordMapper.rkey(deviceId, hourStart, part))

        /** [observation] at the precision a record keeps it: centimeters, centimeters per second and hundredths of a degree. */
        fun atRecordPrecision(observation: LocationObservation): LocationObservation {
            val owner = "Sample"
            return observation.copy(
                accuracyMeters = observation.accuracyMeters?.toCentimeters(owner)?.centimetersToMeters(),
                speedMetersPerSecond = observation.speedMetersPerSecond?.toCentimeters(owner)?.centimetersToMeters(),
                bearingDegrees =
                    observation.bearingDegrees
                        ?.toDouble()
                        ?.toBearingHundredths(owner)
                        ?.hundredthsToBearing()
                        ?.toFloat(),
            )
        }

        private fun <T> union(
            first: List<T>,
            second: List<T>,
            idOf: (T) -> String,
            combine: (T, T) -> T,
        ): List<T> {
            val byId = linkedMapOf<String, T>()
            (first + second).forEach { entry -> byId[idOf(entry)] = byId[idOf(entry)]?.let { combine(it, entry) } ?: entry }
            return byId.values.toList()
        }

        private fun mergeSamples(
            kept: HourSample,
            other: HourSample,
        ): HourSample {
            val at = maxOf(kept.at, other.at)
            val first = kept.observation
            val second = other.observation
            return when {
                first == null || second == null -> HourSample(kept.id, at)
                sameStoredLocationSample(first, second) -> kept.copy(at = at)
                else -> maxOf(kept, other, compareBy({ atRecordPrecision(it.observation!!).copy(ownerId = "").toString() })).copy(at = at)
            }
        }

        private fun mergeTransitions(
            kept: HourTransition,
            other: HourTransition,
        ): HourTransition {
            val at = maxOf(kept.at, other.at)
            val first = kept.activity
            val second = other.activity
            return when {
                first == null || second == null -> HourTransition(kept.id, at)
                first.copy(ownerId = second.ownerId) == second -> kept.copy(at = at)
                else -> maxOf(kept, other, compareBy({ it.activity?.copy(ownerId = "").toString() })).copy(at = at)
            }
        }
    }
}

/**
 * One numbered part of a device's hour: an `app.logdate.place.sample` record, and the unit that
 * syncs. A part syncs on its own, so a new sample rewrites only the hour's last part.
 *
 * @property number 1 for the first part, whose key has no number, then 2, 3 and so on.
 * @property deletedSampleIds The ids of samples deleted from this part, whose locations are gone.
 * @property syncVersion The position in the space's change log of the write this part was read from.
 */
data class LocationHourPart(
    val number: Int,
    val deviceId: String,
    val hourStart: Instant,
    val observations: List<LocationObservation> = emptyList(),
    val activities: List<ActivityObservation> = emptyList(),
    val deletedSampleIds: List<String> = emptyList(),
    val deletedTransitionIds: List<String> = emptyList(),
    val syncVersion: Long = 0,
) {
    /** The item id of this part: its record key in lowercase. */
    val id: String get() = LocationHour.itemId(deviceId, hourStart, number)

    /** This part as an hour holding only what the part does; an id the part lists as deleted is deleted whatever else it holds. */
    fun toHour(): LocationHour =
        LocationHour(
            deviceId = deviceId,
            hourStart = hourStart,
            samples =
                (deletedSampleIds.map { HourSample(it, hourStart) } + observations.map { HourSample(it.id, it.timestamp, it) })
                    .distinctBy { it.id }
                    .sortedWith(LocationHour.sampleOrder),
            transitions =
                (deletedTransitionIds.map { HourTransition(it, hourStart) } + activities.map { HourTransition(it.id, it.timestamp, it) })
                    .distinctBy { it.id }
                    .sortedWith(LocationHour.transitionOrder),
        )

    /** This part and [other], another copy of the same part: the union of what they hold, with every deletion winning. */
    fun merge(other: LocationHourPart): LocationHourPart {
        if (other.number != number) throw SpaceRecordMappingException("Only copies of the same part merge")
        return toHour().merge(other.toHour()).asPart(number, maxOf(syncVersion, other.syncVersion))
    }
}

/** All of this hour's entries as part [number], without checking that they fit: the merge of two copies of one part. */
internal fun LocationHour.asPart(
    number: Int,
    syncVersion: Long = 0,
): LocationHourPart =
    LocationHourPart(
        number = number,
        deviceId = deviceId,
        hourStart = hourStart,
        observations = samples.mapNotNull { it.observation },
        activities = transitions.mapNotNull { it.activity },
        deletedSampleIds = samples.filter { it.deleted }.map { it.id }.sorted(),
        deletedTransitionIds = transitions.filter { it.deleted }.map { it.id }.sorted(),
        syncVersion = syncVersion,
    )
