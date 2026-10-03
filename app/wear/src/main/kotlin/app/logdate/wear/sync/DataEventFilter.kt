package app.logdate.wear.sync

import com.google.android.gms.wearable.DataEvent

/**
 * Whether the watch acts on a data event: only a change another device made.
 *
 * The Data Layer reports a device's own writes back to it, and a data item's URI host is the node
 * that created it, so an event from [localNodeId] is the watch hearing its own write. A deletion
 * event has only a URI; LogDate announces removals with explicit `/delete` items instead. When the
 * watch's own node is unknown, every change is acted on, as before this check existed.
 */
internal fun isChangeFromAnotherDevice(
    eventType: Int,
    sourceNodeId: String?,
    localNodeId: String?,
): Boolean = eventType == DataEvent.TYPE_CHANGED && (localNodeId == null || sourceNodeId != localNodeId)
