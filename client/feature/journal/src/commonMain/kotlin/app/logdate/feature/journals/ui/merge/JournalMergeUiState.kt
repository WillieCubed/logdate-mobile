package app.logdate.feature.journals.ui.merge

import app.logdate.client.repository.journals.JournalMergeCandidate
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview

sealed interface JournalMergeStage {
    data object Picker : JournalMergeStage

    data class Review(
        val preview: JournalMergePreview,
    ) : JournalMergeStage

    data class Merging(
        val preview: JournalMergePreview,
    ) : JournalMergeStage

    data class Complete(
        val operation: JournalMergeOperation,
    ) : JournalMergeStage
}

enum class JournalMergeError { Failed, Changed, Unavailable }

data class JournalMergeUiState(
    val sourceTitle: String = "",
    val query: String = "",
    val candidates: List<JournalMergeCandidate> = emptyList(),
    val stage: JournalMergeStage = JournalMergeStage.Picker,
    val loading: Boolean = true,
    val recovery: Boolean = false,
    val error: JournalMergeError? = null,
) {
    val visibleCandidates: List<JournalMergeCandidate>
        get() = candidates.filter { it.journal.title.contains(query.trim(), ignoreCase = true) }
}
