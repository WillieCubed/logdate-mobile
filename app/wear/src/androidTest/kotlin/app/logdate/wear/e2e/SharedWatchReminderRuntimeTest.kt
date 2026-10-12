package app.logdate.wear.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.wear.reminders.SharedPreferencesWatchReminderPreferences
import app.logdate.wear.reminders.SharedWatchReminderSettings
import app.logdate.wear.reminders.WatchReminderPreferences
import app.logdate.wear.reminders.WatchReminders
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class SharedWatchReminderRuntimeTest {
    @Test
    fun nativePreferencesRetainTheFirstObservedPeerTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "peer-reminder-test-${Uuid.random()}"
        try {
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            SharedPreferencesWatchReminderPreferences(preferences).mergePhoneReminderAt(
                "phone",
                2_000,
                Instant.fromEpochMilliseconds(1_000),
            )
            val reopened = SharedPreferencesWatchReminderPreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE))
            reopened.mergePhoneReminderAt("phone", 2_000, Instant.fromEpochMilliseconds(1_500))
            assertEquals(Instant.fromEpochMilliseconds(1_000), reopened.lastReminderAt())
            assertEquals(2_000L, preferences.getLong("phone_reminder_at:phone", 0))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun watchSwitchPublishesThroughInstalledMirrorAndDataClient() =
        runBlocking {
            val koin = KoinJavaComponent.getKoin()
            val storage = koin.get<KeyValueStorage>()
            val before = storage.getString(StampedPreferenceStore.STORAGE_KEY)
            val local = koin.get<WatchReminderPreferences>()
            val enabledBefore = local.enabled.value
            val reminders = koin.get<WatchReminders>()
            koin.get<SharedWatchReminderSettings>()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val data = Wearable.getDataClient(context)
            val node =
                Wearable
                    .getNodeClient(context)
                    .localNode
                    .await()
                    .id
            try {
                reminders.setEnabled(true)
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
                        if (published?.shared?.get("reminders.enabled")?.value == JsonPrimitive(true)) break
                        delay(50)
                    }
                }
                assertEquals(true, local.enabled.value)
            } finally {
                reminders.setEnabled(enabledBefore)
                if (before ==
                    null
                ) {
                    storage.remove(StampedPreferenceStore.STORAGE_KEY)
                } else {
                    storage.putString(StampedPreferenceStore.STORAGE_KEY, before)
                }
            }
        }
}
