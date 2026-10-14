package app.logdate.feature.timeline.ui.marks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

internal data class MarkedMomentEditingState(
    val caption: String,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

internal class MarkedMomentViewModel(
    private val mark: MarkedMoment,
    private val marks: MarkedMomentRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MarkedMomentEditingState(mark.caption))
    val state = mutableState.asStateFlow()

    private val writes = Mutex()
    private var resolved = false

    fun editCaption(caption: String) {
        if (mutableState.value.busy || resolved) return
        mutableState.update { it.copy(caption = caption, failed = false) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                writes.withLock {
                    if (!resolved) retainCaption()
                }
            }
        }
    }

    fun submit(
        action: suspend (String) -> Boolean,
        onSuccess: () -> Unit = {},
    ) {
        if (mutableState.value.busy || resolved) return
        mutableState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                val success =
                    writes.withLock {
                        if (!retainCaption()) return@withLock false
                        val accepted = action(mutableState.value.caption)
                        if (accepted) resolved = marks.getMark(mark.id)?.state != app.logdate.client.repository.moments.MarkState.OPEN
                        accepted
                    }
                mutableState.update { it.copy(failed = !success) }
                if (success) onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Napier.w("Could not complete marked moment action", failure)
                mutableState.update { it.copy(failed = true) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun retainCaption(): Boolean =
        try {
            // Once text is accepted, finish its small local transaction even if navigation disposes the screen.
            val retained =
                withContext(NonCancellable) {
                    marks.updateCaption(mark.id, mutableState.value.caption, Clock.System.now())
                }
            mutableState.update { it.copy(failed = !retained) }
            retained
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Napier.w("Could not retain marked moment caption", failure)
            mutableState.update { it.copy(failed = true) }
            false
        }
}
