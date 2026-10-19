@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import androidx.room.Room
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.transcription.OfflineFirstTranscriptionRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.cloud.AssociationSyncResult
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.JournalConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.space.RoomSpaceSwapStore
import app.logdate.client.sync.space.SpaceItemChanges
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import app.logdate.client.sync.space.SpaceLocation
import app.logdate.client.sync.space.SpaceMediaDataSource
import app.logdate.client.sync.space.SpaceMediaResolver
import app.logdate.client.sync.space.SpacePart
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordRegistry
import app.logdate.client.sync.space.SpaceRoutingMediaDataSource
import app.logdate.client.sync.space.SpaceSyncDependencies
import app.logdate.client.sync.space.SpaceTransportGate
import app.logdate.client.sync.space.XrpcSpaceDataHomes
import app.logdate.client.sync.space.blobCidFor
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.mediaFor
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseUuidKey
import app.logdate.client.sync.space.recordRef
import app.logdate.client.sync.space.toDatetime
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.lexicon.app.logdate.DefsMedia
import app.logdate.lexicon.app.logdate.DefsRecordRef
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.Journal
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import studio.hypertext.atproto.lexicon.data.LexiconData
import java.nio.file.Files
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal const val JOURNAL = "app.logdate.journal.journal"
internal const val ENTRY = "app.logdate.journal.entry"
internal const val TRANSCRIPT = "app.logdate.journal.transcript"
internal val IDENTITY_KEY = ByteArray(32) { (it * 5).toByte() }
private val MEDIA_KEY = ByteArray(32) { (it * 11).toByte() }

internal class SpaceAccount(
    val owner: String,
    val did: String,
    val token: String,
    val refreshToken: String,
    val origin: String,
    val apiBaseUrl: String,
)

internal suspend fun ServerClientE2EHarness.signUpForSpace(): SpaceAccount {
    val created = apiClient.createAccountWithSyntheticPasskey("spacesync_${Random.nextInt(100000, 999999)}").data
    return SpaceAccount(
        owner = created.account.id.toString(),
        did = requireNotNull(created.account.did) { "The new account has no DID" },
        token = created.tokens.accessToken,
        refreshToken = created.tokens.refreshToken,
        origin = baseUrl.removeSuffix("/api/v1"),
        apiBaseUrl = baseUrl,
    )
}

/**
 * One device of [account]: its own Room database, outbox, inbox, media store and sync engine,
 * talking to the in-process server over HTTP.
 */
