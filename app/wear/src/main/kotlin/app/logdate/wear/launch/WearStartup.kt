package app.logdate.wear.launch

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface WearStartupState {
    data object Loading : WearStartupState

    data object Ready : WearStartupState

    data class Failed(
        val cause: Throwable,
    ) : WearStartupState
}

internal class WearStartup(
    private val scope: CoroutineScope,
    private val openStorage: suspend () -> Unit,
    private val restoreRemoval: suspend () -> Unit,
    private val recoverRecording: suspend () -> Unit,
    private val startObservers: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow<WearStartupState>(WearStartupState.Loading)
    val state: StateFlow<WearStartupState> = mutableState.asStateFlow()
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true || state.value is WearStartupState.Ready) return
        mutableState.value = WearStartupState.Loading
        job =
            scope
                .launch(start = CoroutineStart.LAZY) {
                    try {
                        openStorage()
                        restoreRemoval()
                        mutableState.value = WearStartupState.Ready
                        scope.launch {
                            try {
                                recoverRecording()
                                startObservers()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Napier.e("Watch background startup failed; retained data remains available", error)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Napier.e("Watch startup failed; retry will reopen retained storage", error)
                        mutableState.value = WearStartupState.Failed(error)
                    }
                }.also { it.start() }
    }

    suspend fun awaitReady(): Boolean = state.first { it !is WearStartupState.Loading } is WearStartupState.Ready
}
