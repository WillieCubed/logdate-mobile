package app.logdate.client.launch

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
    private val startDeviceWork: (suspend () -> Unit)? = null,
    private val initialize: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow<ApplicationStartupState>(ApplicationStartupState.Loading)
    val state: StateFlow<ApplicationStartupState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var retryAfter: Job? = null

    @Synchronized
    fun start() {
        if (state.value is ApplicationStartupState.Ready) return
        val currentJob = job
        if (currentJob?.isActive == true) {
            if (state.value is ApplicationStartupState.Failed && retryAfter !== currentJob) {
                retryAfter = currentJob
                mutableState.value = ApplicationStartupState.Loading
                currentJob.invokeOnCompletion { retryWhenFinished(currentJob) }
            }
            return
        }
        mutableState.value = ApplicationStartupState.Loading
        val attempt =
            scope
                .launch(start = CoroutineStart.LAZY) {
                    try {
                        initialize()
                        mutableState.value = ApplicationStartupState.Ready
                        startDeviceWork?.let { start ->
                            // Device observers own installation work; their dependency graph must
                            // not hold validated local content behind optional service startup.
                            scope.launch {
                                try {
                                    start()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Napier.w("Device-owned startup work could not start; journal access remains available")
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        mutableState.value = ApplicationStartupState.Failed(error)
                    }
                }
        job = attempt
        attempt.start()
    }

    @Synchronized
    private fun retryWhenFinished(previous: Job) {
        if (retryAfter !== previous) return
        retryAfter = null
        if (job === previous && state.value !is ApplicationStartupState.Ready) start()
    }

    suspend fun awaitReady(): Boolean = state.first { it !is ApplicationStartupState.Loading } is ApplicationStartupState.Ready
}