internal class SpaceDevice(
    account: SpaceAccount,
    scope: CoroutineScope,
) : AutoCloseable {
    private val file = Files.createTempFile("personal-space-device", ".db")
    val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = account.origin)
    private val ownerProvider = mockk<CanonicalOwnerProvider> { coEvery { getCanonicalOwnerId() } returns account.owner }
    val metadata = DatabaseSyncMetadataService(database.syncMetadataDao(), config, ownerProvider)
    private val transactions = RoomSyncTransactionManager(database)
    val inbox =
        DownloadInbox(
            database.downloadInboxDao(),
            transactions,
            { DownloadScope(account.owner, account.origin) },
            { System.currentTimeMillis() },
        )
    private val realJournals =
        OfflineFirstJournalRepository(
            journalDao = database.journalDao(),
            remoteDataSource = mockk(relaxed = true),
            draftRepository = mockk(relaxed = true),
            syncMetadataService = metadata,
            database = database,
            currentScope = { JournalMergeScope(account.owner, account.origin) },
            mergeTransactionManager = transactions,
            externalScope = scope,
        )

    /** Journals; applying one from the space fails while [failApplying] is set, as a full disk would. */
    val journals = FailingJournals(realJournals)
    private val transcripts =
        OfflineFirstTranscriptionRepository(
            database.transcriptionDao(),
            database.audioNoteDao(),
            mockk(relaxed = true),
            transactionManager = transactions,
        )
    val notes =
        OfflineFirstJournalNotesRepository(
            database.textNoteDao(),
            database.imageNoteDao(),
            database.audioNoteDao(),
            database.videoNoteDao(),
            database.journalContentDao(),
            realJournals,
            database.mediaCaptionDao(),
            transactionManager = transactions,
            syncMetadataService = metadata,
            transcriptionRepository = transcripts,
            syncScope = scope,
        )
    val media = FileReadingMediaManager()
    val mediaRefs = InMemoryMediaRefs()
    val conflicts = ListedConflicts()

    /** The sealing key this device holds; another one leaves the space's records unreadable here. */
    var identityKey: ByteArray = IDENTITY_KEY

    /** Answers every personal-space request with `429` and this `Retry-After`, in seconds, while set. */
    var refuseWithRetryAfter: Int? = null

    /** How many personal-space requests reached the server or were refused on the way. */
    var spaceRequests = 0
        private set

    private val http =
        HttpClient(OkHttp) {
            engine {
                addInterceptor(
                    Interceptor { chain ->
                        val request = chain.request()
                        if ("/xrpc/" in request.url.encodedPath) spaceRequests++
                        val wait = refuseWithRetryAfter
                        if (wait == null || "/xrpc/" !in request.url.encodedPath) return@Interceptor chain.proceed(request)
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(429)
                            .message("Too Many Requests")
                            .header("Retry-After", wait.toString())
                            .body("""{"error":"RateLimitExceeded","message":"Slow down"}""".toResponseBody())
                            .build()
                    },
                )
            }
            install(ContentNegotiation) { json() }
        }

    private val session = UserSession(account.token, account.refreshToken, account.owner)
    private val sessions =
        mockk<SessionStorage> {
            every { getSession() } returns session
            every { getSessionFlow() } returns flowOf(session)
            every { getOriginBoundSession() } returns OriginBoundSession(account.origin, session)
        }
    private val accounts =
        object : CloudAccountRepository by mockk(relaxed = true), CloudRequestLocationProvider {
            override fun captureLocation() = CloudRequestLocation(account.origin, account.apiBaseUrl)

            override fun isCurrentOrigin(origin: String) = origin == account.origin
        }
    private val homes = XrpcSpaceDataHomes(http)
    private val journalResolver = JournalConflictResolver()
    private val noteResolver = LastWriteWinsResolver<JournalNote>()

    val manager =
        DefaultSyncManager(
            cloudContentDataSource = mockk(relaxed = true),
            cloudJournalDataSource = mockk(relaxed = true),
            cloudAssociationDataSource = NoAssociations,
            cloudMediaDataSource =
                SpaceRoutingMediaDataSource(
                    legacy = mockk(relaxed = true),
                    space =
                        SpaceMediaDataSource(homes, AesGcmMediaPayloadCrypto(MEDIA_KEY), {
                            SpaceLocation(
                                account.origin,
                                app.logdate.space.SpaceRef
                                    .personal(account.did),
                            )
                        }),
                    uploadsToSpace = { true },
                ),
            cloudDraftDataSource = mockk(relaxed = true),
            cloudAccountRepository = accounts,
            sessionStorage = sessions,
            mediaManager = media,
            mediaSyncRefStore = mediaRefs,
            journalRepository = journals,
            journalNotesRepository = notes,
            journalContentRepository =
                mockk(relaxed = true) { every { observeJournalsForContents(any()) } returns flowOf(emptyMap()) },
            journalConflictResolver = journalResolver,
            noteConflictResolver = noteResolver,
            conflictStore = conflicts,
            deadLetterStore = mockk(relaxed = true),
            retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true) { coEvery { nextAttemptAt(any(), any()) } returns null },
            syncMetadataService = metadata,
            transactionManager = transactions,
            dataUsagePolicy = UnrestrictedData,
            downloadInbox = inbox,
            syncScope = scope,
            spaceSync =
                SpaceSyncDependencies(
                    registry = SpaceRecordRegistry(listOf(journalKind(journals, journalResolver), noteKind(notes, noteResolver))),
                    gate = SpaceTransportGate({ true }, { true }, { AccountStorageMode.PERSONAL_SPACE_V1 }),
                    homes = homes,
                    swapStore = RoomSpaceSwapStore(database.spaceRecordStateDao()),
                    accountDid = { account.did },
                    identityKey = { identityKey },
                ),
        )

    override fun close() {
        http.close()
        database.close()
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
        Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
    }
}

