@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.mappers.JournalRecordMapper
import app.logdate.client.sync.space.mappers.SpaceJournal
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.server.space.SpaceStore
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.PhotoPresentation
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import app.logdate.space.XrpcDataHome
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.koin.core.context.GlobalContext
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
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
import kotlin.uuid.Uuid

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
    fun `a favorite journal with a description and a unicode title reaches another device and its edits come back`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val journal =
                Journal(
                    title = "Lac d'Annecy 🏊 日本語",
                    description = "Every swim\nthis summer",
                    isFavorited = true,
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
            assertTrue(arrived.isFavorited)
            assertEquals(journal.created.toEpochMilliseconds(), arrived.created.toEpochMilliseconds())

            tablet.journals.update(
                arrived.copy(title = "Lac d'Annecy et Léman", description = "", isFavorited = false, lastUpdated = later(1)),
            )
            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            val edited = assertNotNull(phone.journals.getJournalById(journal.id))
            assertEquals("Lac d'Annecy et Léman", edited.title)
            assertEquals("", edited.description)
            assertFalse(edited.isFavorited)
            assertEquals(0, phone.inbox.count())
        }

    @Test
    fun `an entry link and a draft reach another device and their removal comes back`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val journal = Journal(title = "Lake trips", created = later(0), lastUpdated = later(0))
            val note = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "First swim")
            phone.journals.create(journal)
            phone.notes.create(note)
            phone.content.addContentToJournal(note.uid, journal.id)
            val photo = ByteArray(300_000) { (it % 199).toByte() }
            val local = phone.media.saveMedia(MediaPayload("view.jpg", "image/jpeg", photo.size.toLong(), photo))
            val draft =
                EditorDraft(
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                Uuid.random(),
                                later(0),
                                48.8566,
                                2.3522,
                                35.5,
                                content = "Dear diary, the lake was calm",
                            ),
                            SerializableImageBlock(
                                Uuid.random(),
                                later(1),
                                uri = local,
                                caption = "The view",
                                presentation = PhotoPresentation.Framed,
                            ),
                        ),
                    selectedJournalIds = listOf(journal.id),
                    createdAt = later(0),
                    lastModifiedAt = later(2),
                )
            phone.journals.saveDraft(draft)

            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(phone.manager.syncDrafts())
            assertFalse("calm" in rawChanges(account), "The server received the draft's text")

            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals(
                listOf(journal.id),
                tablet.content
                    .observeJournalsForContent(note.uid)
                    .first()
                    .map { it.id },
            )
            assertSynced(tablet.manager.syncDrafts())
            val arrived = assertNotNull(tablet.journals.getDraft(draft.id))
            assertEquals(draft.selectedJournalIds, arrived.selectedJournalIds)
            assertEquals(draft.blocks[0], arrived.blocks[0])
            val image = arrived.blocks[1] as SerializableImageBlock
            assertEquals("The view", image.caption)
            assertEquals(PhotoPresentation.Framed, image.presentation)
            assertContentEquals(photo, tablet.media.readMedia(assertNotNull(image.uri)).data)

            tablet.content.removeContentFromJournal(note.uid, journal.id)
            assertSynced(tablet.manager.uploadPendingChanges())
            tablet.journals.deleteDraft(draft.id)
            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            assertSynced(phone.manager.syncDrafts())

            assertEquals(emptyList(), phone.content.observeJournalsForContent(note.uid).first())
            assertNull(phone.journals.getDraft(draft.id))
            assertEquals(0, phone.inbox.count())
        }

    @Test
    fun `a journal with a cover reaches another device with the cover file`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val picture = ByteArray(250_000) { (it % 173).toByte() }
            val local = phone.media.saveMedia(MediaPayload("cover.jpg", "image/jpeg", picture.size.toLong(), picture))
            val journal = Journal(title = "Lake trips", coverImageUri = local, created = later(0), lastUpdated = later(0))
            phone.journals.create(journal)
            val stored = assertNotNull(phone.journals.getJournalById(journal.id)?.coverImageUri)

            assertSynced(phone.manager.uploadPendingChanges())
            assertTrue(phone.metadata.getPendingUploads(EntityType.JOURNAL).isEmpty(), "The journal is still waiting to upload")
            assertEquals(stored, phone.journals.getJournalById(journal.id)?.coverImageUri)

            assertSynced(tablet.manager.downloadRemoteChanges())
            val cover = assertNotNull(tablet.journals.getJournalById(journal.id)?.coverImageUri)
            assertFalse(cover.startsWith("https://"), "The cover was recovered into the media store")
            assertContentEquals(picture, tablet.media.readMedia(cover).data)
            assertEquals(0, tablet.inbox.count())

            tablet.journals.update(
                tablet.journals.getJournalById(journal.id)!!.copy(title = "Lake and river trips", lastUpdated = later(1)),
            )
            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            assertEquals("Lake and river trips", phone.journals.getJournalById(journal.id)?.title)
            assertContentEquals(
                picture,
                phone.media.readMedia(assertNotNull(phone.journals.getJournalById(journal.id)?.coverImageUri)).data,
            )
            assertEquals(0, phone.inbox.count())
        }

    @Test
    fun `journals merged on one device are merged on another`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val lake = Journal(title = "Lake", created = later(0), lastUpdated = later(0))
            val river = Journal(title = "River", created = later(0), lastUpdated = later(0))
            val swim = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Swim")
            val both = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Both")
            listOf(lake, river).forEach { phone.journals.create(it) }
            listOf(swim, both).forEach { phone.notes.create(it) }
            phone.content.addContentToJournal(swim.uid, lake.id)
            phone.content.addContentToJournal(both.uid, lake.id)
            phone.content.addContentToJournal(both.uid, river.id)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())

            val merged = phone.journals.merge(assertNotNull(phone.journals.previewMerge(lake.id, river.id)), Uuid.random())
            assertIs<JournalMergeResult.Merged>(merged)
            assertSynced(phone.manager.uploadPendingChanges())
            assertEquals(emptyList(), phone.journals.pendingJournalMerges(), "The merge is still waiting to upload")

            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals(
                listOf(river.id),
                tablet.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                setOf(swim.uid, both.uid),
                tablet.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid }
                    .toSet(),
            )
            assertEquals(
                listOf(river.id),
                tablet.content
                    .observeJournalsForContent(both.uid)
                    .first()
                    .map { it.id },
            )
            assertEquals(0, tablet.inbox.count())

            val fresh = device(account)
            assertSynced(fresh.manager.downloadRemoteChanges())
            assertEquals(
                listOf(river.id),
                fresh.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                setOf(swim.uid, both.uid),
                fresh.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid }
                    .toSet(),
            )
        }

    @Test
    fun `a journal merged on another device moves its entries into the survivor once`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val lake = Journal(title = "Lake", created = later(0), lastUpdated = later(0))
            val river = Journal(title = "River", created = later(0), lastUpdated = later(0))
            val swim = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Swim")
            val both = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Both")
            listOf(lake, river).forEach { phone.journals.create(it) }
            listOf(swim, both).forEach { phone.notes.create(it) }
            phone.content.addContentToJournal(swim.uid, lake.id)
            phone.content.addContentToJournal(both.uid, lake.id)
            phone.content.addContentToJournal(both.uid, river.id)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals(
                2,
                tablet.content
                    .observeContentForJournal(lake.id)
                    .first()
                    .size,
            )

            writeFromAnotherDevice(account, JOURNAL, lake.id.toString(), mergedJournalRecord(lake, river))
            assertSynced(tablet.manager.downloadRemoteChanges())

            assertEquals(
                listOf(river.id),
                tablet.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                setOf(swim.uid, both.uid),
                tablet.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid }
                    .toSet(),
            )
            assertEquals(
                listOf(river.id),
                tablet.content
                    .observeJournalsForContent(both.uid)
                    .first()
                    .map { it.id },
            )
            assertEquals(0, tablet.inbox.count())

            assertSynced(tablet.manager.uploadPendingChanges())
            assertSynced(phone.manager.downloadRemoteChanges())
            assertEquals(
                listOf(river.id),
                phone.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                setOf(swim.uid, both.uid),
                phone.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid }
                    .toSet(),
            )
        }

    @Test
    fun `a journal merge that arrives before the surviving journal waits for it`() =
        spaceTest { account ->
            val phone = device(account)
            val tablet = device(account)
            val lake = Journal(title = "Lake", created = later(0), lastUpdated = later(0))
            val river = Journal(title = "River", created = later(0), lastUpdated = later(0))
            val swim = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Swim")
            phone.journals.create(lake)
            phone.notes.create(swim)
            phone.content.addContentToJournal(swim.uid, lake.id)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())

            writeFromAnotherDevice(account, JOURNAL, lake.id.toString(), mergedJournalRecord(lake, river))
            assertSynced(tablet.manager.downloadRemoteChanges())
            assertEquals(emptyList(), tablet.journals.allJournalsObserved.first(), "The merged journal is still listed")
            assertEquals(
                listOf(swim.uid),
                tablet.content
                    .observeContentForJournal(lake.id)
                    .first()
                    .map { it.uid },
                "Entries moved nowhere",
            )

            phone.journals.create(river)
            assertSynced(phone.manager.uploadPendingChanges())
            assertSynced(tablet.manager.downloadRemoteChanges())

            assertEquals(
                listOf(river.id),
                tablet.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                listOf(swim.uid),
                tablet.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid },
            )
            assertEquals(0, tablet.inbox.count())
        }

    @Test
    fun `a journal merged before this device has any of it is never listed and its entries land in the survivor`() =
        spaceTest { account ->
            val phone = device(account)
            val laptop = device(account)
            val lake = Journal(title = "Lake", created = later(0), lastUpdated = later(0))
            val river = Journal(title = "River", created = later(0), lastUpdated = later(0))
            val swim = JournalNote.Text(creationTimestamp = later(0), lastUpdated = later(0), content = "Swim")
            listOf(lake, river).forEach { phone.journals.create(it) }
            phone.notes.create(swim)
            phone.content.addContentToJournal(swim.uid, lake.id)
            assertSynced(phone.manager.uploadPendingChanges())
            writeFromAnotherDevice(account, JOURNAL, lake.id.toString(), mergedJournalRecord(lake, river))

            assertSynced(laptop.manager.downloadRemoteChanges())

            assertEquals(
                listOf(river.id),
                laptop.journals.allJournalsObserved
                    .first()
                    .map { it.id },
            )
            assertEquals(
                listOf(swim.uid),
                laptop.content
                    .observeContentForJournal(river.id)
                    .first()
                    .map { it.uid },
            )
            assertEquals(0, laptop.inbox.count())
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

        /** Writes [record] into the account's personal space as a second device of the account would. */
        suspend fun writeFromAnotherDevice(
            account: SpaceAccount,
            collection: String,
            rkey: String,
            record: JsonObject,
        ) {
            val client = KtorXrpcClient(harness.httpClient, account.origin)
            val home = XrpcDataHome(client, harness.httpClient, account.origin) { BearerToken(account.token) }
            val space = SpaceRef.personal(account.did)
            val sealed =
                SealedRecordCodec.seal(
                    SealingKeys.personal(IDENTITY_KEY),
                    space,
                    account.did,
                    SealedPayload(collection, record, rkey),
                )
            home.putRecord(space, sealed.rkey, sealed.record, swap = RecordSwap.Unconditional)
        }

        /** The record another device writes when it merges [source] into [survivor]. */
        fun mergedJournalRecord(
            source: Journal,
            survivor: Journal,
        ): JsonObject = JournalRecordMapper().toRecord(SpaceJournal(source, mergedInto = survivor.id))

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
