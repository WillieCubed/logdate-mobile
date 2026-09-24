package app.logdate.feature.core.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteType
import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.QueuedUpload
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `retrying a sync issue requests a backup immediately`() =
        runTest {
            val manager = RecordingSyncManager()
            val viewModel = SyncIssuesViewModel(manager, FakeJournalRepository(), FakeJournalNotesRepository())

            viewModel.retry("NOTE:entry-1")
            advanceUntilIdle()

            assertEquals(listOf("NOTE:entry-1"), manager.retriedIssues)
            assertEquals(listOf(true), manager.syncRequests)
            assertEquals(SyncIssueRetryFeedback.REQUESTED, viewModel.retryFeedback.value)
        }

    @Test
    fun `sync issues report when a retry cannot be scheduled`() =
        runTest {
            val manager = RecordingSyncManager(failOnSync = true)
            val viewModel = SyncIssuesViewModel(manager, FakeJournalRepository(), FakeJournalNotesRepository())

            viewModel.retry("NOTE:entry-1")
            advanceUntilIdle()

            assertEquals(SyncIssueRetryFeedback.COULD_NOT_START, viewModel.retryFeedback.value)
        }

    @Test
    fun `sync issues identify the local journal that failed`() =
        runTest {
            val journalId = Uuid.random()
            val record =
                SyncDeadLetterRecord(
                    id = "JOURNAL:$journalId",
                    entityType = "JOURNAL",
                    entityId = journalId.toString(),
                    operation = "CREATE",
                    retryCount = 9,
                    lastError = "Service Unavailable",
                    failedAt = 0L,
                )
            val manager = RecordingSyncManager(deadLetters = listOf(record))
            val viewModel =
                SyncIssuesViewModel(
                    manager,
                    FakeJournalRepository(journals = mapOf(journalId to Journal(id = journalId, title = "Travel"))),
                    FakeJournalNotesRepository(),
                )

            viewModel.labels.launchIn(backgroundScope)
            advanceUntilIdle()

            assertEquals("Travel", viewModel.labels.value[record.id])
        }

    @Test
    fun `the queue is grouped by kind with the largest group first`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 0),
                queue =
                    listOf(
                        queued(EntityType.MEDIA, "m1"),
                        queued(EntityType.NOTE, "n1", retryCount = 2),
                        queued(EntityType.NOTE, "n2"),
                        queued(EntityType.NOTE, "n3", retryCount = 1),
                    ),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertEquals(
            listOf(
                QueuedGroup(EntityType.NOTE, count = 3, retrying = 2, previewIds = listOf("n1", "n2", "n3")),
                QueuedGroup(EntityType.MEDIA, count = 1, retrying = 0),
            ),
            state.groups,
        )
    }

    @Test
    fun `a group past the preview limit only queues the first three ids for lookup`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 0),
                queue =
                    listOf(
                        queued(EntityType.NOTE, "n1"),
                        queued(EntityType.NOTE, "n2"),
                        queued(EntityType.NOTE, "n3"),
                        queued(EntityType.NOTE, "n4"),
                        queued(EntityType.NOTE, "n5"),
                    ),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertEquals(listOf("n1", "n2", "n3"), state.groups.single().previewIds)
    }

    @Test
    fun `a kind with no title lookup never queues preview ids`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 0),
                queue = listOf(queued(EntityType.MEDIA, "m1"), queued(EntityType.ASSOCIATION, "a1"), queued(EntityType.HEALTH, "h1")),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertTrue(state.groups.all { it.previewIds.isEmpty() })
    }

    @Test
    fun `the live queue decides the count and not the last status snapshot`() {
        // The status snapshot said 19; the queue has since drained to 2.
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 19),
                queue = listOf(queued(EntityType.NOTE, "n1"), queued(EntityType.NOTE, "n2")),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertEquals(2, state.pendingCount)
    }

    @Test
    fun `an unreadable queue falls back to the snapshot count and says so`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 19),
                queue = emptyList(),
                failedCount = 0,
                queueUnavailable = true,
            )

        assertEquals(19, state.pendingCount)
        assertTrue(state.queueUnavailable)
    }

    @Test
    fun `a failed snapshot count cannot show everything backed up`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 0).copy(queueReadable = false),
                queue = emptyList(),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertTrue(state.queueUnavailable)
    }

    @Test
    fun `an item of an unknown kind is still listed`() {
        val state =
            buildSyncStatusUiState(
                status = status(pendingUploads = 1),
                queue = listOf(queued(kind = null, "future-1")),
                failedCount = 0,
                queueUnavailable = false,
            )

        assertEquals(listOf(QueuedGroup(kind = null, count = 1, retrying = 0)), state.groups)
    }

    @Test
    fun `the paused reason and a failed attempt come through`() {
        val state =
            buildSyncStatusUiState(
                status =
                    status(
                        pendingUploads = 3,
                        pausedReason = SyncPausedReason.OFFLINE,
                        lastError = SyncError(SyncErrorType.NETWORK_ERROR, "offline"),
                    ),
                queue = emptyList(),
                failedCount = 4,
                queueUnavailable = false,
            )

        assertEquals(SyncPausedReason.OFFLINE, state.pausedReason)
        assertTrue(state.lastAttemptFailed)
        assertEquals(4, state.failedCount)
    }

    @Test
    fun `back up now without an account asks for one instead of queueing`() =
        runTest {
            val syncManager = RecordingSyncManager()
            val viewModel = viewModel(syncManager, session = null)

            viewModel.syncNow()

            assertEquals(SyncStatusFeedback.NeedsAccount, viewModel.feedback.value)
            assertEquals(emptyList(), syncManager.syncRequests)
        }

    @Test
    fun `back up now asks for an immediate run and says so`() =
        runTest {
            val syncManager = RecordingSyncManager()
            val viewModel = viewModel(syncManager, session = UserSession("a", "r", "account"))

            viewModel.syncNow()

            assertEquals(SyncStatusFeedback.Requested, viewModel.feedback.value)
            assertEquals(listOf(true), syncManager.syncRequests)
        }

    @Test
    fun `back up now reports a request that could not be made`() =
        runTest {
            val syncManager = RecordingSyncManager(failOnSync = true)
            val viewModel = viewModel(syncManager, session = UserSession("a", "r", "account"))

            viewModel.syncNow()

            assertEquals(SyncStatusFeedback.CouldNotStart, viewModel.feedback.value)
        }

    @Test
    fun `an already observed queued request does not leave stale confirmation feedback`() =
        runTest {
            val syncManager = RecordingSyncManager(queueOnSync = true)
            val viewModel = viewModel(syncManager, session = UserSession("a", "r", "account"))

            viewModel.syncNow()

            assertEquals(null, viewModel.feedback.value)
        }

    @Test
    fun `a queue that cannot be read is flagged instead of shown as empty`() =
        runTest {
            val metadata = FakeMetadata(pendingUploads = flow { throw IllegalStateException("database closed") })
            val viewModel =
                SyncStatusViewModel(
                    syncManager = RecordingSyncManager(pendingUploads = 5),
                    syncMetadataService = metadata,
                    sessionStorage = FakeSessionStorage(UserSession("a", "r", "account")),
                    journalRepository = FakeJournalRepository(),
                    journalNotesRepository = FakeJournalNotesRepository(),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.queueUnavailable)
            assertEquals(5, viewModel.uiState.value.pendingCount)
        }

    @Test
    fun `a queued note shows its content as a preview`() =
        runTest {
            val noteId = Uuid.random()
            val viewModel =
                statusViewModel(
                    queue = listOf(queued(EntityType.NOTE, noteId.toString())),
                    journalNotesRepository =
                        FakeJournalNotesRepository(
                            notes = mapOf(noteId to textNote(noteId, content = "Had a great day at the park")),
                        ),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertEquals(
                listOf(QueuedItemPreview(noteId.toString(), "Had a great day at the park", NoteType.TEXT)),
                group.previews,
            )
        }

    @Test
    fun `a queued journal shows its title as a preview`() =
        runTest {
            val journalId = Uuid.random()
            val viewModel =
                statusViewModel(
                    queue = listOf(queued(EntityType.JOURNAL, journalId.toString())),
                    journalRepository =
                        FakeJournalRepository(journals = mapOf(journalId to Journal(id = journalId, title = "Travel"))),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertEquals(listOf(QueuedItemPreview(journalId.toString(), "Travel")), group.previews)
        }

    @Test
    fun `a queued draft shows its text as a preview`() =
        runTest {
            val draftId = Uuid.random()
            val draft =
                EditorDraft(
                    id = draftId,
                    blocks =
                        listOf(
                            SerializableTextBlock(id = Uuid.random(), timestamp = Instant.fromEpochMilliseconds(0), content = "Draft text"),
                        ),
                )
            val viewModel =
                statusViewModel(
                    queue = listOf(queued(EntityType.DRAFT, draftId.toString())),
                    journalRepository = FakeJournalRepository(drafts = mapOf(draftId to draft)),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertEquals(listOf(QueuedItemPreview(draftId.toString(), "Draft text")), group.previews)
        }

    @Test
    fun `a queued voice note without a caption falls back to a generic label`() =
        runTest {
            val noteId = Uuid.random()
            val viewModel =
                statusViewModel(
                    queue = listOf(queued(EntityType.NOTE, noteId.toString())),
                    journalNotesRepository =
                        FakeJournalNotesRepository(
                            notes =
                                mapOf(
                                    noteId to
                                        JournalNote.Audio(
                                            mediaRef = "audio.m4a",
                                            uid = noteId,
                                            creationTimestamp = Instant.fromEpochMilliseconds(0),
                                            lastUpdated = Instant.fromEpochMilliseconds(0),
                                        ),
                                ),
                        ),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertEquals(listOf(QueuedItemPreview(noteId.toString(), null, NoteType.AUDIO)), group.previews)
        }

    @Test
    fun `a kind without a title stays a count-only row`() =
        runTest {
            val viewModel = statusViewModel(queue = listOf(queued(EntityType.MEDIA, "media-1")))
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertTrue(group.previewIds.isEmpty())
            assertTrue(group.previews.isEmpty())
        }

    @Test
    fun `a journal deleted locally after being queued falls back to a count-only row without crashing`() =
        runTest {
            val journalId = Uuid.random()
            // No journal in the repository -- it was deleted locally after this device queued it.
            val viewModel = statusViewModel(queue = listOf(queued(EntityType.JOURNAL, journalId.toString())))
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            val group =
                viewModel.uiState.value.groups
                    .single()
            assertEquals(1, group.count)
            assertTrue(group.previews.isEmpty())
        }

    private fun statusViewModel(
        queue: List<QueuedUpload>,
        journalRepository: JournalRepository = FakeJournalRepository(),
        journalNotesRepository: JournalNotesRepository = FakeJournalNotesRepository(),
    ) = SyncStatusViewModel(
        syncManager = RecordingSyncManager(),
        syncMetadataService = FakeMetadata(pendingUploads = MutableStateFlow(queue)),
        sessionStorage = FakeSessionStorage(UserSession("a", "r", "account")),
        journalRepository = journalRepository,
        journalNotesRepository = journalNotesRepository,
    )

    private fun textNote(
        id: Uuid,
        content: String,
    ) = JournalNote.Text(
        uid = id,
        creationTimestamp = Instant.fromEpochMilliseconds(0),
        lastUpdated = Instant.fromEpochMilliseconds(0),
        content = content,
    )

    private fun viewModel(
        syncManager: SyncManager,
        session: UserSession?,
    ) = SyncStatusViewModel(
        syncManager = syncManager,
        syncMetadataService = FakeMetadata(),
        sessionStorage = FakeSessionStorage(session),
        journalRepository = FakeJournalRepository(),
        journalNotesRepository = FakeJournalNotesRepository(),
    )

    private fun status(
        pendingUploads: Int,
        pausedReason: SyncPausedReason? = null,
        lastError: SyncError? = null,
    ) = SyncStatus(
        isEnabled = true,
        lastSyncTime = null,
        pendingUploads = pendingUploads,
        isSyncing = false,
        hasErrors = lastError != null,
        lastError = lastError,
        pausedReason = pausedReason,
    )

    private fun queued(
        kind: EntityType?,
        id: String,
        retryCount: Int = 0,
    ) = QueuedUpload(entityType = kind, entityId = id, operation = PendingOperation.CREATE, retryCount = retryCount)

    private class RecordingSyncManager(
        private val failOnSync: Boolean = false,
        private val queueOnSync: Boolean = false,
        pendingUploads: Int = 0,
        private val deadLetters: List<SyncDeadLetterRecord> = emptyList(),
    ) : SyncManager {
        val syncRequests = mutableListOf<Boolean>()
        val retriedIssues = mutableListOf<String>()

        override val syncStatusFlow: StateFlow<SyncStatus> =
            MutableStateFlow(
                SyncStatus(
                    isEnabled = true,
                    lastSyncTime = null,
                    pendingUploads = pendingUploads,
                    isSyncing = false,
                    hasErrors = false,
                ),
            )

        override fun sync(startNow: Boolean) {
            if (failOnSync) throw IllegalStateException("WorkManager unavailable")
            syncRequests += startNow
            if (queueOnSync) {
                (syncStatusFlow as MutableStateFlow<SyncStatus>).value =
                    syncStatusFlow.value.copy(requestState = BackupRequestState.QUEUED)
            }
        }

        override suspend fun uploadPendingChanges(): SyncResult = SyncResult(success = true)

        override suspend fun downloadRemoteChanges(): SyncResult = SyncResult(success = true)

        override suspend fun syncContent(): SyncResult = SyncResult(success = true)

        override suspend fun syncJournals(): SyncResult = SyncResult(success = true)

        override suspend fun syncAssociations(): SyncResult = SyncResult(success = true)

        override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

        override suspend fun fullSync(): SyncResult = SyncResult(success = true)

        override suspend fun getSyncStatus(): SyncStatus = syncStatusFlow.value

        override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = MutableStateFlow(deadLetters)

        override suspend fun retryDeadLetter(id: String) {
            retriedIssues += id
        }

        override suspend fun discardDeadLetter(id: String) {}
    }

    private class FakeMetadata(
        private val pendingUploads: Flow<List<QueuedUpload>> = MutableStateFlow(emptyList()),
    ) : SyncMetadataService {
        override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> = emptyList()

        override suspend fun markAsSynced(
            entityId: String,
            entityType: EntityType,
            syncedAt: Instant,
            version: Long,
        ) {}

        override suspend fun getLastSyncTime(entityType: EntityType): Instant? = null

        override suspend fun updateLastSyncTime(
            entityType: EntityType,
            syncedAt: Instant,
        ) {}

        override suspend fun enqueuePending(
            entityId: String,
            entityType: EntityType,
            operation: PendingOperation,
        ) {}

        override suspend fun resetSyncStatus(
            entityId: String,
            entityType: EntityType,
        ) {}

        override suspend fun getPendingCount(): Int = 0

        override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

        override fun observePendingUploads(): Flow<List<QueuedUpload>> = pendingUploads

        override suspend fun incrementRetryCount(
            entityId: String,
            entityType: EntityType,
        ) {}

        override suspend fun clearPending() {}

        override suspend fun resetAllCursors() {}
    }

    private class FakeSessionStorage(
        private val session: UserSession?,
    ) : SessionStorage {
        override fun getSession(): UserSession? = session

        override fun getSessionFlow(): Flow<UserSession?> = MutableStateFlow(session)

        override suspend fun hasValidSession(): Boolean = session != null

        override fun saveSession(session: UserSession) {}

        override fun clearSession() {}
    }

    private class FakeJournalRepository(
        private val journals: Map<Uuid, Journal> = emptyMap(),
        private val drafts: Map<Uuid, EditorDraft> = emptyMap(),
    ) : JournalRepository {
        override val allJournalsObserved: Flow<List<Journal>> = MutableStateFlow(journals.values.toList())

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(journals.getValue(id))

        override suspend fun getJournalById(id: Uuid): Journal? = journals[id]

        override suspend fun create(journal: Journal): Uuid = journal.id

        override suspend fun update(journal: Journal) {}

        override suspend fun delete(journalId: Uuid) {}

        override suspend fun saveDraft(draft: EditorDraft) {}

        override suspend fun getLatestDraft(): EditorDraft? = null

        override suspend fun getAllDrafts(): List<EditorDraft> = drafts.values.toList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = drafts[id]

        override suspend fun deleteDraft(id: Uuid) {}
    }

    private class FakeJournalNotesRepository(
        private val notes: Map<Uuid, JournalNote> = emptyMap(),
    ) : JournalNotesRepository {
        override val allNotesObserved: Flow<List<JournalNote>> = MutableStateFlow(notes.values.toList())

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes[noteId]

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) {}

        override suspend fun removeById(noteId: Uuid) {}

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {}

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}
    }
}
