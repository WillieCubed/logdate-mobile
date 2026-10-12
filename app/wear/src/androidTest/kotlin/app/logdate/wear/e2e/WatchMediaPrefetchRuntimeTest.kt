package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.sync.v2.media.WatchMediaPrefetchScheduler
import app.logdate.wear.sync.v2.media.WatchMediaPrefetchWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WatchMediaPrefetchRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = WorkManager.getInstance(context)
    private val koin = KoinJavaComponent.getKoin()

    @After fun cancelTestWork() {
        manager.cancelUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME).result.get()
    }

    @Test fun workIsUniqueAndRequiresChargingAndUnmeteredNetwork() =
        runBlocking {
            val scheduler = WatchMediaPrefetchScheduler(manager, kotlinx.coroutines.flow.flowOf(false))
            scheduler.setEnabled(true).result.get()
            scheduler.setEnabled(true).result.get()
            val waiting = manager.getWorkInfosForUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME).get().filter { !it.state.isFinished }
            assertEquals(1, waiting.size)
            assertTrue(waiting.single().constraints.requiresCharging())
            assertEquals(NetworkType.UNMETERED, waiting.single().constraints.requiredNetworkType)
            assertEquals(TimeUnit.HOURS.toMillis(1), waiting.single().periodicityInfo?.repeatIntervalMillis)
            scheduler.setEnabled(false).result.get()
            assertTrue(manager.getWorkInfosForUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME).get().all { it.state.isFinished })
        }

    @Test fun installedWorkerCompletesWithoutFetchingWhenRedesignIsDisabled() =
        runBlocking {
            val flags = koin.get<FeatureFlagStore>()
            val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                val worker = TestListenableWorkerBuilder<WatchMediaPrefetchWorker>(context).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
            } finally {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
            }
        }

    @Test fun appStartupObservesPersistedFlagChangesForPrefetchScheduling() =
        runBlocking {
            val flags = koin.get<FeatureFlagStore>()
            val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            try {
                koin.get<WatchMediaPrefetchScheduler>()
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                waitUntil {
                    manager
                        .getWorkInfosForUniqueWork(
                            WatchMediaPrefetchWorker.UNIQUE_WORK_NAME,
                        ).get()
                        .any { !it.state.isFinished }
                }
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                waitUntil { manager.getWorkInfosForUniqueWork(WatchMediaPrefetchWorker.UNIQUE_WORK_NAME).get().all { it.state.isFinished } }
            } finally {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
            }
        }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "Prefetch scheduling did not follow the persisted flag" }
            Thread.sleep(50)
        }
    }
}
