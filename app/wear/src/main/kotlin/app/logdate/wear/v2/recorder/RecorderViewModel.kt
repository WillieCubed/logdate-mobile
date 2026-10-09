package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the watch is recording, as the screens that react to it see it. */
sealed interface RecorderState {
    data object Ready : RecorderState

    data object Recording : RecorderState
}

/** Whether Home has to stay on Today, where Stop is. */
val RecorderState.locksPaging: Boolean
    get() = this is RecorderState.Recording

/**
 * The recorder the whole watch app shares. It belongs to the activity, so a recording outlives the
 * screen that started it. Recording itself arrives with the record gesture; until then the state
 * is [RecorderState.Ready].
 */
class RecorderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Ready)

    val state: StateFlow<RecorderState> = mutableState.asStateFlow()
}
