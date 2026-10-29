package app.logdate.client.e2e

import android.util.Log
import java.io.Closeable
import java.util.Timer
import java.util.TimerTask

/** Bounded thread evidence; callers retain their original probe deadlines. */
internal class NativeDraftProbeTrace(
    private val probeThread: Thread,
    private val tag: String = TAG,
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
                                Log.i(tag, "thread=${thread.name} state=${thread.state}")
                                stack.take(64).forEach { Log.i(tag, it.toString()) }
                            }
                    }.onFailure { Log.w(tag, "Stack capture unavailable: ${it.javaClass.name}") }
                }
            },
            90_000L,
        )
    }

    fun reportFailure(failure: Throwable) {
        runCatching {
            Log.e(tag, "Probe body failed: ${failure.javaClass.name}")
            failure.stackTrace.take(64).forEach { Log.e(tag, it.toString()) }
        }
    }

    override fun close() {
        timer.cancel()
    }

    private companion object {
        const val TAG = "NativeCurrentDraftTrace"
    }
}
