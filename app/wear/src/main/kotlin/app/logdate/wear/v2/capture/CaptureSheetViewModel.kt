package app.logdate.wear.v2.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.FeelingSaver
import app.logdate.wear.capture.TextNoteSaver
import app.logdate.wear.sync.PhoneReachability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the last save from the + sheet went. */
enum class CaptureResult {
    NONE,
    SAVED,
    FAILED,
}

/**
 * What the + sheet shows.
 *
 * @property phoneInReach Whether a phone running LogDate is in reach, so its camera can be used.
 * @property choosingFeeling Whether the five feelings are open under Mood.
 * @property saving Whether a save is running; the sheet takes no other action meanwhile.
 */
data class CaptureSheetUiState(
    val phoneInReach: Boolean = false,
    val choosingFeeling: Boolean = false,
    val saving: Boolean = false,
    val result: CaptureResult = CaptureResult.NONE,
)

/** The + sheet: written text and feelings are saved here; the phone camera is its own screen. */
class CaptureSheetViewModel(
    private val textSaver: TextNoteSaver,
    private val feelingSaver: FeelingSaver,
    phoneReachability: PhoneReachability,
    private val haptics: LogDateHaptics,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureSheetUiState())

    val uiState: StateFlow<CaptureSheetUiState> =
        combine(mutableState, phoneInReach(phoneReachability)) { state, inReach -> state.copy(phoneInReach = inReach) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CaptureSheetUiState())

    fun saveText(text: String) {
        if (text.isBlank()) return
        save { textSaver.save(text) }
    }

    fun toggleFeelings() {
        mutableState.update { state -> state.copy(choosingFeeling = !state.choosingFeeling) }
    }

    fun saveFeeling(feeling: Feeling) {
        save { feelingSaver.save(feeling) }
    }

    fun dismissResult() {
        mutableState.update { state -> state.copy(result = CaptureResult.NONE) }
    }

    private fun save(block: suspend () -> Boolean) {
        if (mutableState.value.saving) return
        mutableState.update { state -> state.copy(saving = true, result = CaptureResult.NONE) }
        viewModelScope.launch {
            val saved = block()
            if (saved) haptics.saveSucceeded() else haptics.actionRejected()
            mutableState.update { state ->
                state.copy(saving = false, choosingFeeling = false, result = if (saved) CaptureResult.SAVED else CaptureResult.FAILED)
            }
        }
    }

    private fun phoneInReach(reachability: PhoneReachability): Flow<Boolean> =
        reachability
            .observe()
            .map { phone -> phone != null }
            .onStart { emit(false) }
            .catch { error ->
                Napier.e("Couldn't tell whether the phone is in reach; its camera is not offered", error)
                emit(false)
            }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
