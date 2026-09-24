package app.logdate.client.sync

import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupRequestStateMapperTest {
    @Test
    fun `queued work is not reported as running`() {
        assertEquals(
            BackupRequestState.QUEUED,
            backupRequestState(listOf(BackupWorkSnapshot(WorkInfo.State.ENQUEUED, 0))),
        )
    }

    @Test
    fun `enqueued retry is distinct from a new request`() {
        assertEquals(
            BackupRequestState.RETRYING,
            backupRequestState(listOf(BackupWorkSnapshot(WorkInfo.State.ENQUEUED, 2))),
        )
    }

    @Test
    fun `a running request takes priority over old terminal work`() {
        assertEquals(
            BackupRequestState.RUNNING,
            backupRequestState(
                listOf(
                    BackupWorkSnapshot(WorkInfo.State.SUCCEEDED, 0),
                    BackupWorkSnapshot(WorkInfo.State.RUNNING, 0),
                ),
            ),
        )
    }

    @Test
    fun `terminal worker failure remains visible even when sync never started`() {
        assertEquals(
            BackupRequestState.FAILED,
            backupRequestState(listOf(BackupWorkSnapshot(WorkInfo.State.FAILED, 1))),
        )
    }

    @Test
    fun `completed worker remains distinguishable from no backup request`() {
        assertEquals(
            BackupRequestState.COMPLETED,
            backupRequestState(listOf(BackupWorkSnapshot(WorkInfo.State.SUCCEEDED, 0))),
        )
    }
}
