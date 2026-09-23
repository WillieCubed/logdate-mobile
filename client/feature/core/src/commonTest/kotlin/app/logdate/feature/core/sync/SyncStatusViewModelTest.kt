package app.logdate.feature.core.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
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
                QueuedGroup(EntityType.NOTE, count = 3, retrying = 2),
                QueuedGroup(EntityType.MEDIA, count = 1, retrying = 0),
            ),
            state.groups,
        )
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
    fun `a queue that cannot be read is flagged instead of shown as empty`() =
        runTest {
            val metadata = FakeMetadata(pendingUploads = flow { throw IllegalStateException("database closed") })
            val viewModel =
                SyncStatusViewModel(
                    syncManager = RecordingSyncManager(pendingUploads = 5),
                    syncMetadataService = metadata,
                    sessionStorage = FakeSessionStorage(UserSession("a", "r", "account")),
                )
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.queueUnavailable)
            assertEquals(5, viewModel.uiState.value.pendingCount)
        }

    private fun viewModel(
        syncManager: SyncManager,
        session: UserSession?,
    ) = SyncStatusViewModel(
        syncManager = syncManager,
        syncMetadataService = FakeMetadata(),
        sessionStorage = FakeSessionStorage(session),
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
        pendingUploads: Int = 0,
    ) : SyncManager {
        val syncRequests = mutableListOf<Boolean>()

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
        }

        override suspend fun uploadPendingChanges(): SyncResult = SyncResult(success = true)

        override suspend fun downloadRemoteChanges(): SyncResult = SyncResult(success = true)

        override suspend fun syncContent(): SyncResult = SyncResult(success = true)

        override suspend fun syncJournals(): SyncResult = SyncResult(success = true)

        override suspend fun syncAssociations(): SyncResult = SyncResult(success = true)

        override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

        override suspend fun fullSync(): SyncResult = SyncResult(success = true)

        override suspend fun getSyncStatus(): SyncStatus = syncStatusFlow.value

        override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = MutableStateFlow(emptyList())

        override suspend fun retryDeadLetter(id: String) {}

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
}
