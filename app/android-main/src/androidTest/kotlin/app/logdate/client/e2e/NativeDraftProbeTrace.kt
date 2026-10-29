package app.logdate.client.e2e

import android.util.Log
import java.io.Closeable
import java.util.Timer
import java.util.TimerTask

/** Bounded stack evidence before the host's unchanged 180-second probe deadline. */
internal class NativeDraftProbeTrace(
    private val probeThread: Thread,
) : Closeable {
    private val timer = Timer("native-draft-probe-trace", true)

    init {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    runCatching {
                        Thread
                            .getAllStackTraces()
                            .entries
                            .filter { (thread, _) -> thread === probeThread || thread.name == "main" }
                            .sortedBy { (thread, _) -> thread.name }
                            .forEach { (thread, stack) ->
                                Log.i(TAG, "thread=${thread.name} state=${thread.state}")
                                stack.take(64).forEach { Log.i(TAG, it.toString()) }
                            }
                    }.onFailure { Log.w(TAG, "Stack capture unavailable: ${it.javaClass.name}") }
                }
            },
            90_000L,
        )
    }

    fun reportFailure(failure: Throwable) {
        runCatching {
            Log.e(TAG, "Probe body failed before activity teardown: ${failure.javaClass.name}")
            failure.stackTrace.take(64).forEach { Log.e(TAG, it.toString()) }
        }
    }

    override fun close() {
        timer.cancel()
    }

    private companion object {
        const val TAG = "NativeCurrentDraftTrace"
    }
}
