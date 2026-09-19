package app.logdate.feature.core.sync

import app.logdate.client.sync.InterruptedUploadException
import app.logdate.client.sync.MissingMediaException
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which explanation a set-aside entry gets in Sync Issues.
 *
 * An entry set aside because LogDate closed while uploading it used to be told it "was tried
 * several times without success" and to retry once back online - neither of which was true, and
 * the second of which sends the user after a connection problem they do not have.
 */
class SyncIssueKindTest {
    private fun setAside(lastError: String) =
        SyncDeadLetterRecord(
            id = "NOTE:1",
            entityType = "NOTE",
            entityId = "1",
            operation = "CREATE",
            retryCount = 2,
            lastError = lastError,
            failedAt = 0L,
        )

    @Test
    fun `an entry set aside for closing the app is explained as that`() {
        val record = setAside(InterruptedUploadException(unfinishedAttempts = 2).message.orEmpty())

        assertEquals(SyncIssueKind.APP_CLOSED, record.issueKind())
    }

    @Test
    fun `an entry whose file is gone is explained as a missing file`() {
        val cause = IllegalStateException("open failed: ENOENT (No such file or directory)")
        val record = setAside(MissingMediaException("/files/audio_notes/recording.m4a", cause).message.orEmpty())

        assertEquals(SyncIssueKind.MISSING_FILE, record.issueKind())
    }

    @Test
    fun `any other failure is explained as a failed upload`() {
        val record = setAside("Failed to upload content: HTTP 500")

        assertEquals(SyncIssueKind.FAILED, record.issueKind())
    }
}
