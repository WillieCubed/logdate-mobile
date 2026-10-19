package app.logdate.client.sync.location

import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.RemoteOutcome
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.SpaceStagedRecords
import app.logdate.client.sync.space.SpaceSync
import app.logdate.client.sync.space.mappers.HistoryKindRecords
import app.logdate.client.sync.space.mappers.LocationHour
import app.logdate.client.sync.space.mappers.PlaceEntryLinkRecordMapper
import app.logdate.client.sync.space.mappers.StoredLocationParts
import app.logdate.client.sync.space.spaceErrorType
import app.logdate.client.sync.space.spaceFetchError
import app.logdate.shared.model.location.HistoryPayload
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** The kinds of location history, in the order they upload: places before the visits and corrections that name them. */
internal val LOCATION_ENTITY_TYPES: List<EntityType> =
    listOf(
        EntityType.PLACE,
        EntityType.PLACE_VISIT,
        EntityType.PLACE_CORRECTION,
        EntityType.PLACE_ENTRY_LINK,
        EntityType.PLACE_JOURNEY,
        EntityType.LOCATION_SAMPLES,
    )

/**
 * Location history syncing through the personal space instead of the location history endpoints.
 *
 * The device keeps location history as history records, each with whether it has uploaded. Uploading
 * first queues the records that haven't, as the items they belong to: a sample or a deletion
 * queues its device's hour, and a place, visit, correction, entry link or journey queues itself.
 * The personal-space upload then writes each queued item as its records and acknowledges the history
 * records it covered. Downloading reads the space's changes for each kind and applies them to the
 * history records, keeping any change that hasn't uploaded yet.
 */
