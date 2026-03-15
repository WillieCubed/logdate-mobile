package app.logdate.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Library screen.
 *
 * Observes all image and video notes and groups them by month for display in the grid.
 */
class LibraryViewModel(
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> =
        notesRepository
            .allNotesObserved
            .map { notes ->
                val mediaNotes =
                    notes.filter { it is JournalNote.Image || it is JournalNote.Video }
                if (mediaNotes.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Content(
                        groups = groupByMonth(mediaNotes),
                        totalCount = mediaNotes.size,
                    )
                }
            }.catch { e ->
                Napier.e("Failed to load library media", e)
                emit(LibraryUiState.Empty)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LibraryUiState.Loading,
            )

    private fun groupByMonth(notes: List<JournalNote>): List<LibraryGridGroup> {
        val timeZone = TimeZone.currentSystemDefault()
        val sorted = notes.sortedByDescending { it.creationTimestamp }
        val grouped =
            sorted.groupBy { note ->
                val localDate = note.creationTimestamp.toLocalDateTime(timeZone)
                "${localDate.year}-${localDate.month}"
            }
        return grouped.map { (_, items) ->
            val firstItem = items.first()
            val localDate = firstItem.creationTimestamp.toLocalDateTime(timeZone)
            val label = "${localDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${localDate.year}"
            LibraryGridGroup(
                label = label,
                items = items.map { it.toGridItem() },
            )
        }
    }

    private fun JournalNote.toGridItem(): LibraryMediaItem =
        LibraryMediaItem(
            uid = uid,
            uri =
                when (this) {
                    is JournalNote.Image -> mediaRef
                    is JournalNote.Video -> mediaRef
                    else -> ""
                },
            thumbnailUri = null,
            isVideo = this is JournalNote.Video,
            timestamp = creationTimestamp,
        )
}
