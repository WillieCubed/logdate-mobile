package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.wear.reminders.EveningReminderWorker
import app.logdate.wear.reminders.WatchReminderPreferences
import app.logdate.wear.reminders.WorkManagerEveningReminderWork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EveningReminderWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = WorkManager.getInstance(context)

    @Before
    @After
    fun clearWaitingChecks() {
        manager.cancelUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME).result.get()
    }

    @Test
    fun a_new_schedule_replaces_the_old_waiting_check() {
        val work = WorkManagerEveningReminderWork(manager)
        work.runAfter(TimeUnit.HOURS.toMillis(1))
        work.runAfter(TimeUnit.HOURS.toMillis(2))
        assertEquals(1, waiting().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun scheduling_after_the_current_run_keeps_its_parent() {
        val work = WorkManagerEveningReminderWork(manager)
        work.runAfter(TimeUnit.HOURS.toMillis(1))
        work.runAfter(TimeUnit.HOURS.toMillis(2), replace = false)
        val pending = waiting()
        assertEquals(1, pending.count { it.state == WorkInfo.State.ENQUEUED })
        assertEquals(1, pending.count { it.state == WorkInfo.State.BLOCKED })
    }

    @Test
    fun disabled_reminders_complete_without_scheduling_more() =
        runBlocking {
            val preferences = KoinJavaComponent.getKoin().get<WatchReminderPreferences>()
            val previous = preferences.enabled.value
            try {
                preferences.setEnabled(false)
                val result = TestListenableWorkerBuilder<EveningReminderWorker>(context).build().doWork()
                assertEquals(ListenableWorker.Result.success(), result)
                assertTrue(waiting().isEmpty())
            } finally {
                preferences.setEnabled(previous)
            }
        }

    private fun waiting() =
        manager
            .getWorkInfosForUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME)
            .get()
            .filter { !it.state.isFinished }
}
