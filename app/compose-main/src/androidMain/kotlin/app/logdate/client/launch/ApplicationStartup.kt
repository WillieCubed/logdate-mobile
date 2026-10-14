package app.logdate.client.launch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface ApplicationStartupState {
    data object Loading : ApplicationStartupState

    data object Ready : ApplicationStartupState

    data class Failed(
        val cause: Throwable,
    ) : ApplicationStartupState
}

/** Shares one off-main initialization attempt across activities and incoming watch work. */
internal class ApplicationStartup(
    private val scope: CoroutineScope,
    private val initialize: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow<ApplicationStartupState>(ApplicationStartupState.Loading)
    val state: StateFlow<ApplicationStartupState> = mutableState.asStateFlow()
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true || state.value is ApplicationStartupState.Ready) return
        mutableState.value = ApplicationStartupState.Loading
        job =
            scope
                .launch(start = CoroutineStart.LAZY) {
                    try {
                        initialize()
                        mutableState.value = ApplicationStartupState.Ready
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        mutableState.value = ApplicationStartupState.Failed(error)
                    }
                }.also { it.start() }
    }

    suspend fun awaitReady(): Boolean = state.first { it !is ApplicationStartupState.Loading } is ApplicationStartupState.Ready
}
