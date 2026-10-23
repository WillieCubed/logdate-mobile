package app.logdate.client.sync.space.mappers

import app.logdate.client.device.crypto.activityHistoryRecordId
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.RemoteOutcome
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceKind
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import kotlin.time.Instant

/**
 * Location samples as a kind of the personal space: one item for each part of each device's UTC
 * hour, stored as an `app.logdate.place.sample` record. Most hours are one part.
 *
 * An hour is only ever added to and deleted from. Two copies of a part meet in
 * [LocationHourPart.merge], so no sample is lost and a deleted one never comes back. A device
 * deletes a sample from an hour another device recorded by rewriting the part that holds the
 * sample with its id in the part's deleted list.
 *
 * @param history which account's history records these are.
 * @param store the stored history records, one for each sample and activity change.
 * @param ownerId the account that owns every sample read from the space.
 * @param deviceId this device's id.
 */
fun locationSamplesSpaceKind(
    history: HistoryScopeProvider,
    store: HistoryRecordStore,
    ownerId: () -> String?,
    deviceId: () -> String?,
): SpaceKind<LocationHourPart> =
    historyKindOf(
        EntityType.LOCATION_SAMPLES,
        LocationSampleRecordMapper(ownerId),
        StoredLocationParts(HistoryStoreAccess(store, history, deviceId)),
        withSyncVersion = { part, version -> part.copy(syncVersion = version) },
        isItemId = { id -> parseHourItemId(id) != null },
    )

private const val HOUR_KEY_LENGTH = 13
private const val MILLIS_PER_HOUR = 3_600_000L

/** The device, hour and part number an item id names, or null when it isn't one. */
internal fun parseHourItemId(id: String): Triple<String, Instant, Int>? {
    val pieces = id.split(':')
    if (pieces.size !in 2..3 || pieces[0].length != HOUR_KEY_LENGTH || pieces[1].isEmpty()) return null
    val hour = Instant.parseOrNull("${pieces[0].uppercase()}:00:00Z") ?: return null
    if (hour != LocationHour.hourOf(hour)) return null
    val number = if (pieces.size == 3) pieces[2].toIntOrNull() ?: return null else 1
    if (pieces.size == 3 && number !in 2..LocationHour.MAX_PARTS) return null
    return Triple(pieces[1], hour, number)
}

/**
 * The samples of each part of each hour, read from and written to the history records.
 *
 * Which part holds which sample is decided from the whole hour, as [LocationHour.parts] lays it
 * out, so every part of an hour is read from the same records.
 */
