package app.logdate.client.domain.events

/**
 * Lets non-Android UI layers ask the platform to run an event inference pass right now.
 *
 * The actual scheduler lives in the Android app module because it talks to WorkManager,
 * which is Android-only. This interface keeps the auto-events settings screen and its
 * ViewModel free of any direct Android dependency — common code wires it up like any other
 * use case, and Koin binds the platform implementation on the Android side.
 *
 * On non-Android platforms, the default implementation is a no-op.
 */
interface EventInferenceLauncher {
    /** Schedules a one-shot inference run. Idempotent if a run is already in flight. */
    fun runNow()
}

/** Platform-agnostic no-op fallback used on iOS / desktop where on-device events aren't enabled. */
object NoopEventInferenceLauncher : EventInferenceLauncher {
    override fun runNow() = Unit
}
