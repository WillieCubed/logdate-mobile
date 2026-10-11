package app.logdate.wear.v2.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** The memory cards on the moment list. Either can be missing. */
data class MemoryCards(
    val onThisDay: OnThisDay? = null,
    val rewind: WeeklyRewind? = null,
)

/** Memory cards follow the local date and changes to the notes they recall. */
class MemoriesViewModel(
    dayClock: DayClock,
    onThisDay: OnThisDayFinder,
    weeklyRewind: WeeklyRewindSource,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<MemoryCards> =
        dayClock
            .observe()
            .flatMapLatest { today ->
                combine(onThisDay.observe(today.date), weeklyRewind.observe(today)) { found, rewind ->
                    MemoryCards(onThisDay = found, rewind = rewind)
                }
            }.catch { error ->
                Napier.e("Couldn't load On this day and Rewind on the watch", error)
                emit(MemoryCards())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MemoryCards())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
