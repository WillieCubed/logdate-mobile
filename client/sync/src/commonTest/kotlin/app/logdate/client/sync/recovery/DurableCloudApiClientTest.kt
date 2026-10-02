package app.logdate.client.sync.recovery

import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurableCloudApiClientTest {
    @Test
    fun `one corrupt retained wire payload cannot block unrelated records`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val selected = DownloadScope("a", "server")
            val inbox = DownloadInbox(db, db, { selected }, { 1000 })
            inbox.stage(
                "NOTE",
                7,
                listOf(
                    WireDownload("broken", 6, false, "corrupt-private-marker"),
                    WireDownload(
                        "note",
                        7,
                        false,
                        """{"id":"note","type":"TEXT","content":"encrypted","createdAt":1,"lastUpdated":1,"serverVersion":7}""",
                    ),
                ),
            )
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 7))
                }

            val response = DurableCloudApiClient(api, inbox).getContentChanges("token", 7).getOrThrow()

            assertEquals(listOf("note"), response.changes.map { it.id })
            val retained = db.get(selected.owner, selected.origin, "NOTE", "broken")!!
            assertEquals("FAILED", retained.state)
            assertEquals("CORRUPT_PAYLOAD", retained.reason)
            assertEquals("corrupt-private-marker", retained.payload)
            assertTrue(retained.nextAttemptAt > 1000)
            assertEquals(7L, inbox.cursor("NOTE"))
        }

    @Test
    fun `deletion uses authoritative version even when its wall clock precedes the applied record`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1000 })
            inbox.stage("NOTE", 10, listOf(WireDownload("note", 10, false, "wire")))
            inbox.applied("NOTE", "note", 10)
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                emptyList(),
                                listOf(
                                    app.logdate.shared.model.sync
                                        .ContentDeletion("note", 5, serverVersion = 11),
                                ),
                                11,
                            ),
                        )
                }
            val response = DurableCloudApiClient(api, inbox).getContentChanges("token", 10).getOrThrow()
            assertEquals(listOf("note"), response.deletions.map { it.id })
            assertEquals(11L, inbox.pending("NOTE").single().version)
        }

    @Test
    fun `wire page is persisted before it can be decrypted and returned`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("a", "server") }, { 1000 })
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                listOf(
                                    ContentChange(
                                        "note",
                                        "TEXT",
                                        content = "encrypted-wire-body",
                                        createdAt = 1,
                                        lastUpdated = 1,
                                        serverVersion = 7,
                                    ),
                                ),
                                emptyList(),
                                7,
                            ),
                        )
                }
            DurableCloudApiClient(api, inbox).getContentChanges("token", 99).getOrThrow()
            assertEquals(7L, inbox.cursor("NOTE"))
            assertTrue(
                inbox
                    .pending("NOTE")
                    .single()
                    .payload
                    .contains("encrypted-wire-body"),
            )
        }

    @Test
    fun `retry returns unacknowledged wire records after a restart even when server delta is empty`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val scope = { DownloadScope("a", "server") }
            val inbox = DownloadInbox(db, db, scope, { 1000 })
            inbox.stage(
                "NOTE",
                7,
                listOf(
                    WireDownload(
                        "note",
                        7,
                        false,
                        """{"id":"note","type":"TEXT","content":"encrypted","createdAt":1,"lastUpdated":1,"serverVersion":7}""",
                    ),
                ),
            )
            val api = fakeCloudApiClient { getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 7)) }
            val restarted = DurableCloudApiClient(api, DownloadInbox(db, db, scope, { 1000 }))
            assertEquals(
                listOf("note"),
                restarted
                    .getContentChanges("token", 7)
                    .getOrThrow()
                    .changes
                    .map { it.id },
            )
        }

    @Test
    fun `persisted work drains while fetching fails without advancing remote cursor`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val scope = { DownloadScope("a", "server") }
            val inbox = DownloadInbox(db, db, scope, { 1000 })
            inbox.stage(
                "NOTE",
                7,
                listOf(
                    WireDownload(
                        "note",
                        7,
                        false,
                        """{"id":"note","type":"TEXT","content":"encrypted","createdAt":1,"lastUpdated":1,"serverVersion":7}""",
                    ),
                ),
            )
            val api = fakeCloudApiClient { getContentChangesResponse = Result.failure(IllegalStateException("private-response")) }
            val restartedInbox = DownloadInbox(db, db, scope, { 1000 })
            val restarted = DurableCloudApiClient(api, restartedInbox)
            assertEquals(
                listOf("note"),
                restarted
                    .getContentChanges("token", 7)
                    .getOrThrow()
                    .changes
                    .map { it.id },
            )
            assertEquals(7L, restartedInbox.cursor("NOTE"))
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.SERVER_UNAVAILABLE, restartedInbox.fetchFailure("NOTE"))
            restartedInbox.applied("NOTE", "note", 7)
            assertTrue(restarted.getContentChanges("token", 7).isFailure)
        }
}