internal class LocationHistorySpaceSync(
    private val space: SpaceSync,
    private val store: HistoryRecordStore,
    private val metadata: SyncMetadataService,
    private val transactions: SyncTransactionManager,
    private val inbox: DownloadInbox?,
    private val uploadsUpdates: Boolean,
    private val savedPlaceIds: suspend () -> List<String>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Whether location history syncs through the personal space right now. */
    suspend fun isActive(): Boolean = space.activeKind(EntityType.LOCATION_SAMPLES) != null

    suspend fun upload(
        owner: String,
        origin: String,
        moreToStage: Boolean,
    ): SyncResult {
        val errors = mutableListOf<SyncError>()
        val queued = enqueueUnsynced(owner, origin, errors)
        var uploaded = 0
        for (type in LOCATION_ENTITY_TYPES) {
            val kind = space.activeKind(type) ?: continue
            val result = uploadKind(kind)
            uploaded += result.uploadedItems
            errors += result.errors
        }
        val more = moreToStage || queued.hasMore || (errors.isEmpty() && store.pending(owner, origin, 1).isNotEmpty())
        return SyncResult(success = errors.isEmpty(), uploadedItems = uploaded, errors = errors, hasMorePending = more)
    }

    private suspend fun uploadKind(kind: SpaceKind<*>): SyncResult {
        @Suppress("UNCHECKED_CAST")
        val typed = kind as SpaceKind<Any>
        val records = kind.local as? HistoryKindRecords<Any> ?: return SyncResult(success = true)
        return space.upload(typed, uploadsUpdates, begin = null) { pending -> records.acknowledgeDeleted(pending.entityId) }
    }

    private class Queued(
        val hasMore: Boolean,
    )

    /** Queues the items that records waiting to upload belong to. */
    private suspend fun enqueueUnsynced(
        owner: String,
        origin: String,
        errors: MutableList<SyncError>,
    ): Queued {
        val waiting = store.pending(owner, origin, WAITING_PER_PASS)
        val items = linkedMapOf<Pair<EntityType, String>, PendingOperation>()
        val hours = linkedSetOf<Pair<String, Instant>>()
        var unroutable = 0
        for (record in waiting) {
            when (val route = routeOf(record)) {
                is Route.Hour -> hours += route.device to route.hourStart
                is Route.Item ->
                    items[route.type to route.id] =
                        if (items[route.type to route.id] == PendingOperation.DELETE) PendingOperation.DELETE else route.operation
                Route.NothingToUpload -> store.acknowledge(owner, origin, record.id, record.deviceVersion, maxOf(1, record.serverVersion))
                Route.NotSyncedHere -> Unit
                Route.Unroutable -> unroutable++
            }
        }
        val samples = space.activeKind(EntityType.LOCATION_SAMPLES)?.local as? StoredLocationParts
        if (samples != null) {
            for ((device, hour) in hours) {
                samples.partsWaiting(device, hour).forEach { items[EntityType.LOCATION_SAMPLES to it] = PendingOperation.UPDATE }
            }
        }
        for ((key, operation) in items) {
            val (type, id) = key
            if (space.activeKind(type) == null) continue
            if (operation == PendingOperation.DELETE) {
                if (!metadata.hasPendingDeletion(id, type)) metadata.enqueuePending(id, type, PendingOperation.DELETE)
            } else {
                metadata.enqueueRepairIfAbsent(id, type)
            }
        }
        savedPlaceIds().forEach { id ->
            val row = store.record(owner, origin, id)
            if (row == null || (row.serverVersion == 0L && !row.dirty)) metadata.enqueueRepairIfAbsent(id, EntityType.PLACE)
        }
        if (unroutable > 0) {
            errors +=
                SyncError(
                    SyncErrorType.UNKNOWN_ERROR,
                    "$unroutable location history records can't be synced from this device",
                    retryable = false,
                )
        }
        return Queued(hasMore = waiting.size == WAITING_PER_PASS)
    }

    private sealed interface Route {
        /** A sample or activity change, which belongs to a part of the hour it happened in. */
        class Hour(
            val device: String,
            val hourStart: Instant,
        ) : Route

        class Item(
            val type: EntityType,
            val id: String,
            val operation: PendingOperation,
        ) : Route

        /** A record the space has no copy of and can't be told about, such as a deleted record whose time was never known. */
        data object NothingToUpload : Route

        /** A kind of record that only the location history endpoints carry. */
        data object NotSyncedHere : Route

        /** A record that no record key can name. */
        data object Unroutable : Route
    }

    private fun routeOf(record: HistoryRecord): Route {
        val operation = if (record.deleted) PendingOperation.DELETE else PendingOperation.UPDATE
        return when (record.recordType) {
            "observation", "activity" -> {
                val at = record.observedAtMillis ?: payloadTime(record) ?: return Route.NothingToUpload
                try {
                    val hour = LocationHour.hourOf(Instant.fromEpochMilliseconds(at))
                    LocationHour.itemId(record.deviceId, hour)
                    Route.Hour(record.deviceId, hour)
                } catch (_: SpaceRecordMappingException) {
                    Route.Unroutable
                }
            }
            "place" -> Route.Item(EntityType.PLACE, record.id, operation)
            "manual" -> Route.Item(EntityType.PLACE_VISIT, record.id, operation)
            "correction" -> Route.Item(EntityType.PLACE_CORRECTION, record.id, operation)
            "journey" -> Route.Item(EntityType.PLACE_JOURNEY, record.id, operation)
            "memory-link" -> linkRoute(record, operation)
            else -> Route.NotSyncedHere
        }
    }

    private fun linkRoute(
        record: HistoryRecord,
        operation: PendingOperation,
    ): Route {
        val link = payloadOf(record)?.let { (it as? HistoryPayload.MemoryLink)?.value } ?: return Route.NothingToUpload
        return try {
            Route.Item(
                EntityType.PLACE_ENTRY_LINK,
                PlaceEntryLinkRecordMapper.keyOf(link.noteId, link.targetEvidenceId).lowercase(),
                operation,
            )
        } catch (_: SpaceRecordMappingException) {
            Route.Unroutable
        }
    }

    private fun payloadOf(record: HistoryRecord): HistoryPayload? =
        record.payload?.let { runCatching { json.decodeFromString(HistoryPayload.serializer(), it) }.getOrNull() }

    private fun payloadTime(record: HistoryRecord): Long? =
        when (val payload = payloadOf(record)) {
            is HistoryPayload.Observation -> payload.value.timestamp.toEpochMilliseconds()
            is HistoryPayload.Activity -> payload.value.timestamp.toEpochMilliseconds()
            else -> null
        }

    /** Applies the location history the personal space changed, one item at a time so one failure leaves the rest applied. */
    suspend fun download(): SyncResult {
        val errors = mutableListOf<SyncError>()
        var downloaded = 0
        inbox.spaceFetchError()?.let { errors += it }
        for (type in LOCATION_ENTITY_TYPES) {
            val kind = space.activeKind(type) ?: continue
            val records = kind.local as? HistoryKindRecords<Any> ?: continue

            @Suppress("UNCHECKED_CAST")
            val staged =
                space.read(kind as SpaceKind<Any>).getOrElse { failure ->
                    errors +=
                        SyncError(
                            failure.spaceErrorType(),
                            "Location history couldn't be read from the personal space: ${failure.message}",
                            failure,
                        )
                    return SyncResult(success = false, downloadedItems = downloaded, errors = errors)
                }
            do {
                val page = staged.page()
                for (item in page.changes) downloaded += apply(staged, records, item, errors)
                for (id in page.deletions) downloaded += delete(staged, records, id, errors)
            } while (page.hasMore)
        }
        return SyncResult(success = errors.isEmpty(), downloadedItems = downloaded, errors = errors)
    }

    private suspend fun apply(
        staged: SpaceStagedRecords<Any>,
        records: HistoryKindRecords<Any>,
        item: Any,
        errors: MutableList<SyncError>,
    ): Int {
        val id = records.entityIdOf(item)
        return try {
            var outcome = RemoteOutcome.DECLINED
            transactions.withTransaction {
                outcome = records.applyDownloaded(item)
                staged.settle(id, outcome)
            }
            if (outcome == RemoteOutcome.STORED) 1 else 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("A location history item couldn't be applied: ${error::class.simpleName}")
            staged.settleFailed(id)
            errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "Location history from another device couldn't be saved", error)
            0
        }
    }

    private suspend fun delete(
        staged: SpaceStagedRecords<Any>,
        records: HistoryKindRecords<Any>,
        id: String,
        errors: MutableList<SyncError>,
    ): Int =
        try {
            transactions.withTransaction {
                records.deleteDownloaded(id)
                staged.settle(id, RemoteOutcome.DELETED)
            }
            1
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("A location history deletion couldn't be applied: ${error::class.simpleName}")
            staged.settleFailed(id)
            errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "A deletion from another device couldn't be saved", error)
            0
        }

    private companion object {
        /** How many records waiting to upload are queued at once. */
        const val WAITING_PER_PASS = 2_000
    }
}
