package app.logdate.client.sync.recovery

import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * A newer version of LogDate on another device can save a kind of entry this version has never
 * seen. The download inbox stores it and moves past it, but it counted as unfinished recovery and
 * as a sync error on every pass, so no full sync on Android, iOS or desktop ever succeeded and
 * Android retried the backup without end.
 */
class UnsupportedContentTypeTest {
    private val db = DownloadInboxTest.Database()
    private var now = 1_000L
    private val events = mutableListOf<SyncDiagnosticEvent>()
    private val inbox =
        DownloadInbox(
            db,
            db,
            { DownloadScope("owner", "origin") },
            { now },
            diagnostics = { event, _ -> events += event },
        )
    private val newer = Uuid.random()
    private val text = Uuid.random()
    private val api =
        fakeCloudApiClient {
            getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                change(newer, type = "MOMENT", serverVersion = 7L),
                                change(text, type = "TEXT", serverVersion = 8L),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 8L,
                    ),
                )
        }
    private val notes = FakeJournalNotesRepository()

    @Test
    fun `an entry of a newer kind does not fail the sync and the rest of the page arrives`() =
        runTest {
            val manager = manager(backgroundScope)

            val result = manager.downloadRemoteChanges()

            assertTrue(result.success, "An entry from a newer version must not fail the sync: ${result.errors}")
            assertNotNull(notes.getNoteById(text), "The readable entry on the same page arrives")
            assertNull(notes.getNoteById(newer))
            assertEquals(8L, inbox.cursor("NOTE"), "The download moves past the page")
            assertEquals(0, inbox.count(), "An entry waiting for an app update is not unfinished recovery")
            assertFalse(manager.getSyncStatus().hasErrors, "Backup status shows no error for it")
        }

    @Test
    fun `an entry of a newer kind waits in the inbox for an update that can read it`() =
        runTest {
            val manager = manager(backgroundScope)
            manager.downloadRemoteChanges()
            now += 2 * 60 * 60 * 1_000L
            api.getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 8L))

            val retried = manager.downloadRemoteChanges()

            assertTrue(retried.success, "Offering it again must not fail the sync either: ${retried.errors}")
            val waiting = db.rows.single { it.entityType == "NOTE" && it.entityId == newer.toString() }
            assertEquals("FAILED", waiting.state)
            assertEquals(DiagnosticReason.UNSUPPORTED_FORMAT.name, waiting.reason)
            assertEquals(2, waiting.attempts, "It was offered to this version again after its retry delay")
            assertEquals(
                "MOMENT",
                Json.decodeFromString<ContentChange>(waiting.payload).type,
                "The original record is still on the device for a later version to read",
            )
        }

    @Test
    fun `sync diagnostics advise an app update for an entry of a newer kind`() =
        runTest {
            manager(backgroundScope).downloadRemoteChanges()

            val retry = events.single { it.outcome == DiagnosticOutcome.RETRY_SCHEDULED }
            assertEquals(DiagnosticReason.UNSUPPORTED_FORMAT, retry.reason)
            assertEquals(DiagnosticAction.UPDATE_APP, retry.action)
        }

    private fun manager(scope: CoroutineScope) =
        testDefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(api, inbox)),
            journalNotesRepository = notes,
            downloadInbox = inbox,
            transactionManager = db,
            syncScope = scope,
        )

    private fun change(
        id: Uuid,
        type: String,
        serverVersion: Long,
    ) = ContentChange(
        id = id.toString(),
        type = type,
        content = "Written on another device",
        createdAt = serverVersion,
        lastUpdated = serverVersion,
        serverVersion = serverVersion,
    )
}
