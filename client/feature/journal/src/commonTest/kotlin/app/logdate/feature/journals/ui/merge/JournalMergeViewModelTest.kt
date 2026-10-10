package app.logdate.feature.journals.ui.merge

import androidx.lifecycle.SavedStateHandle
import app.logdate.client.domain.journals.MergeJournalsUseCase
import app.logdate.client.repository.journals.JournalMergeCandidate
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.feature.journals.ui.detail.FakeDetailJournalContentRepository
import app.logdate.feature.journals.ui.detail.FakeDetailJournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class JournalMergeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = Journal(title = "Travel")
    private val older = Journal(title = "Home", lastUpdated = Instant.parse("2026-10-01T00:00:00Z"))
    private val destination = Journal(title = "Travel archive", lastUpdated = Instant.parse("2026-10-09T00:00:00Z"))
    private val note = Uuid.random()
    private val preview = JournalMergePreview(source, destination, setOf(note), setOf(note, Uuid.random()))
    private lateinit var repository: MergeRepositoryFake

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = MergeRepositoryFake(preview)
        repository.candidates.value = listOf(source, older, destination).map { JournalMergeCandidate(it, 2) }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        JournalMergeViewModel(MergeJournalsUseCase(repository, FakeDetailJournalContentRepository(), MergeSyncManagerFake()), handle)

    @Test
    fun `picker excludes source sorts newest first searches title and returns from review with query intact`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.open(source.id)
            advanceUntilIdle()
            assertEquals(
                listOf(destination.id, older.id),
                vm.uiState.value.candidates
                    .map { it.journal.id },
            )
            vm.updateQuery("archive")
            assertEquals(
                listOf(destination.id),
                vm.uiState.value.visibleCandidates
                    .map { it.journal.id },
            )
            vm.choose(destination.id)
            advanceUntilIdle()
            val review = assertIs<JournalMergeStage.Review>(vm.uiState.value.stage)
            assertEquals(2, review.preview.combinedCount)
            assertEquals(1, review.preview.overlapCount)
            vm.changeDestination()
            assertIs<JournalMergeStage.Picker>(vm.uiState.value.stage)
            assertEquals("archive", vm.uiState.value.query)
            assertEquals(0, repository.mergeCalls)
        }

    @Test
    fun `double confirmation produces one operation and local failure keeps review`() =
        runTest(dispatcher) {
            repository.result = JournalMergeResult.Failed
            val vm = viewModel()
            vm.open(source.id)
            advanceUntilIdle()
            vm.choose(destination.id)
            advanceUntilIdle()
            vm.confirm()
            vm.confirm()
            advanceUntilIdle()
            assertEquals(1, repository.mergeCalls)
            assertIs<JournalMergeStage.Review>(vm.uiState.value.stage)
            assertEquals(JournalMergeError.Failed, vm.uiState.value.error)
        }

    @Test
    fun `changed review requires another explicit confirmation`() =
        runTest(dispatcher) {
            val changed = preview.copy(sourceContentIds = preview.sourceContentIds + Uuid.random())
            repository.result = JournalMergeResult.ReviewChanged(changed)
            val vm = viewModel()
            vm.open(source.id)
            advanceUntilIdle()
            vm.choose(destination.id)
            advanceUntilIdle()
            vm.confirm()
            advanceUntilIdle()
            val review = assertIs<JournalMergeStage.Review>(vm.uiState.value.stage)
            assertEquals(changed, review.preview)
            assertEquals(JournalMergeError.Changed, vm.uiState.value.error)
            assertEquals(1, repository.mergeCalls)
            repository.result = JournalMergeResult.Merged(repository.operation)
            vm.confirm()
            advanceUntilIdle()
            assertIs<JournalMergeStage.Complete>(vm.uiState.value.stage)
            assertEquals(2, repository.mergeCalls)
        }

    @Test
    fun `restores review and recognizes durable completed operation after process death`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            first.open(source.id)
            advanceUntilIdle()
            first.updateQuery("archive")
            first.choose(destination.id)
            advanceUntilIdle()
            val restored = viewModel(handle)
            restored.open(source.id)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Review>(restored.uiState.value.stage)
            assertEquals("archive", restored.uiState.value.query)
            first.confirm()
            advanceUntilIdle()
            repository.durable = repository.operation
            val completed = viewModel(handle)
            completed.open(source.id)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Complete>(completed.uiState.value.stage)
        }

    @Test
    fun `unavailable destination returns to picker without discarding search`() =
        runTest(dispatcher) {
            repository.result = JournalMergeResult.Unavailable
            val vm = viewModel()
            vm.open(source.id)
            advanceUntilIdle()
            vm.updateQuery("archive")
            vm.choose(destination.id)
            advanceUntilIdle()
            vm.confirm()
            advanceUntilIdle()
            assertIs<JournalMergeStage.Picker>(vm.uiState.value.stage)
            assertEquals(JournalMergeError.Unavailable, vm.uiState.value.error)
            assertEquals("archive", vm.uiState.value.query)
        }

    @Test
    fun `restart after retarget commit recognizes the new durable destination`() =
        runTest(dispatcher) {
            repository.durable = repository.operation.copy(destinationId = older.id)
            val handle = SavedStateHandle()
            val vm = viewModel(handle)
            vm.open(source.id, repository.operation.operationId)
            advanceUntilIdle()
            vm.choose(destination.id)
            advanceUntilIdle()
            vm.confirm()
            val processDeathSnapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            repository.durable = repository.operation
            val restored = viewModel(processDeathSnapshot)
            restored.open(source.id, repository.operation.operationId)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Complete>(restored.uiState.value.stage)
        }

    @Test
    fun `old recovery route recognizes an aliased retarget operation after process death`() =
        runTest(dispatcher) {
            val originalOperationId = repository.operation.operationId
            repository.durable = repository.operation.copy(operationId = Uuid.random())
            val restored = viewModel()
            restored.open(source.id, originalOperationId)
            advanceUntilIdle()
            val complete = assertIs<JournalMergeStage.Complete>(restored.uiState.value.stage)
            assertEquals(repository.durable, complete.operation)
            assertEquals(0, repository.retargetCalls)
        }

    @Test
    fun `aliased recovery remains a picker if its new destination also disappeared`() =
        runTest(dispatcher) {
            val originalOperationId = repository.operation.operationId
            repository.durable = repository.operation.copy(operationId = Uuid.random(), needsDestination = true)
            val restored = viewModel()
            restored.open(source.id, originalOperationId)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Picker>(restored.uiState.value.stage)
            assertEquals(source.title, restored.uiState.value.sourceTitle)
        }

    @Test
    fun `deleted destination recovery reviews pending source snapshot then retargets only on confirmation`() =
        runTest(dispatcher) {
            repository.durable = repository.operation
            val vm = viewModel()
            vm.open(source.id, repository.operation.operationId)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Picker>(vm.uiState.value.stage)
            assertEquals(source.title, vm.uiState.value.sourceTitle)
            vm.choose(destination.id)
            advanceUntilIdle()
            assertIs<JournalMergeStage.Review>(vm.uiState.value.stage)
            assertEquals(0, repository.retargetCalls)
            assertNull(vm.uiState.value.error)
            vm.confirm()
            advanceUntilIdle()
            assertEquals(1, repository.retargetCalls)
            assertIs<JournalMergeStage.Complete>(vm.uiState.value.stage)
        }

    private class MergeRepositoryFake(
        private val previewValue: JournalMergePreview,
    ) : JournalRepository by FakeDetailJournalRepository() {
        val candidates = MutableStateFlow(emptyList<JournalMergeCandidate>())
        var operation =
            JournalMergeOperation(
                Uuid.random(),
                previewValue.source.id,
                previewValue.destination.id,
                previewValue.sourceContentIds,
                JournalMergeScope("owner", "https://example.test"),
                previewValue.source.title,
                previewValue.destination.title,
                source = previewValue.source,
            )
        var result: JournalMergeResult = JournalMergeResult.Merged(operation)
        var durable: JournalMergeOperation? = null
        var mergeCalls = 0
        var retargetCalls = 0
        override val allJournalsObserved = candidates.map { values -> values.map { it.journal } }

        override suspend fun previewMerge(
            sourceId: Uuid,
            destinationId: Uuid,
        ) = previewValue

        override suspend fun previewPendingMerge(
            operationId: Uuid,
            destinationId: Uuid,
        ) = previewValue

        override suspend fun getJournalMerge(operationId: Uuid) = durable

        override suspend fun merge(
            preview: JournalMergePreview,
            operationId: Uuid,
        ): JournalMergeResult {
            mergeCalls++
            if (result is JournalMergeResult.Merged) {
                operation = operation.copy(operationId = operationId)
                return JournalMergeResult.Merged(operation)
            }
            return result
        }

        override suspend fun retargetPendingMerge(
            operationId: Uuid,
            preview: JournalMergePreview,
            replacementOperationId: Uuid,
        ): JournalMergeResult {
            retargetCalls++
            return result
        }

        override suspend fun resolveJournalId(journalId: Uuid) = journalId

        override suspend fun applyJournalRedirect(
            sourceId: Uuid,
            destinationId: Uuid,
            expectedScope: JournalMergeScope?,
        ) = Unit

        override suspend fun pendingJournalMerges() = listOfNotNull(durable)

        override suspend fun markJournalMergeSynced(operation: JournalMergeOperation) = Unit
    }
}

private class MergeSyncManagerFake : SyncManager {
    override val syncStatusFlow = MutableStateFlow(SyncStatus(false, null, 0, false, false))

    override fun sync(startNow: Boolean) = Unit

    override suspend fun uploadPendingChanges() = SyncResult(true)

    override suspend fun downloadRemoteChanges() = SyncResult(true)

    override suspend fun syncContent() = SyncResult(true)

    override suspend fun syncJournals() = SyncResult(true)

    override suspend fun syncAssociations() = SyncResult(true)

    override suspend fun syncDrafts() = SyncResult(true)

    override suspend fun fullSync() = SyncResult(true)

    override suspend fun getSyncStatus() = syncStatusFlow.value

    override fun observeDeadLetters() = flowOf(emptyList<app.logdate.client.sync.metadata.SyncDeadLetterRecord>())

    override suspend fun retryDeadLetter(id: String) = Unit

    override suspend fun discardDeadLetter(id: String) = Unit
}
