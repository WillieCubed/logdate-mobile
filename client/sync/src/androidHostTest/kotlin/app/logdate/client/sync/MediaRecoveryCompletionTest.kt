package app.logdate.client.sync

import androidx.work.Data
import app.logdate.client.sync.metadata.UploadScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaRecoveryCompletionTest {
    @Test
    fun `full recovery proof remains bound to the original owner and server`() {
        val scope = UploadScope("owner-a", "https://a.example")
        val output = mediaRecoveryCompletionOutput(AndroidLogDateSyncWorker.SYNC_TYPE_FULL, scope)
        assertEquals(scope, completedMediaRecoveryScope(Data.fromByteArray(output.toByteArray())))
    }

    @Test
    fun `partial legacy and unauthenticated success cannot prove full recovery`() {
        val scope = UploadScope("owner", "https://sync.example")
        for (type in listOf(AndroidLogDateSyncWorker.SYNC_TYPE_UPLOAD, AndroidLogDateSyncWorker.SYNC_TYPE_DOWNLOAD, "legacy")) {
            assertNull(completedMediaRecoveryScope(mediaRecoveryCompletionOutput(type, scope)))
        }
        assertNull(completedMediaRecoveryScope(Data.EMPTY))
        assertNull(completedMediaRecoveryScope(mediaRecoveryCompletionOutput(AndroidLogDateSyncWorker.SYNC_TYPE_FULL, null)))
    }
}
