package app.logdate.reminders.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.ReminderSignalObserver
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ReminderSignalRuntimeTest {
    @Test fun savedRoomMarkQueuesAnotherInstalledEvaluation(): Unit =
        runBlocking {
            val koin = KoinJavaComponent.getKoin()
            val context: Context = ApplicationProvider.getApplicationContext()
            val work = WorkManager.getInstance(context)
            val flags = koin.get<FeatureFlagStore>()
            val shared = koin.get<WatchPreferencesRepository>()
            val marks = koin.get<SyncableMarkedMomentRepository>()
            val previousFlag = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val previousReminders = shared.get(SharedPreferenceKey.RemindersEnabled)
            val previousPlaces = shared.get(SharedPreferenceKey.CapturePlaces)
            val id = Uuid.random()

            suspend fun infos() =
                withContext(Dispatchers.IO) {
                    work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get(10, TimeUnit.SECONDS)
                }

            suspend fun awaitSuccessfulNewEvaluation(previous: List<WorkInfo>) =
                withTimeout(45_000) {
                    while (true) {
                        val finished =
                            infos().firstOrNull { candidate ->
                                previous.none { it.id == candidate.id } && candidate.state.isFinished
                            }
                        if (finished != null) {
                            assertEquals(WorkInfo.State.SUCCEEDED, finished.state, "Installed signal worker must execute successfully")
                            break
                        }
                        delay(100)
                    }
                }
            try {
                shared.set(SharedPreferenceKey.CapturePlaces, false)
                shared.set(SharedPreferenceKey.RemindersEnabled, true)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                koin.get<ReminderSignalObserver>().start()
                val before = infos()
                awaitSuccessfulNewEvaluation(before)
                val initial = infos()
                val now = Clock.System.now()
                assertNotNull(
                    marks.applyFromSync(
                        MarkedMoment(id, now, "UTC", null, null, MarkState.OPEN, "", emptySet(), "signal-runtime-fixture", now, 1),
                    ),
                )
                assertNotNull(marks.getMark(id))
                awaitSuccessfulNewEvaluation(initial)
            } finally {
                shared.set(SharedPreferenceKey.RemindersEnabled, previousReminders)
                shared.set(SharedPreferenceKey.CapturePlaces, previousPlaces)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previousFlag)
                marks.applyDeletionFromSync(id, Clock.System.now(), 2)
            }
        }
}
