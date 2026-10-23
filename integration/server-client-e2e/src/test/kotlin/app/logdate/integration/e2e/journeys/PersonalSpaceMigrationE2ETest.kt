@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.space.migration.JournalDataKind
import app.logdate.client.sync.space.migration.JournalMoveProblem
import app.logdate.client.sync.space.migration.JournalMoveState
import app.logdate.client.sync.space.migration.SpaceMigrationStage
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.server.auth.AccountRepository
import app.logdate.server.migration.LegacyCopyCleanup
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.StorageModeErrorCodes
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import app.logdate.shared.model.sync.LegacyRecordKinds
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.GlobalContext
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.RepoBlockStore
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Moving an existing journal from legacy storage into its sealed personal space, against the real
 * server: devices running an app from before client media encryption and one from before personal
 * spaces write a journal with every kind of data, an updated device downloads it and moves it, and
 * every check that keeps data safe is exercised on the way.
 */
class PersonalSpaceMigrationE2ETest {
    private val midnight = Instant.parse("2026-10-20T00:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    /** What a person wrote before the move, to compare with what arrives after it. */
    private class WrittenJournal(
        val travel: Journal,
        val family: Journal,
        val text: JournalNote.Text,
        val photo: JournalNote.Image,
        val photoBytes: ByteArray,
        val recording: JournalNote.Audio,
        val olderPhoto: JournalNote.Image,
        val olderPhotoBytes: ByteArray,
        val draft: EditorDraft,
        val draftPhoto: ByteArray,
        val mark: MarkedMoment,
        val placeId: String,
        val sampleIds: Set<String>,
    )

    @Test
    fun `a realistic journal moves into the personal space with every kind of data and media`() =
        moveTest { account ->
            val written = writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)

            moveJournal(mover)

            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, storageMode(account))
            val uploads = mover.requests.count { it.endsWith("/xrpc/app.logdate.space.uploadBlob") }
            val copies = mover.requests.count { it.contains("/api/v1/space-migration/media/") }
            assertEquals(
                1,
                uploads,
                "Only the file the server encrypted is uploaded again: ${mover.requests.filter { "uploadBlob" in it }}",
            )
            assertEquals(4, copies, "Every legacy file is offered to the server to copy")

            val fresh = device(account)
            fresh.holdIdentityKey(account)
            synced(fresh.manager.fullSync())
            assertEquals(
                setOf("Travel", "Family"),
                fresh.journals.allJournalsObserved
                    .first()
                    .map { it.title }
                    .toSet(),
            )
            assertEquals("Walked to the lake before work", (fresh.notes.getNoteById(written.text.uid) as JournalNote.Text).content)
            val photo = assertIs<JournalNote.Image>(fresh.notes.getNoteById(written.photo.uid))
            assertEquals("The lake at dawn", photo.caption)
            assertContentEquals(written.photoBytes, fresh.media.readMedia(photo.mediaRef).data)
            val older = assertIs<JournalNote.Image>(fresh.notes.getNoteById(written.olderPhoto.uid))
            assertContentEquals(written.olderPhotoBytes, fresh.media.readMedia(older.mediaRef).data)
            val recording = assertIs<JournalNote.Audio>(fresh.notes.getNoteById(written.recording.uid))
            assertEquals("Swam to the far buoy", recording.transcript?.plainText)
            assertEquals(
                setOf(written.travel.id),
                fresh.content
                    .observeJournalsForContent(written.text.uid)
                    .first()
                    .map { it.id }
                    .toSet(),
            )
            synced(fresh.manager.syncDrafts())
            val draft = assertNotNull(fresh.journals.getDraft(written.draft.id))
            val draftImage = draft.blocks.filterIsInstance<SerializableImageBlock>().single()
            assertContentEquals(written.draftPhoto, fresh.media.readMedia(assertNotNull(draftImage.uri)).data)
            assertEquals("Heron at the lake", fresh.marks.getMark(written.mark.id)?.caption)
            val history = fresh.historyStore.records(account.owner, account.origin)
            assertNotNull(history.firstOrNull { it.id == written.placeId && it.recordType == "place" })
            assertEquals(written.sampleIds, history.filter { it.recordType == "observation" && !it.deleted }.map { it.id }.toSet())

