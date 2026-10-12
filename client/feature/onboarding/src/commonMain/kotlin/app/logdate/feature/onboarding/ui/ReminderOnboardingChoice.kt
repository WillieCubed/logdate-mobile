package app.logdate.feature.onboarding.ui

import app.logdate.client.domain.reminders.ReminderPreferences
import kotlinx.coroutines.CancellationException

enum class ReminderOnboardingNext { REQUEST_PERMISSION, CONTINUE }

class ReminderOnboardingChoice(
    private val preferences: ReminderPreferences,
    private val markHandled: suspend () -> Result<Unit>,
) {
    suspend fun choose(
        enabled: Boolean,
        permissionHandled: Boolean,
    ): Result<ReminderOnboardingNext> =
        try {
            check(preferences.setEnabled(enabled)) { "The reminder choice could not be saved" }
            val next =
                if (enabled && !permissionHandled) {
                    ReminderOnboardingNext.REQUEST_PERMISSION
                } else {
                    markHandled().getOrThrow()
                    ReminderOnboardingNext.CONTINUE
                }
            Result.success(next)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
}
