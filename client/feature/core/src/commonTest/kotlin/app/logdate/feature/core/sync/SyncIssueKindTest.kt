package app.logdate.feature.core.sync

import app.logdate.client.sync.InterruptedUploadException
import app.logdate.client.sync.MissingMediaException
import app.logdate.client.sync.metadata.SyncDeadLetterReason
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.effectiveReason
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

        assertEquals(SyncDeadLetterReason.APP_CLOSED, record.effectiveReason())
    }

    @Test
    fun `an entry whose file is gone is explained as a missing file`() {
        val cause = IllegalStateException("open failed: ENOENT (No such file or directory)")
        val record = setAside(MissingMediaException("/files/audio_notes/recording.m4a", cause).message.orEmpty())

        assertEquals(SyncDeadLetterReason.MISSING_FILE, record.effectiveReason())
    }

    @Test
    fun `any other failure is explained as a failed upload`() {
        val record = setAside("Failed to upload content: HTTP 500")

        assertEquals(SyncDeadLetterReason.UNKNOWN, record.effectiveReason())
    }

    @Test
    fun `a server outage is explained as a server problem`() {
        assertEquals(SyncDeadLetterReason.SERVER_UNAVAILABLE, setAside("An unknown error occurred: Service Unavailable").effectiveReason())
        assertEquals(SyncDeadLetterReason.SERVER_UNAVAILABLE, setAside("HTTP 503").effectiveReason())
    }

    @Test
    fun `an expired session is explained as a sign in problem`() {
        assertEquals(SyncDeadLetterReason.SIGN_IN_REQUIRED, setAside("HTTP 401 Unauthorized").effectiveReason())
    }
}
