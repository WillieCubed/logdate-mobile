package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.MediaTooLargeException
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteMediaUploadErrorClassificationTest {
    @Test
    fun `only explicit cloud quota refusals become storage errors`() {
        assertEquals(
            SyncErrorType.STORAGE_ERROR,
            mediaUploadSyncErrorType(CloudApiException("QUOTA_EXCEEDED", "quota")),
        )
        assertEquals(
            SyncErrorType.STORAGE_ERROR,
            mediaUploadSyncErrorType(CloudApiException("STORAGE_LIMIT_EXCEEDED", "quota")),
        )
        assertEquals(
            SyncErrorType.MEDIA_TOO_LARGE_ERROR,
            mediaUploadSyncErrorType(MediaTooLargeException("fixture exceeds upload limit")),
        )
        assertEquals(SyncErrorType.UNKNOWN_ERROR, mediaUploadSyncErrorType(IllegalStateException("provider unavailable")))
        assertEquals(
            SyncErrorType.UNKNOWN_ERROR,
            mediaUploadSyncErrorType(CloudApiException("MEDIA_STORAGE_UNAVAILABLE", "temporary failure")),
        )
    }
}