/** Unmetered, unrestricted data, so media syncs. */
private object UnrestrictedData : DataUsagePolicy {
    override val policy = flowOf(DataUsageMode.Unrestricted)

    override suspend fun currentMode() = DataUsageMode.Unrestricted

    override suspend fun currentRestriction() = DataRestriction.NONE
}

/**
 * Memberships stay on their own endpoints, which the server refuses to an account kept in a
 * personal space, and these accounts have none.
 */
private object NoAssociations : CloudAssociationDataSource {
    override suspend fun uploadAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ) = Result.success(Instant.fromEpochMilliseconds(0))

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: List<JournalContentAssociation>,
    ) = Result.success(Unit)

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ) = Result.success(AssociationSyncResult(emptyList(), emptyList(), Instant.fromEpochMilliseconds(0)))
}

/** Journals whose creation from the space fails while [failApplying] is set. */
internal class FailingJournals(
    private val delegate: OfflineFirstJournalRepository,
) : SyncableJournalRepository by delegate {
    var failApplying = false

    override suspend fun createFromSync(journal: Journal) {
        check(!failApplying) { "The disk is full" }
        delegate.createFromSync(journal)
    }
}

/** Which uploaded file each note's media is, kept in memory. */
internal class InMemoryMediaRefs : MediaSyncRefStore {
    private val refs = mutableMapOf<String, MediaSyncRef>()

    override suspend fun get(noteId: Uuid): MediaSyncRef? = refs[noteId.toString()]

    override suspend fun upsert(ref: MediaSyncRef) {
        refs[ref.noteId] = ref
    }

    override suspend fun delete(noteId: Uuid) {
        refs.remove(noteId.toString())
    }
}

/** The conflicts listed for review, kept in memory. */
internal class ListedConflicts : SyncConflictStore {
    val records = mutableListOf<SyncConflictRecord>()

    override suspend fun list(): List<SyncConflictRecord> = records.toList()

    override suspend fun add(record: SyncConflictRecord) {
        records.removeAll { it.id == record.id }
        records += record
    }

    override suspend fun remove(id: String) {
        records.removeAll { it.id == id }
    }

    override suspend fun clear() = records.clear()
}

/** Keeps saved media in memory, and reads a file handed to it from disk as the real stores do. */
internal class FileReadingMediaManager : InMemoryMediaManager() {
    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String {
        val data = SystemFileSystem.source(Path(sourceFilePath)).buffered().use { it.readByteArray() }
        return saveMedia(MediaPayload(fileName, mimeType, data.size.toLong(), data))
    }
}

/** A minimal journal mapping, written for these tests: title, description and timestamps. */
private object TestJournalMapper : SpaceRecordMapper<Journal> {
    override val collection = JOURNAL

    override fun rkeyOf(value: Journal) = value.id.toString()

    override fun toRecord(value: Journal): JsonObject =
        buildJsonObject {
            put("\$type", JOURNAL)
            put("title", value.title)
            if (value.description.isNotEmpty()) put("description", value.description)
            put("createdAt", value.created.toDatetime("$JOURNAL createdAt"))
            put("updatedAt", value.lastUpdated.toDatetime("$JOURNAL updatedAt"))
        }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ) = Journal(
        id = parseUuidKey(JOURNAL, rkey),
        title = record.getValue("title").jsonPrimitive.content,
        description = record["description"]?.jsonPrimitive?.content.orEmpty(),
        created = parseDatetime("$JOURNAL createdAt", record.getValue("createdAt").jsonPrimitive.content),
        lastUpdated = parseDatetime("$JOURNAL updatedAt", record.getValue("updatedAt").jsonPrimitive.content),
    )
}

