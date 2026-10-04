package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.sync.recovery.DownloadScope
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/** Re-encrypt readable local copies without replacing a newer pending edit or deletion. */
internal suspend fun enqueueUnreadableRepairs(
    entityType: EntityType,
    logLabel: String,
    unreadable: List<Uuid>,
    heldLocally: Set<Uuid>,
    selected: DownloadScope?,
    observedVersions: Map<Uuid, Long>,
    syncMetadataService: SyncMetadataService,
    unreadableCloudRecordStore: UnreadableCloudRecordStore,
    ensureScope: (DownloadScope?) -> Unit,
) {
    if (unreadable.isEmpty()) return
    val (repairable, cloudOnly) = unreadable.partition { it in heldLocally }
    val pendingById = syncMetadataService.getPendingUploads(entityType).associateBy { it.entityId }
    for (id in repairable) {
        ensureScope(selected)
        val version = observedVersions[id]
        if (selected != null && version == null) continue
        val pendingCreate = pendingById[id.toString()]?.takeIf { it.operation == PendingOperation.CREATE }
        if (pendingCreate != null && version != null) {
            syncMetadataService.bindCreateToServerVersion(entityType, pendingCreate, version)
        }
        syncMetadataService.enqueueRepairIfAbsent(id.toString(), entityType, version, selected?.origin)
    }
    if (cloudOnly.isNotEmpty()) {
        unreadableCloudRecordStore.record(entityType, cloudOnly)
    }
    Napier.w(
        "${repairable.size} unreadable $logLabel(s) queued to re-upload from this device; " +
            "${cloudOnly.size} exist only in the cloud and were left in place",
    )
}
