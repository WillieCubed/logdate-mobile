package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.v2.receipts.PhoneReceiptBook
import app.logdate.client.sync.v2.receipts.ReceiptAcknowledger
import app.logdate.client.sync.v2.receipts.WatchCaptureCollisionPolicy
import app.logdate.client.sync.v2.receipts.WatchReceiptNotifier
import app.logdate.client.sync.v2.receipts.WatchReceiptResponder
import app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class PhoneWatchReceiptsRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recordChangesReachThePhoneListener() {
        assertListener("DATA_CHANGED", "/logdate/v2/w2p/record/note/${Uuid.random()}")
    }

    @Test
    fun mediaChannelsReachThePhoneListener() {
        assertListener("CHANNEL_EVENT", "/logdate/v2/w2p/media/note/${Uuid.random()}")
    }

    @Test
    fun receiptRequestsReachThePhoneListener() {
        assertListener("REQUEST_RECEIVED", WearPaths.RECEIPTS)
    }

    @Test
    fun installedApplicationResolvesTheReceiptAndIngestionGraph() {
        val koin = KoinJavaComponent.getKoin()
        assertNotNull(koin.get<PeerDeliveryLedger>())
        assertIs<WatchCaptureCollisionPolicy>(koin.get<CreateCollisionPolicy>())
        assertNotNull(koin.get<PhoneReceiptBook>())
        assertNotNull(koin.get<ReceiptAcknowledger>())
        assertNotNull(koin.get<WatchReceiptResponder>())
        assertNotNull(koin.get<WatchReceiptNotifier>())
        assertNotNull(koin.get<WatchRecordIngestorV2>())
    }

    private fun assertListener(
        action: String,
        path: String,
    ) {
        val intent = Intent("com.google.android.gms.wearable.$action", "wear://watch$path".toUri()).setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        assertEquals(listOf("app.logdate.client.sync.PhoneDataLayerListenerService"), services.map { it.serviceInfo.name })
    }
}