private fun journalKind(
    journals: FailingJournals,
    resolver: ConflictResolver<Journal>,
) = SpaceKind.single(
    EntityType.JOURNAL,
    TestJournalMapper,
    object : SpaceLocalRecords<Journal> {
        override suspend fun load(entityId: String) = journals.getJournalById(Uuid.parse(entityId))

        override val conflictResolver = resolver

        override fun lastUpdatedOf(item: Journal) = item.lastUpdated

        override suspend fun acknowledgeUpload(
            item: Journal,
            seq: Long,
            syncedAt: Instant,
        ) = journals.updateSyncMetadata(item.id, seq, syncedAt)

        override suspend fun replaceLocal(item: Journal) {
            if (journals.getJournalById(item.id) == null) journals.createFromSync(item) else journals.updateFromSync(item)
        }
    },
) { journal, version -> journal.copy(syncVersion = version) }

/** A recording's transcript, with the note it belongs to. */
private data class NoteTranscript(
    val noteId: Uuid,
    val document: TranscriptDocument,
)

/** Text and audio notes as single-block entries, written for these tests. */
private object TestEntryMapper : SpaceRecordMapper<JournalNote> {
    override val collection = ENTRY

    override fun rkeyOf(value: JournalNote) = value.uid.toString()

    override fun toRecord(value: JournalNote): JsonObject {
        val owner = "$ENTRY/${value.uid}"
        val block =
            when (value) {
                is JournalNote.Text ->
                    buildJsonObject {
                        put("\$type", "$ENTRY#textBlock")
                        put("id", value.uid.toString())
                        put("text", value.content)
                    }
                is JournalNote.Audio ->
                    buildJsonObject {
                        put("\$type", "$ENTRY#audioBlock")
                        put("id", value.uid.toString())
                        put("media", encodeRecord(DefsMedia.serializer(), SpaceMediaResolver.mediaFor(value.mediaRef, owner)))
                        put("durationMs", value.durationMs)
                        if (value.transcript != null) {
                            put("transcript", encodeRecord(DefsRecordRef.serializer(), DefsRecordRef(TRANSCRIPT, transcriptKey(value.uid))))
                        }
                    }
                else -> error("These tests map only text and audio notes")
            }
        return buildJsonObject {
            put("\$type", ENTRY)
            put("blocks", buildJsonArray { add(block) })
            put("createdAt", value.creationTimestamp.toDatetime("$owner createdAt"))
            put("updatedAt", value.lastUpdated.toDatetime("$owner updatedAt"))
        }
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): JournalNote {
        val uid = parseUuidKey(ENTRY, rkey)
        val block = (record.getValue("blocks") as JsonArray).single() as JsonObject
        val created = parseDatetime("created", record.getValue("createdAt").jsonPrimitive.content)
        val updated = parseDatetime("updated", record.getValue("updatedAt").jsonPrimitive.content)
        return when ((block["\$type"] as JsonPrimitive).contentOrNull) {
            "$ENTRY#textBlock" ->
                JournalNote.Text(
                    uid = uid,
                    creationTimestamp = created,
                    lastUpdated = updated,
                    content = block.getValue("text").jsonPrimitive.content,
                )
            else ->
                JournalNote.Audio(
                    uid = uid,
                    creationTimestamp = created,
                    lastUpdated = updated,
                    mediaRef =
                        SpaceMediaResolver.downloaded(
                            uid,
                            LexiconData.json.decodeFromJsonElement(DefsMedia.serializer(), block.getValue("media")),
                        ),
                    durationMs = block.getValue("durationMs").jsonPrimitive.longOrNull ?: 0,
                )
        }
    }

    override fun blobCidsOf(value: JournalNote): List<String> =
        (value as? JournalNote.Audio)?.let { listOf(SpaceMediaResolver.blobCidFor(it.mediaRef, "$ENTRY/${value.uid}")) }.orEmpty()
}

internal fun transcriptKey(noteId: Uuid) = "$noteId:$noteId"

