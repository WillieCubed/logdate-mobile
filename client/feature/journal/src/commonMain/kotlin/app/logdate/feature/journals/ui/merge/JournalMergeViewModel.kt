package app.logdate.feature.journals.ui.merge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.journals.MergeJournalsUseCase
import app.logdate.client.repository.journals.JournalMergeResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class JournalMergeViewModel(
    private val mergeJournals: MergeJournalsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(JournalMergeUiState(query = savedStateHandle[QUERY] ?: ""))
    val uiState: StateFlow<JournalMergeUiState> = mutableState.asStateFlow()
    private var sourceId: Uuid? = null
    private var recoveryId: Uuid? = null
    private var previewJob: Job? = null

    fun open(
        sourceId: Uuid,
        pendingOperationId: Uuid? = null,
    ) {
        if (this.sourceId == sourceId && recoveryId == pendingOperationId) return
        this.sourceId = sourceId
        recoveryId = pendingOperationId
        savedStateHandle[SOURCE] = sourceId.toString()
        savedStateHandle[RECOVERY] = pendingOperationId?.toString()
        mutableState.update { it.copy(recovery = pendingOperationId != null, loading = true) }
        viewModelScope.launch {
            try {
                val operationId = savedStateHandle.get<String>(OPERATION)?.let(Uuid::parse)
                val operation = (pendingOperationId ?: operationId)?.let { mergeJournals.operation(it) }
                val restoredDestination = savedStateHandle.get<String>(DESTINATION)
                val recoveredCommit =
                    pendingOperationId != null &&
                        operation != null &&
                        !operation.needsDestination &&
                        (
                            operation.operationId != pendingOperationId ||
                                savedStateHandle.get<String>(STAGE) == COMPLETE ||
                                savedStateHandle.get<String>(STAGE) == MERGING &&
                                operation.destinationId.toString() == restoredDestination
                        )
                if (operation != null && (pendingOperationId == null || recoveredCommit)) {
                    mutableState.update { it.copy(stage = JournalMergeStage.Complete(operation), loading = false) }
                    return@launch
                }
                operation?.let { pending ->
                    if (pendingOperationId != null && pending.needsDestination) {
                        recoveryId = pending.operationId
                        savedStateHandle[RECOVERY] = pending.operationId.toString()
                        savedStateHandle[STAGE] = PICKER
                        savedStateHandle[DESTINATION] = null
                    }
                    mutableState.update { it.copy(sourceTitle = pending.sourceTitle) }
                }
                val destinationId = savedStateHandle.get<String>(DESTINATION)?.let(Uuid::parse)
                if (destinationId != null && savedStateHandle.get<String>(STAGE) != PICKER) choose(destinationId)
                mergeJournals.observeCandidates().collect { candidates ->
                    mutableState.update { state ->
                        state.copy(
                            sourceTitle = candidates.firstOrNull { it.journal.id == sourceId }?.journal?.title ?: state.sourceTitle,
                            candidates = candidates.filterNot { it.journal.id == sourceId }.sortedByDescending { it.journal.lastUpdated },
                            loading = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("Could not load journals for merge", error)
                mutableState.update { it.copy(loading = false, error = JournalMergeError.Failed) }
            }
        }
    }

    fun updateQuery(query: String) {
        savedStateHandle[QUERY] = query
        mutableState.update { it.copy(query = query) }
    }

    fun choose(destinationId: Uuid) {
        val sourceId = sourceId ?: return
        if (mutableState.value.stage is JournalMergeStage.Merging) return
        previewJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        previewJob =
            viewModelScope.launch {
                try {
                    val preview =
                        recoveryId?.let { mergeJournals.previewPending(it, destinationId) }
                            ?: if (recoveryId == null) mergeJournals.preview(sourceId, destinationId) else null
                    if (preview == null) {
                        mutableState.update { it.copy(loading = false, error = JournalMergeError.Unavailable) }
                    } else {
                        savedStateHandle[DESTINATION] = destinationId.toString()
                        savedStateHandle[STAGE] = REVIEW
                        mutableState.update {
                            it.copy(
                                sourceTitle = preview.source.title,
                                stage = JournalMergeStage.Review(preview),
                                loading = false,
                                error = null,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Napier.e("Could not preview journal merge", error)
                    mutableState.update { it.copy(loading = false, error = JournalMergeError.Failed) }
                }
            }
    }

    fun changeDestination() {
        if (mutableState.value.stage is JournalMergeStage.Merging) return
        previewJob?.cancel()
        savedStateHandle[STAGE] = PICKER
        savedStateHandle[DESTINATION] = null
        mutableState.update { it.copy(stage = JournalMergeStage.Picker, error = null, loading = false) }
    }

    fun confirm() {
        val review = mutableState.value.stage as? JournalMergeStage.Review ?: return
        val operationId = recoveryId ?: savedStateHandle.get<String>(OPERATION)?.let(Uuid::parse) ?: Uuid.random()
        savedStateHandle[OPERATION] = operationId.toString()
        savedStateHandle[STAGE] = MERGING
        mutableState.update { it.copy(stage = JournalMergeStage.Merging(review.preview), error = null) }
        viewModelScope.launch {
            val result =
                try {
                    recoveryId?.let { mergeJournals.retarget(it, review.preview) } ?: mergeJournals(review.preview, operationId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Napier.e("Could not merge journals", error)
                    JournalMergeResult.Failed
                }
            savedStateHandle[STAGE] = REVIEW
            when (result) {
                is JournalMergeResult.Merged -> {
                    savedStateHandle[STAGE] = COMPLETE
                    savedStateHandle[OPERATION] = result.operation.operationId.toString()
                    savedStateHandle[DESTINATION] = result.operation.destinationId.toString()
                    mutableState.update { it.copy(stage = JournalMergeStage.Complete(result.operation)) }
                }
                is JournalMergeResult.ReviewChanged ->
                    mutableState.update {
                        it.copy(stage = JournalMergeStage.Review(result.preview), error = JournalMergeError.Changed)
                    }
                JournalMergeResult.Failed ->
                    mutableState.update {
                        it.copy(stage = review, error = JournalMergeError.Failed)
                    }
                JournalMergeResult.Unavailable -> {
                    savedStateHandle[STAGE] = PICKER
                    savedStateHandle[DESTINATION] = null
                    mutableState.update { it.copy(stage = JournalMergeStage.Picker, error = JournalMergeError.Unavailable) }
                }
            }
        }
    }

    private companion object {
        const val SOURCE = "journal_merge_source"
        const val RECOVERY = "journal_merge_recovery"
        const val QUERY = "journal_merge_query"
        const val DESTINATION = "journal_merge_destination"
        const val OPERATION = "journal_merge_operation"
        const val STAGE = "journal_merge_stage"
        const val PICKER = "picker"
        const val REVIEW = "review"
        const val MERGING = "merging"
        const val COMPLETE = "complete"
    }
}
