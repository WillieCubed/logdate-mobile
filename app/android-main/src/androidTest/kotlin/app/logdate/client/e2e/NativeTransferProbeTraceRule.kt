package app.logdate.client.e2e

import android.util.Log
import app.logdate.client.datastore.LogDateConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.Koin

/** Capture the unchanged probe's blocked phase without altering its deadline or outcome. */
internal class NativeTransferProbeTraceRule : TestWatcher() {
    private var trace: NativeDraftProbeTrace? = null

    @Volatile private var runtimeScope: CoroutineScope? = null

    override fun starting(description: Description) {
        trace = createTrace()
        nativeTransferPhase("probe_started")
    }

    fun bindCurrentProbeThread() {
        trace?.close()
        trace = createTrace()
        nativeTransferPhase("probe_body_thread")
    }

    suspend fun awaitConfiguration(koin: Koin) {
        nativeTransferPhase("before_configuration_resolution")
        val configuration = koin.get<LogDateConfigDataSource>()
        nativeTransferPhase("configuration_resolved")
        runtimeScope = koin.get<CoroutineScope>()
        nativeTransferPhase("before_configuration_wait")
        configuration.awaitConfigurationLoaded()
        nativeTransferPhase("configuration_loaded")
    }

    private fun createTrace(): NativeDraftProbeTrace =
        NativeDraftProbeTrace(
            Thread.currentThread(),
            TRANSFER_TRACE_TAG,
            includeRuntimeWorkers = true,
            sampleRuntime = ::sampleScope,
        )

    private fun sampleScope() {
        val scope = runtimeScope ?: return
        val job = scope.coroutineContext[Job]
        Log.i(TRANSFER_TRACE_TAG, "configuration_scope_active=${scope.isActive} job_cancelled=${job?.isCancelled}")
        job?.children?.take(32)?.forEachIndexed { index, child ->
            Log.i(
                TRANSFER_TRACE_TAG,
                "scope_child=$index active=${child.isActive} completed=${child.isCompleted} cancelled=${child.isCancelled}",
            )
        }
    }

    override fun failed(
        failure: Throwable,
        description: Description,
    ) {
        trace?.reportFailure(failure)
    }

    override fun finished(description: Description) {
        trace?.close()
        trace = null
        runtimeScope = null
    }
}

internal fun nativeTransferPhase(phase: String) {
    Log.i(TRANSFER_TRACE_TAG, phase)
}

private const val TRANSFER_TRACE_TAG = "NativeTransferProbeTrace"
