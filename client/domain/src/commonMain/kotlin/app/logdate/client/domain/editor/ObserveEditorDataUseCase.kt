package app.logdate.client.domain.editor

import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

/**
 * Composes the four reactive data sources needed by the editor into a single flow.
 */
class ObserveEditorDataUseCase(
    private val fetchTodayNotes: FetchTodayNotesUseCase,
    private val getCurrentUserJournals: GetCurrentUserJournalsUseCase,
    private val fetchMostRecentDraft: FetchMostRecentDraftUseCase,
    private val getAllDrafts: GetAllDraftsUseCase,
) {
    operator fun invoke(): Flow<EditorData> =
        combine(
            fetchTodayNotes()
                .catch { e ->
                    Napier.e("Failed to load entries: ${e.message}", e)
                    emit(emptyList())
                },
            getCurrentUserJournals()
                .catch { e ->
                    Napier.e("Failed to load journals: ${e.message}", e)
                    emit(emptyList())
                },
            fetchMostRecentDraft()
                .catch { e ->
                    Napier.e("Failed to load recent draft: ${e.message}", e)
                    emit(null)
                },
            getAllDrafts()
                .catch { e ->
                    Napier.e("Failed to load all drafts: ${e.message}", e)
                    emit(emptyList())
                },
        ) { todayNotes, journals, mostRecentDraft, allDrafts ->
            EditorData(
                todayNotes = todayNotes,
                journals = journals,
                mostRecentDraft = mostRecentDraft,
                allDrafts = allDrafts,
            )
        }
}

/**
 * Snapshot of the external data the editor observes.
 */
data class EditorData(
    val todayNotes: List<JournalNote>,
    val journals: List<Journal>,
    val mostRecentDraft: EntryDraft?,
    val allDrafts: List<EntryDraft>,
)
