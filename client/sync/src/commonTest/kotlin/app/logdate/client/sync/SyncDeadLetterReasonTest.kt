package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.metadata.SyncDeadLetterReason
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.effectiveReason
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDeadLetterReasonTest {
    @Test
    fun `server refusal keeps its status category when saved`() {
        assertEquals(
            SyncDeadLetterReason.SERVER_UNAVAILABLE,
            classifySyncFailure(CloudApiException("UNKNOWN_ERROR", "Service Unavailable", statusCode = 503)),
        )
    }

    @Test
    fun `authentication failure is distinct from a server outage`() {
        assertEquals(
            SyncDeadLetterReason.SIGN_IN_REQUIRED,
            classifySyncFailure(CloudApiException("UNAUTHORIZED", "Expired", statusCode = 401)),
        )
    }

    @Test
    fun `old records without a typed reason retain known failure categories`() {
        val oldRecord =
            SyncDeadLetterRecord(
                id = "NOTE:1",
                entityType = "NOTE",
                entityId = "1",
                operation = "CREATE",
                retryCount = 9,
                lastError = "An unknown error occurred: Service Unavailable",
                failedAt = 0L,
            )

        assertEquals(SyncDeadLetterReason.SERVER_UNAVAILABLE, oldRecord.effectiveReason())
    }
}
