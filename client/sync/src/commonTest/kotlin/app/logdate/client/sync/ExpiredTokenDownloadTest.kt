package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.JournalChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * A session an hour old makes the server reject reads, and a full sync downloads before it
 * uploads. Without a refresh on that first rejection the whole run failed: the device still looked
 * signed in and quietly stopped backing anything up.
 */
class ExpiredTokenDownloadTest {
    private class ExpiredThenValidApiClient : FakeCloudApiClient() {
        var journalReads = 0
            private set
        private var rejectedOnce = false

        override suspend fun getJournalChanges(
            accessToken: String,
            since: Long,
            limit: Int?,
        ): Result<JournalChangesResponse> {
            journalReads++
            if (!rejectedOnce) {
                rejectedOnce = true
                return Result.failure(
                    CloudApiException("EXPIRED", "Access token expired", statusCode = 401),
                )
            }
            return Result.success(
                JournalChangesResponse(emptyList(), emptyList(), Clock.System.now().toEpochMilliseconds()),
            )
        }
    }

    @Test
    fun `a download rejected for an expired token refreshes and retries instead of failing the sync`() =
        runTest {
            val apiClient = ExpiredThenValidApiClient()
            val syncManager =
                testDefaultSyncManager(
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                )

            val result = syncManager.downloadRemoteChanges()

            assertEquals(
                2,
                apiClient.journalReads,
                "the rejected read should have been retried once with a fresh token",
            )
            assertTrue(
                result.success,
                "sync should recover from an expired token, errors were ${result.errors.map { it.message }}",
            )
        }
}
