@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.metadata.EntityType
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.server.space.SpaceStore
import app.logdate.shared.model.Journal
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Devices of one account sync through its sealed personal space on a real server, with real Room
 * databases and the real sync engine. Journals use the real mapping; the note mappings are minimal ones
 * written for these tests.
 */
class PersonalSpaceSyncE2ETest {
    @Test
    fun `journals created, edited, raced and deleted on one device reach the other through the personal space`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val journal = Journal(title = "Lake trips", description = "Every swim this summer")
            phone.journals.create(journal)
            val uploaded = phone.manager.uploadPendingChanges()
            assertSynced(uploaded)

            val stored = rawChanges(account)
            assertFalse("Lake trips" in stored, "The server received the journal's title")
            assertFalse(JOURNAL in stored, "The server received the record's type")

            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals("Lake trips", tablet.journals.getJournalById(journal.id)?.title)
            assertEquals("Every swim this summer", tablet.journals.getJournalById(journal.id)?.description)

            tablet.journals.update(
                tablet.journals.getJournalById(journal.id)!!.copy(title = "Lake and river trips", lastUpdated = later(1)),
            )
            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            assertEquals("Lake and river trips", phone.journals.getJournalById(journal.id)?.title)

            // Both rename it before either downloads the other's change. The journal's resolver
            // won't pick between two titles, so the conflict is listed and the space keeps the first.
            tablet.journals.update(tablet.journals.getJournalById(journal.id)!!.copy(title = "Tablet's name", lastUpdated = later(2)))
            assertSynced(tablet.manager.uploadPendingChanges())
            phone.journals.update(phone.journals.getJournalById(journal.id)!!.copy(title = "Phone's name", lastUpdated = later(3)))
            val raced = phone.manager.uploadPendingChanges()
            assertEquals(SyncErrorType.CONFLICT_ERROR, raced.errors.single().type)
            assertEquals(
                journal.id.toString(),
                phone.conflicts.records
                    .single()
                    .entityId,
            )
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals("Tablet's name", tablet.journals.getJournalById(journal.id)?.title)
            // The phone's next download brings the version the space kept.
            assertSynced(phone.manager.downloadRemoteChanges())
            assertEquals("Tablet's name", phone.journals.getJournalById(journal.id)?.title)

            phone.journals.delete(journal.id)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertNull(tablet.journals.getJournalById(journal.id))
            assertEquals(0, tablet.inbox.count())
            assertTrue(phone.metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
        }

    @Test
    fun `a journal with a description and a unicode title reaches another device and its edits come back`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val journal =
                Journal(
                    title = "Lac d'Annecy 🏊 日本語",
                    description = "Every swim\nthis summer",
                    created = later(0),
                    lastUpdated = later(0),
                )
            phone.journals.create(journal)
            assertSynced(phone.manager.uploadPendingChanges())
            assertFalse("Annecy" in rawChanges(account), "The server received the journal's title")

            assertSynced(tablet.manager.downloadRemoteChanges())
            val arrived = assertNotNull(tablet.journals.getJournalById(journal.id))
            assertEquals(journal.title, arrived.title)
            assertEquals(journal.description, arrived.description)
            assertEquals(journal.created.toEpochMilliseconds(), arrived.created.toEpochMilliseconds())

