package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

/** The watch asks for the phone's whole library window, with `MessageClient.sendRequest` to [WearPaths.WINDOW_RESYNC]. */
@Serializable
data class WindowResyncRequest(
    val schema: Int = WearProtocol.WINDOW_SCHEMA,
    val context: PeerDeliveryContext,
)

/** One record in the window and the version published, so the watch can tell which copies it lacks. */
@Serializable
data class WindowMemberRef(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val revision: Long = 0,
)

/** The phone's whole window. Anything the watch has from the window and this does not list has left it. */
@Serializable
data class WindowResyncResponse(
    val schema: Int = WearProtocol.WINDOW_SCHEMA,
    val context: PeerDeliveryContext,
    val members: List<WindowMemberRef>,
    val publishedAt: Long,
    val revision: Long = 0,
)

/** Link provenance and time accompany every window removal. */
object WindowTombstoneDataMap {
    const val KEY_REMOVED_AT = "removedAt"
    const val KEY_CONTEXT = "context"
    const val KEY_REVISION = "windowRevision"

    fun toDataMap(
        removedAt: Long,
        context: PeerDeliveryContext,
        revision: Long = 0,
    ): Map<String, String> =
        mapOf(
            KEY_REMOVED_AT to removedAt.toString(),
            KEY_CONTEXT to WearJson.encode(PeerDeliveryContext.serializer(), context),
            KEY_REVISION to revision.toString(),
        )
}