            val legacy = rawLegacy(account, "/contents")
            assertEquals(403, legacy.first)
            assertEquals(StorageModeErrorCodes.SYNC_FORMAT_UPGRADE_REQUIRED, legacy.second)
        }

    @Test
    fun `an interrupted move resumes where it stopped`() =
        moveTest { account ->
            writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)
            var writes = 0
            mover.onRequest = { request ->
                if (request.url.encodedPath.endsWith("app.logdate.space.putRecord") &&
                    ++writes > 5
                ) {
                    throw IOException("The network dropped")
                }
                null
            }
            syncUntilSettled(mover)
            val stopped = assertIs<JournalMoveState.Stopped>(mover.manager.journalStorageMove.state.value)
            assertEquals(JournalMoveProblem.Offline, stopped.problem)
            val checkpoint = assertNotNull(mover.checkpoints.load(mover.scopeOfAccount))
            assertEquals(SpaceMigrationStage.UPLOADING, checkpoint.stage)
            assertTrue(checkpoint.uploadedThrough.isNotEmpty(), "The checkpoint names how far the upload got")
            assertEquals(AccountStorageMode.LEGACY, storageMode(account))

            mover.onRequest = { null }
            val before = mover.requests.size
            mover.manager.journalStorageMove.retry()

            assertIs<JournalMoveState.Moved>(mover.manager.journalStorageMove.state.value)
            val resumedWrites = mover.requests.drop(before).count { it.endsWith("app.logdate.space.putRecord") }
            val allRecords = spaceRecordCount(account)
            assertTrue(resumedWrites < allRecords, "The resumed upload wrote $resumedWrites of $allRecords records again")
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, storageMode(account))
        }

    @Test
    fun `a copy that never matches keeps the account on legacy storage and says why`() =
        moveTest { account ->
            val written = writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)
            val lost =
                SealedRecordCodec.sealedRkey(
                    SealingKeys.personal(IDENTITY_KEY),
                    "app.logdate.journal.entry",
                    written.text.uid.toString(),
                )
            mover.onRequest = { request ->
                if (request.url.encodedPath.endsWith("app.logdate.space.putRecord") && lost in bodyOf(request)) {
                    refuse(request, 200, """{"cid":"bafyreih5n4wqmtgxa3bqhmvp2u3gfhgr2b5fo5m4tgmukbgjyrjbpk7lfa","seq":1}""")
                } else {
                    null
                }
            }

            syncUntilSettled(mover)

            val stopped = assertIs<JournalMoveState.Stopped>(mover.manager.journalStorageMove.state.value)
            assertEquals(JournalMoveProblem.CopyDiffers(JournalDataKind.ENTRIES, 1), stopped.problem)
            assertNotNull(stopped.retryAt, "The move tries again on its own later")
            assertEquals(AccountStorageMode.LEGACY, storageMode(account))
            assertEquals(SpaceMigrationPhase.UPLOADING, migrationPhase(account))
            val legacyWriter = device(account, AppVersion.LEGACY_ONLY)
            legacyWriter.holdIdentityKey(account)
            legacyWriter.notes.create(JournalNote.Text(creationTimestamp = later(9), lastUpdated = later(9), content = "Still syncing"))
            synced(legacyWriter.manager.fullSync())
        }

    @Test
    fun `an app from before the move is told to update and this one without spaces shows why`() =
        moveTest { account ->
            writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)
            moveJournal(mover)

            val older = device(account, AppVersion.LEGACY_ONLY)
            older.holdIdentityKey(account)
            older.notes.create(JournalNote.Text(creationTimestamp = later(5), lastUpdated = later(5), content = "Written on the old app"))
            val refused = older.manager.uploadPendingChanges()
            assertTrue(!refused.success)
            val upgrade = rawLegacy(account, "/journals")
            assertEquals(403, upgrade.first)
            assertEquals(StorageModeErrorCodes.SYNC_FORMAT_UPGRADE_REQUIRED, upgrade.second)
            assertEquals(1, older.metadata.getPendingUploads(EntityType.NOTE).size, "The old app keeps its change queued")

            val blocked = device(account, AppVersion.CURRENT_WITHOUT_SPACES)
            blocked.holdIdentityKey(account)
            val result = blocked.manager.fullSync()
            assertEquals(listOf(SyncErrorType.ACCOUNT_STORAGE_ERROR), result.errors.map { it.type }.distinct(), result.errors.toString())
        }

    @Test
    fun `the journal can be switched back while the legacy copy is kept`() =
        moveTest { account ->
            writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)
            moveJournal(mover)
            val afterMove = JournalNote.Text(creationTimestamp = later(20), lastUpdated = later(20), content = "Written after the move")
            mover.notes.create(afterMove)
            synced(mover.manager.uploadPendingChanges())

            mover.manager.journalStorageMove.switchBack()

            assertEquals(JournalMoveState.SwitchedBack, mover.manager.journalStorageMove.state.value)
            assertEquals(AccountStorageMode.LEGACY, storageMode(account))
            assertEquals(SpaceMigrationPhase.REVERTED, migrationPhase(account))
            val older = device(account, AppVersion.LEGACY_ONLY)
            older.holdIdentityKey(account)
            synced(older.manager.fullSync())
            assertEquals("Written after the move", (older.notes.getNoteById(afterMove.uid) as JournalNote.Text).content)
            assertEquals(written(older).size, written(mover).size)

            syncUntilSettled(mover)
            assertEquals(
                JournalMoveState.SwitchedBack,
                mover.manager.journalStorageMove.state.value,
                "A switched-back journal doesn't move again on its own",
            )
        }

    @Test
    fun `the legacy copy is deleted 30 days after the switch and the public repository is empty`() =
        moveTest { account ->
            writeJournal(account)
            val mover = device(account)
            mover.holdIdentityKey(account)
            moveJournal(mover)
            val cleanup = GlobalContext.get().get<LegacyCopyCleanup>()

            assertEquals(0, cleanup.runOnce(Clock.System.now() + 29.days).erased)
            assertTrue(legacyCount(account, LegacyRecordKinds.ENTRIES) > 0, "The legacy copy is kept inside the window")
            assertTrue(cleanup.runOnce(Clock.System.now() + 31.days).erased >= 1, "Moves from other tests share the database")

            LegacyRecordKinds.ALL.forEach { kind -> assertEquals(0, legacyCount(account, kind), kind) }
            assertEquals(SpaceMigrationPhase.CLEANED, migrationPhase(account))
            assertNull(
                GlobalContext
                    .get()
                    .get<RepoBlockStore>()
                    .readHead(AtprotoDid.require(account.did))
                    .getOrThrow(),
            )
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, storageMode(account))
            mover.manager.journalStorageMove.switchBack()
            assertEquals(
                JournalMoveProblem.SwitchBackWindowClosed,
                assertIs<JournalMoveState.Stopped>(mover.manager.journalStorageMove.state.value).problem,
            )

            val fresh = device(account)
            fresh.holdIdentityKey(account)
            synced(fresh.manager.fullSync())
            assertEquals(written(mover).size, written(fresh).size, "The personal space still holds every entry")
        }

    // --- Writing the journal before the move -----------------------------------------------------

    private suspend fun MoveScope.writeJournal(account: SpaceAccount): WrittenJournal {
        val old = device(account, AppVersion.BEFORE_MEDIA_ENCRYPTION)
        old.holdIdentityKey(account)
        val olderPhotoBytes = ByteArray(90_000) { (it % 97).toByte() }
        val olderPhoto =
            JournalNote.Image(
                creationTimestamp = later(0),
                lastUpdated = later(0),
                mediaRef = old.media.saveMedia(MediaPayload("old.jpg", "image/jpeg", olderPhotoBytes.size.toLong(), olderPhotoBytes)),
                caption = "From the old phone",
            )
        old.notes.create(olderPhoto)
        synced(old.manager.fullSync())

        val phone = device(account, AppVersion.LEGACY_ONLY)
        phone.holdIdentityKey(account)
        synced(phone.manager.fullSync())
        val travel = Journal(title = "Travel", created = later(0), lastUpdated = later(0))
        val family = Journal(title = "Family", created = later(0), lastUpdated = later(0))
        phone.journals.create(travel)
        phone.journals.create(family)
        val text = JournalNote.Text(creationTimestamp = later(1), lastUpdated = later(1), content = "Walked to the lake before work")
        val photoBytes = ByteArray(300_000) { (it % 199).toByte() }
        val photo =
            JournalNote.Image(
                creationTimestamp = later(2),
                lastUpdated = later(2),
                mediaRef = phone.media.saveMedia(MediaPayload("lake.jpg", "image/jpeg", photoBytes.size.toLong(), photoBytes)),
                caption = "The lake at dawn",
            )
        val audioBytes = ByteArray(120_000) { (it % 61).toByte() }
        val recording =
            JournalNote.Audio(
                mediaRef = phone.media.saveMedia(MediaPayload("swim.m4a", "audio/mp4", audioBytes.size.toLong(), audioBytes)),
                durationMs = 42_000,
                creationTimestamp = later(3),
                lastUpdated = later(3),
                transcript = TranscriptDocument.fromPlainText("Swam to the far buoy"),
            )
        listOf(text, photo, recording).forEach { phone.notes.create(it) }
        phone.content.addContentToJournal(text.uid, travel.id)
        phone.content.addContentToJournal(photo.uid, family.id)
        val draftPhoto = ByteArray(80_000) { (it % 83).toByte() }
        val draft =
            EditorDraft(
                blocks =
                    listOf(
                        SerializableTextBlock(Uuid.random(), later(4), 48.8566, 2.3522, 35.5, content = "Dear diary"),
                        SerializableImageBlock(
                            Uuid.random(),
                            later(4),
                            uri = phone.media.saveMedia(MediaPayload("draft.jpg", "image/jpeg", draftPhoto.size.toLong(), draftPhoto)),
                            caption = "Unfinished",
                        ),
                    ),
                selectedJournalIds = listOf(travel.id),
                createdAt = later(4),
                lastModifiedAt = later(4),
            )
        phone.journals.saveDraft(draft)
        val mark =
            MarkedMoment(
                id = Uuid.random(),
                capturedAt = later(5),
                timeZoneId = "America/Chicago",
                location = null,
                heartRateBpm = 88,
                state = MarkState.OPEN,
                caption = "Heron at the lake",
                resolvedNoteIds = emptySet(),
                originDeviceId = "watch-1",
                lastUpdated = later(5),
                syncVersion = 0,
            )
        phone.marks.applyFromSync(mark)
        phone.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
        val placeId = Uuid.random().toString()
        phone.history(
            account,
            placeId,
            "place",
            HistoryPayload.Place(SemanticPlace(placeId, "Grandma's house", 41.87, -87.62, userConfirmed = true)),
        )
        val correction = HistoryCorrection(Uuid.random().toString(), "place:$placeId", HistoryField.LABEL, "Grandma's")
        phone.history(account, correction.id, "correction", HistoryPayload.Correction(correction))
        val samples =
            (0 until 3).map { index ->
                LocationObservation(
                    id = "sample-$index",
                    ownerId = account.owner,
                    deviceId = phone.deviceId,
                    timestamp = midnight + 9.hours + (index * 10).minutes,
                    latitude = 41.8781 + index * 0.001,
                    longitude = -87.6297,
                    accuracyMeters = 12.5f,
                    speedMetersPerSecond = 1.5f,
                    bearingDegrees = 90f,
                    activity = TravelMode.WALKING,
                    timeZoneId = "America/Chicago",
                    source = "BACKGROUND_PERIODIC",
                )
            }
        samples.forEach { phone.history(account, "observation:${it.id}", "observation", HistoryPayload.Observation(it)) }
        synced(phone.manager.fullSync())
        synced(phone.manager.syncDrafts())
        return WrittenJournal(
            travel,
            family,
            text,
            photo,
            photoBytes,
            recording,
            olderPhoto,
            olderPhotoBytes,
            draft,
            draftPhoto,
            mark,
            placeId,
            samples.map { "observation:${it.id}" }.toSet(),
        )
    }

    private suspend fun MovingDevice.history(
        account: SpaceAccount,
        id: String,
        type: String,
        payload: HistoryPayload,
    ) = historyStore.put(
        account.owner,
        account.origin,
        HistoryRecord(id, type, json.encodeToString(HistoryPayload.serializer(), payload), deviceId),
    )

    // --- Moving and checking ---------------------------------------------------------------------

    /** Syncs [device] until it moved the journal; fails with what stopped it otherwise. */
    private suspend fun moveJournal(device: MovingDevice) {
        syncUntilSettled(device)
        val state = device.manager.journalStorageMove.state.value
        assertIs<JournalMoveState.Moved>(state, "The move didn't finish: $state")
    }

    /** Runs full syncs, each of which moves the journal once the device is ready, until the move moved or stopped. */
    private suspend fun syncUntilSettled(device: MovingDevice) {
        repeat(6) {
            device.manager.fullSync()
            when (device.manager.journalStorageMove.state.value) {
                is JournalMoveState.Moved, is JournalMoveState.Stopped, JournalMoveState.SwitchedBack -> return
                else -> Unit
            }
        }
    }

    private suspend fun written(device: MovingDevice) = device.notes.allNotesObserved.first()

    private fun synced(result: SyncResult) =
        assertTrue(result.success, result.errors.joinToString { it.cause?.stackTraceToString() ?: it.toString() })

    private fun later(minutes: Int) = midnight + minutes.minutes

    private suspend fun storageMode(account: SpaceAccount): AccountStorageMode? =
        GlobalContext
            .get()
            .get<AccountRepository>()
            .findById(Uuid.parse(account.owner))
            ?.storageMode

    private suspend fun migrationPhase(account: SpaceAccount): SpaceMigrationPhase =
        GlobalContext
            .get()
            .get<app.logdate.server.migration.SpaceMigrationStore>()
            .get(java.util.UUID.fromString(account.owner))
            ?.phase ?: SpaceMigrationPhase.NONE

    private suspend fun MoveScope.legacyCount(
        account: SpaceAccount,
        kind: String,
    ): Int {
        val body =
            harness.httpClient
                .get(
                    "${account.apiBaseUrl}/space-migration/legacy-inventory",
                ) { header(HttpHeaders.Authorization, "Bearer ${account.token}") }
                .bodyAsText()
        val inventory =
            json.decodeFromString(
                app.logdate.shared.model.sync.LegacyInventory
                    .serializer(),
                body,
            )
        return inventory.kinds.single { it.kind == kind }.count
    }

    private suspend fun MoveScope.spaceRecordCount(account: SpaceAccount): Int {
        val body =
            harness.httpClient
                .get(
                    "${account.origin}/xrpc/app.logdate.space.listRepoOps?space=at://${account.did}/space/app.logdate.space.personal/self&limit=500",
                ) {
                    header(HttpHeaders.Authorization, "Bearer ${account.token}")
                }.bodyAsText()
        return Regex("\"rkey\"").findAll(body).count()
    }

    /** The status and error code a legacy sync route answers the account with. */
    private suspend fun MoveScope.rawLegacy(
        account: SpaceAccount,
        path: String,
    ): Pair<Int, String?> {
        val response = harness.httpClient.get("${account.apiBaseUrl}$path") { header(HttpHeaders.Authorization, "Bearer ${account.token}") }
        val code =
            runCatching {
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["code"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
        return response.status.value to code
    }

    // --- Harness ---------------------------------------------------------------------------------

    private class MoveScope(
        val harness: ServerClientE2EHarness,
        val scope: kotlinx.coroutines.CoroutineScope,
        val descriptor: app.logdate.shared.model.ServerDescriptor,
    ) {
        val devices = mutableListOf<MovingDevice>()

        /** A new device of [account] running [version], which knows the server's descriptor as an app would after sign-in. */
        suspend fun device(
            account: SpaceAccount,
            version: AppVersion = AppVersion.CURRENT,
        ): MovingDevice =
            MovingDevice(account, scope, version).also {
                it.config.updateServerDescriptor(descriptor)
                devices += it
            }
    }

    private fun moveTest(block: suspend MoveScope.(SpaceAccount) -> Unit) =
        runTest(timeout = 5.minutes) {
            withServerClientHarness {
                val account = signUpForSpace()
                val accounts = GlobalContext.get().get<AccountRepository>()
                check(
                    accounts.updateStorageModeIfUnchanged(
                        Uuid.parse(account.owner),
                        AccountStorageMode.PERSONAL_SPACE_V1,
                        AccountStorageMode.LEGACY,
                    ),
                ) {
                    "The account was meant to start in its personal space"
                }
                val descriptor =
                    app.logdate.client.networking
                        .DefaultServerDiscoveryClient(httpClient)
                        .discoverServer(account.origin)
                        .getOrThrow()
                        .copy(serverOrigin = account.origin, apiBaseUrl = account.apiBaseUrl)
                val scope = MoveScope(this, backgroundScope, descriptor)
                try {
                    scope.block(account)
                } finally {
                    scope.devices.forEach { it.close() }
                }
            }
        }
}
