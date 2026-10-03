package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.WatchSyncWorker
import app.logdate.wear.sync.WorkManagerWatchSyncWork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Runs the watch's sync worker on a Wear OS emulator against the app's real dependency graph.
 *
 * The managed emulator has no paired phone, so a run finds no phone in reach. It must succeed, spend
 * no attempts on what is waiting, and set no further run, because the phone coming into reach starts
 * a sync by itself.
 */
@RunWith(AndroidJUnit4::class)
class WatchSyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager = WorkManager.getInstance(context)

    @Before
    fun clearScheduledRuns() {
        workManager.cancelUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).result.get()
    }

    @After
    fun cancelScheduledRuns() {
        workManager.cancelUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).result.get()
    }

    @Test
    fun `a run with no phone in reach succeeds without spending attempts`() =
        runBlocking {
            val metadata = KoinJavaComponent.getKoin().get<SyncMetadataService>()
            val noteId = Uuid.random().toString()
            metadata.enqueuePending(noteId, EntityType.NOTE, PendingOperation.CREATE)
            try {
                val result = TestListenableWorkerBuilder<WatchSyncWorker>(context).build().doWork()

                assertEquals(ListenableWorker.Result.success(), result)
                assertEquals(0, metadata.getPendingUploads(EntityType.NOTE).single { it.entityId == noteId }.retryCount)
                val waiting = workManager.getWorkInfosForUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).get()
                assertTrue(waiting.none { it.state == WorkInfo.State.ENQUEUED }, "No run should be set without a phone")
            } finally {
                metadata.markAsSynced(noteId, EntityType.NOTE, Clock.System.now(), 0L)
            }
        }

    @Test
    fun `scheduling again replaces the waiting run`() {
        val work = WorkManagerWatchSyncWork(workManager)

        work.runAfter(TimeUnit.HOURS.toMillis(1))
        work.runAfter(TimeUnit.HOURS.toMillis(2))

        val waiting = workManager.getWorkInfosForUniqueWork(WatchSyncWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, waiting.count { it.state == WorkInfo.State.ENQUEUED })
    }
}
