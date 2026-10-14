package app.logdate.wear.sync.v2.removal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.reminders.ActivityRegistration
import app.logdate.wear.reminders.EveningReminderScheduler
import app.logdate.wear.v2.playback.MomentPlayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WatchDataWiperTest {
    @Test
    fun `server revocation cannot bypass a prepared removal with newly pending captures`() =
        runTest {
            val marker = "prepared:link"
            val secure = mockk<SecureStorage>()
            coEvery { secure.getString(WatchRemovalLock.KEY) } returns marker
            val database = mockk<LogDateDatabase>()
            val outbox = mockk<SyncMetadataDao>()
            every { database.syncMetadataDao() } returns outbox
            coEvery { outbox.getAllPending() } returns listOf(mockk<PendingUploadEntity>())
            val activity = mockk<ActivityRegistration>()
            val wiper =
                WatchDataWiper(
                    context = mockk<Context>(),
                    database = database,
                    preferences = mockk<DataStore<Preferences>>(),
                    secure = secure,
                    sessions = mockk<SessionStorage>(),
                    lock = WatchRemovalLock(secure),
                    activity = activity,
                    evening = mockk<EveningReminderScheduler>(),
                    identity = mockk<IdentityKeyManager>(),
                    owner = mockk<CanonicalOwnerProvider>(),
                    mediaKeys = mockk<MediaPayloadKeyProvider>(),
                    recorder = mockk<WearRecorder>(),
                    player = mockk<MomentPlayer>(),
                    caches = mockk<WatchRemovalCaches>(),
                )

            assertFailsWith<IllegalStateException> { wiper.wipe() }
            assertEquals(marker, secure.getString(WatchRemovalLock.KEY))
            coVerify(exactly = 0) { activity.unregister() }
        }
}
