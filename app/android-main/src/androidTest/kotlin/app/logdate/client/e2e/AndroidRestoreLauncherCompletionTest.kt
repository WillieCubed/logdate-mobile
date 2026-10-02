package app.logdate.client.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.RestoreError
import app.logdate.feature.core.restore.RestoreOutcome
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRestoreLauncherCompletionTest {
    @Test
    fun `successive cloud restore work ids each deliver one outcome`() {
        val launcher = AndroidRestoreLauncher(ApplicationProvider.getApplicationContext())
        val outcomes = mutableListOf<RestoreOutcome>()
        launcher.setRestoreCompletionCallback(outcomes::add)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        launcher.beginRestoreWork(first)
        launcher.completeRestoreForWork(first, RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
        launcher.beginRestoreWork(second)
        launcher.completeRestoreForWork(first, RestoreOutcome.Cancelled)
        launcher.completeRestoreForWork(second, RestoreOutcome.Cancelled)

        assertEquals(2, outcomes.size)
        assertEquals(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED), outcomes[0])
        assertEquals(RestoreOutcome.Cancelled, outcomes[1])
    }
}
