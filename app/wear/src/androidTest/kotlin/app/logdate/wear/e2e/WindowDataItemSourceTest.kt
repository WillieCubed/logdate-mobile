package app.logdate.wear.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.v2.window.DataClientWindowItemSource
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Uses the emulator's real DataClient persistence; this is not a paired transport test. */
@RunWith(AndroidJUnit4::class)
class WindowDataItemSourceTest {
    @Test fun resyncQueriesPersistedLiveItemsAndDeletionMarkers() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val data = Wearable.getDataClient(context)
            val local =
                Wearable
                    .getNodeClient(context)
                    .localNode
                    .await()
                    .id
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), local, "source-test-watch", LinkMode.PAIR)
            val id = Uuid.random()
            val paths = listOf(WearPaths.windowItem(RecordKind.MOMENT, id, peer), WearPaths.windowTombstone(RecordKind.MOMENT, id, peer))
            val written = mutableListOf<android.net.Uri>()
            try {
                for (path in paths) {
                    val request = PutDataMapRequest.create(path)
                    request.dataMap.putString("sourceTest", id.toString())
                    written += data.putDataItem(request.asPutDataRequest()).await().uri
                }
                val records = DataClientWindowItemSource(data).items(peer).filter { it.path in paths }
                assertEquals(paths.toSet(), records.map { it.path }.toSet())
                assertEquals(setOf(local), records.map { it.sourceNodeId }.toSet())
                assertEquals(setOf(id.toString()), records.map { it.data["sourceTest"] }.toSet())
            } finally {
                for (uri in written) data.deleteDataItems(uri).await()
            }
        }
}
