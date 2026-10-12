package app.logdate.reminders.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.StateDocumentCodec
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class ReminderHistoryRuntimeTest {
    @Test
    fun nativeStorageRetainsPostedHistoryAfterReopening() =
        runBlocking {
            val storage = KoinJavaComponent.getKoin().get<KeyValueStorage>()
            val before = storage.getString(ReminderPostedHistory.STORAGE_KEY)
            try {
                storage.remove(ReminderPostedHistory.STORAGE_KEY)
                val history = KoinJavaComponent.getKoin().get<ReminderPostedHistory>()
                val posted = Clock.System.now()
                history.recordPostedAt(posted)
                assertEquals(posted.toEpochMilliseconds(), ReminderPostedHistory(storage).latest()?.toEpochMilliseconds())
                history.adoptPeer("history-test-watch", posted.toEpochMilliseconds() + 60_000)
                val observed = history.latest()
                ReminderPostedHistory(storage).adoptPeer("history-test-watch", posted.toEpochMilliseconds() + 60_000)
                assertEquals(observed, ReminderPostedHistory(storage).latest())
            } finally {
                if (before == null) {
                    storage.remove(ReminderPostedHistory.STORAGE_KEY)
                } else {
                    storage.putString(ReminderPostedHistory.STORAGE_KEY, before)
                }
            }
        }

    @Test
    fun installedPhonePublishesDurablePostedHistory() =
        runBlocking {
            assumeTrue(
                "Requires a paired phone emulator with Wearable.API",
                InstrumentationRegistry.getArguments().getString("requireWearableDataLayer") == "true",
            )
            val koin = KoinJavaComponent.getKoin()
            val storage = koin.get<KeyValueStorage>()
            val before = storage.getString(ReminderPostedHistory.STORAGE_KEY)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val data = Wearable.getDataClient(context)
            val node =
                Wearable
                    .getNodeClient(context)
                    .localNode
                    .await()
                    .id
            try {
                storage.remove(ReminderPostedHistory.STORAGE_KEY)
                val history = koin.get<ReminderPostedHistory>()
                val posted = Clock.System.now()
                history.recordPostedAt(posted)
                assertEquals(posted.toEpochMilliseconds(), ReminderPostedHistory(storage).latest()?.toEpochMilliseconds())
                withTimeout(10_000) {
                    while (true) {
                        val items = data.getDataItems(Uri.parse("wear://$node/logdate/state")).await()
                        val published =
                            try {
                                items.firstOrNull()?.let { item ->
                                    val text = DataMapItem.fromDataItem(item).dataMap.getString(StateDocumentCodec.KEY) ?: return@let null
                                    (StateDocumentCodec.decode(text) as? Decoded.Supported)?.value
                                }
                            } finally {
                                items.release()
                            }
                        if (published?.lastReminderAt == posted.toEpochMilliseconds()) break
                        delay(50)
                    }
                }
            } finally {
                if (before == null) {
                    storage.remove(ReminderPostedHistory.STORAGE_KEY)
                } else {
                    storage.putString(ReminderPostedHistory.STORAGE_KEY, before)
                }
            }
        }
}
