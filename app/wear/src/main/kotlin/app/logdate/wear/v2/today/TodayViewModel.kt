package app.logdate.wear.v2.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.ObserveCampfireUseCase
import app.logdate.client.domain.today.TodaySummaryCalculator
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today on the watch: the day's moments, places and voice time, the campfire, and an offer to get
 * LogDate on a paired Android phone that lacks it. The day moves at midnight and when the time
 * zone changes.
 */
class TodayViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
    observeCampfire: ObserveCampfireUseCase,
    phoneLinks: PhoneLinkRepository,
    private val phoneHandoff: PhoneHandoff,
) : ViewModel() {
    private val mutableHandoff = MutableStateFlow(HandoffState.IDLE)

    val handoff: StateFlow<HandoffState> = mutableHandoff.asStateFlow()

    val uiState: StateFlow<TodayUiState> =
        combine(observeDay(dayClock, notesRepository), observeCampfire(), phoneLinks.observe()) { day, campfire, phone ->
            readyState(day, campfire, phone)
        }.catch { error ->
            Napier.e("Couldn't load today on the watch", error)
            emit(TodayUiState.Failed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState.Loading)

    /** Opens LogDate's Play listing on the phone Today offered it for. */
    fun getPhoneApp() {
        val card = (uiState.value as? TodayUiState.Ready)?.phoneCard as? PhoneCard.GetApp ?: return
        if (mutableHandoff.value == HandoffState.SENDING) return
        mutableHandoff.value = HandoffState.SENDING
        viewModelScope.launch {
            mutableHandoff.value =
                when (phoneHandoff.openAppListing(card.nodeId)) {
                    HandoffResult.SENT -> HandoffState.SENT
                    HandoffResult.FAILED -> HandoffState.FAILED
                }
        }
    }

    fun dismissHandoff() {
        mutableHandoff.value = HandoffState.IDLE
    }

    private data class Day(
        val window: TodayWindow,
        val notes: List<JournalNote>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDay(
        dayClock: DayClock,
        notesRepository: JournalNotesRepository,
    ): Flow<Day> =
        dayClock.observe().flatMapLatest { window ->
            notesRepository.observeNotesInRange(window.start, window.end).map { notes ->
                Day(window = window, notes = notes.filter { note -> note.creationTimestamp in window })
            }
        }

    private fun readyState(
        day: Day,
        campfire: CampfireState?,
        phone: PhoneLink,
    ): TodayUiState {
        val summary = TodaySummaryCalculator.calculate(day.notes) { tag -> Feeling.fromTag(tag) != null }
        return TodayUiState.Ready(
            date = day.window.date,
            timeZone = day.window.timeZone,
            summary = summary,
            feeling = summary.feelingTag?.let(Feeling::fromTag),
            campfire = campfire?.toPresentation(),
            phoneCard = if (phone is PhoneLink.AppMissing) PhoneCard.GetApp(phone.nodeId) else PhoneCard.None,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
