package app.logdate.client.events

import app.logdate.client.domain.events.EventInferenceLauncher

/**
 * Android implementation of [EventInferenceLauncher] that delegates to [EventInferenceScheduler].
 * Bound in the Android Koin module so the auto-events settings ViewModel can fire one-shot
 * runs without depending on WorkManager directly.
 */
class AndroidEventInferenceLauncher(
    private val scheduler: EventInferenceScheduler,
) : EventInferenceLauncher {
    override fun runNow() {
        scheduler.enqueueImmediateRun()
    }
}
