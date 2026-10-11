package app.logdate.wear.v2.surfaces

import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Instant

internal class CountingRefresher : SurfaceRefresher {
    var count = 0

    override fun refreshAll() {
        count++
    }
}

internal class RecordingSwitch : ComponentSwitch {
    val states = mutableMapOf<String, Boolean>()

    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        states[className] = enabled
    }
}

/** A clock that reads the test scheduler's virtual time. */
internal class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
) : Clock {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
