package app.logdate.client.e2e

import android.util.Log
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Capture the unchanged probe's blocked phase without altering its deadline or outcome. */
internal class NativeTransferProbeTraceRule : TestWatcher() {
    private var trace: NativeDraftProbeTrace? = null

    override fun starting(description: Description) {
        trace = NativeDraftProbeTrace(Thread.currentThread(), TRANSFER_TRACE_TAG)
        nativeTransferPhase("probe_started")
    }

    fun bindCurrentProbeThread() {
        trace?.close()
        trace = NativeDraftProbeTrace(Thread.currentThread(), TRANSFER_TRACE_TAG)
        nativeTransferPhase("probe_body_thread")
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
    }
}

internal fun nativeTransferPhase(phase: String) {
    Log.i(TRANSFER_TRACE_TAG, phase)
}

private const val TRANSFER_TRACE_TAG = "NativeTransferProbeTrace"
