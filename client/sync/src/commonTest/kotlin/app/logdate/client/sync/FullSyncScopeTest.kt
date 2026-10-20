package app.logdate.client.sync

import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FullSyncScopeTest {
    @Test
    fun `successful full sync proves recovery for its owner and server`() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            val bound = requireNotNull(session.getOriginBoundSession())
            val result = testDefaultSyncManager(sessionStorage = session).fullSync()
            assertTrue(result.success)
            assertEquals(UploadScope(bound.session.accountId, bound.origin), result.recoveredMediaScope)
        }

    @Test
    fun `owner change while a full phase waits for its lock prevents phase execution`() =
        runTest {
            for (entityPhase in listOf(false, true)) {
                val session = fakeSessionStorage(authenticated = true)
                val bound = requireNotNull(session.getOriginBoundSession())
                val manager = testDefaultSyncManager(sessionStorage = session)
                var calls = 0
                manager.syncMutex.lock()
                val waiting =
                    async {
                        withContext(FullSyncScope(UploadScope(bound.session.accountId, bound.origin))) {
                            if (entityPhase) {
                                manager.syncEntityType(
                                    EntityType.DRAFT,
                                    "test",
                                    { null },
                                    { _, _ ->
                                        calls++
                                        SyncResult(true)
                                    },
                                    {
                                        calls++
                                        SyncResult(true)
                                    },
                                )
                            } else {
                                manager.runFullSyncPhase("test", "test") {
                                    calls++
                                    SyncResult(true)
                                }
                            }
                        }
                    }
                runCurrent()
                session.saveSession(bound.session.copy(accountId = "replacement-owner"))
                manager.syncMutex.unlock()
                val result = waiting.await()
                assertFalse(result.success)
                assertTrue(result.hasMorePending)
                assertNull(result.recoveredMediaScope)
                assertEquals(0, calls, "No phase may run for the replacement owner under the old run")
            }
        }

    @Test
    fun `scope change during download cannot complete the old run`() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            var enrolled = false
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun getContentChanges(
                        accessToken: String,
                        since: Long,
                        limit: Int?,
                    ): Result<ContentChangesResponse> {
                        session.currentOrigin = "https://replacement.example"
                        return super.getContentChanges(accessToken, since, limit)
                    }
                }
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    deviceEnrollment = { enrolled = true },
                )
            val result = manager.fullSync()
            assertFalse(result.success)
            assertTrue(result.hasMorePending)
            assertNull(result.recoveredMediaScope)
            assertFalse(enrolled, "The superseded run must not continue to enrollment")
        }

    @Test
    fun `token refresh for the same owner does not invalidate full recovery`() =
        runTest {
            val session = fakeSessionStorage(authenticated = true)
            val bound = requireNotNull(session.getOriginBoundSession())
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun getContentChanges(
                        accessToken: String,
                        since: Long,
                        limit: Int?,
                    ): Result<ContentChangesResponse> {
                        session.saveSession(bound.session.copy(accessToken = "refreshed"))
                        return super.getContentChanges(accessToken, since, limit)
                    }
                }
            val result =
                testDefaultSyncManager(
                    sessionStorage = session,
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                ).fullSync()
            assertTrue(result.success)
            assertEquals(UploadScope(bound.session.accountId, bound.origin), result.recoveredMediaScope)
        }
}