/** Transcripts with their revision, language and whether they're final, written for these tests. */
private object TestTranscriptMapper : SpaceRecordMapper<NoteTranscript> {
    override val collection = TRANSCRIPT

    override fun rkeyOf(value: NoteTranscript) = transcriptKey(value.noteId)

    override fun toRecord(value: NoteTranscript): JsonObject =
        buildJsonObject {
            put("\$type", TRANSCRIPT)
            put("entry", encodeRecord(DefsRecordRef.serializer(), recordRef(ENTRY, value.noteId)))
            put("blockId", value.noteId.toString())
            put("language", value.document.language)
            put("text", value.document.plainText)
            put("revision", value.document.revision.toLong())
            put("status", if (value.document.status == TranscriptDocumentStatus.FINAL) "complete" else "inProgress")
            put("createdAt", "2026-10-01T09:00:00Z")
        }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): NoteTranscript {
        val noteId = parseUuidKey(TRANSCRIPT, rkey.substringBefore(':'))
        val text = record["text"]?.jsonPrimitive?.content.orEmpty()
        val document = TranscriptDocument.fromPlainText(text)
        return NoteTranscript(
            noteId,
            document.copy(
                revision =
                    record
                        .getValue("revision")
                        .jsonPrimitive.longOrNull
                        ?.toInt() ?: 0,
                status =
                    if (record.getValue("status").jsonPrimitive.content ==
                        "complete"
                    ) {
                        TranscriptDocumentStatus.FINAL
                    } else {
                        TranscriptDocumentStatus.REFINING
                    },
                language = record["language"]?.jsonPrimitive?.content ?: "en-US",
            ),
        )
    }
}

private fun noteKind(
    notes: OfflineFirstJournalNotesRepository,
    resolver: ConflictResolver<JournalNote>,
): SpaceKind<JournalNote> {
    val entry = SpacePart.main(TestEntryMapper)
    val transcript =
        SpacePart<JournalNote, NoteTranscript>(
            mapper = TestTranscriptMapper,
            recordsOf = { note -> (note as? JournalNote.Audio)?.transcript?.let { listOf(NoteTranscript(note.uid, it)) }.orEmpty() },
            ownerOf = { rkey -> rkey.substringBefore(':') },
            rkeysOf = { id -> listOf("$id:$id") },
        )
    val syncable: SyncableJournalNotesRepository = notes
    val local =
        object : SpaceLocalRecords<JournalNote> {
            override suspend fun load(entityId: String) = notes.getNoteById(Uuid.parse(entityId))

            override val conflictResolver = resolver

            override fun lastUpdatedOf(item: JournalNote) = item.lastUpdated

            override suspend fun acknowledgeUpload(
                item: JournalNote,
                seq: Long,
                syncedAt: Instant,
            ) = syncable.updateSyncMetadata(item, seq, syncedAt)

            override suspend fun replaceLocal(item: JournalNote) {
                syncable.deleteFromSync(item.uid)
                syncable.createFromSync(item)
            }
        }
    return SpaceKind(
        entityType = EntityType.NOTE,
        main = entry,
        local = local,
        withSyncVersion = { note, version -> note.withSyncVersion(version) },
        others = listOf(transcript),
    ) { changes: SpaceItemChanges<JournalNote>, current ->
        val entryChange = changes.of(entry).lastOrNull() as? SpacePartChange.Written
        val base = entryChange?.value ?: current ?: return@SpaceKind null
        if (base !is JournalNote.Audio) return@SpaceKind base
        when (val change = changes.of(transcript).lastOrNull()) {
            is SpacePartChange.Written -> base.copy(transcript = change.value.document)
            is SpacePartChange.Deleted -> base.copy(transcript = null)
            null -> base.copy(transcript = (current as? JournalNote.Audio)?.transcript ?: base.transcript)
        }
    }
}

private fun JournalNote.withSyncVersion(version: Long): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(syncVersion = version)
        is JournalNote.Image -> copy(syncVersion = version)
        is JournalNote.Video -> copy(syncVersion = version)
        is JournalNote.Audio -> copy(syncVersion = version)
    }
