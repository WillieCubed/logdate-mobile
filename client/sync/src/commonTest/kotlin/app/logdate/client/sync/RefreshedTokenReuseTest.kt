package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.JournalChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * Refreshing a token wrote it to the account repository's own storage key, which the session
 * storage never reads. The session kept handing out the token that had already been rejected, so
 * every request in production paid a 401 and a refresh before doing any work - visible in the
 * server logs as an unbroken 401 -> refresh -> retry cycle that never settled, against a server
 * that was already timing out.
 */
class RefreshedTokenReuseTest {
    private class RejectsStaleTokenApiClient : FakeCloudApiClient() {
        val tokensSeen = mutableListOf<String>()

        override suspend fun getJournalChanges(
            accessToken: String,
            since: Long,
            limit: Int?,
        ): Result<JournalChangesResponse> {
            tokensSeen.add(accessToken)
            if (accessToken == "test-access-token") {
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
    fun `a refreshed token is kept so the next request does not have to be rejected first`() =
        runTest {
            val apiClient = RejectsStaleTokenApiClient()
            val sessionStorage = fakeSessionStorage()
            val syncManager =
                testDefaultSyncManager(
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    sessionStorage = sessionStorage,
                )

            syncManager.downloadRemoteChanges()

            assertEquals(
                "new-token",
                sessionStorage.getSession()?.accessToken,
                "the refreshed token should have replaced the rejected one in the session",
            )

            apiClient.tokensSeen.clear()
            syncManager.downloadRemoteChanges()

            assertEquals(
                listOf("new-token"),
                apiClient.tokensSeen,
                "the second run should use the refreshed token without being rejected again",
            )
        }
}
