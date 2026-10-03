package app.logdate.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class WearHomeViewModel(
    notesRepository: JournalNotesRepository,
    syncManager: SyncManager,
) : ViewModel() {
    private val today: LocalDate
        get() =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

    val uiState: StateFlow<WearHomeUiState> =
        combine(
            notesRepository.observeNotesForDay(today).map { it.size },
            syncManager.syncStatusFlow.map(::syncBadgeFor),
        ) { entryCount, syncBadge ->
            WearHomeUiState(
                greeting =
                    when (currentTimeOfDay()) {
                        TimeOfDay.MORNING -> "Good morning"
                        TimeOfDay.AFTERNOON -> "Good afternoon"
                        TimeOfDay.EVENING -> "Good evening"
                    },
                entryCount = entryCount,
                entryCountLabel =
                    when (entryCount) {
                        0 -> "No entries yet"
                        1 -> "1 entry today"
                        else -> "$entryCount entries today"
                    },
                syncBadge = syncBadge,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearHomeUiState(),
        )

    private fun currentTimeOfDay(): TimeOfDay {
        val hour =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .hour
        return when {
            hour < 12 -> TimeOfDay.MORNING
            hour < 17 -> TimeOfDay.AFTERNOON
            else -> TimeOfDay.EVENING
        }
    }
}

/** What the home screen says about sync: nothing while it is healthy. */
internal fun syncBadgeFor(status: SyncStatus): SyncBadge =
    when {
        status.hasErrors -> SyncBadge.ERROR
        status.isEnabled && status.pendingUploads > 0 -> SyncBadge.SYNCING
        else -> SyncBadge.NONE
    }

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
}

enum class SyncBadge {
    NONE,
    SYNCING,
    ERROR,
}

data class WearHomeUiState(
    val greeting: String = "",
    val entryCount: Int = 0,
    val entryCountLabel: String = "",
    val syncBadge: SyncBadge = SyncBadge.NONE,
)