internal class StoredLocationParts(
    private val access: HistoryStoreAccess,
) : HistoryKindRecords<LocationHourPart> {
    override val conflictResolver: ConflictResolver<LocationHourPart> =
        object : ConflictResolver<LocationHourPart> {
            override fun resolve(
                local: LocationHourPart,
                remote: LocationHourPart,
                localTimestamp: Instant,
                remoteTimestamp: Instant,
            ): ConflictResolution<LocationHourPart> {
                val merged = local.merge(remote)
                return when {
                    sameStoredLocationPart(merged, remote) -> ConflictResolution.KeepRemote(remote)
                    sameStoredLocationPart(merged, local) -> ConflictResolution.KeepLocal(local)
                    else -> ConflictResolution.Merge(merged)
                }
            }
        }

    override fun entityIdOf(item: LocationHourPart): String = item.id

    // A part is never deleted on its own: a sample's deletion is listed in the part that held it.
    override suspend fun acknowledgeDeleted(entityId: String) = Unit

    override suspend fun load(entityId: String): LocationHourPart? {
        val (device, hour, number) = parseHourItemId(entityId) ?: return null
        return read(device, hour).hour?.parts()?.getOrNull(number - 1)
    }

    override fun lastUpdatedOf(item: LocationHourPart): Instant =
        maxOf(
            item.hourStart,
            item.observations.maxOfOrNull { it.timestamp } ?: item.hourStart,
            item.activities.maxOfOrNull { it.timestamp } ?: item.hourStart,
        )

    /** The ids of every part of the hour starting at [hour] on [device]. */
    suspend fun partIds(
        device: String,
        hour: Instant,
    ): List<String> =
        read(device, hour)
            .hour
            ?.parts()
            .orEmpty()
            .map { it.id }

    /** The ids of the parts of the hour starting at [hour] on [device] that hold a record waiting to upload. */
    suspend fun partsWaiting(
        device: String,
        hour: Instant,
    ): List<String> {
        val current = read(device, hour)
        val dirtySamples =
            current.sampleRows
                .filter { it.record.dirty }
                .map { it.entry.id }
                .toSet()
        val dirtyChanges =
            current.transitionRows
                .filter { it.record.dirty }
                .map { it.entry.id }
                .toSet()
        return current.hour
            ?.parts()
            .orEmpty()
            .filter { part ->
                part.observations.any { it.id in dirtySamples } ||
                    part.deletedSampleIds.any { it in dirtySamples } ||
                    part.activities.any { it.id in dirtyChanges } ||
                    part.deletedTransitionIds.any { it in dirtyChanges }
            }.map { it.id }
    }

    override suspend fun acknowledgeUpload(
        item: LocationHourPart,
        seq: Long,
        syncedAt: Instant,
    ) {
        val current = read(item.deviceId, item.hourStart)
        val uploaded = item.stored()
        val samples = uploaded.observations.associateBy { it.id }
        val deletedSamples = uploaded.deletedSampleIds.toSet()
        val changes = uploaded.activities.map { it.id }.toSet()
        val deletedChanges = uploaded.deletedTransitionIds.toSet()
        // A record changed since the part was read is a newer edit, and stays to upload.
        current.sampleRows
            .filter { row ->
                row.record.dirty &&
                    if (row.entry.deleted) {
                        row.entry.id in deletedSamples
                    } else {
                        sameStoredLocationSample(samples[row.entry.id], row.entry.observation)
                    }
            }.forEach { access.acknowledge(it.record, seq) }
        current.transitionRows
            .filter { row -> row.record.dirty && row.entry.id in if (row.entry.deleted) deletedChanges else changes }
            .forEach { access.acknowledge(it.record, seq) }
    }

    override suspend fun replaceLocal(item: LocationHourPart) {
        val current = read(item.deviceId, item.hourStart)
        val known = current.sampleRows.associateBy { it.entry.id }
        val knownChanges = current.transitionRows.associateBy { it.entry.id }
        val writes = mutableListOf<HistoryRecord>()
        for (observation in item.observations) {
            if (known[observation.id] == null && observation.id !in item.deletedSampleIds) writes += liveSample(item, observation)
        }
        for (id in item.deletedSampleIds) {
            val row = known[id]
            if (row?.entry?.deleted != true) writes += deletedSample(item, id, row?.entry?.at ?: item.hourStart, row?.record)
        }
        for (activity in item.activities) {
            if (knownChanges[activity.id] == null && activity.id !in item.deletedTransitionIds) writes += liveTransition(item, activity)
        }
        for (id in item.deletedTransitionIds) {
            val row = knownChanges[id]
            if (row?.entry?.deleted != true) writes += deletedTransition(item, id, row?.entry?.at ?: item.hourStart, row?.record)
        }
        if (writes.isNotEmpty()) access.put(*writes.toTypedArray())
    }

    override suspend fun applyDownloaded(item: LocationHourPart): RemoteOutcome {
        replaceLocal(item)
        return RemoteOutcome.STORED
    }

    /** The part's record was deleted from the space, so the samples this device holds for it are deleted here too. */
    override suspend fun deleteDownloaded(entityId: String) {
        val (device, hour, number) = parseHourItemId(entityId) ?: return
        val current = read(device, hour)
        val part = current.hour?.parts()?.getOrNull(number - 1) ?: return
        val samples = current.sampleRows.associateBy { it.entry.id }
        val changes = current.transitionRows.associateBy { it.entry.id }
        val writes =
            part.observations.mapNotNull { sample -> samples[sample.id]?.let { deletedSample(part, sample.id, it.entry.at, it.record) } } +
                part.activities.mapNotNull { change ->
                    changes[change.id]?.let { deletedTransition(part, change.id, it.entry.at, it.record) }
                }
        if (writes.isNotEmpty()) access.put(*writes.toTypedArray())
    }

    private suspend fun liveSample(
        part: LocationHourPart,
        observation: LocationObservation,
    ) = HistoryRecord(
        id = SAMPLE_PREFIX + observation.id,
        recordType = SAMPLE,
        payload = access.encode(HistoryPayload.Observation(observation.copy(ownerId = access.scope().owner))),
        deviceId = part.deviceId,
        deviceVersion = 1,
        serverVersion = part.syncVersion,
        dirty = false,
        observedAtMillis = observation.timestamp.toEpochMilliseconds(),
    )

    private fun deletedSample(
        part: LocationHourPart,
        id: String,
        at: Instant,
        previous: HistoryRecord?,
    ) = HistoryRecord(
        id = SAMPLE_PREFIX + id,
        recordType = SAMPLE,
        payload = null,
        deviceId = part.deviceId,
        deviceVersion = (previous?.deviceVersion ?: 0) + 1,
        serverVersion = maxOf(part.syncVersion, previous?.serverVersion ?: 0),
        deleted = true,
        dirty = false,
        observedAtMillis = at.toEpochMilliseconds(),
    )

    private suspend fun liveTransition(
        part: LocationHourPart,
        activity: ActivityObservation,
    ) = HistoryRecord(
        id = activityHistoryRecordId(activity.id),
        recordType = TRANSITION,
        payload = access.encode(HistoryPayload.Activity(activity.copy(ownerId = access.scope().owner))),
        deviceId = part.deviceId,
        deviceVersion = 1,
        serverVersion = part.syncVersion,
        dirty = false,
        observedAtMillis = activity.timestamp.toEpochMilliseconds(),
    )

    /**
     * A deleted activity change keeps only its id and time, in a payload that says nothing else,
     * because its stored id is a digest the id can't be recovered from.
     */
    private suspend fun deletedTransition(
        part: LocationHourPart,
        id: String,
        at: Instant,
        previous: HistoryRecord?,
    ) = HistoryRecord(
        id = activityHistoryRecordId(id),
        recordType = TRANSITION,
        payload =
            access.encode(
                HistoryPayload.Activity(ActivityObservation(id, access.scope().owner, part.deviceId, at, "UNKNOWN", "EXIT")),
            ),
        deviceId = part.deviceId,
        deviceVersion = (previous?.deviceVersion ?: 0) + 1,
        serverVersion = maxOf(part.syncVersion, previous?.serverVersion ?: 0),
        deleted = true,
        dirty = false,
        observedAtMillis = at.toEpochMilliseconds(),
    )

    private class SampleRow(
        val record: HistoryRecord,
        val entry: HourSample,
    )

    private class TransitionRow(
        val record: HistoryRecord,
        val entry: HourTransition,
    )

    private class Read(
        val hour: LocationHour?,
        val sampleRows: List<SampleRow>,
        val transitionRows: List<TransitionRow>,
    )

    private suspend fun read(
        device: String,
        hour: Instant,
    ): Read {
        val scope = access.scope()
        val start = hour.toEpochMilliseconds()
        val end = start + MILLIS_PER_HOUR
        val sampleRows =
            access.store.recordsBetween(scope.owner, scope.origin, SAMPLE, device, start, end).mapNotNull { record ->
                sampleEntry(record)?.let { SampleRow(record, it) }
            }
        val transitionRows =
            access.store.recordsBetween(scope.owner, scope.origin, TRANSITION, device, start, end).mapNotNull { record ->
                transitionEntry(record)?.let { TransitionRow(record, it) }
            }
        if (sampleRows.isEmpty() && transitionRows.isEmpty()) return Read(null, emptyList(), emptyList())
        val loaded =
            LocationHour(
                deviceId = device,
                hourStart = hour,
                samples = sampleRows.map { it.entry }.sortedWith(LocationHour.sampleOrder),
                transitions = transitionRows.map { it.entry }.sortedWith(LocationHour.transitionOrder),
            )
        return Read(loaded, sampleRows, transitionRows)
    }

    private fun sampleEntry(record: HistoryRecord): HourSample? {
        if (record.deleted) {
            val at = record.observedAtMillis ?: return null
            return HourSample(record.id.removePrefix(SAMPLE_PREFIX), Instant.fromEpochMilliseconds(at))
        }
        val observation = (access.payloadOf(record) as? HistoryPayload.Observation)?.value ?: return null
        return HourSample(observation.id, observation.timestamp, observation)
    }

    private fun transitionEntry(record: HistoryRecord): HourTransition? {
        val activity = (access.payloadOf(record) as? HistoryPayload.Activity)?.value ?: return null
        if (record.deleted) {
            return HourTransition(
                activity.id,
                record.observedAtMillis?.let(Instant::fromEpochMilliseconds) ?: activity.timestamp,
            )
        }
        return HourTransition(activity.id, activity.timestamp, activity)
    }

    /** [this] at the precision the records keep, without its version. */
    private fun LocationHourPart.stored(): LocationHourPart =
        copy(observations = observations.map(LocationHour::atRecordPrecision), syncVersion = 0)

    private companion object {
        const val SAMPLE = "observation"
        const val SAMPLE_PREFIX = "observation:"
        const val TRANSITION = "activity"
    }
}
