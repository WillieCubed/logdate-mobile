package app.logdate.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class WearHomeViewModel(
    notesRepository: JournalNotesRepository,
    private val syncManager: SyncManager,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    companion object {
        private const val SYNC_POLL_INTERVAL_MS = 30_000L
    }

    private val today: LocalDate
        get() =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

    private val _syncBadge = MutableStateFlow(SyncBadge.NONE)
    private var pollingJob: Job? = null

    val uiState =
        combine(
            notesRepository.observeNotesForDay(today).map { it.size },
            _syncBadge,
        ) { entryCount, syncBadge ->
            val timeOfDay = currentTimeOfDay()
            WearHomeUiState(
                greeting =
                    when (timeOfDay) {
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

    init {
        startSyncPolling()
    }

    fun startSyncPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob =
            viewModelScope.launch {
                while (isActive) {
                    refreshSyncBadge()
                    delay(SYNC_POLL_INTERVAL_MS)
                }
            }
    }

    fun stopSyncPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun refreshSyncBadge() {
        try {
            val connected = dataLayerClient.isPhoneConnected()
            val status = syncManager.getSyncStatus()

            _syncBadge.value =
                when {
                    status.hasErrors -> SyncBadge.ERROR
                    connected && status.pendingUploads > 0 -> SyncBadge.SYNCING
                    else -> SyncBadge.NONE
                }
        } catch (e: Exception) {
            Napier.w("Failed to refresh sync badge", e)
        }
    }

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
