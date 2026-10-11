package app.logdate.client.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem

/**
 * Snapshot of a data event's path, payload, type and writer.
 * Created before [DataEventBuffer] is recycled by the system.
 */
data class PhoneDataLayerSnapshotEvent(
    val path: String,
    val data: Map<String, String>,
    val type: Int = DataEvent.TYPE_CHANGED,
    /** The node that wrote the data item: the host of its URI. */
    val sourceNodeId: String? = null,
) {
    /**
     * Whether the phone acts on this event: only a change another device made. The Data Layer
     * reports the phone's own writes, such as the notes it publishes for the watch, back to the
     * phone. A deletion has no payload; LogDate announces removals with explicit `/delete` items.
     */
    fun isChangeFromAnotherDevice(localNodeId: String?): Boolean =
        type == DataEvent.TYPE_CHANGED && (localNodeId == null || sourceNodeId != localNodeId)
}

data class PhoneDataLayerSyncDependencies(
    val noteIngestor: WatchNoteIngestor,
    val journalIngestor: WatchJournalIngestor,
    val healthSnapshotDao: HealthSnapshotDao,
    val peerStates: PeerStateStore,
    val recordIngestorV2: WatchRecordIngestorV2? = null,
)

internal fun DataEvent.stringPayload(): Map<String, String> {
    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
    val stringMap = mutableMapOf<String, String>()
    for (key in dataMap.keySet()) {
        if (key.startsWith("_")) continue
        dataMap.getString(key)?.let { stringMap[key] = it }
    }
    return stringMap
}