            tablet.journals.update(arrived.copy(title = "Lac d'Annecy et Léman", description = "", lastUpdated = later(1)))
            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            val edited = assertNotNull(phone.journals.getJournalById(journal.id))
            assertEquals("Lac d'Annecy et Léman", edited.title)
            assertEquals("", edited.description)
            assertEquals(0, phone.inbox.count())
        }

    @Test
    fun `a recording, its transcript and its file reach another device, change there and are deleted`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val sound = ByteArray(700_000) { (it % 211).toByte() }
            val local = phone.media.saveMedia(MediaPayload("memo.m4a", "audio/mp4", sound.size.toLong(), sound))
            val note =
                JournalNote.Audio(
                    mediaRef = local,
                    durationMs = 4_000,
                    creationTimestamp = later(0),
                    lastUpdated = later(0),
                    transcript = TranscriptDocument.fromPlainText("Swam to the far buoy"),
                )
            phone.notes.create(note)

            val uploaded = phone.manager.uploadPendingChanges()
            assertSynced(uploaded)
            assertFalse("Swam" in rawChanges(account), "The server received the transcript's text")
            val downloaded = tablet.manager.downloadRemoteChanges()
            assertSynced(downloaded)

            val applied = assertIs<JournalNote.Audio>(tablet.notes.getNoteById(note.uid))
            assertEquals("Swam to the far buoy", applied.transcript?.plainText)
            assertFalse(applied.mediaRef.startsWith("https://"), "The recording was recovered into the media store")
            assertContentEquals(sound, tablet.media.readMedia(applied.mediaRef).data)
            assertEquals(0, tablet.inbox.count())

            val edited =
                (
                    phone.notes.getNoteById(
                        note.uid,
                    ) as JournalNote.Audio
                ).copy(transcript = TranscriptDocument.fromPlainText("Swam past the far buoy"), lastUpdated = later(1))
            phone.notes.deleteFromSync(note.uid)
            phone.notes.createFromSync(edited)
            phone.metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, app.logdate.client.sync.metadata.PendingOperation.UPDATE)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals("Swam past the far buoy", (tablet.notes.getNoteById(note.uid) as JournalNote.Audio).transcript?.plainText)

            phone.notes.removeById(note.uid)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertNull(tablet.notes.getNoteById(note.uid))
            assertEquals(0, tablet.inbox.count())
        }

    @Test
    fun `a rate limit holds every request until its Retry-After`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            phone.journals.create(Journal(title = "Lake trips"))
            assertSynced(phone.manager.uploadPendingChanges())
            tablet.refuseWithRetryAfter = 120

            val refused = tablet.manager.downloadRemoteChanges()
            val requests = tablet.spaceRequests
            tablet.journals.create(Journal(title = "River trips"))
            tablet.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertFalse(refused.success)
            assertEquals(1, requests, "Only the refused request was sent")
            assertEquals(requests, tablet.spaceRequests, "Nothing was sent while the limit applied")
            assertEquals(1, tablet.metadata.getPendingUploads(EntityType.JOURNAL).size)
        }

    @Test
    fun `a device behind purged deletions lists everything again and removes what was deleted`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val kept = Journal(title = "Kept")
            val removed = Journal(title = "Removed")
            phone.journals.create(kept)
            phone.journals.create(removed)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertNotNull(tablet.journals.getJournalById(removed.id))

            phone.journals.delete(removed.id)
            assertSynced(phone.manager.uploadPendingChanges())
            purgeDeletions()
            phone.journals.update(phone.journals.getJournalById(kept.id)!!.copy(title = "Kept and renamed", lastUpdated = later(1)))
            assertSynced(phone.manager.uploadPendingChanges())
            val result = tablet.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.joinToString { it.cause?.stackTraceToString() ?: it.toString() })
            assertNull(tablet.journals.getJournalById(removed.id))
            assertEquals("Kept and renamed", tablet.journals.getJournalById(kept.id)?.title)
            assertEquals(0, tablet.inbox.count())
        }

    @Test
    fun `a journal deleted while another device couldn't read it never comes back there`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account).apply { identityKey = ByteArray(32) { 3 } }
            val journal = Journal(title = "Deleted later")
            phone.journals.create(journal)
            assertSynced(phone.manager.uploadPendingChanges())
            tablet.manager.downloadRemoteChanges()
            assertNull(tablet.journals.getJournalById(journal.id))

            phone.journals.delete(journal.id)
            assertSynced(phone.manager.uploadPendingChanges())
            tablet.manager.downloadRemoteChanges()
            tablet.identityKey = IDENTITY_KEY
            tablet.inbox.release()
            tablet.manager.downloadRemoteChanges()

            assertNull(tablet.journals.getJournalById(journal.id))
            assertEquals(0, tablet.inbox.count())
        }

    @Test
    fun `a journal deleted while another device waited to apply it is never applied there`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val journal = Journal(title = "Deleted later")
            phone.journals.create(journal)
            assertSynced(phone.manager.uploadPendingChanges())
            tablet.journals.failApplying = true
            assertFalse(tablet.manager.downloadRemoteChanges().success)
            assertEquals(1, tablet.inbox.count(), "The journal waits to be applied")

            phone.journals.delete(journal.id)
            assertSynced(phone.manager.uploadPendingChanges())
            tablet.journals.failApplying = false
            tablet.inbox.release()
            assertSynced(tablet.manager.downloadRemoteChanges())
            tablet.inbox.release()
            tablet.manager.downloadRemoteChanges()

            assertNull(tablet.journals.getJournalById(journal.id))
            assertEquals(0, tablet.inbox.count())
        }

    private fun assertSynced(result: app.logdate.client.sync.SyncResult) =
        assertTrue(
            result.success,
            result.errors.joinToString {
                it.cause?.stackTraceToString()
                    ?: it.toString()
            },
        )

    private class SpaceScenario(
        val harness: ServerClientE2EHarness,
        val scope: kotlinx.coroutines.CoroutineScope,
    ) {
        val devices = mutableListOf<SpaceDevice>()

        fun device(account: SpaceAccount) = SpaceDevice(account, scope).also { devices += it }

        suspend fun rawChanges(account: SpaceAccount): String =
            harness.httpClient
                .get("${account.origin}/xrpc/app.logdate.space.listRepoOps") {
                    parameter("space", "at://${account.did}/space/app.logdate.space.personal/self")
                    parameter("since", "0")
                    header(HttpHeaders.Authorization, "Bearer ${account.token}")
                }.bodyAsText()

        /** Runs the server's cleanup as if every deletion so far were old enough to purge. */
        suspend fun purgeDeletions() {
            GlobalContext.get().get<SpaceStore>().purge(Clock.System.now() + 1.minutes, Instant.fromEpochMilliseconds(0)) {}
        }
    }

    private fun spaceTest(block: suspend SpaceScenario.(SpaceAccount) -> Unit) =
        runTest {
            withServerClientHarness {
                val scenario = SpaceScenario(this, backgroundScope)
                try {
                    scenario.block(signUpForSpace())
                } finally {
                    scenario.devices.forEach { it.close() }
                }
            }
        }

    private companion object {
        private val start = Clock.System.now()

        fun later(minutes: Int): Instant = start + minutes.minutes
    }
}
